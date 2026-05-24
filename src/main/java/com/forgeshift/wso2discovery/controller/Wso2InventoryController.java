package com.forgeshift.wso2discovery.controller;

import com.forgeshift.wso2discovery.config.DiscoveryProperties;
import com.forgeshift.wso2discovery.dto.DiscoverResourceRequest;
import com.forgeshift.wso2discovery.dto.InventoryResponse;
import com.forgeshift.wso2discovery.service.wso2.Wso2InventoryService;
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
 * POST /wso2/inventory — cheap, list-only pre-flight pass over a WSO2 tenant.
 *
 * Distinct from the per-resource POST endpoints:
 *  - this endpoint does not allocate a revision
 *  - this endpoint does not write to MongoDB
 *  - this endpoint hits only the LIST endpoints on WSO2 (no per-item enrichment)
 *
 * Returns one envelope with summary lists for every supported resource type
 * so a UI can render an inventory page in one round-trip.
 */
@Slf4j
@RestController
@RequestMapping("/wso2")
@RequiredArgsConstructor
public class Wso2InventoryController {

    private final Wso2InventoryService inventoryService;
    private final DiscoveryProperties discoveryProps;

    @PostMapping("/inventory")
    public ResponseEntity<InventoryResponse> inventory(@Valid @RequestBody DiscoverResourceRequest req) {
        if (!StringUtils.hasText(req.getCompanyName())) {
            req.setCompanyName(discoveryProps.getDefaultCompanyName());
        }
        log.info("POST /wso2/inventory company={} tenant={} txn={}",
                req.getCompanyName(), req.getWso2Tenant(), req.getRequestTransactionId());
        InventoryResponse response = inventoryService.inventory(req);
        return ResponseEntity.ok(response);
    }
}
