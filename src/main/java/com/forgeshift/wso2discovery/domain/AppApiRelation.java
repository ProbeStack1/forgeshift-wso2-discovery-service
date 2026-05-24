package com.forgeshift.wso2discovery.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * Denormalized application <-> API join.
 *
 * One row per (companyName, wso2Tenant, applicationId, apiId) - derived from
 * Wso2 subscription snapshots after a subscriptions discovery completes.
 * Lets the UI answer "which APIs does this app subscribe to?" with a single
 * indexed read instead of joining the subscriptions and APIs collections in
 * application code.
 *
 * Mirrors Apigee's {@code app_product_relations}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document("app_api_relations")
@CompoundIndexes({
        @CompoundIndex(name = "idx_app", def = "{'companyName': 1, 'wso2Tenant': 1, 'applicationId': 1}"),
        @CompoundIndex(name = "idx_api", def = "{'companyName': 1, 'wso2Tenant': 1, 'apiId': 1}")
})
public class AppApiRelation {

    /** Composite id: {@code <companyName>|<wso2Tenant>|<applicationId>|<apiId>}. Idempotent. */
    @Id
    private String id;

    private String companyName;
    private String wso2Tenant;

    @Indexed
    private String applicationId;
    private String applicationName;

    @Indexed
    private String apiId;
    private String apiName;
    private String apiVersion;
    private String apiContext;

    @Indexed
    private String subscriptionId;
    private String throttlingPolicy;
    private String status;       // UNBLOCKED / BLOCKED / etc.

    /** Discovery that produced this row. */
    private String discoveryId;
    private Integer revision;

    private Instant createdAt;
    private Instant updatedAt;
}
