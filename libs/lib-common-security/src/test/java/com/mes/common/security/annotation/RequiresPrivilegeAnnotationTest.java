package com.mes.common.security.annotation;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.annotation.ElementType;
import java.lang.annotation.RetentionPolicy;

import static org.assertj.core.api.Assertions.assertThat;

class RequiresPrivilegeAnnotationTest {

    @Test
    void hasRuntimeRetention() {
        var retention = RequiresPrivilege.class.getAnnotation(java.lang.annotation.Retention.class);
        assertThat(retention).isNotNull();
        assertThat(retention.value()).isEqualTo(RetentionPolicy.RUNTIME);
    }

    @Test
    void targetsMethod() {
        var target = RequiresPrivilege.class.getAnnotation(java.lang.annotation.Target.class);
        assertThat(target).isNotNull();
        assertThat(target.value()).containsExactly(ElementType.METHOD);
    }

    @Test
    void isMetaAnnotatedWithPreAuthorize() {
        var preAuthorize = RequiresPrivilege.class.getAnnotation(PreAuthorize.class);
        assertThat(preAuthorize).isNotNull();
        assertThat(preAuthorize.value()).contains("hasAuthority");
    }
}
