package com.mes.engineering.workinstruction.service;

import com.mes.engineering.workinstruction.domain.ElectronicSignature;
import com.mes.engineering.workinstruction.domain.WorkInstructionRevision;
import com.mes.engineering.workinstruction.repository.ElectronicSignatureRepository;
import com.mes.udf.api.JwtClaimsExtractor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Records 21 CFR Part 11-style electronic signatures. Verification (password re-auth via KC) and
 * signature persistence happen in the caller's transaction so the signature and the status change
 * commit atomically. Failed verification attempts are logged (FR-008) but never persisted as
 * signature rows.
 */
@Service
@Transactional
public class SignatureService {

    private static final Logger LOG = LoggerFactory.getLogger(SignatureService.class);

    private final KeycloakCredentialVerifier verifier;
    private final ElectronicSignatureRepository signatureRepository;

    public SignatureService(KeycloakCredentialVerifier verifier,
                            ElectronicSignatureRepository signatureRepository) {
        this.verifier = verifier;
        this.signatureRepository = signatureRepository;
    }

    /**
     * Verify the approver's password and persist an immutable signature. Throws
     * {@link WorkInstructionValidationException} (422) on verification failure, leaving the
     * revision status unchanged.
     */
    public ElectronicSignature verifyAndSign(Jwt jwt, String password,
                                             WorkInstructionRevision revision, String meaning) {
        String username = jwt.getClaimAsString("preferred_username");
        if (!verifier.verify(username, password)) {
            LOG.warn("E-signature verification FAILED for user '{}' on revision {} (meaning={})",
                    username, revision.getId(), meaning);
            throw new WorkInstructionValidationException(
                    "Electronic signature verification failed",
                    List.of("SIGNATURE_VERIFICATION_FAILED"));
        }
        ElectronicSignature signature = new ElectronicSignature();
        signature.setRevision(revision);
        signature.setSignerUserId(JwtClaimsExtractor.nullSafeSubject(jwt));
        signature.setSignerFullName(fullNameOf(jwt, username));
        signature.setSignedAt(Instant.now());
        signature.setMeaning(meaning);
        return signatureRepository.save(signature);
    }

    /** Signer identity for the revision's approvedBy field (null-safe sub per ERR-MES-060). */
    public String signerOf(Jwt jwt) {
        return JwtClaimsExtractor.nullSafeSubject(jwt);
    }

    private static String fullNameOf(Jwt jwt, String username) {
        String given = jwt.getClaimAsString("given_name");
        String family = jwt.getClaimAsString("family_name");
        if (given != null && !given.isBlank() && family != null && !family.isBlank()) {
            return given + " " + family;
        }
        String name = jwt.getClaimAsString("name");
        if (name != null && !name.isBlank()) {
            return name;
        }
        return (username != null && !username.isBlank()) ? username : "unknown";
    }
}
