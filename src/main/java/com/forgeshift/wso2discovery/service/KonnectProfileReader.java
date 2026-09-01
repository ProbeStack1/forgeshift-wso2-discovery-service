package com.forgeshift.wso2discovery.service;

import com.forgeshift.wso2discovery.client.KonnectCredentials;
import com.forgeshift.wso2discovery.config.Wso2Properties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Reads Kong Konnect profiles written by the profile-config service from the
 * {@code kong_konnect_profiles} collection, falling back to the static
 * {@code forgeshift.wso2.kong.*-fallback} config when no profile matches.
 *
 * <p>Deliberately a trimmed copy of the migration service's
 * {@code KongKonnectProfileReader}: same collection, same field aliases, same
 * control-plane selection rules, so both services resolve the same credentials
 * for a given company. Raw {@code org.bson.Document} reads avoid sharing the
 * profile-config domain classes across services.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KonnectProfileReader {

    private final MongoTemplate mongoTemplate;
    private final Wso2Properties props;

    /**
     * Resolves Konnect credentials for one company / profile / control plane.
     */
    public KonnectCredentials resolve(String companyName, String profileName, String requestedControlPlaneId) {
        if (StringUtils.hasText(companyName)) {
            String desiredName = StringUtils.hasText(profileName) ? profileName : "primary";
            Document doc = findProfile(companyName, desiredName);
            if (doc != null) {
                Document controlPlane = selectedControlPlane(doc, requestedControlPlaneId);
                log.debug("Using Kong Konnect profile (company={}, profileName={})", companyName, desiredName);
                return KonnectCredentials.builder()
                        .source("profile")
                        .konnectBaseUrl(firstString(doc, "adminUrl", "konnectBaseUrl"))
                        .konnectAccessToken(firstString(doc, "konnectPat", "konnectAccessToken"))
                        .controlPlaneId(controlPlaneId(doc, controlPlane))
                        .controlPlaneName(controlPlaneName(doc, controlPlane))
                        .build();
            }
        }
        log.debug("No Kong Konnect profile found for company={} - using static fallback", companyName);
        return KonnectCredentials.builder()
                .source("static")
                .konnectBaseUrl(props.getKong().getBaseUrlFallback())
                .konnectAccessToken(props.getKong().getAccessTokenFallback())
                .controlPlaneId(StringUtils.hasText(requestedControlPlaneId)
                        ? requestedControlPlaneId : props.getKong().getControlPlaneIdFallback())
                .build();
    }

    /**
     * Finds the named ACTIVE profile, or the sole ACTIVE one when "primary" is
     * requested but not present.
     */
    private Document findProfile(String companyName, String desiredName) {
        Query query = Query.query(Criteria.where("companyName").is(companyName)
                .and("profileName").is(desiredName));
        Document doc = mongoTemplate.find(query, Document.class, props.getKong().getProfilesCollection())
                .stream()
                .filter(KonnectProfileReader::isActive)
                .findFirst()
                .orElse(null);
        if (doc != null || !"primary".equalsIgnoreCase(desiredName)) {
            return doc;
        }
        Query fallback = Query.query(Criteria.where("companyName").is(companyName))
                .with(Sort.by(Sort.Direction.DESC, "lastUpdatedAt", "updatedAt", "createdAt"));
        List<Document> active = mongoTemplate.find(fallback, Document.class, props.getKong().getProfilesCollection())
                .stream()
                .filter(KonnectProfileReader::isActive)
                .toList();
        if (active.size() == 1) {
            log.info("No Kong Konnect profile named 'primary' for company={}; using sole ACTIVE profile '{}'",
                    companyName, active.get(0).getString("profileName"));
            return active.get(0);
        }
        return null;
    }

    /**
     * Picks the requested control plane, or the only one when unambiguous.
     */
    private static Document selectedControlPlane(Document doc, String requestedControlPlaneId) {
        Object value = doc.get("controlPlanes");
        if (!(value instanceof List<?> controlPlanes) || controlPlanes.isEmpty()) {
            return null;
        }
        List<Document> docs = controlPlanes.stream()
                .map(KonnectProfileReader::asDocument)
                .filter(Objects::nonNull)
                .toList();
        if (StringUtils.hasText(requestedControlPlaneId)) {
            return docs.stream()
                    .filter(cp -> requestedControlPlaneId.equals(cp.getString("controlPlaneId"))
                            || requestedControlPlaneId.equals(cp.getString("controlPlaneName"))
                            || requestedControlPlaneId.equals(cp.getString("name")))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Control Plane not found in Kong Konnect profile: " + requestedControlPlaneId));
        }
        if (docs.size() == 1) {
            return docs.get(0);
        }
        throw new IllegalArgumentException(
                "Multiple Kong control planes found. Pass kongControlPlane to select the target control plane.");
    }

    private static Document asDocument(Object value) {
        if (value instanceof Document document) {
            return document;
        }
        if (value instanceof Map<?, ?> map) {
            Document document = new Document();
            map.forEach((key, mapValue) -> {
                if (key != null) {
                    document.put(key.toString(), mapValue);
                }
            });
            return document;
        }
        return null;
    }

    private static String controlPlaneId(Document doc, Document controlPlane) {
        if (controlPlane != null && StringUtils.hasText(controlPlane.getString("controlPlaneId"))) {
            return controlPlane.getString("controlPlaneId");
        }
        return doc.getString("controlPlaneId");
    }

    private static String controlPlaneName(Document doc, Document controlPlane) {
        if (controlPlane != null) {
            String name = controlPlane.getString("controlPlaneName");
            if (name == null) {
                name = controlPlane.getString("name");
            }
            if (name != null) {
                return name;
            }
        }
        return doc.getString("controlPlaneName");
    }

    private static String firstString(Document doc, String... keys) {
        for (String key : keys) {
            String value = doc.getString(key);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private static boolean isActive(Document doc) {
        Object status = doc.get("status");
        return status == null || "ACTIVE".equalsIgnoreCase(status.toString());
    }
}
