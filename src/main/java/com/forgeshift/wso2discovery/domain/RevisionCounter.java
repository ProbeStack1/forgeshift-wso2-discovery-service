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
 * Per (companyName, tenant) monotonic revision counter.
 *
 * One document per pair. The collection name is taken from
 * {@code forgeshift.discovery.revisions-collection} (default
 * {@code discovery_revisions}). We mirror that name here for the cases that
 * use Spring Data repositories directly; MongoTemplate paths read the
 * configured collection name.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document("discovery_revisions")
@CompoundIndexes({
        @CompoundIndex(name = "idx_company_tenant", def = "{'companyName': 1, 'wso2Tenant': 1}", unique = true)
})
public class RevisionCounter {

    @Id
    private String id;

    private String companyName;

    private String wso2Tenant;

    private int current;

    private String lastDiscoveryId;

    private String lastUserEmail;

    private Instant updatedAt;
}
