package com.mes.labour.training.api;

import com.mes.common.security.annotation.RequiresPrivilege;
import com.mes.labour.training.api.dto.TrainingDtos.CreateTrainingEventRequest;
import com.mes.labour.training.api.dto.TrainingDtos.PatchTrainingEventRequest;
import com.mes.labour.training.api.dto.TrainingDtos.TrainingEventDto;
import com.mes.labour.training.api.dto.TrainingDtos.TrainingHistoryEntryDto;
import com.mes.labour.training.service.TrainingService;
import com.mes.udf.api.JwtClaimsExtractor;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/labour")
public class TrainingController {

    private final TrainingService trainingService;

    public TrainingController(TrainingService trainingService) {
        this.trainingService = trainingService;
    }

    @PostMapping("/training-events")
    @RequiresPrivilege("labour:training:manage")
    public ResponseEntity<TrainingEventDto> create(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateTrainingEventRequest request) {

        TrainingEventDto dto = trainingService.create(JwtClaimsExtractor.orgId(jwt), request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(dto.getId()).toUri();
        return ResponseEntity.created(location).body(dto);
    }

    @GetMapping("/training-events")
    @RequiresPrivilege("labour:training:view")
    public Page<TrainingEventDto> list(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        return trainingService.list(JwtClaimsExtractor.orgId(jwt), PageRequest.of(page, size));
    }

    @GetMapping("/training-events/{eventId}")
    @RequiresPrivilege("labour:training:view")
    public TrainingEventDto get(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID eventId) {

        return trainingService.get(JwtClaimsExtractor.orgId(jwt), eventId);
    }

    @PatchMapping("/training-events/{eventId}")
    @RequiresPrivilege("labour:training:manage")
    public TrainingEventDto patch(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID eventId,
            @Valid @RequestBody PatchTrainingEventRequest request) {

        return trainingService.patch(JwtClaimsExtractor.orgId(jwt), eventId, request);
    }

    @GetMapping("/employees/{employeeId}/training")
    @RequiresPrivilege("labour:training:view")
    public Map<String, Object> employeeHistory(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID employeeId) {

        List<TrainingHistoryEntryDto> content =
                trainingService.historyFor(JwtClaimsExtractor.orgId(jwt), employeeId);
        return Map.of("content", content, "totalElements", content.size());
    }
}
