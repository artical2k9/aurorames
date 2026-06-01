package com.mes.workorder.bom.service;

import com.mes.workorder.bom.api.dto.BomExplosionNode;
import com.mes.workorder.bom.domain.BillOfMaterials;
import com.mes.workorder.bom.repository.BomLineRepository;
import com.mes.workorder.bom.repository.BomRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
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

    public List<BomExplosionNode> explode(UUID orgId, UUID bomId, String format,
                                           LocalDate asOfDate, String asOfUnit) {
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

        List<Object[]> effectiveRows = filterByEffectivity(rows, asOfDate, asOfUnit);
        List<BomExplosionNode> nodes = effectiveRows.stream().map(this::toNode).toList();

        if ("indented".equals(format)) {
            return buildTree(nodes, bom.getParentItemId().toString());
        }
        return nodes;
    }

    private List<Object[]> filterByEffectivity(List<Object[]> rows, LocalDate asOfDate, String asOfUnit) {
        if (asOfDate == null && asOfUnit == null) {
            return rows;
        }
        Map<String, List<Object[]>> byFindNumber = new LinkedHashMap<>();
        for (Object[] row : rows) {
            byFindNumber.computeIfAbsent((String) row[14], k -> new ArrayList<>()).add(row);
        }
        List<Object[]> result = new ArrayList<>();
        for (var entry : byFindNumber.entrySet()) {
            String findNumber = entry.getKey();
            List<Object[]> included = new ArrayList<>();
            boolean hadControlledRows = false;
            for (Object[] row : entry.getValue()) {
                String method = (String) row[9];
                if ("DATE".equals(method) && asOfDate != null) {
                    hadControlledRows = true;
                    if (isDateEffective(row, asOfDate)) {
                        included.add(row);
                    }
                } else if ("UNIT".equals(method) && asOfUnit != null) {
                    hadControlledRows = true;
                    if (isUnitEffective(row, asOfUnit)) {
                        included.add(row);
                    }
                } else {
                    included.add(row);
                }
            }
            if (hadControlledRows && included.isEmpty()) {
                throw new BomValidationException(
                        "No effective BOM line for find number '" + findNumber
                        + "' at " + (asOfDate != null ? "date " + asOfDate : "unit " + asOfUnit));
            }
            result.addAll(included);
        }
        return result;
    }

    private boolean isDateEffective(Object[] row, LocalDate asOfDate) {
        String fromStr = (String) row[10];
        if (fromStr == null) {
            return true;
        }
        LocalDate from = LocalDate.parse(fromStr);
        String toStr = (String) row[11];
        LocalDate to = toStr != null ? LocalDate.parse(toStr) : null;
        return !asOfDate.isBefore(from) && (to == null || !asOfDate.isAfter(to));
    }

    private boolean isUnitEffective(Object[] row, String asOfUnit) {
        String fromUnit = (String) row[12];
        if (fromUnit == null) {
            return true;
        }
        String toUnit = (String) row[13];
        return asOfUnit.compareTo(fromUnit) >= 0 && (toUnit == null || asOfUnit.compareTo(toUnit) <= 0);
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
        node.setFindNumber((String) row[14]);
        if (row[15] != null) {
            node.setQuantity(new BigDecimal(row[15].toString()));
        }
        node.setMakeBuyCode((String) row[16]);
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
