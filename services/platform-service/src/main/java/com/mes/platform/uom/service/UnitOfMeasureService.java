package com.mes.platform.uom.service;

import com.mes.platform.uom.api.dto.CreateUomRequest;
import com.mes.platform.uom.api.dto.PatchUomRequest;
import com.mes.platform.uom.api.dto.UnitOfMeasureDto;
import com.mes.platform.uom.domain.UnitOfMeasure;
import com.mes.platform.uom.repository.UnitOfMeasureRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class UnitOfMeasureService {

    private final UnitOfMeasureRepository repository;

    public UnitOfMeasureService(UnitOfMeasureRepository repository) {
        this.repository = repository;
    }

    public List<UnitOfMeasureDto> listActive() {
        return repository.findByActiveTrue().stream()
                .map(UnitOfMeasureDto::from)
                .toList();
    }

    public List<UnitOfMeasureDto> listAll() {
        return repository.findAll().stream()
                .map(UnitOfMeasureDto::from)
                .toList();
    }

    @Transactional
    public UnitOfMeasureDto create(CreateUomRequest request) {
        if (repository.existsByCode(request.code())) {
            throw new UomConflictException(request.code());
        }
        UnitOfMeasure uom = new UnitOfMeasure(
                request.code(), request.isoCode(), request.name(), request.category()
        );
        return UnitOfMeasureDto.from(repository.save(uom));
    }

    @Transactional
    public UnitOfMeasureDto patch(String code, PatchUomRequest request) {
        UnitOfMeasure uom = repository.findByCode(code)
                .orElseThrow(() -> new UomNotFoundException(code));
        if (request.isoCode() != null) {
            uom.setIsoCode(request.isoCode());
        }
        if (request.name() != null) {
            uom.setName(request.name());
        }
        if (request.active() != null) {
            uom.setActive(request.active());
        }
        return UnitOfMeasureDto.from(repository.save(uom));
    }
}
