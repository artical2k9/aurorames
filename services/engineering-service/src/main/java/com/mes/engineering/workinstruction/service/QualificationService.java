package com.mes.engineering.workinstruction.service;

import com.mes.engineering.workinstruction.api.dto.QualificationResultDto;
import com.mes.engineering.workinstruction.client.LabourServiceClient;
import com.mes.engineering.workinstruction.domain.WorkInstructionRevision;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Evaluates whether an operator holds every skill a work-instruction revision requires, by
 * delegating to labour-service. Fails closed: if labour-service cannot be reached, the operator
 * is reported as not qualified with reason {@code VERIFICATION_UNAVAILABLE}.
 */
@Service
public class QualificationService {

    private static final String REASON_OK = "OK";
    private static final String REASON_UNAVAILABLE = "VERIFICATION_UNAVAILABLE";

    /** Labour statuses that count as currently qualified (mirrors CertificationState.qualifies()). */
    private static final Set<String> QUALIFYING_STATUSES = Set.of("HELD_ACTIVE", "EXPIRING_SOON");

    private final SkillRequirementService skillRequirementService;
    private final LabourServiceClient labourClient;

    public QualificationService(SkillRequirementService skillRequirementService,
                                LabourServiceClient labourClient) {
        this.skillRequirementService = skillRequirementService;
        this.labourClient = labourClient;
    }

    public QualificationResultDto evaluate(WorkInstructionRevision revision, UUID employeeId,
                                           String iamUserId) {
        boolean hasEmployeeId = employeeId != null;
        boolean hasIamUserId = iamUserId != null && !iamUserId.isBlank();
        if (hasEmployeeId == hasIamUserId) {
            throw new WorkInstructionValidationException(
                    "Exactly one of employeeId or iamUserId must be provided");
        }

        List<UUID> skillIds = skillRequirementService.skillIdsForRevision(revision.getId());
        if (skillIds.isEmpty()) {
            return new QualificationResultDto(true, List.of(), REASON_OK);
        }

        Optional<LabourServiceClient.QualificationResponse> response =
                labourClient.evaluate(employeeId, iamUserId, skillIds);
        if (response.isEmpty()) {
            // Fail closed — labour-service unavailable; never report qualified on uncertainty.
            return new QualificationResultDto(false, List.of(), REASON_UNAVAILABLE);
        }

        LabourServiceClient.QualificationResponse result = response.get();
        List<QualificationResultDto.MissingSkill> missing = result.results().stream()
                .filter(r -> r.status() == null || !QUALIFYING_STATUSES.contains(r.status()))
                .map(r -> new QualificationResultDto.MissingSkill(
                        r.skillId(), r.skillCode(), r.status()))
                .toList();

        boolean qualified = result.employeeActive() && missing.isEmpty();
        return new QualificationResultDto(qualified, missing, REASON_OK);
    }
}
