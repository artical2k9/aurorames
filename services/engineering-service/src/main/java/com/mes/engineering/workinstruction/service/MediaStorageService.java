package com.mes.engineering.workinstruction.service;

import java.io.InputStream;
import java.util.UUID;

/**
 * Object-store abstraction for work-instruction media binaries. Implemented over MinIO; behind an
 * interface so the storage backend is swappable and unit-testable. Object keys follow the layout
 * {@code {orgId}/{instructionId}/{attachmentId}.{ext}}.
 */
public interface MediaStorageService {

    /** Build the canonical object key for a new attachment. */
    String objectKey(UUID orgId, UUID instructionId, UUID attachmentId, String fileName);

    /** Stream {@code data} ({@code size} bytes, {@code contentType}) to the store at {@code objectKey}. */
    void put(String objectKey, InputStream data, long size, String contentType);

    /** Open a stream to read the object back (caller closes). */
    InputStream get(String objectKey);

    /** Remove the binary. Callers must check the reference count first (shared across revisions). */
    void delete(String objectKey);
}
