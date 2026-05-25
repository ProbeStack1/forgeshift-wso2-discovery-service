package com.forgeshift.wso2discovery.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * One entry in the {@code GET /wso2/history} response. Mirrors the Apigee
 * {@code HistorySnapshotItem}: a past discovery identified by a historyId
 * (the requestTransactionId stamped on every snapshot of that run) and the
 * per-resource counts that made it up.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class HistorySnapshot {

    @Schema(description = "Discovery transaction id (requestTransactionId)",
            example = "NKTA_probestack_carbon.super_20260525174630990")
    private String historyId;

    @Schema(description = "When the discovery run produced this snapshot",
            example = "2026-05-25T12:16:49.755626957Z")
    private OffsetDateTime fetchedAt;

    @Schema(description = "Monotonic revision for this (companyName, wso2Tenant)", example = "170")
    private Integer revision;

    @Schema(description = "Per-resource-type counts, e.g. {\"apis\":12,\"applications\":5,...}")
    private Map<String, Object> summary;
}
