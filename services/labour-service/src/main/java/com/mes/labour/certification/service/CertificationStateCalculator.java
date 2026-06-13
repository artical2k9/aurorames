package com.mes.labour.certification.service;

import com.mes.labour.certification.domain.Certification;
import com.mes.labour.certification.domain.CertificationState;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Comparator;
import java.util.Optional;

/**
 * Pure derivation of certification state — never stored, computed at read time so
 * gating can never act on a stale state (spec SC-005: zero gating false-positives).
 */
public final class CertificationStateCalculator {

    private CertificationStateCalculator() {
    }

    public static CertificationState stateOf(Certification cert, LocalDate today, int warningDays) {
        if (cert.isRevoked()) {
            return CertificationState.REVOKED;
        }
        LocalDate expiry = cert.getExpiryDate();
        if (expiry == null) {
            return CertificationState.ACTIVE;
        }
        if (expiry.isBefore(today)) {
            return CertificationState.EXPIRED;
        }
        if (!expiry.isAfter(today.plusDays(warningDays))) {
            return CertificationState.EXPIRING_SOON;
        }
        return CertificationState.ACTIVE;
    }

    /**
     * The governing certification among awards for one (employee, skill): latest expiry wins,
     * never-expires (null expiry) ranks highest, award date descending breaks ties.
     * Revoked certifications never govern.
     */
    public static Optional<Certification> governing(Collection<Certification> certifications) {
        return certifications.stream()
                .filter(c -> !c.isRevoked())
                .max(Comparator
                        .comparing((Certification c) -> c.getExpiryDate() == null
                                ? LocalDate.MAX : c.getExpiryDate())
                        .thenComparing(Certification::getAwardDate));
    }
}
