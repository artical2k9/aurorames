package com.mes.workorder.bom.service;

public class BomConflictException extends RuntimeException {

    public BomConflictException(String message) {
        super(message);
    }
}
