package com.mes.routing.route.repository;

import com.mes.routing.route.domain.OperationGroup;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OperationGroupRepository extends JpaRepository<OperationGroup, UUID> {

    List<OperationGroup> findByRouteIdOrderByGroupSequenceNumberAsc(UUID routeId);

    Optional<OperationGroup> findByRouteIdAndId(UUID routeId, UUID id);

    /** Count groups sharing a group-sequence number — used to derive Normal vs Parallel. */
    int countByRouteIdAndGroupSequenceNumber(UUID routeId, int groupSequenceNumber);

    void deleteByRouteId(UUID routeId);
}
