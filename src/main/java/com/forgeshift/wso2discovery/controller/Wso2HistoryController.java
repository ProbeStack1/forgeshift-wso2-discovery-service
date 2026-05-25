package com.forgeshift.wso2discovery.controller;

import com.forgeshift.wso2discovery.config.DiscoveryProperties;
import com.forgeshift.wso2discovery.dto.DiscoverResourceResponse;
import com.forgeshift.wso2discovery.dto.HistoryResponse;
import com.forgeshift.wso2discovery.dto.HistoryRevisionsResponse;
import com.forgeshift.wso2discovery.service.Wso2HistoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-only history surface mirroring the Apigee HistoryApi.
 *
 *   GET /wso2/history             - distinct discoveries with per-resource counts
 *   GET /wso2/history/revisions   - revision numbers + current counter
 *   GET /wso2/history/details     - raw snapshot rows for one (resourceType, discoveryId|revision)
 *
 * All three are pure Mongo reads across the {@code discovery_wso2_*} collections.
 */
@Slf4j
@RestController
@RequestMapping("/wso2/history")
@RequiredArgsConstructor
public class Wso2HistoryController {

    private final Wso2HistoryService service;
    private final DiscoveryProperties discoveryProps;

    @GetMapping
    public ResponseEntity<HistoryResponse> list(
            @RequestParam(required = false) String companyName,
            @RequestParam String wso2Tenant,
            @RequestParam(required = false) String resourceType,
            @RequestParam(required = false) String requestTransactionId,
            @RequestParam(defaultValue = "10") int limit) {
        String c = StringUtils.hasText(companyName) ? companyName : discoveryProps.getDefaultCompanyName();
        log.info("GET /wso2/history company={} tenant={} resourceType={} requestTransactionId={} limit={}",
                c, wso2Tenant, resourceType, requestTransactionId, limit);
        return ResponseEntity.ok(service.list(c, wso2Tenant, resourceType, requestTransactionId, limit));
    }

    @GetMapping("/revisions")
    public ResponseEntity<HistoryRevisionsResponse> revisions(
            @RequestParam(required = false) String companyName,
            @RequestParam String wso2Tenant) {
        String c = StringUtils.hasText(companyName) ? companyName : discoveryProps.getDefaultCompanyName();
        log.info("GET /wso2/history/revisions company={} tenant={}", c, wso2Tenant);
        return ResponseEntity.ok(service.revisions(c, wso2Tenant));
    }

    @GetMapping("/details")
    public ResponseEntity<DiscoverResourceResponse> details(
            @RequestParam(required = false) String companyName,
            @RequestParam String wso2Tenant,
            @RequestParam String resourceType,
            @RequestParam(required = false) String discoveryId,
            @RequestParam(required = false) Integer revision,
            @RequestParam(defaultValue = "100") int limit) {
        String c = StringUtils.hasText(companyName) ? companyName : discoveryProps.getDefaultCompanyName();
        log.info("GET /wso2/history/details company={} tenant={} resourceType={} discoveryId={} revision={} limit={}",
                c, wso2Tenant, resourceType, discoveryId, revision, limit);
        return ResponseEntity.ok(service.details(c, wso2Tenant, resourceType, discoveryId, revision, limit));
    }
}
