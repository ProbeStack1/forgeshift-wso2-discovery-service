package com.forgeshift.wso2discovery.client;

import com.forgeshift.wso2discovery.config.Wso2Properties;
import com.forgeshift.wso2discovery.dto.Wso2RolePermissionDetail;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for SOAP request generation and response parsing.
 */
class Wso2UserStoreSoapClientTest {

    private HttpServer server;
    private CapturedRequest capturedRequest;
    private String responseXml;
    private Wso2UserStoreSoapClient client;
    private Wso2Credentials credentials;
    private Wso2Properties properties;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/", this::handle);
        server.start();

        properties = new Wso2Properties();
        properties.getSoap().setUserStoreServicePath("/services/RemoteUserStoreManagerService");
        properties.getSoap().setUserAdminServicePath("/services/UserAdmin");
        properties.getSoap().setProfileName("default");

        String baseUrl = "http://localhost:" + server.getAddress().getPort();
        credentials = Wso2Credentials.builder()
                .baseUrl(baseUrl)
                .username("admin")
                .password("admin")
                .build();
        client = new Wso2UserStoreSoapClient(WebClient.builder().build(), properties);
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void getUserClaimValues_sendsUserNameAndProfileNameWithoutClaimFilters() {
        responseXml = """
                <soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/">
                  <soapenv:Body>
                    <ns:getUserClaimValuesResponse xmlns:ns="http://service.ws.um.carbon.wso2.org">
                      <ns:return>
                        <ns:claimUri>http://wso2.org/claims/emailaddress</ns:claimUri>
                        <ns:value>api.developer@example.com</ns:value>
                      </ns:return>
                      <ns:return>
                        <ns:claimUri>http://wso2.org/claims/givenname</ns:claimUri>
                        <ns:value>Sarah</ns:value>
                      </ns:return>
                      <ns:return>
                        <ns:claimUri>http://wso2.org/claims/lastname</ns:claimUri>
                        <ns:value>Williams</ns:value>
                      </ns:return>
                    </ns:getUserClaimValuesResponse>
                  </soapenv:Body>
                </soapenv:Envelope>
                """;

        Map<String, String> claims = client.getUserClaimValues(credentials, "api.developer");

        assertEquals("api.developer@example.com", claims.get("http://wso2.org/claims/emailaddress"));
        assertEquals("Sarah", claims.get("http://wso2.org/claims/givenname"));
        assertEquals("Williams", claims.get("http://wso2.org/claims/lastname"));
        assertEquals("/services/RemoteUserStoreManagerService", capturedRequest.path());
        assertEquals("urn:getUserClaimValues", capturedRequest.soapAction());
        assertTrue(capturedRequest.body().contains("<ser:userName>api.developer</ser:userName>"));
        assertTrue(capturedRequest.body().contains("<ser:profileName>default</ser:profileName>"));
        assertFalse(capturedRequest.body().contains("<ser:claims>"));
    }

    @Test
    void getRolePermissions_callsUserAdminAndParsesResourcePathAndSelected() {
        responseXml = """
                <soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/">
                  <soapenv:Body>
                    <ns:getRolePermissionsResponse xmlns:ns="http://org.apache.axis2/xsd">
                      <ns:return>
                        <ns:resourcePath>/permission/admin/login</ns:resourcePath>
                        <ns:selected>true</ns:selected>
                      </ns:return>
                      <ns:return>
                        <ns:resourcePath>/permission/admin/manage/api</ns:resourcePath>
                        <ns:selected>false</ns:selected>
                      </ns:return>
                    </ns:getRolePermissionsResponse>
                  </soapenv:Body>
                </soapenv:Envelope>
                """;

        List<Wso2RolePermissionDetail> permissions = client.getRolePermissions(credentials, "Internal/creator");

        assertEquals("/services/UserAdmin", capturedRequest.path());
        assertEquals("urn:getRolePermissions", capturedRequest.soapAction());
        assertTrue(capturedRequest.body().contains("<xsd:roleName>Internal/creator</xsd:roleName>"));
        assertEquals(2, permissions.size());
        assertEquals("/permission/admin/login", permissions.get(0).getResourcePath());
        assertTrue(permissions.get(0).getSelected());
        assertEquals("/permission/admin/manage/api", permissions.get(1).getResourcePath());
        assertFalse(permissions.get(1).getSelected());
    }

    @Test
    void getRolePermissions_limitsPermissionsUsingConfiguredMaximum() {
        properties.getSoap().setMaxRolePermissionsPerRole(1);
        responseXml = """
                <soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/">
                  <soapenv:Body>
                    <ns:getRolePermissionsResponse xmlns:ns="http://org.apache.axis2/xsd">
                      <ns:return>
                        <ns:resourcePath>/permission/admin/login</ns:resourcePath>
                        <ns:selected>true</ns:selected>
                      </ns:return>
                      <ns:return>
                        <ns:resourcePath>/permission/admin/manage/api</ns:resourcePath>
                        <ns:selected>true</ns:selected>
                      </ns:return>
                    </ns:getRolePermissionsResponse>
                  </soapenv:Body>
                </soapenv:Envelope>
                """;

        List<Wso2RolePermissionDetail> permissions = client.getRolePermissions(credentials, "Internal/creator");

        assertEquals(1, permissions.size());
        assertEquals("/permission/admin/login", permissions.get(0).getResourcePath());
    }

    /**
     * Captures one SOAP request and sends the test-provided XML response.
     */
    private void handle(HttpExchange exchange) throws IOException {
        capturedRequest = new CapturedRequest(
                exchange.getRequestURI().getPath(),
                exchange.getRequestHeaders().getFirst("SOAPAction"),
                new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
        byte[] response = responseXml.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "text/xml;charset=UTF-8");
        exchange.sendResponseHeaders(200, response.length);
        exchange.getResponseBody().write(response);
        exchange.close();
    }

    private record CapturedRequest(String path, String soapAction, String body) {
    }
}
