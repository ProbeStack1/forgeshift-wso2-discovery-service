package com.forgeshift.wso2discovery.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * One row per WSO2 tenant the service has ever observed.
 *
 * Auto-upserted by every discovery flow. Drives the UI's "which tenants do
 * we have data for?" list without scanning every per-resource collection.
 *
 * Mirrors Apigee's {@code apigee_organizations}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document("wso2_organizations")
@CompoundIndexes({
        @CompoundIndex(name = "idx_company_tenant", def = "{'companyName': 1, 'wso2Tenant': 1}", unique = true)
})
public class Wso2OrganizationEntity {

    /** Composite id: {@code <companyName>|<wso2Tenant>}. */
    @Id
    private String id;

    private String companyName;
    private String wso2Tenant;
    private String wso2BaseUrl;

    private Instant firstSeenAt;
    private Instant lastSeenAt;

    private String lastDiscoveryId;
    private Integer lastRevision;
    private String lastUserEmail;
    private String lastOperation;

    /** Roll-up: total snapshot rows currently held for this tenant. Eventually-consistent. */
    private long totalSnapshots;
}
