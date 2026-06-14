package com.mes.engineering.workinstruction.service;

import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.errors.MinioException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.util.Locale;
import java.util.UUID;

/** MinIO-backed {@link MediaStorageService}. */
@Service
public class MinioMediaStorageService implements MediaStorageService {

    private static final Logger LOG = LoggerFactory.getLogger(MinioMediaStorageService.class);

    private final MinioClient client;
    private final MediaProperties properties;
    private volatile boolean bucketReady = false;

    public MinioMediaStorageService(MediaProperties properties) {
        this.properties = properties;
        this.client = MinioClient.builder()
                .endpoint(properties.getEndpoint())
                .credentials(properties.getAccessKey(), properties.getSecretKey())
                .build();
    }

    /** Best-effort bucket creation at startup; also ensured lazily on first write. */
    @EventListener(ApplicationReadyEvent.class)
    void ensureBucketOnStartup() {
        try {
            ensureBucket();
        } catch (RuntimeException ex) {
            LOG.warn("Could not ensure MinIO bucket '{}' at startup ({}); will retry on first upload",
                    properties.getBucket(), ex.getMessage());
        }
    }

    @Override
    public String objectKey(UUID orgId, UUID instructionId, UUID attachmentId, String fileName) {
        String ext = "";
        if (fileName != null) {
            int dot = fileName.lastIndexOf('.');
            if (dot >= 0 && dot < fileName.length() - 1) {
                ext = "." + fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
            }
        }
        return orgId + "/" + instructionId + "/" + attachmentId + ext;
    }

    @Override
    public void put(String objectKey, InputStream data, long size, String contentType) {
        ensureBucket();
        try {
            client.putObject(PutObjectArgs.builder()
                    .bucket(properties.getBucket())
                    .object(objectKey)
                    .stream(data, size, -1)
                    .contentType(contentType)
                    .build());
        } catch (MinioException | IOException | GeneralSecurityException ex) {
            throw new MediaStorageException("Failed to store media object " + objectKey, ex);
        }
    }

    @Override
    public InputStream get(String objectKey) {
        try {
            return client.getObject(GetObjectArgs.builder()
                    .bucket(properties.getBucket())
                    .object(objectKey)
                    .build());
        } catch (MinioException | IOException | GeneralSecurityException ex) {
            throw new MediaStorageException("Failed to read media object " + objectKey, ex);
        }
    }

    @Override
    public void delete(String objectKey) {
        try {
            client.removeObject(RemoveObjectArgs.builder()
                    .bucket(properties.getBucket())
                    .object(objectKey)
                    .build());
        } catch (MinioException | IOException | GeneralSecurityException ex) {
            throw new MediaStorageException("Failed to delete media object " + objectKey, ex);
        }
    }

    private void ensureBucket() {
        if (bucketReady) {
            return;
        }
        synchronized (this) {
            if (bucketReady) {
                return;
            }
            try {
                boolean exists = client.bucketExists(
                        BucketExistsArgs.builder().bucket(properties.getBucket()).build());
                if (!exists) {
                    client.makeBucket(MakeBucketArgs.builder().bucket(properties.getBucket()).build());
                }
                bucketReady = true;
            } catch (MinioException | IOException | GeneralSecurityException ex) {
                throw new MediaStorageException(
                        "Failed to ensure MinIO bucket " + properties.getBucket(), ex);
            }
        }
    }
}
