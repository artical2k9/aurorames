package com.mes.udf.service;

import java.util.UUID;

@FunctionalInterface
public interface UdfValueNullifier {
    void nullifyValues(UUID orgId, String fieldKey);
}
