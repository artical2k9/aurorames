package com.mikemes.common.security.privilege;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;
import java.util.Set;

public record PrivilegeManifest(Map<String, Set<String>> rolePrivileges) {

    @JsonCreator
    public PrivilegeManifest(@JsonProperty("rolePrivileges") Map<String, Set<String>> rolePrivileges) {
        this.rolePrivileges = Map.copyOf(rolePrivileges);
    }

    public Set<String> getPrivilegesForRole(String role) {
        return rolePrivileges.getOrDefault(role, Set.of());
    }
}
