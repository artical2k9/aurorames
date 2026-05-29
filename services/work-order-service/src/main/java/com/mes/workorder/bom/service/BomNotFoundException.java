package com.mes.workorder.bom.service;

public class BomNotFoundException extends RuntimeException {

    public BomNotFoundException(String message) {
        super(message);
    }
}
