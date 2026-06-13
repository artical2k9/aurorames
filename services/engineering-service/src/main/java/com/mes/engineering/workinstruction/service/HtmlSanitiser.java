package com.mes.engineering.workinstruction.service;

import org.owasp.html.HtmlPolicyBuilder;
import org.owasp.html.PolicyFactory;
import org.owasp.html.Sanitizers;

/**
 * Sanitises step body HTML before persistence. Allows the formatting a rich-text editor
 * produces (headings, bold/italic, lists, tables, links, images) and strips everything else —
 * notably {@code <script>}, event handlers, and {@code javascript:} URLs. Defence against
 * stored XSS in controlled documents (NIST 800-171, AS9100D §7.5 document integrity).
 */
public final class HtmlSanitiser {

    private static final PolicyFactory POLICY = Sanitizers.FORMATTING
            .and(Sanitizers.BLOCKS)
            .and(Sanitizers.TABLES)
            .and(Sanitizers.IMAGES)
            .and(Sanitizers.LINKS)
            .and(new HtmlPolicyBuilder()
                    .allowElements("ul", "ol", "li", "h1", "h2", "h3", "h4", "pre", "hr", "span")
                    .toFactory());

    private HtmlSanitiser() {
    }

    /** Returns sanitised HTML; null in → null out (an empty body is permitted). */
    public static String sanitise(String rawHtml) {
        if (rawHtml == null) {
            return null;
        }
        return POLICY.sanitize(rawHtml);
    }
}
