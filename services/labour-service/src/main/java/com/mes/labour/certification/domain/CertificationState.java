package com.mes.labour.certification.domain;

public enum CertificationState {
    ACTIVE, EXPIRING_SOON, EXPIRED, REVOKED;

    public boolean qualifies() {
        return this == ACTIVE || this == EXPIRING_SOON;
    }
}
