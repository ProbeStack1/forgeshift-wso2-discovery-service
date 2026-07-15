package com.forgeshift.wso2discovery.service;

import com.forgeshift.wso2discovery.config.DiscoveryProperties;
import com.forgeshift.wso2discovery.domain.DiscoverySnapshot;
import com.forgeshift.wso2discovery.domain.ResourceType;
import com.forgeshift.wso2discovery.dto.ComparisonResponse;
import com.forgeshift.wso2discovery.repository.BaseDiscoveryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;

/**
 * Diffs two discovery snapshots by their {@code discoveryId}s and reports
 * per-resource-type added / removed / changed / unchanged items.
 *
 * <p>"Changed" detection is a DEEP compare of the whole stored payload (Map/List
 * equality is structural in Java), minus {@link #IGNORED_KEYS}. It used to compare a
 * hand-written list of ~13 field names, which was wrong in two ways: 9 of those names
 * ({@code state}, {@code status}, {@code enabled}, {@code endpoint}, {@code throttlingPolicy},
 * …) don't exist in a WSO2 payload at all (so they compared null==null forever), and
 * {@code lifecycleStatus} was misspelled — the real field is {@code lifeCycleStatus} — so a
 * publish/unpublish was never detected. Everything that actually matters for a migration
 * ({@code endpointConfig}, {@code operations}, {@code policies}, {@code apiPolicies},
 * {@code securityScheme}, {@code corsConfiguration}, {@code mediationPolicies}) was ignored,
 * so a re-pointed backend or an added resource reported "unchanged".</p>
 *
 * <p>The deep compare errs toward reporting "changed": a false "changed" only costs an
 * unnecessary re-assessment, while a false "unchanged" would wrongly lock a resource that
 * really did change.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class Wso2ComparisonService {

    private final BaseDiscoveryRepository repository;
    private final DiscoveryProperties discoveryProps;

    /**
     * Payload keys excluded from the diff: they carry no migration meaning and would otherwise
     * report a change for a resource that is functionally identical. Everything else — including
     * {@code lastUpdatedTime} — participates: it only moves when the source object really changed,
     * which makes it a useful signal rather than noise.
     */
    private static final java.util.Set<String> IGNORED_KEYS = java.util.Set.of(
            "hasThumbnail",
            "workflowStatus"
    );

    public ComparisonResponse compare(String companyName, String wso2Tenant,
                                      String sourceDiscoveryId, String targetDiscoveryId,
                                      String resourceTypeFilter) {
        if (!StringUtils.hasText(companyName) || !StringUtils.hasText(wso2Tenant)) {
            throw new IllegalArgumentException("companyName and wso2Tenant are required");
        }
        if (!StringUtils.hasText(sourceDiscoveryId) || !StringUtils.hasText(targetDiscoveryId)) {
            throw new IllegalArgumentException("sourceDiscoveryId and targetDiscoveryId are required");
        }
        if (sourceDiscoveryId.equals(targetDiscoveryId)) {
            throw new IllegalArgumentException("sourceDiscoveryId and targetDiscoveryId must differ");
        }

        List<ResourceType> targets = StringUtils.hasText(resourceTypeFilter)
                ? List.of(ResourceType.fromSlug(resourceTypeFilter))
                : ResourceType.ALL;

        Map<String, ComparisonResponse.ResourceDiff> diff = new LinkedHashMap<>();
        ComparisonResponse.Summary summary = ComparisonResponse.Summary.builder().build();

        for (ResourceType rt : targets) {
            String collection = rt.collectionName(discoveryProps.getCollectionPrefix());

            List<DiscoverySnapshot> srcSnapshots = repository.findByDiscoveryId(collection, sourceDiscoveryId);
            List<DiscoverySnapshot> tgtSnapshots = repository.findByDiscoveryId(collection, targetDiscoveryId);

            // skip resource types neither side has data for
            if (srcSnapshots.isEmpty() && tgtSnapshots.isEmpty()) continue;

            // Filter to the given tenant - defense-in-depth in case different
            // discoveryIds came from different tenants
            srcSnapshots = filterTenant(srcSnapshots, companyName, wso2Tenant);
            tgtSnapshots = filterTenant(tgtSnapshots, companyName, wso2Tenant);

            Map<String, DiscoverySnapshot> srcById = indexById(srcSnapshots);
            Map<String, DiscoverySnapshot> tgtById = indexById(tgtSnapshots);

            ComparisonResponse.ResourceDiff rd = diffOne(srcById, tgtById);
            diff.put(rt.getSlug(), rd);

            summary.setAdded(summary.getAdded() + rd.getAdded().size());
            summary.setRemoved(summary.getRemoved() + rd.getRemoved().size());
            summary.setChanged(summary.getChanged() + rd.getChanged().size());
            summary.setUnchanged(summary.getUnchanged() + rd.getUnchangedCount());
        }

        return ComparisonResponse.builder()
                .companyName(companyName)
                .wso2Tenant(wso2Tenant)
                .sourceDiscoveryId(sourceDiscoveryId)
                .targetDiscoveryId(targetDiscoveryId)
                .resourceType(resourceTypeFilter)
                .diff(diff)
                .summary(summary)
                .build();
    }

    // ---------------- internals ----------------

    private ComparisonResponse.ResourceDiff diffOne(Map<String, DiscoverySnapshot> src,
                                                    Map<String, DiscoverySnapshot> tgt) {
        List<ComparisonResponse.DiffItem> added = new ArrayList<>();
        List<ComparisonResponse.DiffItem> removed = new ArrayList<>();
        List<ComparisonResponse.DiffItem> changed = new ArrayList<>();
        int unchanged = 0;

        // Removed = in src not in tgt
        for (Map.Entry<String, DiscoverySnapshot> e : src.entrySet()) {
            if (!tgt.containsKey(e.getKey())) {
                removed.add(toItem(e.getValue(), null));
            }
        }
        // Added = in tgt not in src; for entries in both, classify changed/unchanged
        for (Map.Entry<String, DiscoverySnapshot> e : tgt.entrySet()) {
            DiscoverySnapshot s = src.get(e.getKey());
            DiscoverySnapshot t = e.getValue();
            if (s == null) {
                added.add(toItem(t, null));
                continue;
            }
            List<String> diffFields = changedFields(s, t);
            if (diffFields.isEmpty()) unchanged++;
            else changed.add(toItem(t, diffFields));
        }

        return ComparisonResponse.ResourceDiff.builder()
                .added(added)
                .removed(removed)
                .changed(changed)
                .unchangedCount(unchanged)
                .build();
    }

    /**
     * Deep-compares every payload key present on either side (minus {@link #IGNORED_KEYS}) and
     * returns the names of those that differ. Nested maps/lists compare structurally, so a change
     * inside {@code endpointConfig} or {@code operations} surfaces as that top-level key.
     */
    private static List<String> changedFields(DiscoverySnapshot s, DiscoverySnapshot t) {
        List<String> diffs = new ArrayList<>();
        Map<String, Object> sp = s.getPayload() != null ? s.getPayload() : Collections.emptyMap();
        Map<String, Object> tp = t.getPayload() != null ? t.getPayload() : Collections.emptyMap();
        java.util.Set<String> keys = new TreeSet<>();   // sorted → deterministic diff output
        keys.addAll(sp.keySet());
        keys.addAll(tp.keySet());
        for (String k : keys) {
            if (IGNORED_KEYS.contains(k)) continue;
            if (!Objects.equals(sp.get(k), tp.get(k))) diffs.add(k);
        }
        return diffs;
    }

    private static ComparisonResponse.DiffItem toItem(DiscoverySnapshot snap, List<String> changedFields) {
        return ComparisonResponse.DiffItem.builder()
                .sourceId(snap.getSourceId())
                .sourceName(snap.getSourceName())
                .sourceVersion(snap.getSourceVersion())
                .changedFields(changedFields)
                .build();
    }

    private static Map<String, DiscoverySnapshot> indexById(List<DiscoverySnapshot> snaps) {
        Map<String, DiscoverySnapshot> m = new LinkedHashMap<>();
        // sourceId can in rare cases collide across revisions of same item; keep
        // the latest one we see (caller already filtered by discoveryId)
        for (DiscoverySnapshot s : snaps) {
            if (s.getSourceId() == null) continue;
            m.put(s.getSourceId(), s);
        }
        // sort by sourceId for deterministic diff order
        Map<String, DiscoverySnapshot> sorted = new LinkedHashMap<>();
        for (String k : new TreeSet<>(m.keySet())) sorted.put(k, m.get(k));
        return sorted;
    }

    private static List<DiscoverySnapshot> filterTenant(List<DiscoverySnapshot> in, String company, String tenant) {
        List<DiscoverySnapshot> out = new ArrayList<>(in.size());
        for (DiscoverySnapshot s : in) {
            if (Objects.equals(s.getCompanyName(), company) && Objects.equals(s.getWso2Tenant(), tenant)) {
                out.add(s);
            }
        }
        return out;
    }
}
