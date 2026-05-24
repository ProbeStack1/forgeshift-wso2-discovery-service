package com.forgeshift.wso2discovery.controller;

import com.forgeshift.wso2discovery.dto.StatsResponse;
import com.forgeshift.wso2discovery.service.Wso2StatsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * GET /wso2/stats/{dimension} — roll-up over our discovery snapshot data.
 *
 * Apigee's equivalent ({@code /organizations/{org}/environments/{env}/stats/{dimension}})
 * proxies their hosted analytics API. WSO2 has no such REST analytics API
 * (Choreo Insights is a separate product), so this serves stats over the
 * discovery data we own, which is what every UI dashboard would query first.
 *
 * Dimensions:
 *   byResourceType   counts per resource slug (apis: N, applications: M, ...)
 *   byTenant         counts per (companyName, wso2Tenant)
 *   byDiscoveryId    counts per (discoveryId, revision) - useful for the UI history table
 *   byTime           counts per UTC day - useful for the UI dashboard chart
 *
 * Optional query params: companyName, wso2Tenant filter the result set.
 */
@Slf4j
@RestController
@RequestMapping("/wso2/stats")
@RequiredArgsConstructor
public class Wso2StatsController {

    private final Wso2StatsService service;

    @GetMapping("/{dimension}")
    public ResponseEntity<StatsResponse> stats(
            @PathVariable String dimension,
            @RequestParam(required = false) String companyName,
            @RequestParam(required = false) String wso2Tenant) {
        log.info("GET /wso2/stats/{} company={} tenant={}", dimension, companyName, wso2Tenant);
        return ResponseEntity.ok(service.compute(dimension, companyName, wso2Tenant));
    }
}
