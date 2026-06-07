package com.mes.platform.health;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class PrivilegeRegistrationHealthIndicator implements HealthIndicator {

    private final AtomicBoolean registered = new AtomicBoolean(false);

    public void markRegistered() {
        registered.set(true);
    }

    @Override
    public Health health() {
        return registered.get()
                ? Health.up().withDetail("privileges", "registered").build()
                : Health.down().withDetail("privileges", "pending").build();
    }
}
