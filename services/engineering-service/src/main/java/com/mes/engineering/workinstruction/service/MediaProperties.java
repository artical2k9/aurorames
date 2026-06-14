package com.mes.engineering.workinstruction.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** MinIO connection + media size-limit configuration (mes.wi.media.*). */
@ConfigurationProperties(prefix = "mes.wi.media")
public class MediaProperties {

    private String endpoint;
    private String accessKey;
    private String secretKey;
    private String bucket = "wi-media";
    private long maxImageBytes = 20_000_000L;
    private long maxVideoBytes = 100_000_000L;

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public String getAccessKey() {
        return accessKey;
    }

    public void setAccessKey(String accessKey) {
        this.accessKey = accessKey;
    }

    public String getSecretKey() {
        return secretKey;
    }

    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    public String getBucket() {
        return bucket;
    }

    public void setBucket(String bucket) {
        this.bucket = bucket;
    }

    public long getMaxImageBytes() {
        return maxImageBytes;
    }

    public void setMaxImageBytes(long maxImageBytes) {
        this.maxImageBytes = maxImageBytes;
    }

    public long getMaxVideoBytes() {
        return maxVideoBytes;
    }

    public void setMaxVideoBytes(long maxVideoBytes) {
        this.maxVideoBytes = maxVideoBytes;
    }
}
