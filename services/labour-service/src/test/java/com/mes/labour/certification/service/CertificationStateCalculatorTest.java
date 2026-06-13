package com.mes.labour.certification.service;

import com.mes.labour.certification.domain.Certification;
import com.mes.labour.certification.domain.CertificationState;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class CertificationStateCalculatorTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 6, 12);
    private static final int WARNING_DAYS = 30;

    private Certification cert(LocalDate expiry, boolean revoked) {
        Certification c = new Certification();
        c.setAwardDate(TODAY.minusMonths(6));
        c.setExpiryDate(expiry);
        c.setRevoked(revoked);
        return c;
    }

    @Test
    void revokedTakesPrecedenceOverEverything() {
        assertThat(CertificationStateCalculator.stateOf(cert(TODAY.plusYears(1), true), TODAY, WARNING_DAYS))
                .isEqualTo(CertificationState.REVOKED);
        assertThat(CertificationStateCalculator.stateOf(cert(TODAY.minusDays(1), true), TODAY, WARNING_DAYS))
                .isEqualTo(CertificationState.REVOKED);
    }

    @Test
    void expiredWhenExpiryBeforeToday() {
        assertThat(CertificationStateCalculator.stateOf(cert(TODAY.minusDays(1), false), TODAY, WARNING_DAYS))
                .isEqualTo(CertificationState.EXPIRED);
    }

    @Test
    void expiringOnExpiryDayStillCounts() {
        // expiry date itself is the last valid day
        assertThat(CertificationStateCalculator.stateOf(cert(TODAY, false), TODAY, WARNING_DAYS))
                .isEqualTo(CertificationState.EXPIRING_SOON);
    }

    @Test
    void expiringSoonAtWindowBoundary() {
        assertThat(CertificationStateCalculator.stateOf(cert(TODAY.plusDays(WARNING_DAYS), false), TODAY, WARNING_DAYS))
                .isEqualTo(CertificationState.EXPIRING_SOON);
    }

    @Test
    void activeJustOutsideWindow() {
        Certification c = cert(TODAY.plusDays(WARNING_DAYS + 1), false);
        assertThat(CertificationStateCalculator.stateOf(c, TODAY, WARNING_DAYS))
                .isEqualTo(CertificationState.ACTIVE);
    }

    @Test
    void nullExpiryNeverExpires() {
        assertThat(CertificationStateCalculator.stateOf(cert(null, false), TODAY, WARNING_DAYS))
                .isEqualTo(CertificationState.ACTIVE);
    }

    @Test
    void qualifyingStatesAreActiveAndExpiringSoon() {
        assertThat(CertificationState.ACTIVE.qualifies()).isTrue();
        assertThat(CertificationState.EXPIRING_SOON.qualifies()).isTrue();
        assertThat(CertificationState.EXPIRED.qualifies()).isFalse();
        assertThat(CertificationState.REVOKED.qualifies()).isFalse();
    }
}
