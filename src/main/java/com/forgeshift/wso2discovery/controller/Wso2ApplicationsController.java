package com.forgeshift.wso2discovery.controller;

import com.forgeshift.wso2discovery.config.DiscoveryProperties;
import com.forgeshift.wso2discovery.dto.DiscoverResourceRequest;
import com.forgeshift.wso2discovery.dto.DiscoverResourceResponse;
import com.forgeshift.wso2discovery.service.wso2.Wso2ApplicationsDiscoveryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * POST /wso2/applications - per-resource discovery for DevPortal Applications.
 */
@Slf4j
@RestController
@RequestMapping("/wso2")
@RequiredArgsConstructor
public class Wso2ApplicationsController {

    private final Wso2ApplicationsDiscoveryService service;
    private final DiscoveryProperties discoveryProps;

    @PostMapping("/applications")
    public ResponseEntity<DiscoverResourceResponse> discover(@Valid @RequestBody DiscoverResourceRequest req) {
        applyDefaults(req);
        log.info("POST /wso2/applications company={} tenant={} env={} txn={}",
                req.getCompanyName(), req.getWso2Tenant(), req.getEnvironment(), req.getRequestTransactionId());
        return ResponseEntity.ok(service.discover(req));
    }

    private void applyDefaults(DiscoverResourceRequest req) {
        if (!StringUtils.hasText(req.getCompanyName())) {
            req.setCompanyName(discoveryProps.getDefaultCompanyName());
        }
    }
}
