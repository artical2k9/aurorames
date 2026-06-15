package com.mes.routing.referencedata.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.envers.Audited;

/** Interim OSP supplier (FR-009a; full supplier master deferred — DEF-002). */
@Entity
@Audited
@Table(name = "supplier", schema = "routing")
public class Supplier extends AuditedReferenceEntity {
}
