package com.mes.auditservice.api;

import com.mes.auditservice.api.dto.VerificationResult;
import com.mes.auditservice.service.TamperVerificationService;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;

@RestController
@RequestMapping("/audit")
public class VerificationController {

    private final TamperVerificationService verificationService;

    public VerificationController(TamperVerificationService verificationService) {
        this.verificationService = verificationService;
    }

    @PostMapping("/verify")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public ResponseEntity<VerificationResult> verify(@RequestBody @NotNull VerifyRequest request) {
        if (request.from() == null || request.to() == null || !request.from().isBefore(request.to())) {
            return ResponseEntity.badRequest().build();
        }
        VerificationResult result = verificationService.verify(request.from(), request.to());
        return ResponseEntity.ok(result);
    }

    public record VerifyRequest(OffsetDateTime from, OffsetDateTime to) {}
}
