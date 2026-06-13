package com.mes.quality.inspectionplan.repository;

import com.mes.quality.inspectionplan.domain.InspectionCharacteristic;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InspectionCharacteristicRepository extends JpaRepository<InspectionCharacteristic, UUID> {

    List<InspectionCharacteristic> findByPlanRevisionIdOrderByCharacteristicNumberAsc(UUID planRevisionId);

    Optional<InspectionCharacteristic> findByPlanRevisionIdAndCharacteristicNumber(
            UUID planRevisionId, Integer characteristicNumber);

    boolean existsByPlanRevisionIdAndCharacteristicNumber(UUID planRevisionId, Integer characteristicNumber);
}
