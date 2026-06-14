# WSO2 Management API Collection

This folder contains a migrator-oriented WSO2 API Manager management API collection.

Files:

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
