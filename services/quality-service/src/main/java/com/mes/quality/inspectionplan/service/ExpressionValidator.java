package com.mes.quality.inspectionplan.service;

import com.mes.quality.inspectionplan.domain.CharacteristicType;
import com.mes.quality.service.QualityValidationException;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Validates CALCULATED-characteristic expressions over a restricted grammar (research R1):
 *
 * <pre>
 * expr   := term (('+'|'-') term)*
 * term   := factor (('*'|'/') factor)*
 * factor := NUMBER | REF | TAG | '(' expr ')'
 * REF    := 'C' characteristicNumber          e.g. C10
 * TAG    := '#{' tagName '}'                   e.g. #{furnace1.temp}  (format-validated only, v1)
 * </pre>
 *
 * Hand-rolled recursive-descent — no scripting engine, zero code-execution surface (§VII).
 * This epic validates structure only; numeric evaluation is an execution-time concern.
 */
public final class ExpressionValidator {

    private static final Pattern TOKEN = Pattern.compile(
            "\\s*(?:"
                    + "(?<number>\\d+(?:\\.\\d+)?)"
                    + "|(?<ref>C\\d+)"
                    + "|(?<tag>#\\{[A-Za-z0-9_.\\-]+\\})"
                    + "|(?<op>[+\\-*/()])"
                    + ")\\s*");

    private ExpressionValidator() {
    }

    /**
     * Parses {@code expression}, validating syntax, and returns the set of referenced
     * characteristic numbers (the integer after each {@code C}). Throws
     * {@link QualityValidationException} (422) on any syntax error.
     */
    public static Set<Integer> parseReferences(String expression) {
        if (expression == null || expression.isBlank()) {
            throw new QualityValidationException("expression must not be blank");
        }
        List<Token> tokens = tokenize(expression);
        Parser parser = new Parser(tokens, expression);
        parser.expr();
        parser.expectEnd();
        return parser.references;
    }

    /**
     * Full validation of one CALCULATED characteristic's expression against its peers in the
     * same revision: syntax, every reference resolves to a SPECIFIC/CALCULATED peer (not self,
     * not COMMON), and the dependency graph stays acyclic. {@code typesByNumber} and
     * {@code calcExpressionsByNumber} describe the revision <em>including</em> this characteristic
     * with its proposed type/expression. Throws {@link QualityValidationException} with a details
     * list enumerating every problem found.
     */
    public static void validate(int characteristicNumber,
                                String expression,
                                Map<Integer, CharacteristicType> typesByNumber,
                                Map<Integer, String> calcExpressionsByNumber) {
        List<String> problems = new ArrayList<>();
        Set<Integer> refs;
        try {
            refs = parseReferences(expression);
        } catch (QualityValidationException ex) {
            throw new QualityValidationException(
                    "Invalid expression for C" + characteristicNumber, List.of(ex.getMessage()));
        }

        for (int ref : refs) {
            if (ref == characteristicNumber) {
                problems.add("C" + characteristicNumber + " references itself");
                continue;
            }
            CharacteristicType refType = typesByNumber.get(ref);
            if (refType == null) {
                problems.add("C" + ref + " does not exist in this revision");
            } else if (refType == CharacteristicType.COMMON) {
                problems.add("C" + ref + " is a COMMON (boolean) characteristic and cannot be referenced");
            }
        }

        if (problems.isEmpty()) {
            detectCycle(characteristicNumber, calcExpressionsByNumber, problems);
        }

        if (!problems.isEmpty()) {
            throw new QualityValidationException(
                    "Invalid expression for C" + characteristicNumber, problems);
        }
    }

    private static void detectCycle(int start,
                                    Map<Integer, String> calcExpressionsByNumber,
                                    List<String> problems) {
        Set<Integer> visiting = new HashSet<>();
        Set<Integer> done = new HashSet<>();
        Deque<Integer> path = new ArrayDeque<>();
        if (hasCycle(start, calcExpressionsByNumber, visiting, done, path)) {
            problems.add("Expression creates a dependency cycle: " + describePath(path, start));
        }
    }

    private static boolean hasCycle(int node,
                                    Map<Integer, String> calcExpressionsByNumber,
                                    Set<Integer> visiting,
                                    Set<Integer> done,
                                    Deque<Integer> path) {
        if (visiting.contains(node)) {
            return true;
        }
        if (done.contains(node)) {
            return false;
        }
        String expr = calcExpressionsByNumber.get(node);
        if (expr == null) {
            done.add(node);
            return false;
        }
        visiting.add(node);
        path.addLast(node);
        for (int ref : safeRefs(expr)) {
            if (hasCycle(ref, calcExpressionsByNumber, visiting, done, path)) {
                return true;
            }
        }
        path.removeLast();
        visiting.remove(node);
        done.add(node);
        return false;
    }

    /** True if {@code expression} references the given characteristic number (lenient on syntax). */
    public static boolean references(String expression, int characteristicNumber) {
        return expression != null && safeRefs(expression).contains(characteristicNumber);
    }

    private static Set<Integer> safeRefs(String expression) {
        try {
            return parseReferences(expression);
        } catch (QualityValidationException ex) {
            return Set.of();
        }
    }

    private static String describePath(Deque<Integer> path, int start) {
        StringBuilder sb = new StringBuilder();
        for (int n : path) {
            sb.append('C').append(n).append(" -> ");
        }
        sb.append('C').append(start);
        return sb.toString();
    }

    private static List<Token> tokenize(String expression) {
        List<Token> tokens = new ArrayList<>();
        Matcher m = TOKEN.matcher(expression);
        int pos = 0;
        while (pos < expression.length()) {
            m.region(pos, expression.length());
            if (!m.lookingAt()) {
                throw new QualityValidationException(
                        "Unexpected character at position " + pos + " in: " + expression);
            }
            if (m.group("number") != null) {
                tokens.add(new Token(TokenType.NUMBER, m.group("number")));
            } else if (m.group("ref") != null) {
                tokens.add(new Token(TokenType.REF, m.group("ref")));
            } else if (m.group("tag") != null) {
                tokens.add(new Token(TokenType.TAG, m.group("tag")));
            } else {
                tokens.add(new Token(TokenType.OP, m.group("op")));
            }
            pos = m.end();
        }
        return tokens;
    }

    private enum TokenType { NUMBER, REF, TAG, OP }

    private record Token(TokenType type, String text) {
    }

    /** Recursive-descent parser over the token list; collects C-references. */
    private static final class Parser {
        private final List<Token> tokens;
        private final String source;
        private final Set<Integer> references = new LinkedHashSet<>();
        private int index;

        Parser(List<Token> tokens, String source) {
            this.tokens = tokens;
            this.source = source;
        }

        void expr() {
            term();
            while (isOp("+") || isOp("-")) {
                index++;
                term();
            }
        }

        void term() {
            factor();
            while (isOp("*") || isOp("/")) {
                index++;
                factor();
            }
        }

        void factor() {
            Token t = peek();
            if (t == null) {
                throw new QualityValidationException("Unexpected end of expression: " + source);
            }
            switch (t.type()) {
                case NUMBER, TAG -> index++;
                case REF -> {
                    references.add(Integer.parseInt(t.text().substring(1)));
                    index++;
                }
                case OP -> {
                    if (!"(".equals(t.text())) {
                        throw new QualityValidationException(
                                "Unexpected token '" + t.text() + "' in: " + source);
                    }
                    index++;
                    expr();
                    if (!isOp(")")) {
                        throw new QualityValidationException("Unbalanced parentheses in: " + source);
                    }
                    index++;
                }
                default -> throw new QualityValidationException("Unexpected token in: " + source);
            }
        }

        void expectEnd() {
            if (index != tokens.size()) {
                throw new QualityValidationException(
                        "Trailing tokens after valid expression: " + source);
            }
        }

        private Token peek() {
            return index < tokens.size() ? tokens.get(index) : null;
        }

        private boolean isOp(String op) {
            Token t = peek();
            return t != null && t.type() == TokenType.OP && t.text().equals(op);
        }
    }
}
