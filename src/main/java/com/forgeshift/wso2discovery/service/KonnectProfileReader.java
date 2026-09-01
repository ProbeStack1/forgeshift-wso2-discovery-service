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
import java.util.Optional;

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
     * Resolves every Kong Konnect control plane a request should be applied to.
     *
     * <p>A migrated <i>user</i> is not environment-specific the way an API is:
     * the same person exists across the organisation, so with no control plane
     * named the answer is <b>all</b> of the profile's control planes rather
     * than an error. Naming one narrows the run to it.
     *
     * @return one credentials entry per target control plane, never empty
     */
    public List<KonnectCredentials> resolveTargets(String companyName, String profileName,
                                                   String requestedControlPlaneId) {
        if (StringUtils.hasText(companyName)) {
            String desiredName = StringUtils.hasText(profileName) ? profileName : "primary";
            Document doc = findProfile(companyName, desiredName);
            if (doc != null) {
                log.debug("Using Kong Konnect profile (company={}, profileName={})", companyName, desiredName);
                String baseUrl = firstString(doc, "adminUrl", "konnectBaseUrl");
                String accessToken = firstString(doc, "konnectPat", "konnectAccessToken");
                List<Document> controlPlanes = controlPlaneDocs(doc);
                if (controlPlanes.isEmpty()) {
                    // Older profiles carry the id on the profile itself.
                    return List.of(credentials("profile", baseUrl, accessToken,
                            doc.getString("controlPlaneId"), doc.getString("controlPlaneName")));
                }
                return targetControlPlanes(controlPlanes, requestedControlPlaneId).stream()
                        .map(cp -> credentials("profile", baseUrl, accessToken,
                                cp.getString("controlPlaneId"), controlPlaneName(doc, cp)))
                        .toList();
            }
        }
        log.debug("No Kong Konnect profile found for company={} - using static fallback", companyName);
        return List.of(credentials("static",
                props.getKong().getBaseUrlFallback(),
                props.getKong().getAccessTokenFallback(),
                isUnspecified(requestedControlPlaneId)
                        ? props.getKong().getControlPlaneIdFallback() : requestedControlPlaneId,
                null));
    }

    /**
     * Narrows to the requested control plane, or returns them all when none was
     * named.
     */
    private static List<Document> targetControlPlanes(List<Document> controlPlanes, String requestedControlPlaneId) {
        if (StringUtils.hasText(requestedControlPlaneId)) {
            Optional<Document> match = controlPlanes.stream()
                    .filter(cp -> requestedControlPlaneId.equals(cp.getString("controlPlaneId"))
                            || requestedControlPlaneId.equals(cp.getString("controlPlaneName"))
                            || requestedControlPlaneId.equals(cp.getString("name")))
                    .findFirst();
            if (match.isPresent()) {
                return List.of(match.get());
            }
            // Asking for a plane that does not exist is an error, unless the
            // value is a placeholder meaning "unspecified".
            if (!isUnspecified(requestedControlPlaneId)) {
                throw new IllegalArgumentException(
                        "Control Plane not found in Kong Konnect profile: " + requestedControlPlaneId
                                + ". Available: " + describe(controlPlanes));
            }
        }
        return controlPlanes;
    }

    private static KonnectCredentials credentials(String source, String baseUrl, String accessToken,
                                                  String controlPlaneId, String controlPlaneName) {
        return KonnectCredentials.builder()
                .source(source)
                .konnectBaseUrl(baseUrl)
                .konnectAccessToken(accessToken)
                .controlPlaneId(controlPlaneId)
                .controlPlaneName(controlPlaneName)
                .build();
    }

    /**
     * Treats {@code "default"} and {@code "all"} as "no control plane
     * requested".
     *
     * <p>Callers have long sent {@code kongControlPlane: "default"} to mean
     * "unspecified" - the w2k UI did so from a localStorage key nothing ever
     * wrote, and the migration status document uses the same placeholder.
     * A control plane genuinely named "default" still wins, because matching
     * is attempted before this fallback applies.
     */
    private static boolean isUnspecified(String requestedControlPlaneId) {
        if (!StringUtils.hasText(requestedControlPlaneId)) {
            return true;
        }
        String trimmed = requestedControlPlaneId.trim();
        return "default".equalsIgnoreCase(trimmed) || "all".equalsIgnoreCase(trimmed);
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
     * Reads the profile's control planes as documents, empty when absent.
     */
    private static List<Document> controlPlaneDocs(Document doc) {
        Object value = doc.get("controlPlanes");
        if (!(value instanceof List<?> controlPlanes) || controlPlanes.isEmpty()) {
            return List.of();
        }
        return controlPlanes.stream()
                .map(KonnectProfileReader::asDocument)
                .filter(Objects::nonNull)
                .toList();
    }

    /**
     * Renders the selectable control planes as {@code name (id)} so the error
     * carries the values the caller can actually pass back.
     */
    private static String describe(List<Document> controlPlanes) {
        return controlPlanes.stream()
                .map(cp -> {
                    String name = cp.getString("controlPlaneName") != null
                            ? cp.getString("controlPlaneName") : cp.getString("name");
                    String id = cp.getString("controlPlaneId");
                    if (!StringUtils.hasText(name)) {
                        return String.valueOf(id);
                    }
                    return StringUtils.hasText(id) ? name + " (" + id + ")" : name;
                })
                .collect(java.util.stream.Collectors.joining(", "));
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
