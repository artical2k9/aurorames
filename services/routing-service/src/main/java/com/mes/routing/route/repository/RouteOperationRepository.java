package com.mes.routing.route.repository;

import com.mes.routing.route.domain.RouteOperation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RouteOperationRepository extends JpaRepository<RouteOperation, UUID> {

    List<RouteOperation> findByRouteIdOrderBySequenceNumberAscOperationNumberAsc(UUID routeId);

    Optional<RouteOperation> findByRouteIdAndId(UUID routeId, UUID id);

    boolean existsByRouteIdAndOperationNumber(UUID routeId, int operationNumber);

    /** Count operations sharing a sequence number — used to derive Normal vs Parallel. */
    int countByRouteIdAndSequenceNumber(UUID routeId, int sequenceNumber);
}
