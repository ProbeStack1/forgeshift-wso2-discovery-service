package com.forgeshift.wso2discovery.dto;

/**
 * Request body for normalized WSO2 user-profile discovery.
 *
 * Reuses the standard discovery request fields so callers can supply the same
 * company, tenant, environment, transaction id, and user email metadata used by
 * the existing per-resource discovery APIs.
 */
public class Wso2UserProfileDiscoveryRequest extends DiscoverResourceRequest {
}
