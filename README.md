# forgeshift-wso2-discovery-service

Spring Boot microservice that discovers WSO2 API Manager artifacts and stores
them as MongoDB snapshots for downstream translation by the Forgeshift
WSO2-to-Kong-Konnect migrator.

Mirrors the Apigee-side `probestack-apigee-discovery-service` one-for-one:
**MongoDB-only** storage (no GCS), per-resource snapshot collections, monotonic
revision counter per tenant, per-resource + bulk + inventory + history +
compare + assetinfo + token-test + stats endpoints, and a `profiles`
collection for multi-tenancy.

---

## Tech stack

| Concern | Choice |
|---|---|
| Language | Java 17 |
| Framework | Spring Boot 3.2.5 |
| HTTP | Spring WebClient (Reactor Netty) |
| Database | MongoDB 6 |
| API docs | springdoc-openapi |
| Tests | JUnit 5, Testcontainers (MongoDB) |

Context path: `/discovery/v1`.

---

## REST API at a glance

| Group | Count | Surface |
|---|---|---|
| Per-resource discovery (synchronous, writes a revision) | 10 | `POST /wso2/<apis,applications,subscriptions,throttlingpolicies,keymanagers,apiproducts,scopes,certificates,mediationpolicies,users>` |
| Inventory (cheap list-only pass) | 1 | `POST /wso2/inventory` |
| Bulk discovery (async fan-out across resource types) | 4 | `POST /discoveries`, `GET /discoveries/{id}`, `GET /discoveries`, `DELETE /discoveries/{id}` |
| History (read-only over Mongo) | 3 | `GET /wso2/history`, `/wso2/history/revisions`, `/wso2/history/details` |
| Compare (diff two discoveries) | 1 | `GET /wso2/compare` |
| Asset info (business-metadata write) | 1 | `POST /wso2/assetinfo` |
| Stats (roll-ups over the discovery data) | 1 | `GET /wso2/stats/{dimension}` — `byResourceType` / `byTenant` / `byDiscoveryId` / `byTime` |
| Internal diagnostics | 1 | `POST /internal/wso2/token/test` |
| Profiles (multi-tenancy CRUD) | 4 | `POST /profiles`, `GET /profiles`, `GET /profiles/{c}/{t}`, `DELETE /profiles/{c}/{t}` |
| Organizations (read) | 2 | `GET /organizations`, `GET /organizations/{c}/{t}` |
| Audit (read) | 2 | `GET /audit`, `GET /audit/{id}` |
| Relations (read) | 3 | `GET /relations`, `/relations/by-app`, `/relations/by-api` |

Full endpoint reference is in the Postman collection
(`postman/forgeshift-wso2-discovery.postman_collection.json`) and the live
Swagger UI at <http://localhost:8081/discovery/v1/swagger-ui.html>.

### Per-resource discovery — common envelope

Request body (`DiscoverResourceRequest`):

```json
{
  "companyName": "probestack",
  "wso2Tenant": "carbon.super",
  "environment": "prod",
  "requestTransactionId": "optional-caller-supplied",
  "userEmail": "ops@example.com"
}
```

Response (`DiscoverResourceResponse`) — only the typed detail list matching
`type` is populated:

```json
{
  "type": "apis",
  "requestTransactionId": "32abcc62-...",
  "revision": 3,
  "companyName": "probestack",
  "wso2Tenant": "carbon.super",
  "totalCount": 17,
  "collectionName": "discovery_wso2_apis",
  "apiDetails": [
    { "id": "47d0...", "name": "PetStore", "version": "1.0.0", "context": "/petstore/1.0.0",
      "lifecycleStatus": "PUBLISHED", "provider": "admin" }
  ],
  "snapshotIds": ["probestack|carbon.super|apis|47d0...|3"],
  "elapsedMs": 4123
}
```

### Bulk discovery

```bash
curl -X POST http://localhost:8081/discovery/v1/discoveries \
  -H "Content-Type: application/json" \
  -d '{"companyName":"probestack","wso2Tenant":"carbon.super","userEmail":"ops@example.com"}'
```

When `resourceTypes` is omitted, the worker fans out to all 10 implemented
slugs sequentially, reusing one revision across the run.

### Try the full flow

```bash
# 1. Bring up Mongo
docker compose up -d mongo

# 2. Run the service (local profile reads .env via spring.config.import)
mvn spring-boot:run -Dspring-boot.run.profiles=local

# 3. Inventory pre-flight (no persistence)
curl -X POST http://localhost:8081/discovery/v1/wso2/inventory \
  -H "Content-Type: application/json" \
  -d '{"wso2Tenant":"carbon.super"}'

# 4. Full bulk discovery
curl -X POST http://localhost:8081/discovery/v1/discoveries \
  -H "Content-Type: application/json" \
  -d '{"wso2Tenant":"carbon.super"}'

# 5. History
curl "http://localhost:8081/discovery/v1/wso2/history?wso2Tenant=carbon.super&limit=10"

# 6. Stats
curl "http://localhost:8081/discovery/v1/wso2/stats/byResourceType?wso2Tenant=carbon.super"
```

---

## MongoDB collections

| Collection | Purpose | Written by |
|---|---|---|
| `discovery_wso2_apis` | API snapshots | `POST /wso2/apis`, bulk |
| `discovery_wso2_applications` | DevPortal application snapshots | `POST /wso2/applications`, bulk |
| `discovery_wso2_subscriptions` | App ↔ API subscription snapshots | `POST /wso2/subscriptions`, bulk |
| `discovery_wso2_throttlingpolicies` | Subscription + application + advanced throttling tiers | `POST /wso2/throttlingpolicies`, bulk |
| `discovery_wso2_keymanagers` | Key Manager snapshots | `POST /wso2/keymanagers`, bulk |
| `discovery_wso2_apiproducts` | API Product snapshots | `POST /wso2/apiproducts`, bulk |
| `discovery_wso2_scopes` | Shared OAuth2 scope snapshots | `POST /wso2/scopes`, bulk |
| `discovery_wso2_certificates` | Endpoint TLS certificate snapshots | `POST /wso2/certificates`, bulk |
| `discovery_wso2_mediationpolicies` | Per-API mediation policy snapshots | `POST /wso2/mediationpolicies`, bulk |
| `discovery_wso2_users` | SCIM 2.0 user snapshots | `POST /wso2/users`, bulk |
| `wso2_user_profiles` | Normalized SCIM user profiles for user migration | `POST /user-profiles` |
| `discovery_revisions` | Per (companyName, wso2Tenant) monotonic counter | every discovery |
| `discovery_jobs` | Bulk-discovery job documents (state machine + per-resource progress) | `POST /discoveries` |
| `wso2_organizations` | Auto-upserted list of tenants we have data for | every discovery + inventory |
| `app_api_relations` | Denormalized application ↔ API join | `POST /wso2/subscriptions` post-process |
| `wso2_migration_audit_info` | Async audit log of every REST request | AuditRequestFilter |
| `profiles` | Per-tenant WSO2 connection profiles for multi-tenancy | `POST /profiles` |
| `probestack_wso2_asset_info` | Business-metadata attached to APIs (businessUnit, projectName, ...) | `POST /wso2/assetinfo` |

### Composite id convention

Snapshot documents use `<companyName>|<wso2Tenant>|<resourceType>|<sourceId>|<revision>`
so re-running the same discovery is an upsert, not a duplicate insert.

`profiles`, `wso2_organizations`, `probestack_wso2_asset_info`, and
`app_api_relations` use their own deterministic composite ids derived from
the (companyName, wso2Tenant, ...) tuple.

---

## Multi-tenancy via `profiles`

The single-tenant default uses the static `forgeshift.wso2.*` config from
`.env` / environment variables. To onboard additional WSO2 tenants without
redeploying, register a profile:

```bash
curl -X POST http://localhost:8081/discovery/v1/profiles \
  -H "Content-Type: application/json" \
  -d '{
    "companyName":"acme",
    "wso2Tenant":"acme.com",
    "wso2BaseUrl":"https://wso2.acme.com:9443",
    "username":"admin",
    "password":"secret",
    "clientId":"...","clientSecret":"...",
    "trustSelfSigned":true
  }'
```

Subsequent discovery calls with `{"companyName":"acme","wso2Tenant":"acme.com"}`
use these credentials. Calls for any other tenant fall back to the static
config. Secrets are masked on every read (`password: "secr..."`).

---

## Audit log

Every request to `/wso2/**`, `/discoveries/**`, `/internal/wso2/**`,
`/profiles/**`, `/organizations/**`, `/audit/**`, `/relations/**` is captured
into `wso2_migration_audit_info` asynchronously. Static + actuator paths are
skipped. Optional headers attach extra context to the audit row:

| Header | Meaning |
|---|---|
| `X-Request-Transaction-Id` | Caller correlation id |
| `X-Company-Name` | Multi-tenancy partner id |
| `X-Wso2-Tenant` | WSO2 tenant being acted on |
| `X-User-Email` | Audit field for who triggered it |
| `X-Request-Source` | `UI` / `API` / `SCHEDULED` |

Disable via `forgeshift.discovery.audit-enabled=false`.

Add a TTL index in ops to expire old rows:
```javascript
db.wso2_migration_audit_info.createIndex({requestedAt:1}, {expireAfterSeconds: 7776000})
```

---

## Module layout

```
src/main/java/com/forgeshift/wso2discovery/
├── Wso2DiscoveryServiceApplication.java
├── client/
│   ├── Wso2Client.java                       # token + Publisher/Admin/DevPortal/SCIM calls
│   └── Wso2Credentials.java                  # resolved creds (profile or static)
├── config/
│   ├── AsyncConfig.java                      # discoveryExecutor thread pool
│   ├── DiscoveryProperties.java              # forgeshift.discovery.*
│   ├── GlobalExceptionHandler.java
│   ├── MongoAuditingConfig.java
│   ├── OpenApiConfig.java
│   ├── WebClientConfig.java
│   └── Wso2Properties.java                   # forgeshift.wso2.*
├── controller/
│   ├── DiscoveryController.java              # bulk POST /discoveries
│   ├── GlobalExceptionHandler.java           # 4xx/5xx envelope; 404 for unknown routes
│   ├── Wso2ApisController.java
│   ├── Wso2ApplicationsController.java
│   ├── Wso2SubscriptionsController.java
│   ├── Wso2ThrottlingPoliciesController.java
│   ├── Wso2KeyManagersController.java
│   ├── Wso2ApiProductsController.java
│   ├── Wso2ScopesController.java
│   ├── Wso2CertificatesController.java
│   ├── Wso2MediationPoliciesController.java
│   ├── Wso2UsersController.java
│   ├── Wso2InventoryController.java
│   ├── Wso2HistoryController.java            # /history, /history/revisions, /history/details
│   ├── Wso2ComparisonController.java         # /compare
│   ├── Wso2AssetInfoController.java          # /assetinfo
│   ├── Wso2StatsController.java              # /stats/{dimension}
│   ├── Wso2TokenTestController.java          # /internal/wso2/token/test
│   ├── Wso2ProfilesController.java           # /profiles CRUD
│   ├── Wso2OrganizationsController.java      # /organizations
│   ├── Wso2AuditController.java              # /audit
│   └── Wso2RelationsController.java          # /relations
├── domain/
│   ├── DiscoveryJob.java
│   ├── DiscoverySnapshot.java
│   ├── DiscoveryState.java
│   ├── ResourceType.java
│   ├── RevisionCounter.java
│   ├── Wso2TenantProfile.java                # profiles collection
│   ├── Wso2OrganizationEntity.java           # wso2_organizations
│   ├── AppApiRelation.java                   # app_api_relations
│   └── MigrationAuditEntry.java              # wso2_migration_audit_info
├── dto/
│   ├── DiscoverResourceRequest.java
│   ├── DiscoverResourceResponse.java
│   ├── StartDiscoveryRequest.java
│   ├── DiscoveryJobResponse.java
│   ├── InventoryResponse.java
│   ├── HistoryResponse.java
│   ├── HistorySnapshot.java
│   ├── HistoryRevisionsResponse.java
│   ├── HistoryDetailsResponse.java
│   ├── ComparisonResponse.java
│   ├── AssetInfoRequest.java
│   ├── AssetInfoResponse.java
│   ├── StatsResponse.java
│   ├── TokenTestRequest.java
│   ├── TokenTestResponse.java
│   ├── Wso2TenantProfileDto.java
│   └── details/                              # one per resource type
│       ├── ApiDetail.java
│       ├── ApplicationDetail.java
│       ├── SubscriptionDetail.java
│       ├── ThrottlingPolicyDetail.java
│       ├── KeyManagerDetail.java
│       ├── ApiProductDetail.java
│       ├── ScopeDetail.java
│       ├── CertificateDetail.java
│       ├── MediationPolicyDetail.java
│       ├── UserDetail.java
│       └── ResourceSummary.java              # used by /wso2/inventory
├── filter/
│   └── AuditRequestFilter.java               # OncePerRequestFilter → MigrationAuditService
├── repository/
│   ├── BaseDiscoveryRepository.java          # interface for any discovery_wso2_* collection
│   ├── MongoBaseDiscoveryRepository.java
│   ├── DiscoveryJobRepository.java
│   ├── RevisionCounterRepository.java
│   ├── Wso2TenantProfileRepository.java
│   ├── Wso2OrganizationRepository.java
│   ├── AppApiRelationRepository.java
│   └── MigrationAuditRepository.java
└── service/
    ├── BaseDiscoveryService.java             # abstract: validate → token (profile-aware) → fetch → snapshot → upsert → org-upsert
    ├── DiscoveryService.java                 # bulk fan-out
    ├── RevisionSequenceService.java
    ├── Wso2HistoryService.java
    ├── Wso2ComparisonService.java
    ├── Wso2AssetInfoService.java
    ├── Wso2StatsService.java
    ├── Wso2TenantProfileService.java         # profile lookup + fallback
    ├── Wso2OrganizationService.java          # auto-upsert wso2_organizations
    ├── AppApiRelationService.java            # populates app_api_relations
    ├── MigrationAuditService.java            # @Async audit writer
    └── wso2/
        ├── Wso2InventoryService.java
        ├── Wso2ApisDiscoveryService.java
        ├── Wso2ApplicationsDiscoveryService.java
        ├── Wso2SubscriptionsDiscoveryService.java
        ├── Wso2ThrottlingPoliciesDiscoveryService.java
        ├── Wso2KeyManagersDiscoveryService.java
        ├── Wso2ApiProductsDiscoveryService.java
        ├── Wso2ScopesDiscoveryService.java
        ├── Wso2CertificatesDiscoveryService.java
        ├── Wso2MediationPoliciesDiscoveryService.java
        └── Wso2UsersDiscoveryService.java
```

---

## Configuration

`.env` (loaded via `spring.config.import: optional:file:.env[.properties]`):

| Env var | Property | Default |
|---|---|---|
| `MONGODB_URI` | `spring.data.mongodb.uri` | `mongodb://localhost:27017/forgeshift_discovery` |
| `WSO2_BASE_URL` | `forgeshift.wso2.base-url` | `https://localhost:9443` |
| `WSO2_USERNAME` | `forgeshift.wso2.username` | `admin` |
| `WSO2_PASSWORD` | `forgeshift.wso2.password` | `admin` |
| `WSO2_CLIENT_ID` | `forgeshift.wso2.client-id` | (required for real runs) |
| `WSO2_CLIENT_SECRET` | `forgeshift.wso2.client-secret` | (required for real runs) |
| `WSO2_TRUST_SELF_SIGNED` | `forgeshift.wso2.trust-self-signed` | `true` |

Internal defaults (override in `application.yml` if needed):

| Property | Default |
|---|---|
| `forgeshift.wso2.publisher-scope` | `apim:api_view` |
| `forgeshift.wso2.admin-scope` | `apim:admin` |
| `forgeshift.wso2.devportal-scope` | `apim:subscribe` |
| `forgeshift.wso2.publisher-api-base` | `/api/am/publisher/v4` |
| `forgeshift.wso2.admin-api-base` | `/api/am/admin/v4` |
| `forgeshift.wso2.devportal-api-base` | `/api/am/devportal/v3` |
| `forgeshift.wso2.scim-api-base` | `/scim2` |
| `forgeshift.wso2.scim-users-path` | `/Users` |
| `forgeshift.wso2.token-test.enabled` | `true` (set `false` in prod) |
| `forgeshift.discovery.collection-prefix` | `discovery_wso2_` |
| `forgeshift.discovery.user-profiles-collection` | `wso2_user_profiles` |
| `forgeshift.discovery.default-company-name` | `probestack` |
| `forgeshift.discovery.audit-enabled` | `true` |
| `forgeshift.discovery.parallel-thread-pool-size` | `4` |

---

## Build & run

```bash
mvn clean package
mvn spring-boot:run -Dspring-boot.run.profiles=local
docker compose up --build         # full local stack including Mongo
```

Mongo verification:
```bash
mongosh "mongodb://localhost:27017/forgeshift_discovery"
> show collections
> db.discovery_wso2_apis.findOne({}, {payload: 0})
> db.wso2_organizations.find()
> db.app_api_relations.countDocuments()
> db.wso2_migration_audit_info.find().sort({requestedAt:-1}).limit(5)
```

---

## What's next

The discovery service is feature-complete against the Apigee reference. Next
milestones for the broader migrator:

1. Verify the 11 discovery + 4 stats + compare endpoints against the live
   WSO2 instance at the configured base URL.
2. Add TTL index on `wso2_migration_audit_info` (or a scheduled cleanup job)
   if audit retention matters in production.
3. Encrypt `profiles.password` and `profiles.clientSecret` at rest. Today
   they're plain-text in Mongo; mask-on-read protects API responses but not
   the DB.
4. Kick off `forgeshift-wso2-kong-migrator` — the translator + Konnect
   deployer that reads these snapshots and writes Kong Services / Routes /
   Plugins.
