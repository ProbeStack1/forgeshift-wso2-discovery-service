package com.forgeshift.wso2discovery.dto;

import com.forgeshift.wso2discovery.domain.DiscoveryJob;
import com.forgeshift.wso2discovery.domain.DiscoveryState;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.Map;

/**
 * Public-facing representation of a {@link DiscoveryJob}.
 */
@Data
@Builder
public class DiscoveryJobResponse {

    private String id;
    private String companyName;
    private String wso2Tenant;
    private String wso2BaseUrl;
    private DiscoveryState state;
    private String discoveryId;
    private Integer revision;
    private Map<String, DiscoveryJob.ResourceProgress> resourceProgress;
    private DiscoveryJob.Counts counts;
    private String createdBy;
    private String lastError;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant completedAt;

    public static DiscoveryJobResponse from(DiscoveryJob j) {
        return DiscoveryJobResponse.builder()
                .id(j.getId())
                .companyName(j.getCompanyName())
                .wso2Tenant(j.getWso2Tenant())
                .wso2BaseUrl(j.getWso2BaseUrl())
                .state(j.getState())
                .discoveryId(j.getDiscoveryId())
                .revision(j.getRevision())
                .resourceProgress(j.getResourceProgress())
                .counts(j.getCounts())
                .createdBy(j.getCreatedBy())
                .lastError(j.getLastError())
                .createdAt(j.getCreatedAt())
                .updatedAt(j.getUpdatedAt())
                .completedAt(j.getCompletedAt())
                .build();
    }
}
