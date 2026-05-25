package com.forgeshift.wso2discovery.service;

import com.forgeshift.wso2discovery.config.Wso2Properties;
import com.forgeshift.wso2discovery.domain.DiscoveryJob;
import com.forgeshift.wso2discovery.domain.DiscoveryState;
import com.forgeshift.wso2discovery.domain.ResourceType;
import com.forgeshift.wso2discovery.dto.DiscoverResourceRequest;
import com.forgeshift.wso2discovery.dto.DiscoverResourceResponse;
import com.forgeshift.wso2discovery.dto.StartDiscoveryRequest;
import com.forgeshift.wso2discovery.repository.DiscoveryJobRepository;
import com.forgeshift.wso2discovery.service.wso2.Wso2ApiProductsDiscoveryService;
import com.forgeshift.wso2discovery.service.wso2.Wso2ApisDiscoveryService;
import com.forgeshift.wso2discovery.service.wso2.Wso2ApplicationsDiscoveryService;
import com.forgeshift.wso2discovery.service.wso2.Wso2CertificatesDiscoveryService;
import com.forgeshift.wso2discovery.service.wso2.Wso2KeyManagersDiscoveryService;
import com.forgeshift.wso2discovery.service.wso2.Wso2MediationPoliciesDiscoveryService;
import com.forgeshift.wso2discovery.service.wso2.Wso2ScopesDiscoveryService;
import com.forgeshift.wso2discovery.service.wso2.Wso2SubscriptionsDiscoveryService;
import com.forgeshift.wso2discovery.service.wso2.Wso2ThrottlingPoliciesDiscoveryService;
import com.forgeshift.wso2discovery.service.wso2.Wso2UsersDiscoveryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.util.StringUtils;

/**
 * Orchestrates a bulk discovery job by fanning out to per-resource services.
 *
 * Per-resource POST endpoints (e.g. {@code POST /wso2/apis}) call those
 * services directly and do not go through this orchestrator. This class
 * exists only for the bulk {@code POST /discoveries} endpoint.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DiscoveryService {

    private final DiscoveryJobRepository jobRepository;
    private final Wso2Properties wso2Props;
    private final Wso2ApisDiscoveryService apisService;
    private final Wso2ApplicationsDiscoveryService applicationsService;
    private final Wso2SubscriptionsDiscoveryService subscriptionsService;
    private final Wso2ThrottlingPoliciesDiscoveryService throttlingPoliciesService;
    private final Wso2KeyManagersDiscoveryService keyManagersService;
    private final Wso2ApiProductsDiscoveryService apiProductsService;
    private final Wso2ScopesDiscoveryService scopesService;
    private final Wso2CertificatesDiscoveryService certificatesService;
    private final Wso2MediationPoliciesDiscoveryService mediationPoliciesService;
    private final Wso2UsersDiscoveryService usersService;

    /**
     * Create a job document and kick off the async fan-out.
     */
    public DiscoveryJob startDiscovery(StartDiscoveryRequest req) {
        if (!StringUtils.hasText(req.getRequestTransactionId())) {
            // Defence in depth: @NotBlank on the DTO catches direct HTTP
            // callers, but other entry points may skip @Valid.
            throw new IllegalArgumentException("requestTransactionId is required (must be supplied by the caller)");
        }
        DiscoveryJob job = DiscoveryJob.builder()
                .companyName(req.getCompanyName())
                .wso2Tenant(req.getWso2Tenant())
                .wso2BaseUrl(wso2Props.getBaseUrl())
                .discoveryId(req.getRequestTransactionId())
                .state(DiscoveryState.PENDING)
                .resourceProgress(new HashMap<>())
                .build();
        job = jobRepository.save(job);

        log.info("Discovery job created: id={} company={} tenant={} discoveryId={}",
                job.getId(), job.getCompanyName(), job.getWso2Tenant(), job.getDiscoveryId());

        runDiscovery(job.getId(), req);
        return job;
    }

    /**
     * Run all requested resource-type discoveries sequentially on a worker
     * thread. Each per-resource service handles its own snapshot persistence
     * and revision allocation.
     */
    @Async("discoveryExecutor")
    public void runDiscovery(String jobId, StartDiscoveryRequest req) {
        DiscoveryJob job = jobRepository.findById(jobId).orElse(null);
        if (job == null) {
            log.warn("Discovery job {} disappeared before worker could run", jobId);
            return;
        }

        try {
            job.setState(DiscoveryState.LISTING);
            jobRepository.save(job);

            List<String> slugs = (req.getResourceTypes() == null || req.getResourceTypes().isEmpty())
                    ? DEFAULT_BULK_SLUGS
                    : req.getResourceTypes();

            for (String slug : slugs) {
                runOne(job, slug, req);
            }

            job.setState(DiscoveryState.COMPLETED);
            job.setCompletedAt(Instant.now());
            jobRepository.save(job);
            log.info("Discovery job {} COMPLETED", jobId);
        } catch (Exception e) {
            log.error("Discovery job {} FAILED: {}", jobId, e.getMessage(), e);
            job.setState(DiscoveryState.FAILED);
            job.setLastError(e.getMessage());
            jobRepository.save(job);
        }
    }

    private void runOne(DiscoveryJob job, String slug, StartDiscoveryRequest req) {
        ResourceType type;
        try {
            type = ResourceType.fromSlug(slug);
        } catch (IllegalArgumentException e) {
            recordProgress(job, slug, "FAILED", 0, "Unknown resource type: " + slug);
            return;
        }

        recordProgress(job, slug, "RUNNING", 0, null);

        DiscoverResourceRequest perReq = new DiscoverResourceRequest();
        perReq.setCompanyName(job.getCompanyName());
        perReq.setWso2Tenant(job.getWso2Tenant());
        perReq.setEnvironment(req.getEnvironment());
        perReq.setUserEmail(req.getUserEmail());
        perReq.setRequestTransactionId(job.getDiscoveryId());

        try {
            DiscoverResourceResponse resp = dispatch(type, perReq);
            // Capture revision once (the first resource sets it; we expect the
            // same revision for all resources in one fan-out).
            if (job.getRevision() == null) {
                job.setRevision(resp.getRevision());
            }
            recordProgress(job, slug, "COMPLETED", resp.getTotalCount(), null);
            job.getCounts().setTotalDiscovered(job.getCounts().getTotalDiscovered() + resp.getTotalCount());
        } catch (Exception e) {
            log.error("Discovery of {} failed: {}", slug, e.getMessage(), e);
            recordProgress(job, slug, "FAILED", 0, e.getMessage());
            job.getCounts().setTotalFailed(job.getCounts().getTotalFailed() + 1);
        }
        jobRepository.save(job);
    }

    private DiscoverResourceResponse dispatch(ResourceType type, DiscoverResourceRequest req) {
        switch (type) {
            case APIS:                return apisService.discover(req);
            case APPLICATIONS:        return applicationsService.discover(req);
            case SUBSCRIPTIONS:       return subscriptionsService.discover(req);
            case THROTTLING_POLICIES: return throttlingPoliciesService.discover(req);
            case KEY_MANAGERS:        return keyManagersService.discover(req);
            case API_PRODUCTS:        return apiProductsService.discover(req);
            case SCOPES:              return scopesService.discover(req);
            case CERTIFICATES:        return certificatesService.discover(req);
            case MEDIATION_POLICIES:  return mediationPoliciesService.discover(req);
            case USERS:               return usersService.discover(req);
            default:
                throw new UnsupportedOperationException(
                        "Discovery for resource type " + type.getSlug() + " is not implemented yet");
        }
    }

    /**
     * Slugs the bulk endpoint runs when the caller doesn't specify any.
     * Kept in declaration order so dependent resources land after their
     * referents (apis before subscriptions, etc.) - matters for any
     * downstream consumer that joins across collections.
     */
    private static final List<String> DEFAULT_BULK_SLUGS = List.of(
            ResourceType.APIS.getSlug(),
            ResourceType.API_PRODUCTS.getSlug(),
            ResourceType.APPLICATIONS.getSlug(),
            ResourceType.SUBSCRIPTIONS.getSlug(),
            ResourceType.THROTTLING_POLICIES.getSlug(),
            ResourceType.KEY_MANAGERS.getSlug(),
            ResourceType.SCOPES.getSlug(),
            ResourceType.CERTIFICATES.getSlug(),
            ResourceType.MEDIATION_POLICIES.getSlug(),
            ResourceType.USERS.getSlug()
    );

    private void recordProgress(DiscoveryJob job, String slug, String state, int count, String error) {
        Map<String, DiscoveryJob.ResourceProgress> map = job.getResourceProgress();
        DiscoveryJob.ResourceProgress p = map.getOrDefault(slug,
                DiscoveryJob.ResourceProgress.builder().build());
        if ("RUNNING".equals(state)) {
            p.setStartedAt(Instant.now());
        }
        if ("COMPLETED".equals(state) || "FAILED".equals(state)) {
            p.setCompletedAt(Instant.now());
        }
        p.setState(state);
        p.setCount(count);
        p.setLastError(error);
        map.put(slug, p);
    }
}
