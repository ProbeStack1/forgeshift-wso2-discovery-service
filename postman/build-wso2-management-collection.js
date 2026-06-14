const fs = require('fs');
const path = require('path');

const OUT_DIR = __dirname;
const collectionPath = path.join(OUT_DIR, 'wso2-management-apis.postman_collection.json');
const catalogPath = path.join(OUT_DIR, 'wso2-management-apis.catalog.json');

const scopes = {
  inventory: 'apim:api_view apim:api_import_export apim:subscribe apim:app_manage apim:sub_manage apim:admin openid internal_user_mgt_list internal_user_mgt_view internal_user_mgt_create internal_user_mgt_update internal_user_mgt_delete',
  publisher: 'apim:api_view apim:api_create apim:api_publish apim:api_manage apim:api_import_export',
  devportal: 'apim:subscribe apim:app_manage apim:sub_manage',
  admin: 'apim:admin',
  scim: 'openid internal_user_mgt_list internal_user_mgt_view internal_user_mgt_create internal_user_mgt_update internal_user_mgt_delete',
};

const catalog = {
  name: 'WSO2 API Manager Management APIs for Forgeshift Migrator',
  version: '1.0.0',
  notes: [
    'This catalog is intentionally migrator-oriented: it covers token acquisition and the WSO2 APIM management surfaces used to discover/export APIs, products, apps, subscriptions, policies, certificates, key managers, scopes, and users.',
    'WSO2 product OpenAPI specs contain many more write operations; add them as needed, but keep migration discovery read-first.',
  ],
  variables: {
    wso2BaseUrl: 'https://localhost:9443',
    tokenPath: '/oauth2/token',
    publisherApiBase: '/api/am/publisher/v4',
    devportalApiBase: '/api/am/devportal/v3',
    adminApiBase: '/api/am/admin/v4',
    scimApiBase: '/scim2',
  },
  scopes,
  endpoints: [
    { group: 'Auth', name: 'Register DCR application', method: 'POST', path: '/client-registration/v0.17/register', auth: 'basic-admin', migratorUse: 'Create a clientId/clientSecret for OAuth token calls when a profile does not already have one.' },
    { group: 'Auth', name: 'Password grant token', method: 'POST', path: '/oauth2/token', auth: 'basic-client', contentType: 'application/x-www-form-urlencoded', migratorUse: 'Acquire bearer token for Publisher, DevPortal, Admin, and SCIM calls.' },
    { group: 'Auth', name: 'Client credentials token', method: 'POST', path: '/oauth2/token', auth: 'basic-client', contentType: 'application/x-www-form-urlencoded', migratorUse: 'Acquire gateway/runtime token for validation probes when required.' },

    { group: 'Publisher APIs', name: 'List APIs', method: 'GET', path: '/api/am/publisher/v4/apis?limit={limit}&offset={offset}', auth: 'bearer', scopes: ['apim:api_view'], migratorUse: 'Primary API inventory.' },
    { group: 'Publisher APIs', name: 'Get API', method: 'GET', path: '/api/am/publisher/v4/apis/{apiId}', auth: 'bearer', scopes: ['apim:api_view'], migratorUse: 'Full API metadata for translation.' },
    { group: 'Publisher APIs', name: 'Get API swagger', method: 'GET', path: '/api/am/publisher/v4/apis/{apiId}/swagger', auth: 'bearer', scopes: ['apim:api_view'], migratorUse: 'OpenAPI definition for route/resource translation.' },
    { group: 'Publisher APIs', name: 'Export API ZIP', method: 'GET', path: '/api/am/publisher/v4/apis/export?apiId={apiId}&format=JSON&preserveStatus=true', auth: 'bearer', scopes: ['apim:api_import_export'], migratorUse: 'Migration bundle with definitions, deployments, sequences, and certificates.' },
    { group: 'Publisher APIs', name: 'List API revisions', method: 'GET', path: '/api/am/publisher/v4/apis/{apiId}/revisions', auth: 'bearer', scopes: ['apim:api_view'], migratorUse: 'Capture deployed revision/deployment details.' },
    { group: 'Publisher APIs', name: 'List API mediation policies', method: 'GET', path: '/api/am/publisher/v4/apis/{apiId}/mediation-policies?limit={limit}&offset={offset}', auth: 'bearer', scopes: ['apim:api_view'], migratorUse: 'Discover per-API mediation sequences.' },
    { group: 'Publisher APIs', name: 'Get mediation policy', method: 'GET', path: '/api/am/publisher/v4/apis/{apiId}/mediation-policies/{policyId}', auth: 'bearer', scopes: ['apim:api_view'], migratorUse: 'Fetch sequence XML/config for Kong plugin translation.' },

    { group: 'API Products', name: 'List API products', method: 'GET', path: '/api/am/publisher/v4/api-products?limit={limit}&offset={offset}', auth: 'bearer', scopes: ['apim:api_view'], migratorUse: 'Product inventory.' },
    { group: 'API Products', name: 'Get API product', method: 'GET', path: '/api/am/publisher/v4/api-products/{apiProductId}', auth: 'bearer', scopes: ['apim:api_view'], migratorUse: 'Product member API mapping.' },

    { group: 'Certificates', name: 'List endpoint certificates', method: 'GET', path: '/api/am/publisher/v4/endpoint-certificates?limit={limit}&offset={offset}', auth: 'bearer', scopes: ['apim:api_view'], migratorUse: 'Certificate metadata inventory.' },
    { group: 'Certificates', name: 'Get endpoint certificate content', method: 'GET', path: '/api/am/publisher/v4/endpoint-certificates/{alias}/content', auth: 'bearer', scopes: ['apim:api_view'], migratorUse: 'Fetch PEM content for Kong ca_certificates.' },
    { group: 'Certificates', name: 'List client certificates', method: 'GET', path: '/api/am/publisher/v4/apis/{apiId}/client-certificates?limit={limit}&offset={offset}', auth: 'bearer', scopes: ['apim:api_view'], migratorUse: 'mTLS/client certificate inventory where enabled.' },

    { group: 'DevPortal Applications', name: 'List applications', method: 'GET', path: '/api/am/devportal/v3/applications?limit={limit}&offset={offset}', auth: 'bearer', scopes: ['apim:app_manage'], migratorUse: 'Application inventory for Kong consumers.' },
    { group: 'DevPortal Applications', name: 'Get application', method: 'GET', path: '/api/am/devportal/v3/applications/{applicationId}', auth: 'bearer', scopes: ['apim:app_manage'], migratorUse: 'Full application metadata.' },
    { group: 'DevPortal Applications', name: 'Get application keys', method: 'GET', path: '/api/am/devportal/v3/applications/{applicationId}/keys/{keyType}', auth: 'bearer', scopes: ['apim:app_manage'], migratorUse: 'Discover existing production/sandbox credentials.' },
    { group: 'DevPortal Applications', name: 'Generate application keys', method: 'POST', path: '/api/am/devportal/v3/applications/{applicationId}/generate-keys', auth: 'bearer', scopes: ['apim:app_manage'], migratorUse: 'Optional lab/test credential generation for validation.' },

    { group: 'DevPortal Subscriptions', name: 'List subscriptions by application', method: 'GET', path: '/api/am/devportal/v3/subscriptions?applicationId={applicationId}&limit={limit}&offset={offset}', auth: 'bearer', scopes: ['apim:subscribe'], migratorUse: 'Reliable all-subscriptions discovery by iterating applications.' },
    { group: 'DevPortal Subscriptions', name: 'List subscriptions by API', method: 'GET', path: '/api/am/devportal/v3/subscriptions?apiId={apiId}&limit={limit}&offset={offset}', auth: 'bearer', scopes: ['apim:subscribe'], migratorUse: 'API dependency graph.' },
    { group: 'DevPortal Subscriptions', name: 'Create subscription', method: 'POST', path: '/api/am/devportal/v3/subscriptions', auth: 'bearer', scopes: ['apim:subscribe'], migratorUse: 'Optional validation setup.' },

    { group: 'Admin Policies', name: 'List subscription throttling policies', method: 'GET', path: '/api/am/admin/v4/throttling/policies/subscription?limit={limit}&offset={offset}', auth: 'bearer', scopes: ['apim:admin'], migratorUse: 'Subscription tier to Kong rate-limiting.' },
    { group: 'Admin Policies', name: 'List application throttling policies', method: 'GET', path: '/api/am/admin/v4/throttling/policies/application?limit={limit}&offset={offset}', auth: 'bearer', scopes: ['apim:admin'], migratorUse: 'Application-level policy inventory.' },
    { group: 'Admin Policies', name: 'List advanced throttling policies', method: 'GET', path: '/api/am/admin/v4/throttling/policies/advanced?limit={limit}&offset={offset}', auth: 'bearer', scopes: ['apim:admin'], migratorUse: 'Resource/API policy inventory.' },
    { group: 'Admin Policies', name: 'List custom throttling policies', method: 'GET', path: '/api/am/admin/v4/throttling/policies/custom?limit={limit}&offset={offset}', auth: 'bearer', scopes: ['apim:admin'], migratorUse: 'Custom policy inventory.' },

    { group: 'Admin System', name: 'List key managers', method: 'GET', path: '/api/am/admin/v4/key-managers?limit={limit}&offset={offset}', auth: 'bearer', scopes: ['apim:admin'], migratorUse: 'Key manager inventory and resident KM mapping.' },
    { group: 'Admin System', name: 'Get key manager', method: 'GET', path: '/api/am/admin/v4/key-managers/{keyManagerId}', auth: 'bearer', scopes: ['apim:admin'], migratorUse: 'Key manager detail.' },
    { group: 'Admin System', name: 'List scopes', method: 'GET', path: '/api/am/admin/v4/scopes?limit={limit}&offset={offset}', auth: 'bearer', scopes: ['apim:admin'], migratorUse: 'OAuth scope inventory.' },
    { group: 'Admin System', name: 'List gateway environments', method: 'GET', path: '/api/am/admin/v4/environments?limit={limit}&offset={offset}', auth: 'bearer', scopes: ['apim:admin'], migratorUse: 'Deployment environment mapping.' },

    { group: 'SCIM Users', name: 'List users', method: 'GET', path: '/scim2/Users?startIndex={startIndex}&count={count}', auth: 'bearer-or-basic-admin', scopes: ['internal_user_mgt_list'], migratorUse: 'User inventory.' },
    { group: 'SCIM Users', name: 'Get user', method: 'GET', path: '/scim2/Users/{userId}', auth: 'bearer-or-basic-admin', scopes: ['internal_user_mgt_view'], migratorUse: 'User detail.' },
    { group: 'SCIM Users', name: 'Create user', method: 'POST', path: '/scim2/Users', auth: 'bearer-or-basic-admin', scopes: ['internal_user_mgt_create'], migratorUse: 'Optional provisioning.' },
    { group: 'SCIM Users', name: 'Patch user', method: 'PATCH', path: '/scim2/Users/{userId}', auth: 'bearer-or-basic-admin', scopes: ['internal_user_mgt_update'], migratorUse: 'Optional provisioning/update.' },
    { group: 'SCIM Users', name: 'Delete user', method: 'DELETE', path: '/scim2/Users/{userId}', auth: 'bearer-or-basic-admin', scopes: ['internal_user_mgt_delete'], migratorUse: 'Optional cleanup.' },
    { group: 'SCIM Groups', name: 'List groups', method: 'GET', path: '/scim2/Groups?startIndex={startIndex}&count={count}', auth: 'bearer-or-basic-admin', scopes: ['internal_group_mgt_list'], migratorUse: 'Groups/roles inventory.' },
    { group: 'SCIM Groups', name: 'Get group', method: 'GET', path: '/scim2/Groups/{groupId}', auth: 'bearer-or-basic-admin', scopes: ['internal_group_mgt_view'], migratorUse: 'Role/group detail.' },
    { group: 'SCIM Roles', name: 'List roles', method: 'GET', path: '/scim2/Roles?startIndex={startIndex}&count={count}', auth: 'bearer-or-basic-admin', scopes: ['internal_role_mgt_view'], migratorUse: 'Roles inventory where supported by WSO2 IS/APIM build.' },
  ],
};

function pmUrl(rawPath) {
  const [pathPart, queryPart] = rawPath.split('?');
  const pathSegments = pathPart.split('/').filter(Boolean);
  const query = queryPart
    ? queryPart.split('&').map((entry) => {
        const [key, value = ''] = entry.split('=');
        return { key, value: value.replace('{limit}', '{{limit}}').replace('{offset}', '{{offset}}').replace('{startIndex}', '{{startIndex}}').replace('{count}', '{{count}}').replace('{apiId}', '{{apiId}}').replace('{applicationId}', '{{applicationId}}') };
      })
    : undefined;
  return {
    raw: '{{wso2BaseUrl}}' + rawPath
      .replace('{apiId}', '{{apiId}}')
      .replace('{apiProductId}', '{{apiProductId}}')
      .replace('{alias}', '{{certificateAlias}}')
      .replace('{applicationId}', '{{applicationId}}')
      .replace('{keyType}', '{{keyType}}')
      .replace('{policyId}', '{{policyId}}')
      .replace('{keyManagerId}', '{{keyManagerId}}')
      .replace('{userId}', '{{userId}}')
      .replace('{groupId}', '{{groupId}}')
      .replace('{limit}', '{{limit}}')
      .replace('{offset}', '{{offset}}')
      .replace('{startIndex}', '{{startIndex}}')
      .replace('{count}', '{{count}}'),
    host: ['{{wso2BaseUrl}}'],
    path: pathSegments.map((segment) => segment
      .replace('{apiId}', '{{apiId}}')
      .replace('{apiProductId}', '{{apiProductId}}')
      .replace('{alias}', '{{certificateAlias}}')
      .replace('{applicationId}', '{{applicationId}}')
      .replace('{keyType}', '{{keyType}}')
      .replace('{policyId}', '{{policyId}}')
      .replace('{keyManagerId}', '{{keyManagerId}}')
      .replace('{userId}', '{{userId}}')
      .replace('{groupId}', '{{groupId}}')),
    ...(query ? { query } : {}),
  };
}

const bearerHeaders = [
  { key: 'Authorization', value: 'Bearer {{wso2AccessToken}}', type: 'text' },
  { key: 'Accept', value: 'application/json', type: 'text' },
];

function requestFor(endpoint) {
  const header = endpoint.auth === 'bearer' || endpoint.auth === 'bearer-or-basic-admin'
    ? [...bearerHeaders]
    : [{ key: 'Accept', value: 'application/json', type: 'text' }];

  let body;
  if (endpoint.name === 'Register DCR application') {
    header.push({ key: 'Authorization', value: 'Basic {{adminBasicAuth}}', type: 'text' });
    header.push({ key: 'Content-Type', value: 'application/json', type: 'text' });
    body = {
      mode: 'raw',
      raw: JSON.stringify({
        callbackUrl: 'https://localhost',
        clientName: 'forgeshift-migrator-{{$timestamp}}',
        owner: '{{wso2Username}}',
        grantType: 'client_credentials password refresh_token',
        saasApp: true,
      }, null, 2),
      options: { raw: { language: 'json' } },
    };
  } else if (endpoint.name === 'Password grant token') {
    header.push({ key: 'Authorization', value: 'Basic {{clientBasicAuth}}', type: 'text' });
    header.push({ key: 'Content-Type', value: 'application/x-www-form-urlencoded', type: 'text' });
    body = {
      mode: 'urlencoded',
      urlencoded: [
        { key: 'grant_type', value: 'password', type: 'text' },
        { key: 'username', value: '{{wso2Username}}', type: 'text' },
        { key: 'password', value: '{{wso2Password}}', type: 'text' },
        { key: 'scope', value: '{{inventoryScope}}', type: 'text' },
      ],
    };
  } else if (endpoint.name === 'Client credentials token') {
    header.push({ key: 'Authorization', value: 'Basic {{gatewayClientBasicAuth}}', type: 'text' });
    header.push({ key: 'Content-Type', value: 'application/x-www-form-urlencoded', type: 'text' });
    body = {
      mode: 'urlencoded',
      urlencoded: [
        { key: 'grant_type', value: 'client_credentials', type: 'text' },
        { key: 'scope', value: '{{gatewayScope}}', type: 'text' },
      ],
    };
  } else if (endpoint.method === 'POST' || endpoint.method === 'PATCH') {
    header.push({ key: 'Content-Type', value: 'application/json', type: 'text' });
    body = { mode: 'raw', raw: '{}', options: { raw: { language: 'json' } } };
  }

  const item = {
    name: endpoint.name,
    request: {
      method: endpoint.method,
      header,
      url: pmUrl(endpoint.path),
      description: [
        endpoint.migratorUse,
        '',
        `Auth: ${endpoint.auth}`,
        endpoint.scopes ? `Scopes: ${endpoint.scopes.join(', ')}` : '',
      ].filter(Boolean).join('\n'),
      ...(body ? { body } : {}),
    },
    response: [],
  };

  if (endpoint.name === 'Register DCR application') {
    item.event = [{
      listen: 'test',
      script: {
        type: 'text/javascript',
        exec: [
          'const json = pm.response.json();',
          'if (json.clientId) pm.collectionVariables.set("wso2ClientId", json.clientId);',
          'if (json.clientSecret) pm.collectionVariables.set("wso2ClientSecret", json.clientSecret);',
          'if (json.clientId && json.clientSecret) {',
          '  pm.collectionVariables.set("clientBasicAuth", btoa(json.clientId + ":" + json.clientSecret));',
          '}',
        ],
      },
    }];
  }

  if (endpoint.name === 'Password grant token') {
    item.event = [{
      listen: 'test',
      script: {
        type: 'text/javascript',
        exec: [
          'const json = pm.response.json();',
          'if (json.access_token) pm.collectionVariables.set("wso2AccessToken", json.access_token);',
          'if (json.scope) pm.collectionVariables.set("lastGrantedScope", json.scope);',
        ],
      },
    }];
  }

  if (endpoint.name === 'Client credentials token') {
    item.event = [{
      listen: 'test',
      script: {
        type: 'text/javascript',
        exec: [
          'const json = pm.response.json();',
          'if (json.access_token) pm.collectionVariables.set("wso2GatewayToken", json.access_token);',
        ],
      },
    }];
  }

  return item;
}

function folder(name) {
  return {
    name,
    item: catalog.endpoints
      .filter((endpoint) => endpoint.group === name)
      .map(requestFor),
  };
}

const groups = [...new Set(catalog.endpoints.map((endpoint) => endpoint.group))];

const collection = {
  info: {
    _postman_id: '41f1fb0f-36dd-4774-bb38-wso2mgmtapis01',
    name: 'WSO2 API Manager Management APIs - Forgeshift Migrator',
    schema: 'https://schema.getpostman.com/json/collection/v2.1.0/collection.json',
    description: [
      '# WSO2 API Manager Management APIs - Forgeshift Migrator',
      '',
      'Collection for direct WSO2 source management calls used by discovery/migration.',
      '',
      'Quick start:',
      '1. Set `wso2BaseUrl`, `wso2Username`, `wso2Password`, and either `clientBasicAuth` or `wso2ClientId`/`wso2ClientSecret`.',
      '2. If you do not have a client, run **Auth / Register DCR application**; its test script stores `wso2ClientId`, `wso2ClientSecret`, and `clientBasicAuth`.',
      '3. Run **Auth / Password grant token**; its test script stores `wso2AccessToken`.',
      '4. Run Publisher, DevPortal, Admin, or SCIM requests.',
      '',
      'Some SCIM endpoints in older WSO2 builds are configured for Basic admin auth instead of bearer tokens. If bearer returns 401, use `Authorization: Basic {{adminBasicAuth}}` for those requests or grant the SCIM/internal user-management scopes to the DCR app.',
    ].join('\n'),
  },
  item: groups.map(folder),
  variable: [
    { key: 'wso2BaseUrl', value: 'https://localhost:9443' },
    { key: 'wso2Username', value: 'admin' },
    { key: 'wso2Password', value: 'admin' },
    { key: 'adminBasicAuth', value: 'YWRtaW46YWRtaW4=', description: 'base64(username:password). Replace for non-dev use.' },
    { key: 'wso2ClientId', value: '' },
    { key: 'wso2ClientSecret', value: '' },
    { key: 'clientBasicAuth', value: '', description: 'base64(wso2ClientId:wso2ClientSecret). Filled by DCR test script.' },
    { key: 'inventoryScope', value: scopes.inventory },
    { key: 'gatewayClientBasicAuth', value: '', description: 'base64(runtimeConsumerKey:runtimeConsumerSecret), used only for gateway validation token.' },
    { key: 'gatewayScope', value: '' },
    { key: 'wso2AccessToken', value: '' },
    { key: 'wso2GatewayToken', value: '' },
    { key: 'lastGrantedScope', value: '' },
    { key: 'limit', value: '50' },
    { key: 'offset', value: '0' },
    { key: 'startIndex', value: '1' },
    { key: 'count', value: '50' },
    { key: 'apiId', value: '' },
    { key: 'apiProductId', value: '' },
    { key: 'applicationId', value: '' },
    { key: 'keyType', value: 'PRODUCTION' },
    { key: 'policyId', value: '' },
    { key: 'certificateAlias', value: '' },
    { key: 'keyManagerId', value: '' },
    { key: 'userId', value: '' },
    { key: 'groupId', value: '' },
  ],
};

fs.writeFileSync(catalogPath, JSON.stringify(catalog, null, 2));
fs.writeFileSync(collectionPath, JSON.stringify(collection, null, 2));

console.log(`Wrote ${catalogPath}`);
console.log(`Wrote ${collectionPath}`);
console.log(`Endpoints: ${catalog.endpoints.length}`);
