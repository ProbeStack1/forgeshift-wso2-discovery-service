package com.forgeshift.wso2discovery.util;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Strips noise from raw WSO2 payloads before persistence:
 *   - null values
 *   - empty strings
 *   - empty maps
 *   - empty lists
 *
 * Applied recursively so nested objects (e.g. {@code endpointConfig.endpoint_security.sandbox})
 * are cleaned too. Saves ~30% on snapshot size without losing any information —
 * an absent field and a null/empty field carry the same semantics for migration.
 *
 * <p><b>What it does NOT do:</b> drop fields with semantically-meaningful default
 * values like {@code false} for booleans or {@code 0} for numbers. Migration code
 * may depend on those being explicit.
 */
public final class PayloadCleaner {

    private PayloadCleaner() {}

    /**
     * Returns a new map with null/empty values removed, recursively. Original
     * input is not modified.
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> strip(Map<String, Object> input) {
        if (input == null) return null;
        Map<String, Object> out = new LinkedHashMap<>(input.size());
        for (Map.Entry<String, Object> e : input.entrySet()) {
            Object v = stripValue(e.getValue());
            if (v != null) {
                out.put(e.getKey(), v);
            }
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private static Object stripValue(Object v) {
        if (v == null) return null;
        if (v instanceof String s) {
            return s.isEmpty() ? null : s;
        }
        if (v instanceof Map<?, ?> m) {
            Map<String, Object> cleaned = strip((Map<String, Object>) m);
            return cleaned.isEmpty() ? null : cleaned;
        }
        if (v instanceof List<?> l) {
            List<Object> cleaned = new ArrayList<>(l.size());
            for (Object item : l) {
                Object si = stripValue(item);
                if (si != null) cleaned.add(si);
            }
            return cleaned.isEmpty() ? null : cleaned;
        }
        // Booleans, numbers, dates, etc. — keep as-is even if "falsy".
        return v;
    }
}
