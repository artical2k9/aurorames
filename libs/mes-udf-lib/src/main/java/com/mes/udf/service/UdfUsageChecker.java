package com.mes.udf.service;

import java.util.UUID;

@FunctionalInterface
public interface UdfUsageChecker {
    long countUsages(UUID orgId, String fieldKey);
}
