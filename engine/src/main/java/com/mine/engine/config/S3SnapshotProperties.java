package com.mine.engine.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for S3 snapshot storage
 * Properties are prefixed with "snapshot.s3" in application.properties
 */
@Data
@Component
@ConfigurationProperties(prefix = "snapshot.s3")
public class S3SnapshotProperties {

    /**
     * Enable S3 snapshot upload/download functionality
     */
    private boolean enabled = false;

    /**
     * S3 bucket name where snapshots will be stored
     */
    private String bucket = "";

    /**
     * AWS region for S3 bucket (default: us-east-1)
     */
    private String region = "us-east-1";

    /**
     * S3 object key/path for the snapshot file (default: engine/stockData.snapshot)
     */
    private String key = "engine/stockData.snapshot";

    /**
     * AWS access key ID (optional - if not provided, uses default credential chain)
     */
    private String accessKey = "";

    /**
     * AWS secret access key (optional - if not provided, uses default credential chain)
     */
    private String secretKey = "";

    /**
     * Custom S3 endpoint URL (optional - useful for S3-compatible storage like MinIO, LocalStack)
     */
    private String endpoint = "";

    /**
     * Enable path-style access for S3 (default: false)
     * Set to true for S3-compatible storage that requires path-style URLs
     */
    private boolean pathStyleAccess = false;

    /**
     * If true, loadSnapshot will prefer S3 even if local file exists (default: false)
     */
    private boolean prefer = false;
}
