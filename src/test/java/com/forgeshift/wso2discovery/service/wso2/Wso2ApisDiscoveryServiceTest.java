package com.forgeshift.wso2discovery.service.wso2;

import com.forgeshift.wso2discovery.domain.DiscoverySnapshot;
import com.forgeshift.wso2discovery.dto.DiscoverResourceResponse;
import com.forgeshift.wso2discovery.dto.details.ApiDetail;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Unit tests for the snapshot -> {@link ApiDetail} projection, focused on the
 * lastChangedAt / lastChangedBy audit fields. No Spring context or WSO2 backend
 * needed: {@code populateDetails} reads only the snapshot payload.
 */
class Wso2ApisDiscoveryServiceTest {

    private final Wso2ApisDiscoveryService service = new Wso2ApisDiscoveryService();

    @Test
    void projectsLastChangedFromPayload() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("context", "/petstore/1.0.0");
        payload.put("lifecycleStatus", "PUBLISHED");
        payload.put("updatedBy", "alice@acme.io");
        payload.put("lastUpdatedTime", "2026-05-20T10:15:30Z");
        payload.put("updatedTime", "2026-05-20 10:15:25"); // older summary value, should be ignored

        DiscoverResourceResponse resp = projectOne(payload);

        ApiDetail d = resp.getApiDetails().get(0);
        assertEquals("alice@acme.io", d.getLastChangedBy());
        assertEquals("2026-05-20T10:15:30Z", d.getLastChangedAt(),
                "lastUpdatedTime from the full API should win over the summary's updatedTime");
    }

    @Test
    void fallsBackToUpdatedTimeWhenFullTimestampAbsent() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("updatedTime", "2026-05-20 10:15:25");
        payload.put("updatedBy", "bob@acme.io");

        ApiDetail d = projectOne(payload).getApiDetails().get(0);
        assertEquals("2026-05-20 10:15:25", d.getLastChangedAt());
        assertEquals("bob@acme.io", d.getLastChangedBy());
    }

    @Test
    void toleratesMissingAuditFields() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("context", "/no-audit/1.0.0");

        ApiDetail d = projectOne(payload).getApiDetails().get(0);
        assertNull(d.getLastChangedAt());
        assertNull(d.getLastChangedBy());
    }

    private DiscoverResourceResponse projectOne(Map<String, Object> payload) {
        DiscoverySnapshot snap = DiscoverySnapshot.builder()
                .sourceId("api-1")
                .sourceName("PetStoreAPI")
                .sourceVersion("1.0.0")
                .payload(payload)
                .build();
        DiscoverResourceResponse resp = DiscoverResourceResponse.builder().build();
        service.populateDetails(resp, List.of(snap));
        return resp;
    }
}
