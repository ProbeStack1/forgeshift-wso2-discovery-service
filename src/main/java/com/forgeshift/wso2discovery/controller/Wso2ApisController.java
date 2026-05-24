package com.forgeshift.wso2discovery.controller;

import com.forgeshift.wso2discovery.config.DiscoveryProperties;
import com.forgeshift.wso2discovery.dto.DiscoverResourceRequest;
import com.forgeshift.wso2discovery.dto.DiscoverResourceResponse;
import com.forgeshift.wso2discovery.service.wso2.Wso2ApisDiscoveryService;
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
 * POST /wso2/apis - per-resource discovery for WSO2 APIs.
 *
 * Synchronous: returns once every API has been persisted to
 * {@code discovery_wso2_apis} at the newly-allocated revision.
 */
@Slf4j
@RestController
@RequestMapping("/wso2")
@RequiredArgsConstructor
public class Wso2ApisController {

    private final Wso2ApisDiscoveryService apisService;
    private final DiscoveryProperties discoveryProps;

    @PostMapping("/apis")
    public ResponseEntity<DiscoverResourceResponse> discoverApis(@Valid @RequestBody DiscoverResourceRequest req) {
        applyDefaults(req);
        log.info("POST /wso2/apis company={} tenant={} env={} txn={}",
                req.getCompanyName(), req.getWso2Tenant(), req.getEnvironment(), req.getRequestTransactionId());
        DiscoverResourceResponse response = apisService.discover(req);
        return ResponseEntity.ok(response);
    }

    private void applyDefaults(DiscoverResourceRequest req) {
        if (!StringUtils.hasText(req.getCompanyName())) {
            req.setCompanyName(discoveryProps.getDefaultCompanyName());
        }
    }
}
