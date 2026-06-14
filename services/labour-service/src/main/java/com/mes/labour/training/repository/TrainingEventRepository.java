package com.mes.labour.training.repository;

import com.mes.labour.training.domain.TrainingEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface TrainingEventRepository extends JpaRepository<TrainingEvent, UUID> {

    Optional<TrainingEvent> findByOrgIdAndId(UUID orgId, UUID id);

    Page<TrainingEvent> findAllByOrgIdOrderByTrainingDateDesc(UUID orgId, Pageable pageable);
}
