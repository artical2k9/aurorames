package com.mes.routing.route.domain;

import com.mes.routing.common.OrgAuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.envers.Audited;

import java.util.UUID;

/**
 * A manually-sequenced grouping of operations (US5). Normal/Parallel is derived from the
 * {@code groupSequenceNumber} (unique → Normal, shared → Parallel — computed in the service);
 * Optional is an explicit toggle. Operations join a group via {@code RouteOperation.groupId}.
 */
@Entity
@Audited
@Table(name = "operation_group", schema = "routing")
public class OperationGroup extends OrgAuditedEntity {

    @Column(name = "route_id", nullable = false)
    private UUID routeId;

    @Column(name = "group_sequence_number", nullable = false)
    private int groupSequenceNumber;

    @Column(name = "name", length = 255)
    private String name;

    @Column(name = "optional", nullable = false)
    private boolean optional = false;

    public UUID getRouteId() {
        return routeId;
    }

    public void setRouteId(UUID routeId) {
        this.routeId = routeId;
    }

    public int getGroupSequenceNumber() {
        return groupSequenceNumber;
    }

    public void setGroupSequenceNumber(int groupSequenceNumber) {
        this.groupSequenceNumber = groupSequenceNumber;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean isOptional() {
        return optional;
    }

    public void setOptional(boolean optional) {
        this.optional = optional;
    }
}
