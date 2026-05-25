package com.mes.common.security.annotation;

import com.mes.common.security.config.MESSecurityAutoConfiguration;
import org.springframework.context.annotation.Import;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Import(MESSecurityAutoConfiguration.class)
public @interface EnableMESSecurity {
}
