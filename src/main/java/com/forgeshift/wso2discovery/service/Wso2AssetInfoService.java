package com.forgeshift.wso2discovery.service;

import com.forgeshift.wso2discovery.config.DiscoveryProperties;
import com.forgeshift.wso2discovery.dto.AssetInfoRequest;
import com.forgeshift.wso2discovery.dto.AssetInfoResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Stores business-metadata against already-discovered WSO2 assets.
 *
 * Mirrors the Apigee AssetInfo write-side. One row per
 * (companyName, wso2Tenant, apiName, apiVersion, appId) tuple. The
 * collection name is configurable via
 * {@code forgeshift.discovery.asset-info-collection} and defaults to
 * {@code probestack_wso2_asset_info}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class Wso2AssetInfoService {

    private final MongoTemplate mongoTemplate;
    private final DiscoveryProperties discoveryProps;

    public AssetInfoResponse save(AssetInfoRequest req) {
        String collection = discoveryProps.getAssetInfoCollection() != null
                ? discoveryProps.getAssetInfoCollection()
                : "probestack_wso2_asset_info";
        Instant now = Instant.now();
        List<String> upsertedIds = new ArrayList<>();

        for (AssetInfoRequest.AssetInfoItem item : req.getAssetInfoItems()) {
            // Composite, deterministic id so re-posting the same row is a no-op
            String id = String.join("|",
                    safe(req.getCompanyName()),
                    safe(req.getWso2Tenant()),
                    safe(item.getApiName()),
                    item.getApiVersion() == null ? "_" : item.getApiVersion(),
                    safe(item.getAppId()));

            Update u = new Update()
                    .set("companyName", req.getCompanyName())
                    .set("wso2Tenant", req.getWso2Tenant())
                    .set("businessUnit", item.getBusinessUnit())
                    .set("appId", item.getAppId())
                    .set("projectName", item.getProjectName())
                    .set("deployableUnit", item.getDeployableUnit())
                    .set("apiName", item.getApiName())
                    .set("apiVersion", item.getApiVersion())
                    .set("updatedAt", now)
                    .setOnInsert("createdAt", now);

            mongoTemplate.upsert(
                    Query.query(Criteria.where("_id").is(id)),
                    u,
                    collection);
            upsertedIds.add(id);
        }

        log.info("[assetinfo] upserted {} rows into {} (company={} tenant={})",
                upsertedIds.size(), collection, req.getCompanyName(), req.getWso2Tenant());

        return AssetInfoResponse.builder()
                .companyName(req.getCompanyName())
                .wso2Tenant(req.getWso2Tenant())
                .collectionName(collection)
                .upsertedIds(upsertedIds)
                .savedCount(upsertedIds.size())
                .savedAt(now)
                .build();
    }

    private static String safe(String s) {
        return s == null ? "_" : s;
    }
}
