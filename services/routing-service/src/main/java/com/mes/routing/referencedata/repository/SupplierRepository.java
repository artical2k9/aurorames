package com.mes.routing.referencedata.repository;

import com.mes.routing.referencedata.domain.Supplier;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SupplierRepository extends JpaRepository<Supplier, UUID> {
    List<Supplier> findByOrgIdOrderByCode(UUID orgId);
    Optional<Supplier> findByOrgIdAndId(UUID orgId, UUID id);
    boolean existsByOrgIdAndCode(UUID orgId, String code);
}
