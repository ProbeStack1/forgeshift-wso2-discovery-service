# WSO2 Management API Collection

This folder contains a migrator-oriented WSO2 API Manager management API collection.

Files:

- `forgeshift-wso2-discovery.postman_collection.json` - Service REST collection, including the WSO2 to Kong user migration workflow.
- `local.postman_environment.json` - Local environment targeting `http://localhost:8081/wso2/discovery/v1`.
- `wso2-management-apis.postman_collection.json` - Postman collection with DCR, token, Publisher, DevPortal, Admin, and SCIM requests.
- `wso2-management-apis.catalog.json` - Machine-readable endpoint catalog for discovery/migration code.
- `build-wso2-management-collection.js` - Regenerates both files from one endpoint list.

Generate:

```bash
node forgeshift-wso2-discovery-service/postman/build-wso2-management-collection.js
```

Postman flow:

1. Set `wso2BaseUrl`, `wso2Username`, and `wso2Password`.
2. Run `Auth / Register DCR application` if you do not already have a WSO2 OAuth client. The test script stores `wso2ClientId`, `wso2ClientSecret`, and `clientBasicAuth`.
3. Run `Auth / Password grant token`. The test script stores `wso2AccessToken`.
4. Use the Publisher, DevPortal, Admin, and SCIM folders.

Notes:

- The catalog is not intended to be every possible WSO2 write operation. It covers the WSO2 management surface needed by the current Forgeshift discovery and migration services.
- Some WSO2 builds secure `/scim2/Users` and `/scim2/Groups` with Basic admin auth rather than bearer tokens. If bearer auth returns `401`, use `Authorization: Basic {{adminBasicAuth}}` for SCIM or grant the required internal user-management scopes to the DCR app.

## WSO2 to Kong user migration REST flow

Import `forgeshift-wso2-discovery.postman_collection.json` and `local.postman_environment.json`, then run the `WSO2 to Kong User Migration` folder in order:

1. `POST /users/discovery` - UI-facing REST call. Backend uses WSO2 SOAP Basic Auth internally to fetch users, claims, user roles, and role permissions. Each returned role has `roleName` and `permissions[]` with `resourcePath` and `selected`.
2. `POST /role-mappings` - Create or update WSO2 role to Kong role/group mappings.
3. `POST /role-mappings/resolve` - Check whether discovered WSO2 roles have active Kong mappings.
4. `POST /users/migration` - Create/update Kong consumers and assign mapped Kong groups.
5. `GET /users/migration/history` - Read stored per-user-role migration history.

Before running migration, configure the service with `KONG_ADMIN_BASE_URL` and optional `KONG_ADMIN_TOKEN`. Discovery and role mapping do not require Kong connectivity.

### Developer-only SOAP troubleshooting

The UI should not call SOAP directly. Use this curl only when validating backend connectivity to WSO2 `UserAdmin#getRolePermissions`:

```bash
curl --location 'https://wso2.probestack.io:9443/services/UserAdmin' \
  --header 'Content-Type: text/xml;charset=UTF-8' \
  --header 'SOAPAction: urn:getRolePermissions' \
  --header 'Authorization: Basic REPLACE_WITH_ADMIN_BASIC' \
  --data '
<soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/"
                  xmlns:xsd="http://org.apache.axis2/xsd">
   <soapenv:Body>
      <xsd:getRolePermissions>
         <xsd:roleName>Internal/creator</xsd:roleName>
      </xsd:getRolePermissions>
   </soapenv:Body>
</soapenv:Envelope>'
```
