package com.forgeshift.wso2discovery.controller;

import com.forgeshift.wso2discovery.config.DiscoveryProperties;
import com.forgeshift.wso2discovery.config.Wso2Properties;
import com.forgeshift.wso2discovery.dto.TokenTestRequest;
import com.forgeshift.wso2discovery.dto.TokenTestResponse;
import com.forgeshift.wso2discovery.service.Wso2TokenService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Diagnostic + cache-management endpoints for the in-memory token cache.
 *
 * <ul>
 *   <li>{@code POST /internal/wso2/token/test} — acquire a token through the
 *       cache and report success/auth-type/elapsed. Optional
 *       {@code invalidateCacheFirst=true} body flag bypasses the cache for
 *       this call (forces a fresh WSO2 round-trip).</li>
 *   <li>{@code POST /internal/wso2/token/invalidate} — drop cache entries
 *       for one tenant (and optionally one scope).</li>
 *   <li>{@code POST /internal/wso2/token/invalidate-all} — flush the whole
 *       cache. Use sparingly; affects all in-flight discoveries.</li>
 *   <li>{@code GET /internal/wso2/token/cache-stats} — size + hit/miss counters.</li>
 * </ul>
 *
 * Gated by {@code forgeshift.wso2.token-test.enabled} (default true; set to
 * false in prod profiles to hide these from the public surface).
 */
@Slf4j
@RestController
@RequestMapping("/internal/wso2/token")
@Validated
@RequiredArgsConstructor
@ConditionalOnProperty(name = "forgeshift.wso2.token-test.enabled", havingValue = "true", matchIfMissing = true)
public class Wso2TokenTestController {

    private final Wso2TokenService tokenService;
    private final Wso2Properties wso2Props;
    private final DiscoveryProperties discoveryProps;

    @PostMapping("/test")
    public ResponseEntity<TokenTestResponse> testToken(@Valid @RequestBody TokenTestRequest req) {
        String company = StringUtils.hasText(req.getCompanyName())
                ? req.getCompanyName()
                : discoveryProps.getDefaultCompanyName();
        String requestedScope = StringUtils.hasText(req.getScope())
                ? req.getScope()
                : wso2Props.getPublisherScope();

        String effectiveScope = "inventory".equalsIgnoreCase(requestedScope)
                ? wso2Props.inventoryScope()
                : requestedScope;

        String serviceTxn = UUID.randomUUID().toString();
        log.info("POST /internal/wso2/token/test serviceTxn={} requestTxn={} company={} tenant={} scope={} bustCache={}",
                serviceTxn, req.getRequestTransactionId(), company, req.getWso2Tenant(),
                effectiveScope, req.isInvalidateCacheFirst());

        if (req.isInvalidateCacheFirst()) {
            tokenService.invalidate(company, req.getWso2Tenant(), effectiveScope);
        }

        long start = System.currentTimeMillis();
        String token = null;
        String error = null;
        try {
            token = tokenService.getToken(effectiveScope, company, req.getWso2Tenant());
        } catch (Exception e) {
            error = e.getMessage();
        }
        long elapsed = System.currentTimeMillis() - start;

        TokenTestResponse.TokenTestResponseBuilder b = TokenTestResponse.builder()
                .serviceTransactionId(serviceTxn)
                .requestTransactionId(req.getRequestTransactionId())
                .companyName(company)
                .wso2Tenant(req.getWso2Tenant())
                .scopeRequested(effectiveScope)
                .elapsedMs(elapsed);

        if (token != null && !token.isBlank()) {
            b.success(true)
                    .tokenAuthType("BEARER")
                    .tokenAcquiredAt(Instant.now())
                    .tokenPrefix(token.length() > 6 ? token.substring(0, 6) + "..." : "***");
        } else {
            b.success(false)
                    .tokenAuthType("NONE")
                    .errorMessage(error != null ? error
                            : "WSO2 returned no access_token. Check server log for the specific 4xx/5xx body.");
        }
        return ResponseEntity.ok(b.build());
    }

    /**
     * Drop cache entries for one tenant. Body shape:
     * <pre>
     *   { "companyName": "probestack", "wso2Tenant": "carbon.super", "scope": "apim:admin" }
     * </pre>
     * Omit {@code scope} to drop every scope for that tenant.
     */
    @PostMapping("/invalidate")
    public ResponseEntity<Map<String, Object>> invalidate(@RequestBody Map<String, String> body) {
        String company = body.get("companyName");
        String tenant = body.get("wso2Tenant");
        String scope = body.get("scope");
        if (!StringUtils.hasText(company)) company = discoveryProps.getDefaultCompanyName();
        if (!StringUtils.hasText(tenant)) {
            throw new IllegalArgumentException("wso2Tenant is required");
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("companyName", company);
        out.put("wso2Tenant", tenant);
        if (StringUtils.hasText(scope)) {
            boolean removed = tokenService.invalidate(company, tenant, scope);
            out.put("scope", scope);
            out.put("removed", removed ? 1 : 0);
        } else {
            int removed = tokenService.invalidate(company, tenant);
            out.put("removed", removed);
        }
        return ResponseEntity.ok(out);
    }

    @PostMapping("/invalidate-all")
    public ResponseEntity<Map<String, Object>> invalidateAll() {
        int removed = tokenService.invalidateAll();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("removed", removed);
        return ResponseEntity.ok(out);
    }

    @GetMapping("/cache-stats")
    public ResponseEntity<Map<String, Object>> stats() {
        return ResponseEntity.ok(tokenService.stats());
    }
}
