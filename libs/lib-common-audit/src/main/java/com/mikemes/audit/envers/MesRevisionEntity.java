package com.mikemes.audit.envers;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.envers.DefaultRevisionEntity;
import org.hibernate.envers.RevisionEntity;

import java.util.Objects;

@Entity
@RevisionEntity(MesRevisionListener.class)
@Table(schema = "audit", name = "mes_revisions")
public class MesRevisionEntity extends DefaultRevisionEntity {

    @Column(name = "user_id", length = 100)
    private String userId;

    @Column(name = "service_source", length = 100)
    private String serviceSource;

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getServiceSource() {
        return serviceSource;
    }

    public void setServiceSource(String serviceSource) {
        this.serviceSource = serviceSource;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof MesRevisionEntity)) {
            return false;
        }
        return super.equals(obj);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode());
    }
}
