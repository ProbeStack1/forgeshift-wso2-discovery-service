package com.forgeshift.wso2discovery.service.wso2;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Recovers a structured rate limit from a WSO2 throttling-policy {@code description}.
 *
 * <p>WSO2's throttling-policy <b>list</b> endpoints (subscription / application / advanced) return
 * only a human-readable {@code description} like {@code "Allows 1000 requests per minute"} — the
 * structured {@code defaultLimit.requestCount}/{@code timeUnit} is present only on the per-policy
 * <i>detail</i> endpoint. This parser extracts the number + window from that prose so a discovered
 * policy still carries a usable limit (the downstream Kong rate-limiting translation needs it).
 *
 * <p>Examples handled:
 * <ul>
 *   <li>{@code "Allows 1000 requests per minute"} &rarr; 1000 / min</li>
 *   <li>{@code "Allows 500 request(s) per minute"} &rarr; 500 / min</li>
 *   <li>{@code "Allows 50000 events per day"} &rarr; 50000 / day</li>
 *   <li>{@code "Allows 1000 total tokens and 10 requests per minute"} &rarr; 10 / min (the request part)</li>
 *   <li>{@code "Allows unlimited requests"} &rarr; {@code null} (no numeric limit)</li>
 * </ul>
 */
public final class ThrottleLimitParser {

    private ThrottleLimitParser() {
    }

    // <number> [request|event|call](s|(s)) per <window>
    private static final Pattern RATE = Pattern.compile(
            "(\\d[\\d,]*)\\s+(?:request|event|call)s?(?:\\(s\\))?\\s+per\\s+"
                    + "(second|sec|minute|min|hour|hr|day|week|month|year)",
            Pattern.CASE_INSENSITIVE);

    /** Parsed limit. {@code requestCount} is non-null only when a numeric "N ... per &lt;unit&gt;" was found. */
    public record Limit(Long requestCount, String timeUnit, Integer unitTime) {
    }

    /** Parse the description; returns {@code null} when there is no numeric rate (e.g. "unlimited"). */
    public static Limit parse(String description) {
        if (description == null) {
            return null;
        }
        Matcher m = RATE.matcher(description);
        if (!m.find()) {
            return null;
        }
        long count;
        try {
            count = Long.parseLong(m.group(1).replace(",", ""));
        } catch (NumberFormatException e) {
            return null;
        }
        return new Limit(count, normalizeUnit(m.group(2)), 1);
    }

    /** Map the matched word onto WSO2's {@code timeUnit} vocabulary: sec, min, hour, day, week, month, year. */
    private static String normalizeUnit(String raw) {
        return switch (raw.toLowerCase()) {
            case "second", "sec" -> "sec";
            case "minute", "min" -> "min";
            case "hour", "hr" -> "hour";
            case "day" -> "day";
            case "week" -> "week";
            case "month" -> "month";
            case "year" -> "year";
            default -> raw.toLowerCase();
        };
    }
}
