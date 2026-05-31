package com.mes.workorder.bom.service;

import com.mes.workorder.bom.api.dto.BomExplosionNode;
import com.mes.workorder.bom.domain.BillOfMaterials;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

@Service
public class BomExportService {

    private static final String[] CSV_HEADERS = {
        "Find #", "Part Number", "Rev", "Description", "Qty", "Make/Buy", "Ctft Risk"
    };

    public byte[] toCsv(List<BomExplosionNode> nodes) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.join(",", CSV_HEADERS)).append("\n");
        flattenNodes(nodes).forEach(node -> {
            String indent = "  ".repeat(node.getDepth() - 1);
            sb.append(csvEscape(""))
              .append(",").append(csvEscape(indent + node.getPartNumber()))
              .append(",").append(csvEscape(node.getRevision()))
              .append(",").append(csvEscape(node.getDescription()))
              .append(",").append("")
              .append(",").append("")
              .append(",").append(node.isCounterfeitRiskAlert() ? "HIGH" : "")
              .append("\n");
        });
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    public byte[] toPdf(BillOfMaterials bom, List<BomExplosionNode> nodes) {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);

            PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            PDType1Font regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);

            float margin = 40;
            float yStart = page.getMediaBox().getHeight() - margin;
            float lineHeight = 14;

            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                float y = yStart;

                cs.beginText();
                cs.setFont(bold, 12);
                cs.newLineAtOffset(margin, y);
                cs.showText("BOM Explosion Report — BOM " + bom.getBomRevision());
                cs.endText();
                y -= lineHeight * 1.5f;

                cs.beginText();
                cs.setFont(regular, 9);
                cs.newLineAtOffset(margin, y);
                cs.showText("Generated: " + LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
                cs.endText();
                y -= lineHeight * 2;

                cs.beginText();
                cs.setFont(bold, 9);
                cs.newLineAtOffset(margin, y);
                cs.showText("Find #   Part Number              Rev    Description                   Qty    Ctft Risk");
                cs.endText();
                y -= lineHeight;

                cs.setFont(regular, 8);
                for (BomExplosionNode node : flattenNodes(nodes)) {
                    if (y < margin + lineHeight) {
                        cs.endText();
                        PDPage newPage = new PDPage(PDRectangle.A4);
                        doc.addPage(newPage);
                        try (PDPageContentStream cs2 = new PDPageContentStream(doc, newPage)) {
                            cs2.beginText();
                            cs2.setFont(regular, 8);
                            cs2.newLineAtOffset(margin, yStart);
                        }
                        y = yStart;
                        cs.beginText();
                        cs.setFont(regular, 8);
                        cs.newLineAtOffset(margin, y);
                    }
                    String indent = "  ".repeat(node.getDepth() - 1);
                    String risk = node.isCounterfeitRiskAlert() ? "HIGH" : "";
                    String line = String.format("%-8s %-24s %-6s %-28s %-6s %s",
                            "",
                            truncate(indent + node.getPartNumber(), 24),
                            truncate(node.getRevision() != null ? node.getRevision() : "", 6),
                            truncate(node.getDescription() != null ? node.getDescription() : "", 28),
                            "",
                            risk);
                    cs.beginText();
                    cs.setFont(regular, 8);
                    cs.newLineAtOffset(margin, y);
                    cs.showText(line);
                    cs.endText();
                    y -= lineHeight;
                }
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            doc.save(baos);
            return baos.toByteArray();
        } catch (IOException e) {
            throw new BomValidationException("Failed to generate PDF: " + e.getMessage());
        }
    }

    private List<BomExplosionNode> flattenNodes(List<BomExplosionNode> nodes) {
        List<BomExplosionNode> result = new ArrayList<>();
        ArrayDeque<BomExplosionNode> stack = new ArrayDeque<>(nodes);
        while (!stack.isEmpty()) {
            BomExplosionNode node = stack.pollFirst();
            result.add(node);
            if (node.getChildren() != null) {
                List<BomExplosionNode> children = new ArrayList<>(node.getChildren());
                for (int i = children.size() - 1; i >= 0; i--) {
                    stack.addFirst(children.get(i));
                }
            }
        }
        return result;
    }

    private String csvEscape(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() > max ? s.substring(0, max - 1) + "…" : s;
    }
}
