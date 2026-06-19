package com.forgeshift.wso2discovery.client;

import com.forgeshift.wso2discovery.config.Wso2Properties;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.Map;

/**
 * Minimal Kong Admin API client used by the WSO2 user migration service.
 */
@Component
@RequiredArgsConstructor
public class KongAdminClient {

    private final WebClient.Builder webClientBuilder;
    private final Wso2Properties properties;

    /**
     * Creates or updates one Kong consumer by username.
     */
    public void upsertConsumer(String userName, String customId) {
        validateConfigured();
        webClientBuilder.build()
                .put()
                .uri(properties.getKong().getBaseUrl() + properties.getKong().getConsumerPath() + "/" + encodePath(userName))
                .headers(this::applyHeaders)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("username", userName, "custom_id", customId != null ? customId : userName))
                .retrieve()
                .toBodilessEntity()
                .timeout(Duration.ofSeconds(properties.getKong().getRequestTimeoutSeconds()))
                .block();
    }

    /**
     * Assigns one Kong ACL/group role to a consumer.
     */
    public void assignRole(String userName, String kongRoleName) {
        validateConfigured();
        String path = properties.getKong().getAclPathTemplate().replace("{consumer}", encodePath(userName));
        webClientBuilder.build()
                .post()
                .uri(properties.getKong().getBaseUrl() + path)
                .headers(this::applyHeaders)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("group", kongRoleName))
                .retrieve()
                .toBodilessEntity()
                .timeout(Duration.ofSeconds(properties.getKong().getRequestTimeoutSeconds()))
                .block();
    }

    /**
     * Adds the configured Kong admin token when present.
     */
    private void applyHeaders(HttpHeaders headers) {
        if (StringUtils.hasText(properties.getKong().getAdminToken())) {
            headers.set("Kong-Admin-Token", properties.getKong().getAdminToken());
        }
    }

    /**
     * Ensures migration does not call an undefined Kong Admin API endpoint.
     */
    private void validateConfigured() {
        if (!StringUtils.hasText(properties.getKong().getBaseUrl())) {
            throw new IllegalStateException("Kong Admin API base URL is not configured");
        }
    }

    /**
     * Encodes path segments without allowing slash traversal.
     */
    private String encodePath(String value) {
        return value.replace("/", "%2F").replace(" ", "%20");
    }
}
