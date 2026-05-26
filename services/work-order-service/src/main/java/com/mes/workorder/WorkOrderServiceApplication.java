package com.mes.workorder;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication
@EnableJpaAuditing
public class WorkOrderServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(WorkOrderServiceApplication.class, args);
    }
}
