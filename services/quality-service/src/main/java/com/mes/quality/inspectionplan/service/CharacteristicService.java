package com.mes.quality.inspectionplan.service;

import com.mes.quality.inspectionplan.api.dto.CharacteristicDto;
import com.mes.quality.inspectionplan.api.dto.CharacteristicMapper;
import com.mes.quality.inspectionplan.api.dto.CharacteristicRequest;
import com.mes.quality.inspectionplan.domain.CharacteristicType;
import com.mes.quality.inspectionplan.domain.InspectionCharacteristic;
import com.mes.quality.inspectionplan.domain.InspectionPlan;
import com.mes.quality.inspectionplan.domain.InspectionPlanRevision;
import com.mes.quality.inspectionplan.domain.RecordingBasis;
import com.mes.quality.inspectionplan.domain.RevisionStatus;
import com.mes.quality.inspectionplan.repository.InspectionCharacteristicRepository;
import com.mes.quality.inspectionplan.repository.InspectionPlanRepository;
import com.mes.quality.inspectionplan.repository.InspectionPlanRevisionRepository;
import com.mes.quality.service.QualityConflictException;
import com.mes.quality.service.QualityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Transactional
public class CharacteristicService {

    private static final String PLAN_NOT_FOUND = "Inspection plan not found: ";

    private final InspectionPlanRepository planRepository;
    private final InspectionPlanRevisionRepository revisionRepository;
    private final InspectionCharacteristicRepository characteristicRepository;

    public CharacteristicService(InspectionPlanRepository planRepository,
                                 InspectionPlanRevisionRepository revisionRepository,
                                 InspectionCharacteristicRepository characteristicRepository) {
        this.planRepository = planRepository;
        this.revisionRepository = revisionRepository;
        this.characteristicRepository = characteristicRepository;
    }

    @Transactional(readOnly = true)
    public List<CharacteristicDto> list(UUID orgId, UUID planId, Integer revisionNumber) {
        InspectionPlan plan = requirePlan(orgId, planId);
        InspectionPlanRevision revision = (revisionNumber != null)
                ? revisionRepository.findByInspectionPlanIdAndRevision(plan.getId(), revisionNumber)
                    .orElseThrow(() -> new QualityNotFoundException(
                            "No revision " + revisionNumber + " for plan: " + planId))
                : displayRevision(plan);
        return characteristicRepository
                .findByPlanRevisionIdOrderByCharacteristicNumberAsc(revision.getId())
                .stream().map(CharacteristicMapper::toDto).toList();
    }

    public CharacteristicDto add(UUID orgId, UUID planId, CharacteristicRequest req) {
        InspectionPlanRevision draft = requireDraft(orgId, planId);
        if (characteristicRepository.existsByPlanRevisionIdAndCharacteristicNumber(
                draft.getId(), req.getCharacteristicNumber())) {
            throw new QualityConflictException(
                    "Characteristic number " + req.getCharacteristicNumber() + " already exists");
        }
        CharacteristicValidator.validate(req.getCharacteristicType(), req.getNominalValue(),
                req.getLowerLimit(), req.getUpperLimit(), req.getExpectedBoolean(),
                req.getExpression(), req.getSampleSizeRule(), req.getSampleSizeCount());

        InspectionCharacteristic entity = new InspectionCharacteristic();
        entity.setPlanRevision(draft);
        apply(entity, req);
        InspectionCharacteristic saved = characteristicRepository.save(entity);

        revalidateExpressions(allInRevision(draft.getId()));
        return CharacteristicMapper.toDto(saved);
    }

    public CharacteristicDto update(UUID orgId, UUID planId, UUID charId, CharacteristicRequest req) {
        InspectionPlanRevision draft = requireDraft(orgId, planId);
        InspectionCharacteristic entity = requireCharacteristic(draft.getId(), charId);

        if (!entity.getCharacteristicNumber().equals(req.getCharacteristicNumber())
                && characteristicRepository.existsByPlanRevisionIdAndCharacteristicNumber(
                        draft.getId(), req.getCharacteristicNumber())) {
            throw new QualityConflictException(
                    "Characteristic number " + req.getCharacteristicNumber() + " already exists");
        }
        CharacteristicValidator.validate(req.getCharacteristicType(), req.getNominalValue(),
                req.getLowerLimit(), req.getUpperLimit(), req.getExpectedBoolean(),
                req.getExpression(), req.getSampleSizeRule(), req.getSampleSizeCount());

        apply(entity, req);
        InspectionCharacteristic saved = characteristicRepository.save(entity);

        // Re-validate every expression in the revision — the edit may break dependents.
        revalidateExpressions(allInRevision(draft.getId()));
        return CharacteristicMapper.toDto(saved);
    }

    public void delete(UUID orgId, UUID planId, UUID charId) {
        InspectionPlanRevision draft = requireDraft(orgId, planId);
        InspectionCharacteristic target = requireCharacteristic(draft.getId(), charId);

        List<InspectionCharacteristic> dependents = allInRevision(draft.getId()).stream()
                .filter(c -> !c.getId().equals(charId))
                .filter(c -> c.getCharacteristicType() == CharacteristicType.CALCULATED)
                .filter(c -> ExpressionValidator.references(
                        c.getExpression(), target.getCharacteristicNumber()))
                .toList();
        if (!dependents.isEmpty()) {
            List<String> names = dependents.stream()
                    .map(c -> "C" + c.getCharacteristicNumber()).toList();
            throw new QualityConflictException(
                    "Cannot delete C" + target.getCharacteristicNumber()
                            + " — referenced by " + String.join(", ", names));
        }
        characteristicRepository.delete(target);
    }

    /** Copies all characteristics from one revision to another (copy-on-revision). */
    public void copyCharacteristics(UUID sourceRevisionId, InspectionPlanRevision targetRevision) {
        for (InspectionCharacteristic source : characteristicRepository
                .findByPlanRevisionIdOrderByCharacteristicNumberAsc(sourceRevisionId)) {
            InspectionCharacteristic copy = new InspectionCharacteristic();
            copy.setPlanRevision(targetRevision);
            copy.setCharacteristicNumber(source.getCharacteristicNumber());
            copy.setName(source.getName());
            copy.setDescription(source.getDescription());
            copy.setSource(source.getSource());
            copy.setCharacteristicType(source.getCharacteristicType());
            copy.setInspectionMethod(source.getInspectionMethod());
            copy.setGaugeType(source.getGaugeType());
            copy.setUnitOfMeasure(source.getUnitOfMeasure());
            copy.setSampleSizeRule(source.getSampleSizeRule());
            copy.setSampleSizeCount(source.getSampleSizeCount());
            copy.setRecordingBasis(source.getRecordingBasis());
            copy.setNominalValue(source.getNominalValue());
            copy.setLowerLimit(source.getLowerLimit());
            copy.setUpperLimit(source.getUpperLimit());
            copy.setExpectedBoolean(source.getExpectedBoolean());
            copy.setExpression(source.getExpression());
            copy.setCustomFields(source.getCustomFields());
            characteristicRepository.save(copy);
        }
    }

    /** Re-validates every CALCULATED expression in the given characteristic set; used by submit-gate too. */
    public void revalidateExpressions(List<InspectionCharacteristic> characteristics) {
        Map<Integer, CharacteristicType> typesByNumber = new HashMap<>();
        Map<Integer, String> expressionsByNumber = new HashMap<>();
        for (InspectionCharacteristic c : characteristics) {
            typesByNumber.put(c.getCharacteristicNumber(), c.getCharacteristicType());
            if (c.getCharacteristicType() == CharacteristicType.CALCULATED) {
                expressionsByNumber.put(c.getCharacteristicNumber(), c.getExpression());
            }
        }
        for (InspectionCharacteristic c : characteristics) {
            if (c.getCharacteristicType() == CharacteristicType.CALCULATED) {
                ExpressionValidator.validate(c.getCharacteristicNumber(), c.getExpression(),
                        typesByNumber, expressionsByNumber);
            }
        }
    }

    public List<InspectionCharacteristic> allInRevision(UUID revisionId) {
        return new ArrayList<>(characteristicRepository
                .findByPlanRevisionIdOrderByCharacteristicNumberAsc(revisionId));
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private void apply(InspectionCharacteristic entity, CharacteristicRequest req) {
        entity.setCharacteristicNumber(req.getCharacteristicNumber());
        entity.setName(req.getName());
        entity.setDescription(req.getDescription());
        entity.setSource(req.getSource());
        entity.setCharacteristicType(req.getCharacteristicType());
        entity.setInspectionMethod(req.getInspectionMethod());
        entity.setGaugeType(req.getGaugeType());
        entity.setUnitOfMeasure(req.getUnitOfMeasure());
        entity.setSampleSizeRule(req.getSampleSizeRule());
        entity.setSampleSizeCount(req.getSampleSizeCount());
        entity.setRecordingBasis(req.getRecordingBasis() != null
                ? req.getRecordingBasis() : defaultRecordingBasis(req.getCharacteristicType()));
        entity.setNominalValue(req.getNominalValue());
        entity.setLowerLimit(req.getLowerLimit());
        entity.setUpperLimit(req.getUpperLimit());
        entity.setExpectedBoolean(req.getExpectedBoolean());
        entity.setExpression(req.getExpression());
        entity.setCustomFields(req.getCustomFields());
    }

    private static RecordingBasis defaultRecordingBasis(CharacteristicType type) {
        return type == CharacteristicType.COMMON ? RecordingBasis.PER_LOT : RecordingBasis.PER_PIECE;
    }

    private InspectionPlanRevision displayRevision(InspectionPlan plan) {
        List<InspectionPlanRevision> all = revisionRepository.findByInspectionPlanId(plan.getId());
        for (RevisionStatus status : new RevisionStatus[]{
                RevisionStatus.APPROVED, RevisionStatus.PENDING_APPROVAL, RevisionStatus.DRAFT}) {
            var match = all.stream()
                    .filter(r -> r.getRevisionStatus() == status)
                    .max(Comparator.comparingInt(InspectionPlanRevision::getRevision));
            if (match.isPresent()) {
                return match.get();
            }
        }
        throw new QualityNotFoundException(PLAN_NOT_FOUND + plan.getId());
    }

    private InspectionPlanRevision requireDraft(UUID orgId, UUID planId) {
        InspectionPlan plan = requirePlan(orgId, planId);
        return revisionRepository
                .findByInspectionPlanIdAndRevisionStatus(plan.getId(), RevisionStatus.DRAFT)
                .orElseThrow(() -> new QualityConflictException(
                        "Characteristics can only be edited on a DRAFT revision: " + planId));
    }

    private InspectionPlan requirePlan(UUID orgId, UUID planId) {
        return planRepository.findByOrgIdAndId(orgId, planId)
                .orElseThrow(() -> new QualityNotFoundException(PLAN_NOT_FOUND + planId));
    }

    private InspectionCharacteristic requireCharacteristic(UUID revisionId, UUID charId) {
        return characteristicRepository.findById(charId)
                .filter(c -> c.getPlanRevision().getId().equals(revisionId))
                .orElseThrow(() -> new QualityNotFoundException("Characteristic not found: " + charId));
    }
}
