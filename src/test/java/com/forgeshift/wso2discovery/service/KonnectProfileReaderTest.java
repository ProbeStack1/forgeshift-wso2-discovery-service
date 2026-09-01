package com.forgeshift.wso2discovery.service;

import com.forgeshift.wso2discovery.client.KonnectCredentials;
import com.forgeshift.wso2discovery.config.Wso2Properties;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Control plane selection, including the "default" placeholder the w2k UI
 * sends when no control plane has been chosen.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class KonnectProfileReaderTest {

    @Mock
    private MongoTemplate mongoTemplate;

    private KonnectProfileReader reader;

    @BeforeEach
    void setUp() {
        reader = new KonnectProfileReader(mongoTemplate, new Wso2Properties());
    }

    @Test
    void treatsDefaultPlaceholderAsUnspecifiedForASingleControlPlane() {
        // The w2k UI reads localStorage "kongControlPlane", which nothing sets,
        // so it always posts the literal string "default". Looking that up as a
        // real control plane name would fail every migration.
        stubProfile(controlPlane("cp-1234", "probestack-wso2-kong"));

        KonnectCredentials creds = reader.resolve("probestack", "primary", "default");

        assertEquals("cp-1234", creds.getControlPlaneId());
        assertEquals("profile", creds.getSource());
    }

    @Test
    void aControlPlaneGenuinelyNamedDefaultStillWins() {
        // Konnect auto-creates a control plane called "default", so the
        // placeholder must not shadow a real one of that name.
        stubProfile(
                controlPlane("cp-real-default", "default"),
                controlPlane("cp-other", "probestack-wso2-kong"));

        KonnectCredentials creds = reader.resolve("probestack", "primary", "default");

        assertEquals("cp-real-default", creds.getControlPlaneId());
    }

    @Test
    void stillSelectsAnExplicitlyRequestedControlPlane() {
        stubProfile(
                controlPlane("cp-1234", "probestack-wso2-kong"),
                controlPlane("cp-5678", "probestack-staging"));

        assertEquals("cp-5678",
                reader.resolve("probestack", "primary", "probestack-staging").getControlPlaneId());
        assertEquals("cp-1234",
                reader.resolve("probestack", "primary", "cp-1234").getControlPlaneId());
    }

    @Test
    void stillRejectsAControlPlaneThatDoesNotExist() {
        stubProfile(controlPlane("cp-1234", "probestack-wso2-kong"));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> reader.resolve("probestack", "primary", "does-not-exist"));
        assertEquals("Control Plane not found in Kong Konnect profile: does-not-exist", ex.getMessage());
    }

    @Test
    void asksForAChoiceWhenThePlaceholderIsAmbiguous() {
        stubProfile(
                controlPlane("cp-1234", "probestack-wso2-kong"),
                controlPlane("cp-5678", "probestack-staging"));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> reader.resolve("probestack", "primary", "default"));
        // An actionable message beats silently picking the wrong control plane.
        assertEquals("Multiple Kong control planes found. Pass kongControlPlane to select the target control plane.",
                ex.getMessage());
    }

    @Test
    void fallbackIgnoresThePlaceholderWhenNoProfileMatches() {
        when(mongoTemplate.find(any(Query.class), eq(Document.class), any())).thenReturn(List.of());
        Wso2Properties props = new Wso2Properties();
        props.getKong().setControlPlaneIdFallback("cp-from-config");
        KonnectProfileReader fallbackReader = new KonnectProfileReader(mongoTemplate, props);

        KonnectCredentials creds = fallbackReader.resolve("probestack", "primary", "default");

        assertEquals("cp-from-config", creds.getControlPlaneId());
        assertEquals("static", creds.getSource());
    }

    private void stubProfile(Document... controlPlanes) {
        Document profile = new Document()
                .append("companyName", "probestack")
                .append("profileName", "primary")
                .append("status", "ACTIVE")
                .append("adminUrl", "https://us.api.konghq.com")
                .append("konnectPat", "kpat_test")
                .append("controlPlanes", List.of(controlPlanes));
        when(mongoTemplate.find(any(Query.class), eq(Document.class), any())).thenReturn(List.of(profile));
    }

    private Document controlPlane(String id, String name) {
        return new Document().append("controlPlaneId", id).append("controlPlaneName", name);
    }
}
