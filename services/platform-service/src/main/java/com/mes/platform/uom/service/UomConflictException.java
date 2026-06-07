package com.mes.platform.uom.service;

public class UomConflictException extends RuntimeException {
    public UomConflictException(String code) {
        super("Unit of measure code already exists: " + code);
    }
}
