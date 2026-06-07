package com.mes.iam.api;

import com.mes.iam.api.dto.ChangeTemporaryPasswordRequest;
import com.mes.iam.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class PublicAuthController {

    private final UserService userService;

    public PublicAuthController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/change-temporary-password")
    public ResponseEntity<Void> changeTemporaryPassword(
            @Valid @RequestBody ChangeTemporaryPasswordRequest request) {
        userService.changeTemporaryPassword(
                request.username(), request.currentPassword(), request.newPassword());
        return ResponseEntity.noContent().build();
    }
}
