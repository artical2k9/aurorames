package com.mikemes.iam.api;

import com.mikemes.common.security.annotation.RequiresPrivilege;
import com.mikemes.iam.api.dto.CreateUserRequest;
import com.mikemes.iam.api.dto.UpdateUserRolesRequest;
import com.mikemes.iam.api.dto.UserResponse;
import com.mikemes.iam.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
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
@RequestMapping("/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    @RequiresPrivilege("iam:users:view")
    public List<UserResponse> listUsers(Principal principal) {
        return userService.listUsers(orgId(principal));
    }

    @PostMapping
    @RequiresPrivilege("iam:users:create")
    public ResponseEntity<UserResponse> createUser(@Valid @RequestBody CreateUserRequest request,
                                                    Principal principal) {
        UserResponse user = userService.createUser(
                request.email(), request.firstName(), request.lastName(),
                orgId(principal), request.roles());
        return ResponseEntity.status(HttpStatus.CREATED).body(user);
    }

    @GetMapping("/{userId}")
    @RequiresPrivilege("iam:users:view")
    public UserResponse getUser(@PathVariable String userId, Principal principal) {
        return userService.getUser(userId, orgId(principal));
    }

    @PutMapping("/{userId}/roles")
    @RequiresPrivilege("iam:users:create")
    public UserResponse setUserRoles(@PathVariable String userId,
                                      @Valid @RequestBody UpdateUserRolesRequest request,
                                      Principal principal) {
        return userService.setUserRoles(userId, orgId(principal), request.roles());
    }

    @PostMapping("/{userId}/deactivate")
    @RequiresPrivilege("iam:users:create")
    public ResponseEntity<Void> deactivateUser(@PathVariable String userId, Principal principal) {
        userService.deactivateUser(userId, orgId(principal));
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
}
