package com.mes.routing.referencedata.repository;

import com.mes.routing.referencedata.domain.RouteType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RouteTypeRepository extends JpaRepository<RouteType, UUID> {
    List<RouteType> findByOrgIdOrderByCode(UUID orgId);
    Optional<RouteType> findByOrgIdAndId(UUID orgId, UUID id);
    boolean existsByOrgIdAndCode(UUID orgId, String code);
}
