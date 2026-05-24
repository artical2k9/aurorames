package com.mikemes.audit.checksum;

import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.mikemes.audit.domain.AuditRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;

@Service
public class ChecksumService {

    private static final Logger LOG = LoggerFactory.getLogger(ChecksumService.class);

    private static final ObjectMapper MAPPER = new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true)
        .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
        .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);

    public String compute(AuditRecord record) {
        String canonical = buildCanonical(record);
        return sha256Hex(canonical);
    }

    public boolean verify(AuditRecord record) {
        String expected = compute(record);
        boolean matches = expected.equals(record.getChecksum());
        if (!matches) {
            LOG.warn("Checksum mismatch for AuditRecord id={} eventId={}",
                record.getId(), record.getEventId());
        }
        return matches;
    }

    private String buildCanonical(AuditRecord r) {
        long epochMillis = r.getOccurredAt() != null
            ? r.getOccurredAt().toInstant().toEpochMilli()
            : 0L;
        return r.getId() + "|" +
            r.getEventType() + "|" +
            r.getEntityType() + "|" +
            r.getEntityId() + "|" +
            r.getUserId() + "|" +
            r.getServiceSource() + "|" +
            r.getAction() + "|" +
            epochMillis + "|" +
            sha256OfJson(r.getPreviousState()) + "|" +
            sha256OfJson(r.getNewState());
    }

    private String sha256OfJson(Map<String, Object> map) {
        if (map == null) {
            return sha256Hex("null");
        }
        try {
            String json = MAPPER.writeValueAsString(map);
            return sha256Hex(json);
        } catch (Exception e) {
            LOG.warn("Failed to serialise map for checksum; treating as null", e);
            return sha256Hex("null");
        }
    }

    private static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
