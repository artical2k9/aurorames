package com.mes.inventory.unit.bom.service;

import com.mes.inventory.bom.api.dto.BomExplosionNode;
import com.mes.inventory.bom.domain.BillOfMaterials;
import com.mes.inventory.bom.repository.BomLineRepository;
import com.mes.inventory.bom.repository.BomRepository;
import com.mes.inventory.bom.service.BomExplosionService;
import com.mes.inventory.bom.service.BomNotFoundException;
import com.mes.inventory.bom.service.BomValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BomExplosionServiceTest {

    @Mock
    BomRepository bomRepository;

    @Mock
    BomLineRepository bomLineRepository;

    private BomExplosionService service;

    @BeforeEach
    void setUp() {
        service = new BomExplosionService(bomRepository, bomLineRepository, 5);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static BillOfMaterials bom(UUID orgId, UUID bomId, UUID parentItemId) {
        BillOfMaterials b = new BillOfMaterials();
        b.setOrgId(orgId);
        b.setParentItemId(parentItemId);
        b.setBomRevision("REV-A");
        return b;
    }

    /**
     * row indices: 0=componentId 1=parentId 2=depth 3=partNo 4=rev 5=desc
     * 6=uom 7=riskLevel 8=status 9=method 10=fromDate 11=toDate
     * 12=fromUnit 13=toUnit 14=findNumber 15=qty 16=makeBuyCode
     */
    private static Object[] row(String componentId, String parentId, int depth, String partNumber) {
        return new Object[]{
            componentId, parentId, depth, partNumber, "A",
            "Description", "EA", "NONE", "ACTIVE",
            null, null, null, null, null,
            "F-001", "2.000", "MAKE"
        };
    }

    private static Object[] rowWithDates(String componentId, String parentId,
                                          String fromDate, String toDate) {
        return new Object[]{
            componentId, parentId, 1, "PN-DATE", "A",
            "Date part", "EA", "NONE", "ACTIVE",
            "DATE", fromDate, toDate, null, null,
            "F-DATE", "1.000", "BUY"
        };
    }

    private static Object[] rowWithUnits(String componentId, String parentId,
                                          String fromUnit, String toUnit) {
        return new Object[]{
            componentId, parentId, 1, "PN-UNIT", "A",
            "Unit part", "EA", "NONE", "ACTIVE",
            "UNIT", null, null, fromUnit, toUnit,
            "F-UNIT", "1.000", "BUY"
        };
    }

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Test
    void explode_throwsBomNotFoundException_whenBomDoesNotExist() {
        UUID orgId = UUID.randomUUID();
        UUID bomId = UUID.randomUUID();
        when(bomRepository.findByOrgIdAndId(orgId, bomId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.explode(orgId, bomId, "flat", null, null, 0))
                .isInstanceOf(BomNotFoundException.class);
    }

    @Test
    void explode_returnsEmptyList_whenBomHasNoLines() {
        UUID orgId = UUID.randomUUID();
        UUID bomId = UUID.randomUUID();
        UUID parentItemId = UUID.randomUUID();

        when(bomRepository.findByOrgIdAndId(orgId, bomId))
                .thenReturn(Optional.of(bom(orgId, bomId, parentItemId)));
        when(bomLineRepository.findExplosionRows(bomId)).thenReturn(Collections.emptyList());

        List<BomExplosionNode> result = service.explode(orgId, bomId, "flat", null, null, 0);
        assertThat(result).isEmpty();
    }

    @Test
    void explode_returnsFlatNodes_forFlatFormat() {
        UUID orgId = UUID.randomUUID();
        UUID bomId = UUID.randomUUID();
        UUID parentItemId = UUID.randomUUID();
        String compId = UUID.randomUUID().toString();

        when(bomRepository.findByOrgIdAndId(orgId, bomId))
                .thenReturn(Optional.of(bom(orgId, bomId, parentItemId)));
        when(bomLineRepository.findExplosionRows(bomId))
                .thenReturn(Collections.singletonList(
                        row(compId, parentItemId.toString(), 1, "PN-COMP")));

        List<BomExplosionNode> result = service.explode(orgId, bomId, "flat", null, null, 0);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getPartNumber()).isEqualTo("PN-COMP");
        assertThat(result.get(0).getDepth()).isEqualTo(1);
        assertThat(result.get(0).getMakeBuyCode()).isEqualTo("MAKE");
    }

    @Test
    void explode_buildsTree_forIndentedFormat() {
        UUID orgId = UUID.randomUUID();
        UUID bomId = UUID.randomUUID();
        UUID parentItemId = UUID.randomUUID();
        String compId = UUID.randomUUID().toString();
        String grandchildId = UUID.randomUUID().toString();

        Object[] parentRow = new Object[]{
            compId, parentItemId.toString(), 1, "COMP", "A",
            "Comp desc", "EA", "NONE", "ACTIVE", null, null, null, null, null,
            "F-001", "1.000", "MAKE"
        };
        Object[] childRow = new Object[]{
            grandchildId, compId, 2, "SUBCOMP", "A",
            "Subcomp desc", "EA", "NONE", "ACTIVE", null, null, null, null, null,
            "F-002", "2.000", "BUY"
        };

        when(bomRepository.findByOrgIdAndId(orgId, bomId))
                .thenReturn(Optional.of(bom(orgId, bomId, parentItemId)));
        when(bomLineRepository.findExplosionRows(bomId))
                .thenReturn(Arrays.asList(parentRow, childRow));

        List<BomExplosionNode> result = service.explode(orgId, bomId, "indented", null, null, 0);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getPartNumber()).isEqualTo("COMP");
        assertThat(result.get(0).getChildren()).hasSize(1);
        assertThat(result.get(0).getChildren().get(0).getPartNumber()).isEqualTo("SUBCOMP");
    }

    @Test
    void explode_throwsValidationException_whenDepthExceedsMaxDepth() {
        UUID orgId = UUID.randomUUID();
        UUID bomId = UUID.randomUUID();
        UUID parentItemId = UUID.randomUUID();

        when(bomRepository.findByOrgIdAndId(orgId, bomId))
                .thenReturn(Optional.of(bom(orgId, bomId, parentItemId)));
        when(bomLineRepository.findExplosionRows(bomId))
                .thenReturn(Collections.singletonList(
                        row(UUID.randomUUID().toString(), parentItemId.toString(), 6, "DEEP")));

        assertThatThrownBy(() -> service.explode(orgId, bomId, "flat", null, null, 0))
                .isInstanceOf(BomValidationException.class)
                .hasMessageContaining("maximum allowed depth");
    }

    @Test
    void explode_filtersToRequestMaxDepth_whenLowerThanServiceMax() {
        UUID orgId = UUID.randomUUID();
        UUID bomId = UUID.randomUUID();
        UUID parentItemId = UUID.randomUUID();

        Object[] depth1 = row(UUID.randomUUID().toString(), parentItemId.toString(), 1, "D1");
        Object[] depth2 = row(UUID.randomUUID().toString(), UUID.randomUUID().toString(), 2, "D2");

        when(bomRepository.findByOrgIdAndId(orgId, bomId))
                .thenReturn(Optional.of(bom(orgId, bomId, parentItemId)));
        when(bomLineRepository.findExplosionRows(bomId)).thenReturn(Arrays.asList(depth1, depth2));

        List<BomExplosionNode> result = service.explode(orgId, bomId, "flat", null, null, 1);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getPartNumber()).isEqualTo("D1");
    }

    @Test
    void explode_filtersByDate_includesEffectiveLine() {
        UUID orgId = UUID.randomUUID();
        UUID bomId = UUID.randomUUID();
        UUID parentItemId = UUID.randomUUID();

        when(bomRepository.findByOrgIdAndId(orgId, bomId))
                .thenReturn(Optional.of(bom(orgId, bomId, parentItemId)));
        when(bomLineRepository.findExplosionRows(bomId))
                .thenReturn(Collections.singletonList(
                        rowWithDates(UUID.randomUUID().toString(), parentItemId.toString(),
                                "2025-01-01", "2025-12-31")));

        List<BomExplosionNode> result = service.explode(
                orgId, bomId, "flat", LocalDate.of(2025, 6, 15), null, 0);

        assertThat(result).hasSize(1);
    }

    @Test
    void explode_filtersByDate_throwsWhenNoEffectiveLineRemains() {
        UUID orgId = UUID.randomUUID();
        UUID bomId = UUID.randomUUID();
        UUID parentItemId = UUID.randomUUID();

        when(bomRepository.findByOrgIdAndId(orgId, bomId))
                .thenReturn(Optional.of(bom(orgId, bomId, parentItemId)));
        when(bomLineRepository.findExplosionRows(bomId))
                .thenReturn(Collections.singletonList(
                        rowWithDates(UUID.randomUUID().toString(), parentItemId.toString(),
                                "2024-01-01", "2024-12-31")));

        assertThatThrownBy(() -> service.explode(
                orgId, bomId, "flat", LocalDate.of(2025, 6, 15), null, 0))
                .isInstanceOf(BomValidationException.class)
                .hasMessageContaining("No effective BOM line");
    }

    @Test
    void explode_filtersByDate_includesOpenEndedLine() {
        UUID orgId = UUID.randomUUID();
        UUID bomId = UUID.randomUUID();
        UUID parentItemId = UUID.randomUUID();

        when(bomRepository.findByOrgIdAndId(orgId, bomId))
                .thenReturn(Optional.of(bom(orgId, bomId, parentItemId)));
        when(bomLineRepository.findExplosionRows(bomId))
                .thenReturn(Collections.singletonList(
                        rowWithDates(UUID.randomUUID().toString(), parentItemId.toString(),
                                "2020-01-01", null)));

        List<BomExplosionNode> result = service.explode(
                orgId, bomId, "flat", LocalDate.of(2025, 6, 15), null, 0);

        assertThat(result).hasSize(1);
    }

    @Test
    void explode_filtersByDate_includesLineWithNullFromDate() {
        UUID orgId = UUID.randomUUID();
        UUID bomId = UUID.randomUUID();
        UUID parentItemId = UUID.randomUUID();

        when(bomRepository.findByOrgIdAndId(orgId, bomId))
                .thenReturn(Optional.of(bom(orgId, bomId, parentItemId)));
        when(bomLineRepository.findExplosionRows(bomId))
                .thenReturn(Collections.singletonList(
                        rowWithDates(UUID.randomUUID().toString(), parentItemId.toString(),
                                null, "2025-12-31")));

        List<BomExplosionNode> result = service.explode(
                orgId, bomId, "flat", LocalDate.of(2025, 6, 15), null, 0);

        assertThat(result).hasSize(1);
    }

    @Test
    void explode_filtersByUnit_includesEffectiveLine() {
        UUID orgId = UUID.randomUUID();
        UUID bomId = UUID.randomUUID();
        UUID parentItemId = UUID.randomUUID();

        when(bomRepository.findByOrgIdAndId(orgId, bomId))
                .thenReturn(Optional.of(bom(orgId, bomId, parentItemId)));
        when(bomLineRepository.findExplosionRows(bomId))
                .thenReturn(Collections.singletonList(
                        rowWithUnits(UUID.randomUUID().toString(), parentItemId.toString(),
                                "100", "200")));

        List<BomExplosionNode> result = service.explode(
                orgId, bomId, "flat", null, "150", 0);

        assertThat(result).hasSize(1);
    }

    @Test
    void explode_filtersByUnit_throwsWhenNoEffectiveLineRemains() {
        UUID orgId = UUID.randomUUID();
        UUID bomId = UUID.randomUUID();
        UUID parentItemId = UUID.randomUUID();

        when(bomRepository.findByOrgIdAndId(orgId, bomId))
                .thenReturn(Optional.of(bom(orgId, bomId, parentItemId)));
        when(bomLineRepository.findExplosionRows(bomId))
                .thenReturn(Collections.singletonList(
                        rowWithUnits(UUID.randomUUID().toString(), parentItemId.toString(),
                                "100", "200")));

        assertThatThrownBy(() -> service.explode(orgId, bomId, "flat", null, "300", 0))
                .isInstanceOf(BomValidationException.class)
                .hasMessageContaining("No effective BOM line");
    }

    @Test
    void explode_filtersByUnit_includesLineWithNullFromUnit() {
        UUID orgId = UUID.randomUUID();
        UUID bomId = UUID.randomUUID();
        UUID parentItemId = UUID.randomUUID();

        when(bomRepository.findByOrgIdAndId(orgId, bomId))
                .thenReturn(Optional.of(bom(orgId, bomId, parentItemId)));
        when(bomLineRepository.findExplosionRows(bomId))
                .thenReturn(Collections.singletonList(
                        rowWithUnits(UUID.randomUUID().toString(), parentItemId.toString(),
                                null, "500")));

        List<BomExplosionNode> result = service.explode(
                orgId, bomId, "flat", null, "250", 0);

        assertThat(result).hasSize(1);
    }

    @Test
    void explode_setsCounterfeitRiskAlert_forHighRisk() {
        UUID orgId = UUID.randomUUID();
        UUID bomId = UUID.randomUUID();
        UUID parentItemId = UUID.randomUUID();

        Object[] highRisk = new Object[]{
            UUID.randomUUID().toString(), parentItemId.toString(), 1, "PN-H", "A",
            "High risk", "EA", "HIGH", "ACTIVE", null, null, null, null, null,
            "F-001", "1.000", "BUY"
        };

        when(bomRepository.findByOrgIdAndId(orgId, bomId))
                .thenReturn(Optional.of(bom(orgId, bomId, parentItemId)));
        when(bomLineRepository.findExplosionRows(bomId))
                .thenReturn(Collections.singletonList(highRisk));

        List<BomExplosionNode> result = service.explode(orgId, bomId, "flat", null, null, 0);

        assertThat(result.get(0).isCounterfeitRiskAlert()).isTrue();
    }

    @Test
    void explode_setsCounterfeitRiskAlert_forCriticalRisk() {
        UUID orgId = UUID.randomUUID();
        UUID bomId = UUID.randomUUID();
        UUID parentItemId = UUID.randomUUID();

        Object[] criticalRisk = new Object[]{
            UUID.randomUUID().toString(), parentItemId.toString(), 1, "PN-C", "A",
            "Critical risk", "EA", "CRITICAL", "ACTIVE", null, null, null, null, null,
            "F-001", "1.000", "MAKE"
        };

        when(bomRepository.findByOrgIdAndId(orgId, bomId))
                .thenReturn(Optional.of(bom(orgId, bomId, parentItemId)));
        when(bomLineRepository.findExplosionRows(bomId))
                .thenReturn(Collections.singletonList(criticalRisk));

        assertThat(service.explode(orgId, bomId, "flat", null, null, 0)
                .get(0).isCounterfeitRiskAlert()).isTrue();
    }

    @Test
    void explode_setsComponentObsoleted_whenStatusIsObsolete() {
        UUID orgId = UUID.randomUUID();
        UUID bomId = UUID.randomUUID();
        UUID parentItemId = UUID.randomUUID();

        Object[] obsolete = new Object[]{
            UUID.randomUUID().toString(), parentItemId.toString(), 1, "PN-OBS", "A",
            "Obsolete", "EA", "NONE", "OBSOLETE", null, null, null, null, null,
            "F-001", "1.000", "MAKE"
        };

        when(bomRepository.findByOrgIdAndId(orgId, bomId))
                .thenReturn(Optional.of(bom(orgId, bomId, parentItemId)));
        when(bomLineRepository.findExplosionRows(bomId))
                .thenReturn(Collections.singletonList(obsolete));

        assertThat(service.explode(orgId, bomId, "flat", null, null, 0)
                .get(0).isComponentObsoleted()).isTrue();
    }

    @Test
    void explode_setsQuantity_fromRowValue() {
        UUID orgId = UUID.randomUUID();
        UUID bomId = UUID.randomUUID();
        UUID parentItemId = UUID.randomUUID();

        Object[] qtyRow = new Object[]{
            UUID.randomUUID().toString(), parentItemId.toString(), 1, "PN-Q", "A",
            "Qty part", "EA", "NONE", "ACTIVE", null, null, null, null, null,
            "F-001", "5.500", "MAKE"
        };

        when(bomRepository.findByOrgIdAndId(orgId, bomId))
                .thenReturn(Optional.of(bom(orgId, bomId, parentItemId)));
        when(bomLineRepository.findExplosionRows(bomId))
                .thenReturn(Collections.singletonList(qtyRow));

        assertThat(service.explode(orgId, bomId, "flat", null, null, 0)
                .get(0).getQuantity()).isEqualByComparingTo("5.500");
    }

    @Test
    void explode_leavesQuantityNull_whenRowHasNullQty() {
        UUID orgId = UUID.randomUUID();
        UUID bomId = UUID.randomUUID();
        UUID parentItemId = UUID.randomUUID();

        Object[] nullQtyRow = new Object[]{
            UUID.randomUUID().toString(), parentItemId.toString(), 1, "PN-NQ", "A",
            "No qty", "EA", "NONE", "ACTIVE", null, null, null, null, null,
            "F-001", null, "MAKE"
        };

        when(bomRepository.findByOrgIdAndId(orgId, bomId))
                .thenReturn(Optional.of(bom(orgId, bomId, parentItemId)));
        when(bomLineRepository.findExplosionRows(bomId))
                .thenReturn(Collections.singletonList(nullQtyRow));

        assertThat(service.explode(orgId, bomId, "flat", null, null, 0)
                .get(0).getQuantity()).isNull();
    }
}
