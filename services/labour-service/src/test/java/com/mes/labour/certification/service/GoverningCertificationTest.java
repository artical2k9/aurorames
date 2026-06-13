package com.mes.labour.certification.service;

import com.mes.labour.certification.domain.Certification;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class GoverningCertificationTest {

    private Certification cert(LocalDate award, LocalDate expiry, boolean revoked) {
        Certification c = new Certification();
        c.setAwardDate(award);
        c.setExpiryDate(expiry);
        c.setRevoked(revoked);
        return c;
    }

    @Test
    void latestExpiryGoverns() {
        Certification older = cert(LocalDate.of(2024, 1, 1), LocalDate.of(2025, 1, 1), false);
        Certification newer = cert(LocalDate.of(2025, 1, 1), LocalDate.of(2027, 1, 1), false);

        Optional<Certification> governing =
                CertificationStateCalculator.governing(List.of(older, newer));
        assertThat(governing).contains(newer);
    }

    @Test
    void neverExpiresRanksHighest() {
        Certification dated = cert(LocalDate.of(2025, 1, 1), LocalDate.of(2030, 1, 1), false);
        Certification unlimited = cert(LocalDate.of(2024, 1, 1), null, false);

        Optional<Certification> governing =
                CertificationStateCalculator.governing(List.of(dated, unlimited));
        assertThat(governing).contains(unlimited);
    }

    @Test
    void revokedCertificationsAreExcluded() {
        Certification revoked = cert(LocalDate.of(2025, 1, 1), LocalDate.of(2030, 1, 1), true);
        Certification valid = cert(LocalDate.of(2024, 1, 1), LocalDate.of(2026, 1, 1), false);

        Optional<Certification> governing =
                CertificationStateCalculator.governing(List.of(revoked, valid));
        assertThat(governing).contains(valid);
    }

    @Test
    void allRevokedYieldsEmpty() {
        Certification revoked = cert(LocalDate.of(2025, 1, 1), LocalDate.of(2030, 1, 1), true);

        assertThat(CertificationStateCalculator.governing(List.of(revoked))).isEmpty();
    }

    @Test
    void equalExpiryTiebreaksOnAwardDateDesc() {
        LocalDate expiry = LocalDate.of(2027, 1, 1);
        Certification earlierAward = cert(LocalDate.of(2024, 1, 1), expiry, false);
        Certification laterAward = cert(LocalDate.of(2025, 1, 1), expiry, false);

        Optional<Certification> governing =
                CertificationStateCalculator.governing(List.of(earlierAward, laterAward));
        assertThat(governing).contains(laterAward);
    }
}
