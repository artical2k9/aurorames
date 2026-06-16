package com.mes.routing.route.repository;

import com.mes.routing.route.domain.MutuallyExclusiveLevel;
import com.mes.routing.route.domain.MutuallyExclusiveSet;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MutuallyExclusiveSetRepository extends JpaRepository<MutuallyExclusiveSet, UUID> {

    List<MutuallyExclusiveSet> findByRouteIdOrderByLevelAscSequenceNumberAsc(UUID routeId);

    List<MutuallyExclusiveSet> findByRouteIdAndLevel(UUID routeId, MutuallyExclusiveLevel level);

    void deleteByRouteIdAndLevel(UUID routeId, MutuallyExclusiveLevel level);
}
