package com.forgeshift.wso2discovery.service.wso2;

import com.forgeshift.wso2discovery.domain.DiscoverySnapshot;
import com.forgeshift.wso2discovery.domain.ResourceType;
import com.forgeshift.wso2discovery.dto.DiscoverResourceRequest;
import com.forgeshift.wso2discovery.dto.DiscoverResourceResponse;
import com.forgeshift.wso2discovery.dto.details.CertificateDetail;
import com.forgeshift.wso2discovery.service.BaseDiscoveryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Discovers endpoint TLS Certificates from
 * {@code GET /api/am/publisher/v4/endpoint-certificates} and writes one
 * snapshot per certificate to {@code discovery_wso2_certificates}.
 */
@Slf4j
@Service
public class Wso2CertificatesDiscoveryService extends BaseDiscoveryService {

    @Override
    public ResourceType getResourceType() {
        return ResourceType.CERTIFICATES;
    }

    @Override
    protected String scopeForResource() {
        return wso2Props.getPublisherScope();
    }

    @Override
    protected List<Map<String, Object>> fetchFromWso2(String accessToken, DiscoverResourceRequest req) {
        List<Map<String, Object>> certs = wso2Client.listCertificates(accessToken);
        log.info("[certificates] Publisher returned {} certificates (company={} tenant={})",
                certs.size(), req.getCompanyName(), req.getWso2Tenant());
        return certs;
    }

    @Override
    protected DiscoverySnapshot buildSnapshot(Map<String, Object> item, DiscoverResourceRequest req, int revision) {
        String alias = str(item.get("alias"));
        String endpoint = str(item.get("endpoint"));

        Map<String, String> meta = new HashMap<>();
        putIfPresent(meta, "endpoint", endpoint);
        putIfPresent(meta, "expiryDate", str(item.get("expiryDate")));
        if (req.getEnvironment() != null) meta.put("environment", req.getEnvironment());

        return DiscoverySnapshot.builder()
                .sourceId(alias)
                .sourceName(alias)
                .payload(item)
                .metadata(meta)
                .build();
    }

    @Override
    protected void populateDetails(DiscoverResourceResponse response, List<DiscoverySnapshot> snapshots) {
        List<CertificateDetail> details = snapshots.stream()
                .map(this::toDetail)
                .collect(Collectors.toList());
        response.setCertificateDetails(details);
    }

    private CertificateDetail toDetail(DiscoverySnapshot snap) {
        Map<String, Object> p = snap.getPayload() != null ? snap.getPayload() : Collections.emptyMap();
        return CertificateDetail.builder()
                .alias(snap.getSourceId())
                .endpoint(str(p.get("endpoint")))
                .subject(str(p.get("subject")))
                .issuer(str(p.get("issuer")))
                .expiryDate(str(p.get("expiryDate")))
                .validFrom(str(p.get("validFrom")))
                .serialNumber(str(p.get("serialNumber")))
                .version(str(p.get("version")))
                .build();
    }

    private static String str(Object o) {
        return o == null ? null : o.toString();
    }

    private static void putIfPresent(Map<String, String> m, String k, String v) {
        if (v != null && !v.isBlank()) m.put(k, v);
    }
}
