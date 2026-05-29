package com.mes.workorder.bom.service;

import com.mes.workorder.bom.api.dto.BomExplosionNode;
import com.mes.workorder.bom.domain.BillOfMaterials;
import com.mes.workorder.bom.repository.BomLineRepository;
import com.mes.workorder.bom.repository.BomRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class BomExplosionService {

    private final BomRepository bomRepository;
    private final BomLineRepository bomLineRepository;
    private final int maxDepth;

    public BomExplosionService(BomRepository bomRepository,
                               BomLineRepository bomLineRepository,
                               @Value("${mes.bom.max-depth:50}") int maxDepth) {
        this.bomRepository = bomRepository;
        this.bomLineRepository = bomLineRepository;
        this.maxDepth = maxDepth;
    }

    public List<BomExplosionNode> explode(UUID orgId, UUID bomId, String format) {
        BillOfMaterials bom = bomRepository.findByOrgIdAndId(orgId, bomId)
                .orElseThrow(() -> new BomNotFoundException("BOM not found: " + bomId));

        List<Object[]> rows = bomLineRepository.findExplosionRows(bomId);

        for (Object[] row : rows) {
            int depth = ((Number) row[2]).intValue();
            if (depth > maxDepth) {
                throw new BomValidationException(
                        "BOM depth exceeds maximum allowed depth of " + maxDepth);
            }
        }

        List<BomExplosionNode> nodes = rows.stream().map(this::toNode).toList();

        if ("indented".equals(format)) {
            return buildTree(nodes, bom.getParentItemId().toString());
        }
        return nodes;
    }

    private BomExplosionNode toNode(Object[] row) {
        BomExplosionNode node = new BomExplosionNode();
        node.setComponentItemId((String) row[0]);
        node.setParentItemId((String) row[1]);
        node.setDepth(((Number) row[2]).intValue());
        node.setPartNumber((String) row[3]);
        node.setRevision((String) row[4]);
        node.setDescription((String) row[5]);
        node.setUnitOfMeasure((String) row[6]);

        String riskLevel = (String) row[7];
        node.setCounterfeitRiskAlert("HIGH".equals(riskLevel) || "CRITICAL".equals(riskLevel));
        node.setComponentObsoleted("OBSOLETE".equals(row[8]));
        return node;
    }

    private List<BomExplosionNode> buildTree(List<BomExplosionNode> flat, String rootParentId) {
        Map<String, List<BomExplosionNode>> byParent = new LinkedHashMap<>();
        for (BomExplosionNode node : flat) {
            byParent.computeIfAbsent(node.getParentItemId(), k -> new ArrayList<>()).add(node);
        }
        List<BomExplosionNode> topLevel = byParent.getOrDefault(rootParentId, List.of());
        topLevel.forEach(node -> populateChildren(node, byParent));
        return topLevel;
    }

    private void populateChildren(BomExplosionNode node, Map<String, List<BomExplosionNode>> byParent) {
        List<BomExplosionNode> children = byParent.getOrDefault(node.getComponentItemId(), List.of());
        node.setChildren(children.isEmpty() ? List.of() : new ArrayList<>(children));
        children.forEach(child -> populateChildren(child, byParent));
    }
}
