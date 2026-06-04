package com.mes.inventory.architecture;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.springframework.kafka.annotation.KafkaListener;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(packages = "com.mes.inventory")
class InventoryArchitectureTest {

    @ArchTest
    static final ArchRule noKafkaListeners = noClasses()
            .should().beAnnotatedWith(KafkaListener.class)
            .as("inventory-service must be producer-only (FR-013): @KafkaListener is prohibited");
}
