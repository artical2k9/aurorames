package com.mes.engineering.workinstruction.service;

/** Metadata + object key resolved within a transaction; the binary is streamed afterwards. */
public record MediaDownload(String fileName, String contentType, long sizeBytes, String objectKey) {
}
