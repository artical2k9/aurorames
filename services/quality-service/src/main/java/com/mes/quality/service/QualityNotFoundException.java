package com.mes.quality.service;

public class QualityNotFoundException extends RuntimeException {
    public QualityNotFoundException(String message) {
        super(message);
    }
}
