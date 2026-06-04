package com.mes.inventory.health;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

@Component
public class PrivilegeRegistrationHealthIndicator implements HealthIndicator {

    private volatile boolean registered = false;

    public void markRegistered() {
        this.registered = true;
    }

    @Override
    public Health health() {
        if (registered) {
            return Health.up().withDetail("privileges", "registered with IAM service").build();
        }
        return Health.down().withDetail("privileges", "not yet registered with IAM service").build();
    }
}
