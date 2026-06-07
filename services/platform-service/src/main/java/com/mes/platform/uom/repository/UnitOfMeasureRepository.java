package com.mes.platform.uom.repository;

import com.mes.platform.uom.domain.UnitOfMeasure;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UnitOfMeasureRepository extends JpaRepository<UnitOfMeasure, UUID> {

    List<UnitOfMeasure> findByActiveTrue();

    List<UnitOfMeasure> findAll();

    Optional<UnitOfMeasure> findByCode(String code);

    boolean existsByCode(String code);
}
