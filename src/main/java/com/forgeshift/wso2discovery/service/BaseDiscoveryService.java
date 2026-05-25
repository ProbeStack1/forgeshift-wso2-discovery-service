package com.forgeshift.wso2discovery.service;

import com.forgeshift.wso2discovery.client.Wso2Client;
import com.forgeshift.wso2discovery.config.DiscoveryProperties;
import com.forgeshift.wso2discovery.config.Wso2Properties;
import com.forgeshift.wso2discovery.domain.DiscoverySnapshot;
import com.forgeshift.wso2discovery.domain.ResourceType;
import com.forgeshift.wso2discovery.dto.DiscoverResourceRequest;
import com.forgeshift.wso2discovery.dto.DiscoverResourceResponse;
import com.forgeshift.wso2discovery.repository.BaseDiscoveryRepository;
import com.forgeshift.wso2discovery.util.PayloadCleaner;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Common scaffolding for per-resource discovery services.
 *
 * Subclasses provide:
 *   - {@link #getResourceType()}      which collection / endpoint it backs
 *   - {@link #fetchFromWso2(...)}     how to call the WSO2 management API
 *   - {@link #buildSnapshot(...)}     how to map a single WSO2 item to a snapshot
 *
 * The base class owns: validation, revision allocation, snapshot upsert,
 * counting, and the response envelope.
 */
@Slf4j
public abstract class BaseDiscoveryService {

    protected Wso2Client wso2Client;
    protected BaseDiscoveryRepository repository;
    protected RevisionSequenceService revisionService;
    protected DiscoveryProperties discoveryProps;
    protected Wso2Properties wso2Props;
    protected Wso2TenantProfileService profileService;
    protected Wso2OrganizationService organizationService;
    protected Wso2TokenService tokenService;

    @Autowired public void setWso2Client(Wso2Client c) { this.wso2Client = c; }
    @Autowired public void setRepository(BaseDiscoveryRepository r) { this.repository = r; }
    @Autowired public void setRevisionService(RevisionSequenceService r) { this.revisionService = r; }
    @Autowired public void setDiscoveryProps(DiscoveryProperties p) { this.discoveryProps = p; }
    @Autowired public void setWso2Props(Wso2Properties p) { this.wso2Props = p; }
    @Autowired public void setProfileService(Wso2TenantProfileService p) { this.profileService = p; }
    @Autowired public void setOrganizationService(Wso2OrganizationService o) { this.organizationService = o; }
    @Autowired public void setTokenService(Wso2TokenService t) { this.tokenService = t; }

    // ---- subclass contract ----

    protected abstract ResourceType getResourceType();

    /**
     * Call WSO2 and return the raw items. Each item is a Map representing one
     * source object (e.g. one API as returned by the Publisher REST API).
     */
    protected abstract List<java.util.Map<String, Object>> fetchFromWso2(String accessToken, DiscoverResourceRequest req);

    /**
     * Build one {@link DiscoverySnapshot} from one raw WSO2 item. Subclasses
     * pull the right id / name / version fields and set them.
     */
    protected abstract DiscoverySnapshot buildSnapshot(java.util.Map<String, Object> item, DiscoverResourceRequest req, int revision);

    /**
     * Populate the resource-specific detail list on the response (e.g.
     * {@code apiDetails}, {@code applicationDetails}). Called after all
     * snapshots have been persisted so subclasses can project the typed
     * summaries the UI needs without a second round trip.
     *
     * Default implementation is a no-op so resource types that don't yet
     * have a typed detail class still return a valid envelope.
     */
    protected void populateDetails(DiscoverResourceResponse response, List<DiscoverySnapshot> snapshots) {
        // override per resource
    }

    // ---- public entry point ----

    public DiscoverResourceResponse discover(DiscoverResourceRequest req) {
        String resourceSlug = getResourceType().getSlug();
        long start = System.currentTimeMillis();

        validate(req);

        // Acquire an access token. Goes through the cache: profile lookup +
        // static fallback + WSO2 token call all happen inside tokenService.
        String token = tokenService.getToken(scopeForResource(),
                req.getCompanyName(), req.getWso2Tenant());
        if (token == null) {
            throw new IllegalStateException("Failed to acquire WSO2 access token");
        }

        // The discoveryId is always supplied by the caller (UI). Mirrors the
        // Apigee discovery contract — the backend never invents one.
        String discoveryId = req.getRequestTransactionId();

        int revision = revisionService.nextRevision(
                req.getCompanyName(),
                req.getWso2Tenant(),
                discoveryId,
                req.getUserEmail());

        // Fetch from WSO2
        List<java.util.Map<String, Object>> items;
        try {
            items = fetchFromWso2(token, req);
        } catch (Exception e) {
            log.error("[{}] fetch failed: {}", resourceSlug, e.getMessage(), e);
            throw new IllegalStateException("Failed to fetch " + resourceSlug + " from WSO2: " + e.getMessage(), e);
        }

        // Persist snapshots
        String collection = getResourceType().collectionName(discoveryProps.getCollectionPrefix());
        List<DiscoverySnapshot> snapshots = new ArrayList<>(items.size());
        List<String> persistedIds = new ArrayList<>(items.size());
        for (java.util.Map<String, Object> item : items) {
            DiscoverySnapshot snap = buildSnapshot(item, req, revision);
            snap.setCompanyName(req.getCompanyName());
            snap.setWso2Tenant(req.getWso2Tenant());
            snap.setDiscoveryId(discoveryId);
            snap.setRevision(revision);
            snap.setResourceType(resourceSlug);
            snap.setSnapshotAt(Instant.now());
            snap.setId(compositeId(req.getCompanyName(), req.getWso2Tenant(), resourceSlug, snap.getSourceId(), revision));
            // Strip nulls/empty values from the raw WSO2 payload to keep snapshots
            // small without losing any semantic information.
            snap.setPayload(PayloadCleaner.strip(snap.getPayload()));
            repository.upsert(collection, snap);
            snapshots.add(snap);
            persistedIds.add(snap.getId());
        }

        // Record the tenant in wso2_organizations so admin tooling can list it.
        organizationService.recordSeen(
                req.getCompanyName(), req.getWso2Tenant(),
                discoveryId, revision, req.getUserEmail(),
                "DISCOVER_" + resourceSlug.toUpperCase());

        long elapsed = System.currentTimeMillis() - start;
        log.info("[{}] discovered {} items in {} ms (company={} tenant={} revision={})",
                resourceSlug, items.size(), elapsed, req.getCompanyName(), req.getWso2Tenant(), revision);

        DiscoverResourceResponse response = DiscoverResourceResponse.builder()
                .resourceType(resourceSlug)
                .discoveryId(discoveryId)
                .revision(revision)
                .companyName(req.getCompanyName())
                .wso2Tenant(req.getWso2Tenant())
                .environment(req.getEnvironment())
                .timestamp(Instant.now().toString())
                .totalCount(items.size())
                .collectionName(collection)
                .snapshotIds(persistedIds)
                .elapsedMs(elapsed)
                .build();

        // Subclass projects the right typed detail list onto the envelope.
        populateDetails(response, snapshots);
        return response;
    }

    /** Scope to request when getting a token for this resource. Override per service. */
    protected String scopeForResource() {
        // Default: publisher view scope; subclasses override for admin/devportal resources.
        return null; // null means "use the Wso2Properties default publisherScope".
    }

    protected void validate(DiscoverResourceRequest req) {
        if (req == null) {
            throw new IllegalArgumentException("Request body is required");
        }
        if (!StringUtils.hasText(req.getWso2Tenant())) {
            throw new IllegalArgumentException("wso2Tenant is required");
        }
        if (!StringUtils.hasText(req.getRequestTransactionId())) {
            // Defence in depth: @NotBlank on the DTO catches direct HTTP
            // callers, but internal callers (the bulk fan-out, tests) bypass
            // @Valid, so we re-check here.
            throw new IllegalArgumentException("requestTransactionId is required (must be supplied by the caller)");
        }
        // companyName is defaulted upstream in the controller.
    }

    protected static String compositeId(String company, String tenant, String resource, String sourceId, int revision) {
        return String.format("%s|%s|%s|%s|%d", company, tenant, resource, sourceId, revision);
    }
}
