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
        // The w2k UI posted the literal string "default" from a localStorage
        // key nothing ever wrote. Looking that up as a real control plane name
        // would fail every migration.
        stubProfile(controlPlane("cp-1234", "probestack-wso2-kong"));

        List<KonnectCredentials> targets = reader.resolveTargets("probestack", "primary", "default");

        assertEquals(1, targets.size());
        assertEquals("cp-1234", targets.get(0).getControlPlaneId());
        assertEquals("profile", targets.get(0).getSource());
    }

    @Test
    void targetsEveryControlPlaneWhenNoneIsNamed() {
        // A migrated user is not environment-specific: the same person belongs
        // in every control plane, so this must not ask the caller to choose.
        stubProfile(
                controlPlane("cp-1234", "probestack-wso2-kong"),
                controlPlane("cp-5678", "probestack-staging"),
                controlPlane("cp-9012", "probestack-dev"));

        assertEquals(List.of("cp-1234", "cp-5678", "cp-9012"),
                reader.resolveTargets("probestack", "primary", "default").stream()
                        .map(KonnectCredentials::getControlPlaneId).toList());
        assertEquals(3, reader.resolveTargets("probestack", "primary", null).size());
        assertEquals(3, reader.resolveTargets("probestack", "primary", "all").size());
    }

    @Test
    void carriesTheControlPlaneNameThroughForReporting() {
        stubProfile(controlPlane("cp-1234", "probestack-wso2-kong"));

        assertEquals("probestack-wso2-kong",
                reader.resolveTargets("probestack", "primary", null).get(0).getControlPlaneName());
    }

    @Test
    void aControlPlaneGenuinelyNamedDefaultStillWins() {
        // Konnect auto-creates a control plane called "default", so the
        // placeholder must not shadow a real one of that name.
        stubProfile(
                controlPlane("cp-real-default", "default"),
                controlPlane("cp-other", "probestack-wso2-kong"));

        List<KonnectCredentials> targets = reader.resolveTargets("probestack", "primary", "default");

        assertEquals(1, targets.size());
        assertEquals("cp-real-default", targets.get(0).getControlPlaneId());
    }

    @Test
    void stillSelectsAnExplicitlyRequestedControlPlane() {
        stubProfile(
                controlPlane("cp-1234", "probestack-wso2-kong"),
                controlPlane("cp-5678", "probestack-staging"));

        // Naming one narrows the run to it.
        assertEquals(List.of("cp-5678"),
                reader.resolveTargets("probestack", "primary", "probestack-staging").stream()
                        .map(KonnectCredentials::getControlPlaneId).toList());
        assertEquals(List.of("cp-1234"),
                reader.resolveTargets("probestack", "primary", "cp-1234").stream()
                        .map(KonnectCredentials::getControlPlaneId).toList());
    }

    @Test
    void stillRejectsAControlPlaneThatDoesNotExist() {
        stubProfile(controlPlane("cp-1234", "probestack-wso2-kong"));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> reader.resolveTargets("probestack", "primary", "does-not-exist"));
        assertEquals("Control Plane not found in Kong Konnect profile: does-not-exist"
                + ". Available: probestack-wso2-kong (cp-1234)", ex.getMessage());
    }

    @Test
    void picksTheProfileMarkedDefaultWhenACompanyHasSeveral() {
        // Profiles are named after the company, so callers never ask for
        // "primary"; without a default two active profiles matched nothing and
        // the resolver fell through to static config.
        stubProfiles(
                profile("probestack-kong-konnect-staging", false, controlPlane("cp-staging", "staging")),
                profile("probestack-kong-konnect", true, controlPlane("cp-1234", "probestack-wso2-kong")));

        List<KonnectCredentials> targets = reader.resolveTargets("probestack", null, null);

        assertEquals(1, targets.size());
        assertEquals("cp-1234", targets.get(0).getControlPlaneId());
        assertEquals("profile", targets.get(0).getSource());
    }

    @Test
    void fallsBackToStaticWhenSeveralProfilesAndNoneIsDefault() {
        // Legacy data written before the default flag existed.
        stubProfiles(
                profile("probestack-kong-konnect-a", false, controlPlane("cp-a", "a")),
                profile("probestack-kong-konnect-b", false, controlPlane("cp-b", "b")));

        assertEquals("static", reader.resolveTargets("probestack", null, null).get(0).getSource());
    }

    @Test
    void aSingleProfileStillWorksWithoutTheDefaultFlag() {
        // Existing single-profile companies must keep working untouched.
        stubProfiles(profile("probestack-kong-konnect", false, controlPlane("cp-1234", "probestack-wso2-kong")));

        List<KonnectCredentials> targets = reader.resolveTargets("probestack", null, null);

        assertEquals("cp-1234", targets.get(0).getControlPlaneId());
        assertEquals("profile", targets.get(0).getSource());
    }

    @Test
    void anExplicitProfileNameStillWinsOverTheDefault() {
        Document staging = profile("probestack-kong-konnect-staging", false, controlPlane("cp-staging", "staging"));
        Document primary = profile("probestack-kong-konnect", true, controlPlane("cp-1234", "probestack-wso2-kong"));
        when(mongoTemplate.find(any(Query.class), eq(Document.class), any()))
                .thenReturn(List.of(staging))      // named lookup
                .thenReturn(List.of(staging, primary));

        assertEquals("cp-staging",
                reader.resolveTargets("probestack", "probestack-kong-konnect-staging", null).get(0).getControlPlaneId());
    }

    @Test
    void fallbackIgnoresThePlaceholderWhenNoProfileMatches() {
        when(mongoTemplate.find(any(Query.class), eq(Document.class), any())).thenReturn(List.of());
        Wso2Properties props = new Wso2Properties();
        props.getKong().setControlPlaneIdFallback("cp-from-config");
        KonnectProfileReader fallbackReader = new KonnectProfileReader(mongoTemplate, props);

        List<KonnectCredentials> targets = fallbackReader.resolveTargets("probestack", "primary", "default");

        assertEquals(1, targets.size());
        assertEquals("cp-from-config", targets.get(0).getControlPlaneId());
        assertEquals("static", targets.get(0).getSource());
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

    private void stubProfiles(Document... profiles) {
        when(mongoTemplate.find(any(Query.class), eq(Document.class), any()))
                .thenReturn(List.of())              // named lookup misses
                .thenReturn(List.of(profiles));     // active-profile sweep
    }

    private Document profile(String profileName, boolean isDefault, Document... controlPlanes) {
        return new Document()
                .append("companyName", "probestack")
                .append("profileName", profileName)
                .append("status", "ACTIVE")
                .append("adminUrl", "https://us.api.konghq.com")
                .append("konnectPat", "kpat_test")
                .append("defaultProfile", isDefault)
                .append("controlPlanes", List.of(controlPlanes));
    }

    private Document controlPlane(String id, String name) {
        return new Document().append("controlPlaneId", id).append("controlPlaneName", name);
    }
}
