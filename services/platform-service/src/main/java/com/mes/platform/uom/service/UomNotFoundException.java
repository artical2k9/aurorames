package com.mes.platform.uom.service;

public class UomNotFoundException extends RuntimeException {
    public UomNotFoundException(String code) {
        super("Unit of measure not found: " + code);
    }
}
