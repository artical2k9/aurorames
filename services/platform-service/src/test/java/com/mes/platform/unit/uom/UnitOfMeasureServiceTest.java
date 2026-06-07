package com.mes.platform.unit.uom;

import com.mes.platform.uom.api.dto.CreateUomRequest;
import com.mes.platform.uom.api.dto.PatchUomRequest;
import com.mes.platform.uom.api.dto.UnitOfMeasureDto;
import com.mes.platform.uom.domain.UnitOfMeasure;
import com.mes.platform.uom.repository.UnitOfMeasureRepository;
import com.mes.platform.uom.service.UomConflictException;
import com.mes.platform.uom.service.UomNotFoundException;
import com.mes.platform.uom.service.UnitOfMeasureService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UnitOfMeasureServiceTest {

    @Mock UnitOfMeasureRepository repository;
    @InjectMocks UnitOfMeasureService service;

    @Test
    void listActive_returnsOnlyActiveEntries() {
        UnitOfMeasure kg = new UnitOfMeasure("KG", "KGM", "Kilogram", "Mass");
        when(repository.findByActiveTrue()).thenReturn(List.of(kg));

        List<UnitOfMeasureDto> result = service.listActive();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).code()).isEqualTo("KG");
    }

    @Test
    void create_newCode_persistsAndReturnsDto() {
        CreateUomRequest req = new CreateUomRequest("XT1", "X1", "Test unit", "Miscellaneous");
        UnitOfMeasure saved = new UnitOfMeasure("XT1", "X1", "Test unit", "Miscellaneous");
        when(repository.existsByCode("XT1")).thenReturn(false);
        when(repository.save(any())).thenReturn(saved);

        UnitOfMeasureDto dto = service.create(req);

        assertThat(dto.code()).isEqualTo("XT1");
        assertThat(dto.name()).isEqualTo("Test unit");
    }

    @Test
    void create_duplicateCode_throwsConflict() {
        when(repository.existsByCode("KG")).thenReturn(true);

        assertThatThrownBy(() -> service.create(
                new CreateUomRequest("KG", "KGM", "Kilogram", "Mass")))
                .isInstanceOf(UomConflictException.class);
    }

    @Test
    void patch_existingCode_updatesFields() {
        UnitOfMeasure uom = new UnitOfMeasure("KG", "KGM", "Kilogram", "Mass");
        when(repository.findByCode("KG")).thenReturn(Optional.of(uom));
        when(repository.save(uom)).thenReturn(uom);

        UnitOfMeasureDto dto = service.patch("KG", new PatchUomRequest(null, "Kilogramme", null));

        assertThat(dto.name()).isEqualTo("Kilogramme");
    }

    @Test
    void patch_unknownCode_throwsNotFound() {
        when(repository.findByCode("ZZZ")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.patch("ZZZ", new PatchUomRequest(null, "X", null)))
                .isInstanceOf(UomNotFoundException.class);
    }

    @Test
    void patch_activeFlag_deactivatesEntry() {
        UnitOfMeasure uom = new UnitOfMeasure("KG", "KGM", "Kilogram", "Mass");
        when(repository.findByCode("KG")).thenReturn(Optional.of(uom));
        when(repository.save(uom)).thenReturn(uom);

        UnitOfMeasureDto dto = service.patch("KG", new PatchUomRequest(null, null, false));

        assertThat(dto.active()).isFalse();
    }
}
