package com.mes.engineering.workinstruction.repository;

import com.mes.engineering.workinstruction.domain.ElectronicSignature;
import org.springframework.data.repository.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Append-only signature store. Intentionally extends the narrow {@link Repository} interface
 * exposing only save and read — there is no update or delete path for signature records
 * (21 CFR Part 11 immutability).
 */
public interface ElectronicSignatureRepository extends Repository<ElectronicSignature, UUID> {

    ElectronicSignature save(ElectronicSignature signature);

    List<ElectronicSignature> findByRevisionIdOrderBySignedAtAsc(UUID revisionId);
}
