package com.mes.udf.service;

import java.util.List;
import java.util.UUID;

@FunctionalInterface
public interface UdfAffectedRecordsFinder {

    List<UUID> findAffectedIds(UUID orgId, String fieldKey);
}
