package com.mes.routing.route.repository;

import com.mes.routing.route.domain.Route;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RouteRepository extends JpaRepository<Route, UUID> {

    Optional<Route> findByOrgIdAndId(UUID orgId, UUID id);

    List<Route> findByBomRevisionId(UUID bomRevisionId);

    List<Route> findByInspectionPlanRevisionId(UUID inspectionPlanRevisionId);

    Page<Route> findByOrgId(UUID orgId, Pageable pageable);

    // Non-null search only — keeping a `:search is null OR ...` guard makes PostgreSQL type the
    // null param as bytea and fail at plan time (text ~~ bytea). The service picks this vs
    // findByOrgId based on whether a search term is present.
    @Query("""
            select r from Route r
            where r.orgId = :orgId
              and (cast(r.partId as string) like concat('%', :search, '%')
                   or lower(r.reasonForRevision) like lower(concat('%', :search, '%')))
            """)
    Page<Route> search(@Param("orgId") UUID orgId, @Param("search") String search, Pageable pageable);

    /** True if a Standard-type route already exists for this part/revision in the org (FR-004b). */
    @Query("""
            select (count(r) > 0) from Route r
            where r.orgId = :orgId and r.partId = :partId and r.partRevision = :rev
              and r.routeTypeId in (
                  select t.id from RouteType t where t.orgId = :orgId and t.isStandard = true)
            """)
    boolean existsStandardRouteForPart(@Param("orgId") UUID orgId,
                                       @Param("partId") UUID partId,
                                       @Param("rev") String rev);
}
