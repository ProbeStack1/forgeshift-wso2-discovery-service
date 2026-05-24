package com.forgeshift.wso2discovery.dto.details;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * UI-friendly summary of one endpoint TLS Certificate.
 *
 * In WSO2 4.x these are uploaded against an alias and bound to a backend
 * endpoint URL via the {@code endpoint} field. Used downstream by the
 * migrator to populate Kong certificate + SNI objects.
 *
 * Populated into {@code DiscoverResourceResponse.certificateDetails} when
 * {@code type == certificates}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CertificateDetail {

    @JsonProperty("alias")
    @Schema(description = "Certificate alias (acts as the WSO2 id)",
            example = "petstore-prod-cert")
    private String alias;

    @JsonProperty("endpoint")
    @Schema(description = "Backend endpoint URL the certificate is bound to",
            example = "https://api.petstore.com")
    private String endpoint;

    @JsonProperty("subject")
    @Schema(description = "Certificate subject DN")
    private String subject;

    @JsonProperty("issuer")
    @Schema(description = "Certificate issuer DN")
    private String issuer;

    @JsonProperty("expiryDate")
    @Schema(description = "Expiry date (ISO-8601 or WSO2's locale-specific string)")
    private String expiryDate;

    @JsonProperty("validFrom")
    @Schema(description = "Validity start date")
    private String validFrom;

    @JsonProperty("serialNumber")
    @Schema(description = "Certificate serial number")
    private String serialNumber;

    @JsonProperty("version")
    @Schema(description = "X.509 version", example = "3")
    private String version;
}
