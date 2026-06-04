package com.mes.inventory.unit.bom.service;

import com.mes.inventory.bom.api.dto.BomExplosionNode;
import com.mes.inventory.bom.domain.BillOfMaterials;
import com.mes.inventory.bom.service.BomExportService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class BomExportServiceTest {

    private final BomExportService service = new BomExportService();

    @Test
    void toCsv_returnsHeaderRow_forEmptyNodeList() {
        byte[] csv = service.toCsv(Collections.emptyList());
        String text = new String(csv, java.nio.charset.StandardCharsets.UTF_8);
        assertThat(text).startsWith("Find #,Part Number,Rev,Description,Qty,Make/Buy,Ctft Risk");
    }

    @Test
    void toCsv_returnsRowForEachNode() {
        BomExplosionNode node = node("PN-001", "A", "Test part", 1, false);
        byte[] csv = service.toCsv(List.of(node));
        String text = new String(csv, java.nio.charset.StandardCharsets.UTF_8);
        assertThat(text).contains("PN-001");
        assertThat(text).contains("Test part");
    }

    @Test
    void toCsv_marksCounterfeitRiskHigh_whenAlertSet() {
        BomExplosionNode node = node("PN-RISK", "A", "Risk part", 1, true);
        byte[] csv = service.toCsv(List.of(node));
        String text = new String(csv, java.nio.charset.StandardCharsets.UTF_8);
        assertThat(text).contains("HIGH");
    }

    @Test
    void toCsv_escapesCommaInPartNumber() {
        BomExplosionNode node = node("PN,COMMA", "A", "desc", 1, false);
        byte[] csv = service.toCsv(List.of(node));
        String text = new String(csv, java.nio.charset.StandardCharsets.UTF_8);
        assertThat(text).contains("\"PN,COMMA\"");
    }

    @Test
    void toCsv_escapesQuoteInValue() {
        BomExplosionNode node = node("PN\"QUOTE", "A", "desc", 1, false);
        byte[] csv = service.toCsv(List.of(node));
        String text = new String(csv, java.nio.charset.StandardCharsets.UTF_8);
        assertThat(text).contains("\"PN\"\"QUOTE\"");
    }

    @Test
    void toCsv_includesNestedNodes_viaFlatten() {
        BomExplosionNode parent = node("PARENT", "A", "parent desc", 1, false);
        BomExplosionNode child = node("CHILD", "B", "child desc", 2, false);
        parent.setChildren(List.of(child));

        byte[] csv = service.toCsv(List.of(parent));
        String text = new String(csv, java.nio.charset.StandardCharsets.UTF_8);
        assertThat(text).contains("PARENT");
        assertThat(text).contains("CHILD");
    }

    @Test
    void toPdf_returnsNonEmptyByteArray() {
        BillOfMaterials bom = new BillOfMaterials();
        bom.setBomRevision("REV-A");
        bom.setParentItemId(UUID.randomUUID());
        bom.setOrgId(UUID.randomUUID());

        BomExplosionNode node = node("PN-PDF", "A", "PDF part", 1, false);

        byte[] pdf = service.toPdf(bom, List.of(node));
        assertThat(pdf).isNotEmpty();
        // PDF files start with the %PDF magic bytes
        assertThat(new String(pdf, 0, 4)).isEqualTo("%PDF");
    }

    @Test
    void toPdf_handlesNullRevisionAndDescription() {
        BillOfMaterials bom = new BillOfMaterials();
        bom.setBomRevision("REV-B");
        bom.setParentItemId(UUID.randomUUID());
        bom.setOrgId(UUID.randomUUID());

        BomExplosionNode node = new BomExplosionNode();
        node.setPartNumber("PN-NULL");
        node.setRevision(null);
        node.setDescription(null);
        node.setDepth(1);
        node.setCounterfeitRiskAlert(false);

        byte[] pdf = service.toPdf(bom, List.of(node));
        assertThat(pdf).isNotEmpty();
    }

    @Test
    void toPdf_handlesCounterfeitRiskAlert() {
        BillOfMaterials bom = new BillOfMaterials();
        bom.setBomRevision("REV-C");
        bom.setParentItemId(UUID.randomUUID());
        bom.setOrgId(UUID.randomUUID());

        BomExplosionNode node = node("PN-RISK", "A", "Risky part", 1, true);

        byte[] pdf = service.toPdf(bom, List.of(node));
        assertThat(pdf).isNotEmpty();
    }

    @Test
    void toPdf_handlesDeepNestedNodes() {
        BillOfMaterials bom = new BillOfMaterials();
        bom.setBomRevision("REV-DEEP");
        bom.setParentItemId(UUID.randomUUID());
        bom.setOrgId(UUID.randomUUID());

        BomExplosionNode parent = node("PARENT", "A", "parent", 1, false);
        BomExplosionNode child = node("CHILD", "A", "child", 2, false);
        BomExplosionNode grandchild = node("GRANDCHILD", "A", "grandchild", 3, false);
        child.setChildren(List.of(grandchild));
        parent.setChildren(List.of(child));

        byte[] pdf = service.toPdf(bom, List.of(parent));
        assertThat(pdf).isNotEmpty();
    }

    @Test
    void toPdf_handlesManyNodes_paginationPath() {
        BillOfMaterials bom = new BillOfMaterials();
        bom.setBomRevision("REV-MANY");
        bom.setParentItemId(UUID.randomUUID());
        bom.setOrgId(UUID.randomUUID());

        List<BomExplosionNode> nodes = new ArrayList<>();
        for (int i = 0; i < 60; i++) {
            nodes.add(node("PN-" + i, "A", "Part " + i + " description text here", 1, i % 5 == 0));
        }

        byte[] pdf = service.toPdf(bom, nodes);
        assertThat(pdf).isNotEmpty();
    }

    @Test
    void toCsv_indentsNestedNodes() {
        BomExplosionNode parent = node("PARENT", "A", "parent", 1, false);
        BomExplosionNode child = node("CHILD", "A", "child", 2, false);
        parent.setChildren(List.of(child));

        byte[] csv = service.toCsv(List.of(parent));
        String text = new String(csv, java.nio.charset.StandardCharsets.UTF_8);
        // depth-2 node gets 2 spaces of indentation
        assertThat(text).contains("  CHILD");
    }

    private static BomExplosionNode node(String partNumber, String revision, String description,
                                          int depth, boolean counterfeitRisk) {
        BomExplosionNode n = new BomExplosionNode();
        n.setPartNumber(partNumber);
        n.setRevision(revision);
        n.setDescription(description);
        n.setDepth(depth);
        n.setCounterfeitRiskAlert(counterfeitRisk);
        n.setQuantity(BigDecimal.ONE);
        n.setMakeBuyCode("MAKE");
        n.setFindNumber("F-001");
        n.setComponentItemId(UUID.randomUUID().toString());
        n.setParentItemId(UUID.randomUUID().toString());
        return n;
    }
}
