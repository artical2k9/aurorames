package com.mes.common.security.privilege;

import org.springframework.security.core.GrantedAuthority;

public final class PrivilegeGrantedAuthority implements GrantedAuthority {

    private final String privilege;

    public PrivilegeGrantedAuthority(String privilege) {
        this.privilege = privilege;
    }

    @Override
    public String getAuthority() {
        return privilege;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof PrivilegeGrantedAuthority other)) {
            return false;
        }
        return privilege.equals(other.privilege);
    }

    @Override
    public int hashCode() {
        return privilege.hashCode();
    }
}
