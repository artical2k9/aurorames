package com.mes.common.security.privilege;

import java.util.Set;

public interface PrivilegeCache {

    Set<String> getPrivilegesForRole(String role);
}
