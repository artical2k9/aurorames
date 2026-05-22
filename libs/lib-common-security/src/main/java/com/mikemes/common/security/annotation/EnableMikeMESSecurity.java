package com.mikemes.common.security.annotation;

import com.mikemes.common.security.config.MikeMESSecurityAutoConfiguration;
import org.springframework.context.annotation.Import;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Import(MikeMESSecurityAutoConfiguration.class)
public @interface EnableMikeMESSecurity {
}
