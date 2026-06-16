package com.mes.routing.route.domain;

import com.mes.routing.common.OrgAuditedEntity;
import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import org.hibernate.annotations.Type;
import org.hibernate.envers.Audited;

import java.util.List;
import java.util.UUID;

/**
 * A subset of members (operations, groups or steps) flagged mutually exclusive within one
 * parallel sequence (US4/US5/US6). The first member clocked-on becomes active and excludes the
 * other <em>members</em> from needing completion; parallel peers not in {@code memberIds} are
 * unaffected. Membership is a strict subset of the parallel set sharing {@code sequenceNumber}.
 */
@Entity
@Audited
@Table(name = "mutually_exclusive_set", schema = "routing")
public class MutuallyExclusiveSet extends OrgAuditedEntity {

    @Column(name = "route_id", nullable = false)
    private UUID routeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "level", nullable = false, length = 20)
    private MutuallyExclusiveLevel level;

    @Column(name = "sequence_number", nullable = false)
    private int sequenceNumber;

    @Type(JsonBinaryType.class)
    @Column(name = "member_ids", columnDefinition = "jsonb", nullable = false)
    private List<UUID> memberIds;

    public UUID getRouteId() {
        return routeId;
    }

    public void setRouteId(UUID routeId) {
        this.routeId = routeId;
    }

    public MutuallyExclusiveLevel getLevel() {
        return level;
    }

    public void setLevel(MutuallyExclusiveLevel level) {
        this.level = level;
    }

    public int getSequenceNumber() {
        return sequenceNumber;
    }

    public void setSequenceNumber(int sequenceNumber) {
        this.sequenceNumber = sequenceNumber;
    }

    public List<UUID> getMemberIds() {
        return memberIds;
    }

    public void setMemberIds(List<UUID> memberIds) {
        this.memberIds = memberIds;
    }
}
