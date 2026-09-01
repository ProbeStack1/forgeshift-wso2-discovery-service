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
import java.util.List;
import java.util.Map;

/**
 * Kong Konnect <b>identity</b> API: organization users and teams.
 *
 * <p>Kept separate from the control plane entity API, because this is a
 * different API on a different host at a different version. Entities live at
 * {@code {region}.api.konghq.com/v2/control-planes/{id}/core-entities}; identity
 * lives at {@code global.api.konghq.com/v3}. Pointing either at the other
 * returns 404 with nothing to explain why, so the two are never mixed.
 *
 * <p>Teams are <b>organization-wide</b>. Unlike consumers there is no control
 * plane in any of these paths, so membership is not something a run can target
 * per control plane.
 */
@Slf4j
@Component
public class KonnectIdentityClient {

    private final WebClient webClient;
    private final Wso2Properties properties;

    public KonnectIdentityClient(@Qualifier("konnectWebClient") WebClient webClient, Wso2Properties properties) {
        this.webClient = webClient;
        this.properties = properties;
    }

    @Data
    @Builder
    public static class KonnectTeam {
        private String id;
        private String name;
        private String description;
        /** True for the teams Konnect ships with, which is all of them today. */
        private boolean systemTeam;
    }

    /**
     * How the organization signs people in.
     *
     * <p>Worth reading before a migration: with no identity provider nobody
     * arrives in Konnect on their own, so every user resolves to nothing and
     * the run reports failures that are really a missing precondition.
     */
    @Data
    @Builder
    public static class KonnectAuthSettings {
        private boolean oidcEnabled;
        private boolean samlEnabled;
        private boolean basicAuthEnabled;
        private boolean idpMappingEnabled;

        /** True when people can arrive through an identity provider. */
        public boolean isSsoConfigured() {
            return oidcEnabled || samlEnabled;
        }
    }

    /**
     * Reads the organization's authentication settings.
     */
    public KonnectAuthSettings getAuthSettings(KonnectCredentials creds) {
        validateConfigured(creds);
        Map<String, Object> body = get(creds, identityUrl(properties.getKong().getIdentityAuthSettingsPath()));
        if (body == null) {
            return KonnectAuthSettings.builder().build();
        }
        return KonnectAuthSettings.builder()
                .oidcEnabled(Boolean.TRUE.equals(body.get("oidc_auth_enabled")))
                .samlEnabled(Boolean.TRUE.equals(body.get("saml_auth_enabled")))
                .basicAuthEnabled(Boolean.TRUE.equals(body.get("basic_auth_enabled")))
                .idpMappingEnabled(Boolean.TRUE.equals(body.get("idp_mapping_enabled")))
                .build();
    }

    /**
     * Every team in the organization. Konnect predefines these, so migration
     * maps onto them rather than creating any.
     */
    @SuppressWarnings("unchecked")
    public List<KonnectTeam> listTeams(KonnectCredentials creds) {
        validateConfigured(creds);
        Map<String, Object> body = get(creds, identityUrl(properties.getKong().getIdentityTeamsPath()));
        List<KonnectTeam> teams = new ArrayList<>();
        if (body == null || !(body.get("data") instanceof List<?> items)) {
            return teams;
        }
        for (Object item : items) {
            if (!(item instanceof Map<?, ?> team)) continue;
            teams.add(KonnectTeam.builder()
                    .id(asString(team.get("id")))
                    .name(asString(team.get("name")))
                    .description(asString(team.get("description")))
                    .systemTeam(Boolean.TRUE.equals(team.get("system_team")))
                    .build());
        }
        return teams;
    }

    /**
     * The Konnect user with this email, or null when the organization has none.
     *
     * <p>Email is the only usable key: Konnect has no concept of a WSO2
     * username, and a person who has never signed in or been invited simply
     * does not exist here yet.
     */
    @SuppressWarnings("unchecked")
    public String findUserIdByEmail(KonnectCredentials creds, String email) {
        validateConfigured(creds);
        if (!StringUtils.hasText(email)) {
            return null;
        }
        String url = identityUrl(properties.getKong().getIdentityUsersPath())
                + "?filter%5Bemail%5D%5Beq%5D=" + UriUtils.encode(email.trim(), StandardCharsets.UTF_8);
        Map<String, Object> body = get(creds, url);
        if (body == null || !(body.get("data") instanceof List<?> items) || items.isEmpty()) {
            return null;
        }
        Object first = items.get(0);
        return first instanceof Map<?, ?> user ? asString(user.get("id")) : null;
    }

    /**
     * Adds one user to one team, treating an existing membership as success so
     * a re-run is safe.
     */
    public KonnectWriteOutcome addUserToTeam(KonnectCredentials creds, String teamId, String userId) {
        validateConfigured(creds);
        String url = identityUrl(properties.getKong().getIdentityTeamsPath())
                + "/" + UriUtils.encodePathSegment(teamId, StandardCharsets.UTF_8) + "/users";
        try {
            webClient.post()
                    .uri(URI.create(url))
                    .header("Authorization", "Bearer " + creds.getKonnectAccessToken())
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .bodyValue(Map.of("id", userId))
                    .retrieve()
                    .toBodilessEntity()
                    .timeout(Duration.ofSeconds(properties.getKong().getRequestTimeoutSeconds()))
                    .block();
            return KonnectWriteOutcome.CREATED;
        } catch (WebClientResponseException e) {
            if (isAlreadyMember(e)) {
                log.debug("User {} is already in team {}", userId, teamId);
                return KonnectWriteOutcome.ALREADY_EXISTS;
            }
            throw e;
        }
    }

    /**
     * Konnect answers a repeat membership with a conflict rather than an error
     * worth surfacing.
     */
    private boolean isAlreadyMember(WebClientResponseException e) {
        int code = e.getStatusCode().value();
        if (code != 400 && code != 409) {
            return false;
        }
        String body = e.getResponseBodyAsString();
        if (body == null) {
            return code == 409;
        }
        String lower = body.toLowerCase();
        return code == 409 || lower.contains("already") || lower.contains("constraint");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> get(KonnectCredentials creds, String url) {
        return (Map<String, Object>) webClient.get()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + creds.getKonnectAccessToken())
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .bodyToMono(Map.class)
                .timeout(Duration.ofSeconds(properties.getKong().getRequestTimeoutSeconds()))
                .block();
    }

    private String identityUrl(String path) {
        String base = properties.getKong().getIdentityBaseUrl();
        return (base.endsWith("/") ? base.substring(0, base.length() - 1) : base) + path;
    }

    /**
     * Only the token is needed here: identity is organization-scoped, so there
     * is no control plane to validate.
     */
    private void validateConfigured(KonnectCredentials creds) {
        if (creds == null || !StringUtils.hasText(creds.getKonnectAccessToken())) {
            throw new IllegalStateException("Kong Konnect access token is not configured");
        }
    }

    private static String asString(Object value) {
        return value == null ? null : value.toString();
    }
}
