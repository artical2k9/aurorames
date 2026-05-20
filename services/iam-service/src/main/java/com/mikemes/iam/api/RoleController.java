package com.mikemes.iam.api;

import com.mikemes.common.security.annotation.RequiresPrivilege;
import com.mikemes.iam.api.dto.CreateRoleRequest;
import com.mikemes.iam.api.dto.PrivilegeResponse;
import com.mikemes.iam.api.dto.RoleResponse;
import com.mikemes.iam.domain.Privilege;
import com.mikemes.iam.domain.Role;
import com.mikemes.iam.service.RoleService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/roles")
public class RoleController {

    private final RoleService roleService;

    public RoleController(RoleService roleService) {
        this.roleService = roleService;
    }

    @GetMapping
    public List<RoleResponse> listRoles(Principal principal) {
        UUID orgId = orgId(principal);
        return roleService.listRoles(orgId).stream()
                .map(r -> toResponse(r, roleService.countActivePrivileges(r.getId())))
                .toList();
    }

    @PostMapping
    @RequiresPrivilege("iam:roles:manage")
    public ResponseEntity<RoleResponse> createRole(@Valid @RequestBody CreateRoleRequest request,
                                                    Principal principal) {
        UUID orgId = orgId(principal);
        Role role = roleService.createRole(orgId, request.name(), request.description());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(toResponse(role, roleService.countActivePrivileges(role.getId())));
    }

    @DeleteMapping("/{roleId}")
    @RequiresPrivilege("iam:roles:manage")
    public ResponseEntity<Void> deleteRole(@PathVariable UUID roleId, Principal principal) {
        roleService.deleteRole(roleId, orgId(principal));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{roleId}/privileges")
    @RequiresPrivilege("iam:roles:manage")
    public List<PrivilegeResponse> getRolePrivileges(@PathVariable UUID roleId, Principal principal) {
        return roleService.getRolePrivileges(roleId, orgId(principal)).stream()
                .map(a -> toPrivilegeResponse(a.getPrivilege()))
                .toList();
    }

    @PutMapping("/{roleId}/privileges/{privilegeId}")
    @RequiresPrivilege("iam:roles:manage")
    public ResponseEntity<Void> grantPrivilege(@PathVariable UUID roleId,
                                                @PathVariable UUID privilegeId,
                                                Principal principal) {
        roleService.grantPrivilege(roleId, privilegeId, orgId(principal));
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{roleId}/privileges/{privilegeId}")
    @RequiresPrivilege("iam:roles:manage")
    public ResponseEntity<Void> revokePrivilege(@PathVariable UUID roleId,
                                                 @PathVariable UUID privilegeId,
                                                 Principal principal) {
        roleService.revokePrivilege(roleId, privilegeId, orgId(principal));
        return ResponseEntity.noContent().build();
    }

    private static UUID orgId(Principal principal) {
        if (principal instanceof JwtAuthenticationToken jwtToken) {
            Jwt jwt = (Jwt) jwtToken.getPrincipal();
            String orgId = jwt.getClaimAsString("org_id");
            if (orgId != null && !orgId.isBlank()) {
                return UUID.fromString(orgId);
            }
        }
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing org_id claim");
    }

    private static RoleResponse toResponse(Role role, long privilegeCount) {
        return new RoleResponse(
                role.getId(),
                role.getName(),
                role.getDescription(),
                role.isSystemRole(),
                privilegeCount,
                role.getCreatedAt(),
                role.getCreatedBy());
    }

    private static PrivilegeResponse toPrivilegeResponse(Privilege p) {
        return new PrivilegeResponse(
                p.getId(),
                p.getPrivilegeKey(),
                p.getModuleName(),
                p.getDescription(),
                p.getRegisteredAt());
    }
}
