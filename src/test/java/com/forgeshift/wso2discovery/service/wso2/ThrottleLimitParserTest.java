package com.forgeshift.wso2discovery.service.wso2;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ThrottleLimitParserTest {

    @Test
    void parsesStandardSubscriptionTiers() {
        ThrottleLimitParser.Limit bronze = ThrottleLimitParser.parse("Allows 1000 requests per minute");
        assertEquals(1000L, bronze.requestCount());
        assertEquals("min", bronze.timeUnit());
        assertEquals(1, bronze.unitTime());

        assertEquals(5000L, ThrottleLimitParser.parse("Allows 5000 requests per minute").requestCount());
        assertEquals(50L, ThrottleLimitParser.parse("Allows 50 request per minute").requestCount());
    }

    @Test
    void handlesRequestSParensAndOtherWindows() {
        ThrottleLimitParser.Limit unauth = ThrottleLimitParser.parse("Allows 500 request(s) per minute");
        assertEquals(500L, unauth.requestCount());
        assertEquals("min", unauth.timeUnit());

        ThrottleLimitParser.Limit perDay = ThrottleLimitParser.parse("Allows 50000 events per day");
        assertEquals(50000L, perDay.requestCount());
        assertEquals("day", perDay.timeUnit());

        ThrottleLimitParser.Limit perMonth = ThrottleLimitParser.parse("Allows 10000 events per month and 1000 active subscriptions");
        assertEquals(10000L, perMonth.requestCount());
        assertEquals("month", perMonth.timeUnit());
    }

    @Test
    void picksTheRequestsClauseForAiTiers() {
        // "1000 total tokens and 10 requests per minute" — the requests-per-minute part is the rate limit.
        ThrottleLimitParser.Limit ai = ThrottleLimitParser.parse("Allows 1000 total tokens and 10 requests per minute");
        assertEquals(10L, ai.requestCount());
        assertEquals("min", ai.timeUnit());
    }

    @Test
    void returnsNullForUnlimitedOrUnparseableOrNull() {
        assertNull(ThrottleLimitParser.parse("Allows unlimited requests"));
        assertNull(ThrottleLimitParser.parse("Allows unlimited events and unlimited active subscriptions"));
        assertNull(ThrottleLimitParser.parse("some unrelated text"));
        assertNull(ThrottleLimitParser.parse(null));
    }
}
