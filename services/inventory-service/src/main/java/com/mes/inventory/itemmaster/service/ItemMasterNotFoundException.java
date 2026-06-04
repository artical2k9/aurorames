package com.mes.inventory.itemmaster.service;

public class ItemMasterNotFoundException extends RuntimeException {
    public ItemMasterNotFoundException(String message) {
        super(message);
    }
}
