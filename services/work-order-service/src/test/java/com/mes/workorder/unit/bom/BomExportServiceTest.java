package com.mes.workorder.unit.bom;

import com.mes.workorder.bom.api.dto.BomExplosionNode;
import com.mes.workorder.bom.domain.BillOfMaterials;
import com.mes.workorder.bom.service.BomExportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class BomExportServiceTest {

    BomExportService service;

    @BeforeEach
    void setUp() {
        service = new BomExportService();
    }

    // ── toCsv ─────────────────────────────────────────────────────────────────

    @Test
    void toCsvContainsHeaderRow() {
        String csv = new String(service.toCsv(List.of()), StandardCharsets.UTF_8);
        assertThat(csv).startsWith("Find #,Part Number,Rev,Description,Qty,Make/Buy,Ctft Risk");
    }

    @Test
    void toCsvContainsNodePartNumber() {
        BomExplosionNode node = node("PN-001", "A", "Widget", false, 1);
        String csv = new String(service.toCsv(List.of(node)), StandardCharsets.UTF_8);
        assertThat(csv).contains("PN-001");
        assertThat(csv).contains("Widget");
    }

    @Test
    void toCsvShowsHighForCounterfeitRiskAlert() {
        BomExplosionNode node = node("PN-002", "B", "High risk part", true, 1);
        String csv = new String(service.toCsv(List.of(node)), StandardCharsets.UTF_8);
        assertThat(csv).contains("HIGH");
    }

    @Test
    void toCsvNoRiskColumnForSafePart() {
        BomExplosionNode node = node("PN-003", "A", "Safe part", false, 1);
        String csv = new String(service.toCsv(List.of(node)), StandardCharsets.UTF_8);
        String dataLine = csv.lines().skip(1).findFirst().orElse("");
        assertThat(dataLine).doesNotContain("HIGH");
    }

    @Test
    void toCsvEscapesCommasInDescription() {
        BomExplosionNode node = node("PN-004", "A", "Bolt, hex, M6", false, 1);
        String csv = new String(service.toCsv(List.of(node)), StandardCharsets.UTF_8);
        assertThat(csv).contains("\"Bolt, hex, M6\"");
    }

    @Test
    void toCsvIndentsChildNodesByDepth() {
        BomExplosionNode child = node("CHILD-001", "A", "Child part", false, 2);
        String csv = new String(service.toCsv(List.of(child)), StandardCharsets.UTF_8);
        assertThat(csv).contains("  CHILD-001");
    }

    @Test
    void toCsvFlattensNestedChildren() {
        BomExplosionNode child = node("CHILD-001", "A", "Child", false, 2);
        BomExplosionNode parent = nodeWithChildren("PARENT-001", "A", "Parent", false, 1, List.of(child));
        String csv = new String(service.toCsv(List.of(parent)), StandardCharsets.UTF_8);
        assertThat(csv).contains("PARENT-001");
        assertThat(csv).contains("CHILD-001");
    }

    @Test
    void toCsvEmptyListProducesHeaderOnly() {
        String csv = new String(service.toCsv(List.of()), StandardCharsets.UTF_8);
        assertThat(csv.lines().count()).isEqualTo(1L);
    }

    // ── toPdf ─────────────────────────────────────────────────────────────────

    @Test
    void toPdfReturnsPdfMagicBytes() {
        BillOfMaterials bom = bomEntity("A");
        byte[] pdf = service.toPdf(bom, List.of(node("PN-001", "A", "Widget", false, 1)));
        assertThat(pdf).startsWith(new byte[]{'%', 'P', 'D', 'F'});
    }

    @Test
    void toPdfSucceedsWithEmptyNodeList() {
        BillOfMaterials bom = bomEntity("A");
        byte[] pdf = service.toPdf(bom, List.of());
        assertThat(pdf).isNotEmpty();
        assertThat(pdf).startsWith(new byte[]{'%', 'P', 'D', 'F'});
    }

    @Test
    void toPdfSucceedsWithHighRiskNode() {
        BillOfMaterials bom = bomEntity("B");
        byte[] pdf = service.toPdf(bom, List.of(node("PN-HIGH", "A", "Risky part", true, 1)));
        assertThat(pdf).isNotEmpty();
    }

    @Test
    void toPdfSucceedsWithNestedChildren() {
        BomExplosionNode child = node("CHILD", "A", "Child part", false, 2);
        BomExplosionNode parent = nodeWithChildren("PARENT", "A", "Parent part", false, 1, List.of(child));
        BillOfMaterials bom = bomEntity("C");
        byte[] pdf = service.toPdf(bom, List.of(parent));
        assertThat(pdf).startsWith(new byte[]{'%', 'P', 'D', 'F'});
    }

    @Test
    void toPdfHandlesNullRevisionAndDescription() {
        BomExplosionNode node = node("PN-NULL", null, null, false, 1);
        BillOfMaterials bom = bomEntity("A");
        byte[] pdf = service.toPdf(bom, List.of(node));
        assertThat(pdf).isNotEmpty();
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private BomExplosionNode node(String pn, String rev, String desc, boolean risk, int depth) {
        BomExplosionNode n = new BomExplosionNode();
        n.setComponentItemId(UUID.randomUUID().toString());
        n.setParentItemId(UUID.randomUUID().toString());
        n.setPartNumber(pn);
        n.setRevision(rev);
        n.setDescription(desc);
        n.setUnitOfMeasure("EA");
        n.setCounterfeitRiskAlert(risk);
        n.setDepth(depth);
        return n;
    }

    private BomExplosionNode nodeWithChildren(String pn, String rev, String desc,
                                              boolean risk, int depth,
                                              List<BomExplosionNode> children) {
        BomExplosionNode n = node(pn, rev, desc, risk, depth);
        n.setChildren(children);
        return n;
    }

    private BillOfMaterials bomEntity(String revision) {
        BillOfMaterials bom = new BillOfMaterials();
        bom.setOrgId(UUID.randomUUID());
        bom.setParentItemId(UUID.randomUUID());
        bom.setBomRevision(revision);
        return bom;
    }
}
