package com.mes.udf.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.context.annotation.ComponentScan;

@AutoConfiguration
@AutoConfigurationPackage(basePackages = "com.mes.udf")
@ComponentScan(basePackages = "com.mes.udf")
public class UdfAutoConfiguration {
}
