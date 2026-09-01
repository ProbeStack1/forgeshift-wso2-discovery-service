package com.forgeshift.wso2discovery.client;

import com.forgeshift.wso2discovery.config.Wso2Properties;
import com.forgeshift.wso2discovery.dto.Wso2RolePermissionDetail;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * SOAP client for WSO2 RemoteUserStoreManagerService operations.
 */
@Slf4j
@Component
public class Wso2UserStoreSoapClient {

    private static final String SOAP_ENV = "http://schemas.xmlsoap.org/soap/envelope/";

    private final WebClient webClient;
    private final Wso2Properties properties;

    /**
     * Declared explicitly rather than left to {@code @RequiredArgsConstructor}.
     * Lombok does not copy a field-level {@code @Qualifier} onto the generated
     * constructor parameter unless {@code lombok.copyableAnnotations} says so,
     * and this project has no {@code lombok.config}. With more than one
     * WebClient bean an unqualified parameter fails the whole application
     * context at startup, which surfaces only as a deployment that never
     * becomes ready. {@link Wso2Client} and {@code KongAdminClient} declare
     * theirs the same way.
     */
    public Wso2UserStoreSoapClient(@Qualifier("wso2WebClient") WebClient webClient,
                                   Wso2Properties properties) {
        this.webClient = webClient;
        this.properties = properties;
    }

    /**
     * Calls SOAP listUsers and returns the user names from return elements.
     */
    public List<String> listUsers(Wso2Credentials credentials) {
        String body = envelope("""
                <ser:listUsers>
                  <ser:filter>%s</ser:filter>
                  <ser:maxItemLimit>%s</ser:maxItemLimit>
                </ser:listUsers>
                """.formatted(escape(properties.getSoap().getUserFilter()), properties.getSoap().getMaxUsers()));
        return returnTextValues(call(credentials, "urn:listUsers", body));
    }

    /**
     * Calls SOAP getRoleNames and returns all role/group names.
     */
    public List<String> getRoleNames(Wso2Credentials credentials) {
        return returnTextValues(call(credentials, "urn:getRoleNames", envelope("<ser:getRoleNames/>")));
    }

    /**
     * Calls SOAP getRoleListOfUser for one WSO2 username.
     */
    public List<String> getRoleListOfUser(Wso2Credentials credentials, String userName) {
        String body = envelope("""
                <ser:getRoleListOfUser>
                  <ser:userName>%s</ser:userName>
                </ser:getRoleListOfUser>
                """.formatted(escape(userName)));
        return returnTextValues(call(credentials, "urn:getRoleListOfUser", body));
    }

    /**
     * Calls SOAP getUserClaimValues and returns all claim URI to value mappings
     * for the configured profile name.
     */
    public Map<String, String> getUserClaimValues(Wso2Credentials credentials, String userName) {
        String body = envelope("""
                <ser:getUserClaimValues>
                  <ser:userName>%s</ser:userName>
                  <ser:profileName>%s</ser:profileName>
                </ser:getUserClaimValues>
                """.formatted(
                escape(userName),
                escape(properties.getSoap().getProfileName())));
        return parseClaims(call(credentials, "urn:getUserClaimValues", body));
    }

    /**
     * Calls SOAP UserAdmin#getRolePermissions and returns permission nodes for
     * one WSO2 role.
     */
    public List<Wso2RolePermissionDetail> getRolePermissions(Wso2Credentials credentials, String roleName) {
        String body = userAdminEnvelope("""
                <xsd:getRolePermissions>
                  <xsd:roleName>%s</xsd:roleName>
                </xsd:getRolePermissions>
                """.formatted(escape(roleName)));
        return parseRolePermissions(callUserAdmin(credentials, "urn:getRolePermissions", body));
    }

    /**
     * Executes one SOAP POST using Basic Auth credentials.
     */
    private String call(Wso2Credentials credentials, String soapAction, String body) {
        validateCredentials(credentials);
        return webClient.post()
                .uri(effectiveBaseUrl(credentials) + properties.getSoap().getUserStoreServicePath())
                .header(HttpHeaders.AUTHORIZATION, basicHeader(credentials))
                .header("SOAPAction", soapAction)
                .contentType(MediaType.TEXT_XML)
                .accept(MediaType.TEXT_XML, MediaType.APPLICATION_XML)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(properties.getRequestTimeoutSeconds()))
                .block();
    }

    /**
     * Executes one SOAP POST against the WSO2 UserAdmin service.
     */
    private String callUserAdmin(Wso2Credentials credentials, String soapAction, String body) {
        validateCredentials(credentials);
        return webClient.post()
                .uri(effectiveBaseUrl(credentials) + properties.getSoap().getUserAdminServicePath())
                .header(HttpHeaders.AUTHORIZATION, basicHeader(credentials))
                .header("SOAPAction", soapAction)
                .contentType(MediaType.TEXT_XML)
                .accept(MediaType.TEXT_XML, MediaType.APPLICATION_XML)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(properties.getRequestTimeoutSeconds()))
                .block();
    }

    /**
     * Wraps an operation payload in a SOAP envelope.
     */
    private String envelope(String operationXml) {
        return """
                <soapenv:Envelope xmlns:soapenv="%s" xmlns:ser="%s">
                  <soapenv:Body>
                    %s
                  </soapenv:Body>
                </soapenv:Envelope>
                """.formatted(SOAP_ENV, properties.getSoap().getNamespace(), operationXml);
    }

    /**
     * Wraps a UserAdmin operation payload in a SOAP envelope.
     */
    private String userAdminEnvelope(String operationXml) {
        return """
                <soapenv:Envelope xmlns:soapenv="%s" xmlns:xsd="%s">
                  <soapenv:Body>
                    %s
                  </soapenv:Body>
                </soapenv:Envelope>
                """.formatted(SOAP_ENV, properties.getSoap().getUserAdminNamespace(), operationXml);
    }

    /**
     * Parses simple SOAP responses where each result is returned in a return element.
     */
    private List<String> returnTextValues(String xml) {
        List<String> values = new ArrayList<>();
        NodeList returns = parse(xml).getElementsByTagNameNS("*", "return");
        for (int index = 0; index < returns.getLength(); index++) {
            String value = returns.item(index).getTextContent();
            if (StringUtils.hasText(value)) {
                values.add(value.trim());
            }
        }
        return values;
    }

    /**
     * Parses claim responses from WSO2's claimURI/value return structure.
     */
    private Map<String, String> parseClaims(String xml) {
        Map<String, String> claims = new LinkedHashMap<>();
        NodeList returns = parse(xml).getElementsByTagNameNS("*", "return");
        for (int index = 0; index < returns.getLength(); index++) {
            Node node = returns.item(index);
            String uri = firstChildText(node, "claimUri", "claimURI");
            String value = childText(node, "value");
            if (StringUtils.hasText(uri) && StringUtils.hasText(value)) {
                claims.put(uri, value);
            }
        }
        return claims;
    }

    /**
     * Parses UserAdmin role permission nodes into resourcePath/selected pairs.
     */
    private List<Wso2RolePermissionDetail> parseRolePermissions(String xml) {
        List<Wso2RolePermissionDetail> permissions = new ArrayList<>();
        NodeList resourcePaths = parse(xml).getElementsByTagNameNS("*", "resourcePath");
        for (int index = 0; index < resourcePaths.getLength(); index++) {
            if (permissions.size() >= maxRolePermissionsPerRole()) {
                break;
            }
            Node resourcePathNode = resourcePaths.item(index);
            String resourcePath = text(resourcePathNode);
            if (!StringUtils.hasText(resourcePath)) {
                continue;
            }
            Node parent = resourcePathNode.getParentNode();
            permissions.add(Wso2RolePermissionDetail.builder()
                    .resourcePath(resourcePath)
                    .selected(Boolean.parseBoolean(childText(parent, "selected")))
                    .build());
        }
        return permissions;
    }

    /**
     * Returns the configured response cap for permissions per role.
     */
    private int maxRolePermissionsPerRole() {
        return Math.max(properties.getSoap().getMaxRolePermissionsPerRole(), 0);
    }

    /**
     * Parses XML securely with external entity resolution disabled.
     */
    private Document parse(String xml) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            return factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to parse WSO2 SOAP response", ex);
        }
    }

    /**
     * Reads the text content of a direct or nested child by local name.
     */
    private String childText(Node node, String localName) {
        if (!(node instanceof Element element)) {
            return null;
        }
        NodeList children = element.getElementsByTagNameNS("*", localName);
        if (children.getLength() == 0) {
            return null;
        }
        String value = children.item(0).getTextContent();
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    /**
     * Reads the first non-blank child value for any of the supplied local names.
     */
    private String firstChildText(Node node, String... localNames) {
        if (localNames == null) {
            return null;
        }
        for (String localName : localNames) {
            String value = childText(node, localName);
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    /**
     * Reads normalized text content from one XML node.
     */
    private String text(Node node) {
        if (node == null) {
            return null;
        }
        String value = node.getTextContent();
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    /**
     * Creates a Basic Auth header without exposing credentials in logs.
     */
    private String basicHeader(Wso2Credentials credentials) {
        String raw = credentials.getUsername() + ":" + credentials.getPassword();
        return "Basic " + Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Returns profile base URL when available, otherwise the static base URL.
     */
    private String effectiveBaseUrl(Wso2Credentials credentials) {
        return StringUtils.hasText(credentials.getBaseUrl()) ? credentials.getBaseUrl() : properties.getBaseUrl();
    }

    /**
     * Fails fast when SOAP Basic Auth credentials are incomplete.
     */
    private void validateCredentials(Wso2Credentials credentials) {
        if (credentials == null || !StringUtils.hasText(credentials.getUsername())
                || !StringUtils.hasText(credentials.getPassword())) {
            throw new IllegalStateException("WSO2 SOAP Basic credentials are not configured");
        }
    }

    /**
     * Escapes XML text values included in SOAP envelopes.
     */
    private String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}
