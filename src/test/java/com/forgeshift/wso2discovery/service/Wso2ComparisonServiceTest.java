package com.forgeshift.wso2discovery.service;

import com.forgeshift.wso2discovery.config.DiscoveryProperties;
import com.forgeshift.wso2discovery.domain.DiscoverySnapshot;
import com.forgeshift.wso2discovery.domain.ResourceType;
import com.forgeshift.wso2discovery.dto.ComparisonResponse;
import com.forgeshift.wso2discovery.repository.BaseDiscoveryRepository;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The delta must detect the changes a migration actually cares about. The previous
 * implementation compared a hand-written list of ~13 field names, of which only 4 exist in a
 * WSO2 payload — so a re-pointed backend or an added resource reported "unchanged", and the
 * misspelled {@code lifecycleStatus} (real field: {@code lifeCycleStatus}) meant a
 * publish/unpublish was never detected either. These tests pin the deep-compare behaviour.
 */
class Wso2ComparisonServiceTest {

    private static final String COMPANY = "probestack";
    private static final String TENANT = "carbon.super";
    private static final String SRC = "disc-1";
    private static final String TGT = "disc-2";

    /** Minimal in-memory repository: only findByDiscoveryId is used by the comparison. */
    private static class StubRepo implements BaseDiscoveryRepository {
        final Map<String, List<DiscoverySnapshot>> byDiscovery = new LinkedHashMap<>();

        @Override
        public List<DiscoverySnapshot> findByDiscoveryId(String collectionName, String discoveryId) {
            return byDiscovery.getOrDefault(discoveryId, List.of());
        }

        @Override public void upsert(String collectionName, DiscoverySnapshot snapshot) { }
        @Override public java.util.Optional<DiscoverySnapshot> findById(String collectionName, String id) {
            return java.util.Optional.empty();
        }
        @Override public List<DiscoverySnapshot> findByCompanyAndTenant(String c, String co, String t) {
            return List.of();
        }
        @Override public long countByDiscoveryId(String collectionName, String discoveryId) { return 0; }
        @Override public long deleteByDiscoveryId(String collectionName, String discoveryId) { return 0; }
    }

    private final StubRepo repo = new StubRepo();
    private final Wso2ComparisonService service =
            new Wso2ComparisonService(repo, new DiscoveryProperties());

    private static DiscoverySnapshot api(String id, Map<String, Object> payload) {
        return DiscoverySnapshot.builder()
                .sourceId(id).sourceName("CustomPolicyApi").sourceVersion("1.0.0")
                .companyName(COMPANY).wso2Tenant(TENANT)
                .payload(payload)
                .build();
    }

    private static Map<String, Object> basePayload() {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("name", "CustomPolicyApi");
        p.put("version", "1.0.0");
        p.put("context", "/custom");
        p.put("lifeCycleStatus", "PUBLISHED");
        p.put("endpointConfig", new LinkedHashMap<>(Map.of(
                "production_endpoints", new LinkedHashMap<>(Map.of("url", "https://postman-echo.com")))));
        p.put("operations", new ArrayList<>(List.of(
                new LinkedHashMap<>(Map.of("verb", "GET", "target", "/get")))));
        return p;
    }

    private ComparisonResponse.ResourceDiff diffApis(Map<String, Object> src, Map<String, Object> tgt) {
        repo.byDiscovery.put(SRC, List.of(api("a31236fc", src)));
        repo.byDiscovery.put(TGT, List.of(api("a31236fc", tgt)));
        ComparisonResponse r = service.compare(COMPANY, TENANT, SRC, TGT, ResourceType.APIS.getSlug());
        return r.getDiff().get(ResourceType.APIS.getSlug());
    }

    @Test
    void identicalPayload_isUnchanged() {
        ComparisonResponse.ResourceDiff d = diffApis(basePayload(), basePayload());
        assertEquals(1, d.getUnchangedCount(), "identical snapshots must be unchanged");
        assertTrue(d.getChanged().isEmpty());
    }

    @Test
    void repointedBackend_isDetected() {
        // The exact change we made live: postman-echo -> httpbin. The old shallow diff missed it.
        Map<String, Object> tgt = basePayload();
        tgt.put("endpointConfig", new LinkedHashMap<>(Map.of(
                "production_endpoints", new LinkedHashMap<>(Map.of("url", "https://httpbin.org")))));

        ComparisonResponse.ResourceDiff d = diffApis(basePayload(), tgt);
        assertEquals(1, d.getChanged().size(), "a re-pointed backend must be reported as changed");
        assertTrue(d.getChanged().get(0).getChangedFields().contains("endpointConfig"));
        assertEquals(0, d.getUnchangedCount());
    }

    @Test
    void addedResource_isDetected() {
        // Adding GET /headers to the API — also invisible to the old diff.
        Map<String, Object> tgt = basePayload();
        tgt.put("operations", new ArrayList<>(List.of(
                new LinkedHashMap<>(Map.of("verb", "GET", "target", "/get")),
                new LinkedHashMap<>(Map.of("verb", "GET", "target", "/headers")))));

        ComparisonResponse.ResourceDiff d = diffApis(basePayload(), tgt);
        assertEquals(1, d.getChanged().size(), "an added operation must be reported as changed");
        assertTrue(d.getChanged().get(0).getChangedFields().contains("operations"));
    }

    @Test
    void lifecycleChange_isDetected_realFieldNameIsLifeCycleStatus() {
        // Regression: the old CHANGE_KEYS had "lifecycleStatus" (lowercase c) — never matched.
        Map<String, Object> tgt = basePayload();
        tgt.put("lifeCycleStatus", "CREATED");

        ComparisonResponse.ResourceDiff d = diffApis(basePayload(), tgt);
        assertEquals(1, d.getChanged().size(), "un-publishing must be reported as changed");
        assertTrue(d.getChanged().get(0).getChangedFields().contains("lifeCycleStatus"));
    }

    @Test
    void cosmeticOnlyChange_doesNotCountAsChanged() {
        // Ignored keys must not manufacture a change for a functionally identical resource.
        Map<String, Object> tgt = basePayload();
        tgt.put("hasThumbnail", true);

        ComparisonResponse.ResourceDiff d = diffApis(basePayload(), tgt);
        assertTrue(d.getChanged().isEmpty(), "ignored keys must not report a change");
        assertEquals(1, d.getUnchangedCount());
    }

    @Test
    void addedAndRemoved_stillClassifyCorrectly() {
        repo.byDiscovery.put(SRC, List.of(api("only-in-src", basePayload())));
        repo.byDiscovery.put(TGT, List.of(api("only-in-tgt", basePayload())));
        ComparisonResponse r = service.compare(COMPANY, TENANT, SRC, TGT, ResourceType.APIS.getSlug());
        ComparisonResponse.ResourceDiff d = r.getDiff().get(ResourceType.APIS.getSlug());
        assertEquals(1, d.getAdded().size());
        assertEquals(1, d.getRemoved().size());
        assertFalse(d.getUnchangedCount() > 0);
    }
}
