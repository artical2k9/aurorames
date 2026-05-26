package com.mes.workorder.itemmaster.service;

public class ItemMasterNotFoundException extends RuntimeException {
    public ItemMasterNotFoundException(String message) {
        super(message);
    }
}
