package com.forgeshift.wso2discovery.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;

/**
 * Past WSO2 user-profile discovery runs for one tenant.
 *
 * <p>Separate from {@code GET /wso2/history}, and necessarily so: that endpoint
 * aggregates the per-resource {@code discovery_wso2_*} collections, while user
 * profiles are written to their own store by the SOAP discovery. A run made
 * through {@code POST /users/discovery} therefore never appears there, which
 * left the only record of it unreadable by anything.
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Wso2UserProfileHistoryResponse {

    private String companyName;
    private String wso2Tenant;
    private int totalRuns;
    private List<Wso2UserProfileRunSummary> runs;

    /**
     * One run, named by the transaction that made it.
     */
    @Data
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Wso2UserProfileRunSummary {
        private String requestTransactionId;
        /** Latest write in the run; null on documents that predate the field. */
        private Instant discoveredAt;
        private int totalUsers;
        /** Distinct roles across the run's users, not a count of assignments. */
        private int totalRoles;
    }
}
