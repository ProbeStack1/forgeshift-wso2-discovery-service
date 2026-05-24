package com.forgeshift.wso2discovery.service;

import com.forgeshift.wso2discovery.domain.AppApiRelation;
import com.forgeshift.wso2discovery.domain.DiscoverySnapshot;
import com.forgeshift.wso2discovery.repository.AppApiRelationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Maintains the denormalized {@code app_api_relations} collection from
 * subscription snapshots.
 *
 * Called by Wso2SubscriptionsDiscoveryService once per discovery run, after
 * every subscription snapshot has been persisted. Lets the UI answer
 * "which APIs does app X subscribe to?" with a single indexed read.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AppApiRelationService {

    private final AppApiRelationRepository repository;

    /** Derive rows from a batch of subscription snapshots. */
    public void recordFromSubscriptionSnapshots(List<DiscoverySnapshot> subscriptionSnapshots) {
        if (subscriptionSnapshots == null || subscriptionSnapshots.isEmpty()) return;
        int saved = 0;

        for (DiscoverySnapshot snap : subscriptionSnapshots) {
            Map<String, Object> p = snap.getPayload() != null ? snap.getPayload() : Collections.emptyMap();
            String applicationId = str(p.get("applicationId"));
            String apiId = str(p.get("apiId"));
            if (applicationId == null || apiId == null) continue;

            String applicationName = mapField(p, "applicationInfo", "name");
            String apiName = mapField(p, "apiInfo", "name");
            String apiVersion = mapField(p, "apiInfo", "version");
            String apiContext = mapField(p, "apiInfo", "context");

            String id = String.join("|",
                    snap.getCompanyName(), snap.getWso2Tenant(), applicationId, apiId);

            Instant now = Instant.now();
            AppApiRelation existing = repository.findById(id).orElse(null);
            AppApiRelation row = existing != null ? existing : AppApiRelation.builder()
                    .id(id)
                    .companyName(snap.getCompanyName())
                    .wso2Tenant(snap.getWso2Tenant())
                    .createdAt(now)
                    .build();

            row.setApplicationId(applicationId);
            row.setApplicationName(applicationName);
            row.setApiId(apiId);
            row.setApiName(apiName);
            row.setApiVersion(apiVersion);
            row.setApiContext(apiContext);
            row.setSubscriptionId(str(p.get("subscriptionId")));
            row.setThrottlingPolicy(str(p.get("throttlingPolicy")));
            row.setStatus(str(p.get("status")));
            row.setDiscoveryId(snap.getDiscoveryId());
            row.setRevision(snap.getRevision());
            row.setUpdatedAt(now);

            try {
                repository.save(row);
                saved++;
            } catch (Exception e) {
                log.warn("Failed to upsert app_api_relations row {}: {}", id, e.getMessage());
            }
        }
        log.info("[app_api_relations] upserted {} rows from {} subscription snapshots",
                saved, subscriptionSnapshots.size());
    }

    public AppApiRelationRepository repository() {
        return repository;
    }

    // helpers
    private static String str(Object o) { return o == null ? null : o.toString(); }

    @SuppressWarnings("unchecked")
    private static String mapField(Map<String, Object> root, String outer, String inner) {
        Object o = root.get(outer);
        if (o instanceof Map<?, ?> m) {
            Object v = ((Map<String, Object>) m).get(inner);
            return v == null ? null : v.toString();
        }
        return null;
    }
}
