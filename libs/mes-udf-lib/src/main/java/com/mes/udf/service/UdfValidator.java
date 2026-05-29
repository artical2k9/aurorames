package com.mes.udf.service;

import com.mes.udf.domain.ModuleKey;
import com.mes.udf.domain.UdfFieldDefinition;
import com.mes.udf.domain.UdfFieldType;
import com.mes.udf.repository.UdfFieldDefinitionRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class UdfValidator {

    private final UdfFieldDefinitionRepository repository;

    public UdfValidator(UdfFieldDefinitionRepository repository) {
        this.repository = repository;
    }

    public List<UdfViolation> validate(UUID orgId, ModuleKey moduleKey,
                                       Map<String, Object> customFields) {
        List<UdfFieldDefinition> defs = repository.findByOrgIdAndModuleKeyAndActiveTrue(orgId, moduleKey);
        if (defs.isEmpty()) {
            return List.of();
        }

        Map<String, Object> fields = customFields != null ? customFields : Map.of();
        List<UdfViolation> violations = new ArrayList<>();

        for (UdfFieldDefinition def : defs) {
            String key = def.getFieldKey();
            Object value = fields.get(key);

            if (def.isRequired() && value == null) {
                violations.add(new UdfViolation(key, fieldError(key, "is required")));
            } else if (value != null) {
                violations.addAll(validateType(def, key, value));
            }
        }

        return violations;
    }

    private static String fieldError(String key, String detail) {
        return "Field '" + key + "' " + detail;
    }

    private List<UdfViolation> validateType(UdfFieldDefinition def, String key, Object value) {
        List<UdfViolation> v = new ArrayList<>();
        UdfFieldType type = def.getFieldType();

        switch (type) {
            case TEXT -> validateText(def, key, value, v);
            case NUMBER -> validateNumber(def, key, value, v);
            case BOOLEAN -> validateBoolean(key, value, v);
            case DATE -> validateDate(key, value, v);
            case LIST -> validateList(def, key, value, v);
        }

        return v;
    }

    private void validateText(UdfFieldDefinition def, String key, Object value,
                               List<UdfViolation> v) {
        Map<String, Object> rules = def.getValidationRules();
        if (rules == null || !rules.containsKey("maxLength")) {
            return;
        }
        int max = ((Number) rules.get("maxLength")).intValue();
        if (value.toString().length() > max) {
            v.add(new UdfViolation(key, fieldError(key, "exceeds maxLength of " + max)));
        }
    }

    private void validateNumber(UdfFieldDefinition def, String key, Object value,
                                 List<UdfViolation> v) {
        double d;
        try {
            d = value instanceof Number ? ((Number) value).doubleValue()
                    : Double.parseDouble(value.toString());
        } catch (NumberFormatException e) {
            v.add(new UdfViolation(key, fieldError(key, "must be a number")));
            return;
        }

        Map<String, Object> rules = def.getValidationRules();
        if (rules == null) {
            return;
        }

        if (rules.containsKey("min")) {
            double min = ((Number) rules.get("min")).doubleValue();
            if (d < min) {
                v.add(new UdfViolation(key, fieldError(key, "is below minimum of " + min)));
            }
        }
        if (rules.containsKey("max")) {
            double max = ((Number) rules.get("max")).doubleValue();
            if (d > max) {
                v.add(new UdfViolation(key, fieldError(key, "exceeds maximum of " + max)));
            }
        }
    }

    private void validateBoolean(String key, Object value, List<UdfViolation> v) {
        if (value instanceof Boolean) {
            return;
        }
        String s = value.toString().toLowerCase(Locale.ROOT);
        if (!s.equals("true") && !s.equals("false")) {
            v.add(new UdfViolation(key, fieldError(key, "must be a boolean (true/false)")));
        }
    }

    private void validateDate(String key, Object value, List<UdfViolation> v) {
        try {
            LocalDate.parse(value.toString());
        } catch (DateTimeParseException e) {
            v.add(new UdfViolation(key, fieldError(key, "must be a valid ISO date (YYYY-MM-DD)")));
        }
    }

    private void validateList(UdfFieldDefinition def, String key, Object value,
                               List<UdfViolation> v) {
        List<String> options = def.getListOptions();
        if (options == null || options.isEmpty()) {
            return;
        }
        if (!options.contains(value.toString())) {
            v.add(new UdfViolation(key, fieldError(key, "must be one of: " + options)));
        }
    }
}
