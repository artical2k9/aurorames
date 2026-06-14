package com.mes.engineering.workinstruction.service;

import com.mes.engineering.workinstruction.domain.ElectronicSignature;
import com.mes.engineering.workinstruction.domain.WorkInstructionRevision;
import com.mes.engineering.workinstruction.repository.ElectronicSignatureRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SignatureServiceTest {

    private final KeycloakCredentialVerifier verifier = mock(KeycloakCredentialVerifier.class);
    private final ElectronicSignatureRepository repository = mock(ElectronicSignatureRepository.class);
    private final SignatureService service = new SignatureService(verifier, repository);

    private static Jwt jwt() {
        return Jwt.withTokenValue("t")
                .header("alg", "none")
                .subject("user-sub-123")
                .claim("preferred_username", "approver")
                .claim("given_name", "Ada")
                .claim("family_name", "Lovelace")
                .build();
    }

    private static WorkInstructionRevision revision() {
        WorkInstructionRevision r = new WorkInstructionRevision();
        r.setRevision(0);
        return r;
    }

    @Test
    void persistsSignatureWithSignerIdentityWhenPasswordValid() {
        when(verifier.verify("approver", "correct")).thenReturn(true);
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ElectronicSignature sig = service.verifyAndSign(jwt(), "correct", revision(), "APPROVED");

        assertThat(sig.getSignerUserId()).isEqualTo("user-sub-123");
        assertThat(sig.getSignerFullName()).isEqualTo("Ada Lovelace");
        assertThat(sig.getMeaning()).isEqualTo("APPROVED");
        assertThat(sig.getSignedAt()).isBeforeOrEqualTo(Instant.now());
        verify(repository).save(any());
    }

    @Test
    void throwsAndDoesNotPersistWhenPasswordInvalid() {
        when(verifier.verify("approver", "wrong")).thenReturn(false);

        assertThatThrownBy(() -> service.verifyAndSign(jwt(), "wrong", revision(), "APPROVED"))
                .isInstanceOf(WorkInstructionValidationException.class)
                .satisfies(ex -> assertThat(
                        ((WorkInstructionValidationException) ex).getDetails())
                        .contains("SIGNATURE_VERIFICATION_FAILED"));

        verify(repository, never()).save(any());
    }

    @Test
    void signerOfFallsBackToPreferredUsernameWhenSubMissing() {
        Jwt noSub = Jwt.withTokenValue("t").header("alg", "none")
                .claim("preferred_username", "fallback-user")
                .build();
        assertThat(service.signerOf(noSub)).isEqualTo("fallback-user");
    }
}
