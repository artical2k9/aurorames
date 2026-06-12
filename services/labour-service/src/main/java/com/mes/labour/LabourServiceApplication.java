package com.mes.labour;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication
@EnableJpaAuditing
public class LabourServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(LabourServiceApplication.class, args);
    }
}
