package com.forgeshift.wso2discovery.client;

import com.forgeshift.wso2discovery.config.Wso2Properties;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.util.UriUtils;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Kong <b>Konnect</b> management API client used by WSO2 user migration.
 *
 * <p>This targets Konnect, not a self-managed Kong Gateway. Two things differ
 * from the classic Admin API and both fail in confusing ways when wrong:
 * <ul>
 *   <li>auth is {@code Authorization: Bearer <PAT>}, not {@code Kong-Admin-Token}</li>
 *   <li>entities live under {@code /v2/control-planes/{cpId}/core-entities/...},
 *       not at the root</li>
 * </ul>
 * A wrong header yields 401 and a wrong path yields 404, neither of which names
 * the real cause, so both are centralised here.
 *
 * <p>Writes are idempotent: Kong reports a duplicate username or group as a
 * unique constraint violation, which is adopted rather than treated as a
 * failure, so re-running a migration is safe.
 */
@Slf4j
@Component
public class KongAdminClient {

    private final WebClient webClient;
    private final Wso2Properties properties;

    public KongAdminClient(@Qualifier("konnectWebClient") WebClient webClient, Wso2Properties properties) {
        this.webClient = webClient;
        this.properties = properties;
    }

    /** Distinguishes a write we performed from one that was already in place. */
    public enum WriteOutcome { CREATED, ALREADY_EXISTS }

    @Data
    @Builder
    public static class ConsumerRef {
        /** Konnect consumer uuid; null when Kong did not return one. */
        private String id;
        private String username;
        private WriteOutcome outcome;
    }

    /**
     * Creates the Kong consumer for one WSO2 user, adopting an existing
     * consumer of the same username instead of failing.
     *
     * <p>A control plane has a single consumer list, unique on both username
     * and custom_id, and the migration service already fills it with WSO2
     * <i>applications</i>. Identity is therefore qualified rather than taken
     * raw from WSO2: the username is namespaced by principal type and tenant,
     * and the custom_id is a fully qualified source key. Passing a raw
     * username would let a user called {@code admin} collide with an
     * application called {@code admin}, and let the same username in two
     * tenants collapse into one consumer.
     */
    public ConsumerRef ensureConsumer(KonnectCredentials creds, String wso2Tenant, String userName) {
        validateConfigured(creds);
        String username = consumerUsername(wso2Tenant, userName);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("username", username);
        payload.put("custom_id", consumerCustomId(wso2Tenant, userName));
        payload.put("tags", consumerTags(wso2Tenant, userName));
        try {
            Map<String, Object> body = post(creds, consumersEndpoint(creds), payload);
            return ConsumerRef.builder()
                    .id(body == null ? null : asString(body.get("id")))
                    .username(username)
                    .outcome(WriteOutcome.CREATED)
                    .build();
        } catch (WebClientResponseException e) {
            if (!isUniqueConstraintError(e)) {
                throw e;
            }
            log.debug("Kong consumer {} already exists - adopting", username);
            return ConsumerRef.builder()
                    .id(findConsumerId(creds, username))
                    .username(username)
                    .outcome(WriteOutcome.ALREADY_EXISTS)
                    .build();
        }
    }

    /**
     * Builds the namespaced consumer username for one WSO2 user, for example
     * {@code user.carbon-super.api-developer}.
     *
     * <p>Each segment is slugified the same way the migration service slugifies
     * application names, so the two naming schemes stay comparable, and the
     * literal dots remain unambiguous separators because a slug cannot contain
     * one.
     */
    public String consumerUsername(String wso2Tenant, String userName) {
        StringBuilder name = new StringBuilder();
        String prefix = properties.getKong().getConsumerUsernamePrefix();
        if (StringUtils.hasText(prefix)) {
            name.append(slug(prefix)).append('.');
        }
        if (StringUtils.hasText(wso2Tenant)) {
            name.append(slug(wso2Tenant)).append('.');
        }
        return name.append(slug(userName)).toString();
    }

    /**
     * Builds the stable external key Kong stores in custom_id.
     *
     * <p>Uses the raw tenant and username so the value stays a faithful pointer
     * back to WSO2. This must never be derived from something like an email
     * address: Kong requires custom_id to be unique, and two WSO2 users sharing
     * a mailbox would collide, leaving the second user unmigrated.
     */
    private String consumerCustomId(String wso2Tenant, String userName) {
        return "wso2:user:" + (StringUtils.hasText(wso2Tenant) ? wso2Tenant : "unknown") + ":" + userName;
    }

    /**
     * Assigns one Kong ACL group to a consumer, treating an existing
     * membership as success.
     */
    public WriteOutcome assignGroup(KonnectCredentials creds, String consumerRef, String kongRoleName) {
        validateConfigured(creds);
        try {
            post(creds, aclEndpoint(creds, consumerRef), Map.of("group", kongRoleName));
            return WriteOutcome.CREATED;
        } catch (WebClientResponseException e) {
            if (isUniqueConstraintError(e) || alreadyInGroup(creds, consumerRef, kongRoleName)) {
                log.debug("Consumer {} already in Kong group {}", consumerRef, kongRoleName);
                return WriteOutcome.ALREADY_EXISTS;
            }
            throw e;
        }
    }

    /**
     * POSTs one JSON body and returns the parsed response.
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> post(KonnectCredentials creds, String url, Map<String, Object> payload) {
        return (Map<String, Object>) webClient.post()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + creds.getKonnectAccessToken())
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .bodyValue(payload)
                .retrieve()
                .bodyToMono(Map.class)
                .timeout(Duration.ofSeconds(properties.getKong().getRequestTimeoutSeconds()))
                .block();
    }

    /**
     * Looks a consumer up by username. Konnect accepts either id or username in
     * the path segment. Returns null when absent or unreadable.
     */
    @SuppressWarnings("unchecked")
    private String findConsumerId(KonnectCredentials creds, String userName) {
        try {
            Map<String, Object> body = (Map<String, Object>) webClient.get()
                    .uri(URI.create(consumersEndpoint(creds) + "/" + encodeSegment(userName)))
                    .header("Authorization", "Bearer " + creds.getKonnectAccessToken())
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .timeout(Duration.ofSeconds(properties.getKong().getRequestTimeoutSeconds()))
                    .block();
            return body == null ? null : asString(body.get("id"));
        } catch (WebClientResponseException e) {
            if (e.getStatusCode().value() != 404) {
                log.warn("Consumer lookup failed for {}: {} {}", userName,
                        e.getStatusCode(), e.getResponseBodyAsString());
            }
            return null;
        } catch (Exception e) {
            log.warn("Consumer lookup failed for {}: {}", userName, e.getMessage());
            return null;
        }
    }

    /**
     * Confirms an existing ACL membership when the POST failed with a conflict
     * that Kong did not phrase as a unique constraint.
     */
    @SuppressWarnings("unchecked")
    private boolean alreadyInGroup(KonnectCredentials creds, String consumerRef, String kongRoleName) {
        try {
            Map<String, Object> body = (Map<String, Object>) webClient.get()
                    .uri(URI.create(aclEndpoint(creds, consumerRef)))
                    .header("Authorization", "Bearer " + creds.getKonnectAccessToken())
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .timeout(Duration.ofSeconds(properties.getKong().getRequestTimeoutSeconds()))
                    .block();
            if (body == null || !(body.get("data") instanceof List<?> items)) {
                return false;
            }
            return items.stream()
                    .filter(Map.class::isInstance)
                    .map(item -> (Map<?, ?>) item)
                    .anyMatch(item -> kongRoleName.equals(asString(item.get("group"))));
        } catch (Exception e) {
            log.warn("ACL lookup failed for consumer {}: {}", consumerRef, e.getMessage());
            return false;
        }
    }

    /**
     * Konnect reports a duplicate as 400 or 409 carrying a unique constraint
     * message; anything else is a real failure.
     */
    private boolean isUniqueConstraintError(WebClientResponseException e) {
        int code = e.getStatusCode().value();
        if (code != 400 && code != 409) {
            return false;
        }
        String body = e.getResponseBodyAsString();
        return body != null && body.contains("(type: unique) constraint failed");
    }

    /**
     * Tags every consumer so a later decK sync can select these entities,
     * matching the migration service convention.
     *
     * <p>{@code principal-type} is what lets anyone tell a migrated person from
     * a migrated application once both share the control plane consumer list.
     */
    private List<String> consumerTags(String wso2Tenant, String userName) {
        List<String> tags = new ArrayList<>();
        tags.add(properties.getKong().getMigratedByTag());
        tags.add(properties.getKong().getPrincipalTypeTag());
        tags.add(properties.getKong().getSourceUserTagPrefix() + ":" + userName);
        if (StringUtils.hasText(wso2Tenant)) {
            tags.add(properties.getKong().getTenantTagPrefix() + ":" + wso2Tenant);
        }
        return tags;
    }

    /**
     * Lowercases and reduces to alphanumerics joined by hyphens, matching the
     * migration service slug rules for application names.
     */
    private static String slug(String value) {
        if (!StringUtils.hasText(value)) {
            return "unknown";
        }
        String slug = value.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        return slug.isEmpty() ? "unknown" : slug;
    }

    private String consumersEndpoint(KonnectCredentials creds) {
        return coreEntities(creds) + properties.getKong().getConsumersPath();
    }

    private String aclEndpoint(KonnectCredentials creds, String consumerRef) {
        return coreEntities(creds) + properties.getKong().getAclPathTemplate()
                .replace("{consumer}", encodeSegment(consumerRef));
    }

    private String coreEntities(KonnectCredentials creds) {
        return trimTrailingSlash(creds.getKonnectBaseUrl())
                + "/v2/control-planes/" + creds.getControlPlaneId() + "/core-entities";
    }

    /**
     * Fails fast with an actionable message rather than calling an undefined
     * endpoint when the Konnect profile is missing or incomplete.
     */
    private void validateConfigured(KonnectCredentials creds) {
        if (creds == null || !StringUtils.hasText(creds.getKonnectBaseUrl())) {
            throw new IllegalStateException("Kong Konnect base URL is not configured");
        }
        if (!StringUtils.hasText(creds.getKonnectAccessToken())) {
            throw new IllegalStateException("Kong Konnect access token is not configured");
        }
        if (!StringUtils.hasText(creds.getControlPlaneId())) {
            throw new IllegalStateException("Kong Konnect control plane id is not configured");
        }
    }

    private static String trimTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private static String encodeSegment(String value) {
        return UriUtils.encodePathSegment(value, StandardCharsets.UTF_8);
    }

    private static String asString(Object value) {
        return value == null ? null : value.toString();
    }
}
