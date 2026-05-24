package com.forgeshift.wso2discovery.service;

import com.forgeshift.wso2discovery.client.Wso2Client;
import com.forgeshift.wso2discovery.client.Wso2Credentials;
import com.forgeshift.wso2discovery.config.Wso2Properties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Token acquisition with per-tenant caching.
 *
 * <p>Mirrors the Apigee discovery service's {@code TokenService}: every
 * outgoing WSO2 call goes through {@link #getToken(String, String, String)}
 * which:
 * <ol>
 *   <li>Checks an in-memory cache keyed by {@code companyName:wso2Tenant:scope}.</li>
 *   <li>On miss: resolves credentials via {@link Wso2TenantProfileService}
 *       (profile lookup with static fallback) and calls
 *       {@link Wso2Client#acquireToken(String, Wso2Credentials)}.</li>
 *   <li>Caches the result with a TTL configured via
 *       {@code forgeshift.wso2.token-cache-ttl-seconds} (default 3300s = 55min).</li>
 * </ol>
 *
 * <p>Cache invalidation:
 * <ul>
 *   <li>{@link #invalidate(String, String)} — flush every scope for one tenant.</li>
 *   <li>{@link #invalidate(String, String, String)} — flush one scope for one tenant.</li>
 *   <li>{@link #invalidateAll()} — flush everything.</li>
 * </ul>
 * Operators should call invalidate after a profile-config service update so
 * the discovery service picks up new credentials immediately.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class Wso2TokenService {

    private final Wso2Client wso2Client;
    private final Wso2TenantProfileService profileService;
    private final Wso2Properties props;

    private final Map<String, CachedToken> cache = new ConcurrentHashMap<>();
    private final AtomicLong hits = new AtomicLong();
    private final AtomicLong misses = new AtomicLong();
    private final AtomicLong evictions = new AtomicLong();

    /**
     * Get a bearer token for ({@code companyName}, {@code wso2Tenant}, {@code scope}).
     * Returns the cached value if still fresh; otherwise resolves creds and
     * acquires a new token from WSO2.
     */
    public String getToken(String scope, String companyName, String wso2Tenant) {
        String key = cacheKey(scope, companyName, wso2Tenant);
        CachedToken hit = cache.get(key);
        if (hit != null && !hit.isExpired()) {
            hits.incrementAndGet();
            log.debug("[token-cache HIT] {}", key);
            return hit.token;
        }

        // Double-checked locking — avoids two simultaneous misses both calling WSO2.
        synchronized (interner(key)) {
            hit = cache.get(key);
            if (hit != null && !hit.isExpired()) {
                hits.incrementAndGet();
                return hit.token;
            }
            misses.incrementAndGet();
            Wso2Credentials creds = profileService.resolve(companyName, wso2Tenant);
            String token = wso2Client.acquireToken(scope, creds);
            if (token == null) {
                // Don't cache failures.
                return null;
            }
            long expiresAt = System.currentTimeMillis() + (props.getTokenCacheTtlSeconds() * 1000L);
            cache.put(key, new CachedToken(token, expiresAt));
            evictOversize();
            log.debug("[token-cache MISS -> populated] {} ttlSec={}", key, props.getTokenCacheTtlSeconds());
            return token;
        }
    }

    /** Drop every cache entry whose key starts with {@code companyName:wso2Tenant:}. */
    public int invalidate(String companyName, String wso2Tenant) {
        String prefix = companyName + ":" + wso2Tenant + ":";
        int removed = 0;
        for (String k : cache.keySet()) {
            if (k.startsWith(prefix)) {
                cache.remove(k);
                removed++;
            }
        }
        log.info("[token-cache INVALIDATE] company={} tenant={} removed={}", companyName, wso2Tenant, removed);
        return removed;
    }

    /** Drop one specific (scope, companyName, wso2Tenant) entry. */
    public boolean invalidate(String companyName, String wso2Tenant, String scope) {
        String key = cacheKey(scope, companyName, wso2Tenant);
        boolean removed = cache.remove(key) != null;
        log.info("[token-cache INVALIDATE-ONE] key={} removed={}", key, removed);
        return removed;
    }

    /** Flush everything. */
    public int invalidateAll() {
        int n = cache.size();
        cache.clear();
        log.info("[token-cache INVALIDATE-ALL] removed={}", n);
        return n;
    }

    /** For the /cache-stats endpoint. */
    public Map<String, Object> stats() {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("size", cache.size());
        s.put("hits", hits.get());
        s.put("misses", misses.get());
        s.put("evictions", evictions.get());
        s.put("maxSize", props.getTokenCacheMaxSize());
        s.put("ttlSeconds", props.getTokenCacheTtlSeconds());
        return s;
    }

    // -------------------- internals --------------------

    private static String cacheKey(String scope, String companyName, String wso2Tenant) {
        String c = StringUtils.hasText(companyName) ? companyName : "_";
        String t = StringUtils.hasText(wso2Tenant) ? wso2Tenant : "_";
        String s = StringUtils.hasText(scope) ? scope : "_";
        return c + ":" + t + ":" + s;
    }

    /**
     * Cheap per-key lock without holding the whole cache lock. String.intern
     * is fine for the scale we expect (<1000 keys) and avoids a separate
     * lock map. The grumbles in the literature are about long-lived intern
     * tables under churn — we churn slowly here.
     */
    private static Object interner(String key) {
        return ("wso2-token-lock:" + key).intern();
    }

    /**
     * Best-effort size cap: when over the configured max, evict the entry
     * that expires first. Not LRU; for a tiny cache this is sufficient.
     */
    private void evictOversize() {
        if (cache.size() <= props.getTokenCacheMaxSize()) return;
        String victim = null;
        long earliest = Long.MAX_VALUE;
        for (Map.Entry<String, CachedToken> e : cache.entrySet()) {
            if (e.getValue().expiryMs < earliest) {
                earliest = e.getValue().expiryMs;
                victim = e.getKey();
            }
        }
        if (victim != null) {
            cache.remove(victim);
            evictions.incrementAndGet();
            log.debug("[token-cache EVICT] {}", victim);
        }
    }

    private static final class CachedToken {
        final String token;
        final long expiryMs;
        CachedToken(String token, long expiryMs) {
            this.token = token;
            this.expiryMs = expiryMs;
        }
        boolean isExpired() {
            return System.currentTimeMillis() >= expiryMs;
        }
    }
}
