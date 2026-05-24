package com.forgeshift.wso2discovery.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.Map;

/**
 * One row per REST request that touches the discovery surface.
 *
 * Written asynchronously by AuditRequestFilter -> MigrationAuditService.
 * Mirrors Apigee's {@code apigee_migration_audit_info}.
 *
 * Add a TTL index in ops if you need automatic expiry:
 *   db.wso2_migration_audit_info.createIndex({recordedAt:1}, {expireAfterSeconds: 7776000})
 *   // = 90 days
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document("wso2_migration_audit_info")
public class MigrationAuditEntry {

    @Id
    private String id;

    @Indexed
    private String serviceTransactionId;

    @Indexed
    private String requestTransactionId;

    @Indexed
    private String companyName;

    @Indexed
    private String wso2Tenant;

    private String userEmail;
    private String requestSource;          // API / UI / SCHEDULED
    private String operation;              // DISCOVER_APIS, INVENTORY, COMPARE, ASSETINFO_WRITE, ...
    private String resourceType;           // apis / applications / ... when applicable
    private String httpMethod;
    private String httpPath;
    private String remoteIp;

    private Instant requestedAt;
    private Instant completedAt;
    private long elapsedMs;

    @Indexed
    private String status;                 // SUCCESS / FAILED / IN_PROGRESS
    private int statusCode;
    private String errorMessage;

    /** Free-form context the controller wants to attach. */
    private Map<String, Object> metadata;
}
