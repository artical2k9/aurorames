package com.mes.audit.domain;

import org.hibernate.annotations.Immutable;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AuditRecordImmutabilityTest {

    @Test
    void auditRecordHasImmutableAnnotation() {
        assertThat(AuditRecord.class).hasAnnotation(Immutable.class);
    }

    @Test
    void authAuditRecordHasImmutableAnnotation() {
        assertThat(AuthAuditRecord.class).hasAnnotation(Immutable.class);
    }

    @Test
    void auditRecordHasNoPublicSetters() {
        List<Method> publicSetters = Arrays.stream(AuditRecord.class.getMethods())
            .filter(m -> m.getName().startsWith("set"))
            .toList();

        assertThat(publicSetters)
            .as("AuditRecord should have no public setters — it is an immutable read model")
            .isEmpty();
    }

    @Test
    void authAuditRecordHasNoPublicSetters() {
        List<Method> publicSetters = Arrays.stream(AuthAuditRecord.class.getMethods())
            .filter(m -> m.getName().startsWith("set"))
            .toList();

        assertThat(publicSetters)
            .as("AuthAuditRecord should have no public setters — it is an immutable read model")
            .isEmpty();
    }

    @Test
    void auditRecordBuilderProducesNonNullObject() {
        AuditRecord record = AuditRecord.builder()
            .entityType("Test")
            .entityId("1")
            .userId("user:test")
            .build();

        assertThat(record).isNotNull();
        assertThat(record.getEntityType()).isEqualTo("Test");
    }
}
