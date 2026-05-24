package com.forgeshift.wso2discovery.controller;

import com.forgeshift.wso2discovery.config.DiscoveryProperties;
import com.forgeshift.wso2discovery.dto.AssetInfoRequest;
import com.forgeshift.wso2discovery.dto.AssetInfoResponse;
import com.forgeshift.wso2discovery.service.Wso2AssetInfoService;
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
 * POST /wso2/assetinfo — attach business-metadata to already-discovered
 * assets. Mirrors the Apigee {@code /apigee/assetinfo} write endpoint.
 *
 * Rows are upserted (deterministic id), so re-posting the same payload is
 * a no-op rather than a duplicate.
 */
@Slf4j
@RestController
@RequestMapping("/wso2")
@RequiredArgsConstructor
public class Wso2AssetInfoController {

    private final Wso2AssetInfoService service;
    private final DiscoveryProperties discoveryProps;

    @PostMapping("/assetinfo")
    public ResponseEntity<AssetInfoResponse> save(@Valid @RequestBody AssetInfoRequest req) {
        if (!StringUtils.hasText(req.getCompanyName())) {
            req.setCompanyName(discoveryProps.getDefaultCompanyName());
        }
        log.info("POST /wso2/assetinfo company={} tenant={} items={}",
                req.getCompanyName(), req.getWso2Tenant(),
                req.getAssetInfoItems() == null ? 0 : req.getAssetInfoItems().size());
        return ResponseEntity.ok(service.save(req));
    }
}
