package com.forgeshift.wso2discovery.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.forgeshift.wso2discovery.domain.Wso2TenantProfile;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * Public-facing profile shape. Used for both reads (with secrets masked) and
 * writes (with secrets accepted). Never leak the raw secret value in responses;
 * the {@link #fromMasked} factory blanks them.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Wso2TenantProfileDto {

    @Schema(description = "Composite id: {companyName}|{profileName}. Server-derived; ignored on POST.")
    private String id;

    @NotBlank
    @Schema(description = "Multi-tenancy partner id", example = "probestack")
    private String companyName;

    @NotBlank
    @Schema(description = "Profile name (unique per company)", example = "primary")
    private String profileName;

    @Schema(description = "Tenants this profile manages",
            example = "[\"carbon.super\", \"bank.local\"]")
    private List<String> tenants;

    @NotBlank
    @Schema(description = "WSO2 base URL", example = "https://wso2.example.com:9443")
    private String wso2BaseUrl;

    @NotBlank
    @Schema(description = "WSO2 admin username")
    private String username;

    @Schema(description = "WSO2 admin password. Required on write, masked on read.")
    private String password;

    @NotBlank
    @Schema(description = "OAuth2 client_id registered against this WSO2 instance")
    private String clientId;

    @Schema(description = "OAuth2 client_secret. Required on write, masked on read.")
    private String clientSecret;

    @Schema(description = "Whether to trust self-signed TLS certs when calling this WSO2")
    private Boolean trustSelfSigned;

    @Schema(description = "Free-form operator notes")
    private String notes;

    private Instant createdAt;
    private Instant updatedAt;

    /** Convert from domain, masking secret fields. */
    public static Wso2TenantProfileDto fromMasked(Wso2TenantProfile p) {
        return Wso2TenantProfileDto.builder()
                .id(p.getId())
                .companyName(p.getCompanyName())
                .profileName(p.getProfileName())
                .tenants(p.getTenants())
                .wso2BaseUrl(p.getWso2BaseUrl())
                .username(p.getUsername())
                .password(mask(p.getPassword()))
                .clientId(p.getClientId())
                .clientSecret(mask(p.getClientSecret()))
                .trustSelfSigned(p.isTrustSelfSigned())
                .notes(p.getNotes())
                .createdAt(p.getCreatedAt())
                .updatedAt(p.getUpdatedAt())
                .build();
    }

    /** Convert to domain. */
    public Wso2TenantProfile toDomain() {
        return Wso2TenantProfile.builder()
                .companyName(companyName)
                .profileName(profileName)
                .tenants(tenants)
                .wso2BaseUrl(wso2BaseUrl)
                .username(username)
                .password(password)
                .clientId(clientId)
                .clientSecret(clientSecret)
                .trustSelfSigned(Boolean.TRUE.equals(trustSelfSigned))
                .notes(notes)
                .build();
    }

    private static String mask(String s) {
        if (s == null || s.isEmpty()) return null;
        if (s.length() <= 6) return "***";
        return s.substring(0, 4) + "..." + s.substring(s.length() - 2);
    }
}
