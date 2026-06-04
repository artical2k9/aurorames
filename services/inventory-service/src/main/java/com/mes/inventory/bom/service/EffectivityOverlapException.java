package com.mes.inventory.bom.service;

import java.util.UUID;

public class EffectivityOverlapException extends RuntimeException {

    private final String findNumber;
    private final UUID conflictingLineId;

    public EffectivityOverlapException(String findNumber, UUID conflictingLineId) {
        super("Date range overlap for BOM line find number " + findNumber
              + " — conflicts with existing line ID " + conflictingLineId);
        this.findNumber = findNumber;
        this.conflictingLineId = conflictingLineId;
    }

    public String getFindNumber() {
        return findNumber;
    }

    public UUID getConflictingLineId() {
        return conflictingLineId;
    }
}
