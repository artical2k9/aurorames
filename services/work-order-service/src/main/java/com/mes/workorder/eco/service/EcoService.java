package com.mes.workorder.eco.service;

import com.mes.workorder.eco.api.dto.CreateEcoRequest;
import com.mes.workorder.eco.api.dto.EcoDto;
import com.mes.workorder.eco.api.dto.EcoMapper;
import com.mes.workorder.eco.domain.EngineeringChangeOrder;
import com.mes.workorder.eco.domain.EcoStatus;
import com.mes.workorder.eco.repository.EcoRepository;
import com.mes.workorder.kafka.EcoEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashSet;
import java.util.Locale;
import java.util.UUID;

@Service
@Transactional
public class EcoService {

    private final EcoRepository ecoRepository;
    private final EcoEventPublisher ecoEventPublisher;

    public EcoService(EcoRepository ecoRepository, EcoEventPublisher ecoEventPublisher) {
        this.ecoRepository = ecoRepository;
        this.ecoEventPublisher = ecoEventPublisher;
    }

    public EcoDto create(UUID orgId, CreateEcoRequest req, String initiatedBy) {
        boolean concurrentWarning = false;
        for (UUID itemId : req.getAffectedItemIds()) {
            if (ecoRepository.countOpenEcosForItemId(orgId, itemId) > 0) {
                concurrentWarning = true;
                break;
            }
        }

        EngineeringChangeOrder eco = new EngineeringChangeOrder();
        eco.setOrgId(orgId);
        eco.setTitle(req.getTitle());
        eco.setDescription(req.getDescription());
        eco.setStatus(EcoStatus.DRAFT);
        eco.setInitiatedBy(initiatedBy);
        eco.setEcoNumber("ECO-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT));
        eco.setAffectedItemIds(new HashSet<>(req.getAffectedItemIds()));

        EngineeringChangeOrder saved = ecoRepository.save(eco);
        EcoDto dto = EcoMapper.toDto(saved);
        dto.setConcurrentEcoWarning(concurrentWarning);
        return dto;
    }

    @Transactional(readOnly = true)
    public EcoDto getEco(UUID orgId, UUID ecoId) {
        return EcoMapper.toDto(ecoRepository.findByOrgIdAndId(orgId, ecoId)
                .orElseThrow(() -> new EcoNotFoundException("ECO not found: " + ecoId)));
    }

    public EcoDto approve(UUID orgId, UUID ecoId, String approver) {
        EngineeringChangeOrder eco = ecoRepository.findByOrgIdAndId(orgId, ecoId)
                .orElseThrow(() -> new EcoNotFoundException("ECO not found: " + ecoId));

        if (eco.getStatus() != EcoStatus.DRAFT) {
            throw new EcoConflictException("ECO is not in DRAFT status: " + eco.getStatus());
        }

        eco.setStatus(EcoStatus.APPROVED);
        eco.setApprovedBy(approver);
        eco.setApprovedAt(Instant.now());
        EngineeringChangeOrder saved = ecoRepository.save(eco);
        ecoEventPublisher.publishApproved(saved);
        return EcoMapper.toDto(saved);
    }

    public void addOutputBom(UUID ecoId, UUID bomId) {
        EngineeringChangeOrder eco = ecoRepository.findById(ecoId)
                .orElseThrow(() -> new EcoNotFoundException("ECO not found: " + ecoId));
        eco.getOutputBomIds().add(bomId);
        ecoRepository.save(eco);
    }
}
