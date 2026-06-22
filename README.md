# ADL2 CDR (EhrBase Fork + Archie 2)

Standalone openEHR Clinical Data Repository forked from [EhrBase v2.29.0](https://github.com/ehrbase/ehrbase) with native **ADL 2** template support powered by [Archie 3.18.0](https://github.com/openEHR/archie).

## Architecture

| Layer | Responsibility |
|-------|----------------|
| `archie/` (submodule) | ADL2 parse, flatten, validate, rules, flat JSON |
| `ehrbase/adl2-knowledge/` | Archie ADL2 adapter for EhrBase knowledge cache |
| `ehrbase/service/` | Clinical persistence, validation orchestration |
| `ehrbase/rest-openehr/` | openEHR REST API including `/definition/template/adl2` |
| `ehrbase/aql-engine/` | AQL query engine (unchanged baseline) |

ADL 1.4 templates remain supported behind the existing endpoints for migration. New ADL2 templates use Archie `OperationalTemplate` end-to-end without node-id remapping.

## ADL2 template support

### Supported data value types (`ELEMENT.value`)

ADL2 templates are converted to WebTemplates by `ArchieOptParser` in `ehrbase/adl2-knowledge/`. The following **data value** RM types can appear as `ELEMENT` values (including multi-choice `ELEMENT` nodes):

| RM type | JSON `_type` | Notes |
|---------|--------------|--------|
| `DV_TEXT` | `DV_TEXT` | ADL string value lists enforced on commit (ADL2 OPT) |
| `DV_CODED_TEXT` | `DV_CODED_TEXT` | Terminology / code constraints via WebTemplate + terminology service |
| `DV_QUANTITY` | `DV_QUANTITY` | Units + magnitude ranges from ADL tuples enforced on commit |
| `DV_COUNT` | `DV_COUNT` | Integer counts |
| `DV_PROPORTION` | `DV_PROPORTION` | Ratios / proportions |
| `DV_BOOLEAN` | `DV_BOOLEAN` | |
| `DV_DATE` | `DV_DATE` | |
| `DV_TIME` | `DV_TIME` | |
| `DV_DATE_TIME` | `DV_DATE_TIME` | |
| `DV_DURATION` | `DV_DURATION` | |
| `DV_ORDINAL` | `DV_ORDINAL` | |
| `DV_IDENTIFIER` | `DV_IDENTIFIER` | |
| `DV_URI` | `DV_URI` | |
| `DV_EHR_URI` | `DV_EHR_URI` | |
| `DV_MULTIMEDIA` | `DV_MULTIMEDIA` | |
| `DV_PARSABLE` | `DV_PARSABLE` | |
| `DV_STATE` | `DV_STATE` | |

### Supported structure and entry types

| Category | RM types |
|----------|----------|
| **Item structures** | `ITEM_TREE`, `CLUSTER`, `ELEMENT` |
| **Composition content** | `OBSERVATION`, `EVALUATION`, `INSTRUCTION`, `ACTION`, `ADMIN_ENTRY`, `SECTION` |
| **Observation data** | `HISTORY`, `POINT_EVENT`, `INTERVAL_EVENT` |
| **Composition context** | `EVENT_CONTEXT` with nested item structures |
| **Archetype slots** | `allow_archetype` slots resolved to nested component archetypes (e.g. medication instruction, observation wrapper) |

### Unsupported / rejected types

| RM type | When rejected |
|---------|----------------|
| `ITEM_TABLE` | Template upload (ADL 1.4 path) and WebTemplate support check |
| `DV_SCALE` | ADL2 WebTemplate build (`ArchieOptParser`) |

Other RM types may parse if Archie can instantiate them, but only the types above are explicitly wired for WebTemplate generation and clinical validation.

### Nested component archetypes

ADL2 composition templates can include **archetype slots** (`allow_archetype`) that resolve to separate registered OPTs at commit time (for example a composition wrapper containing an `OBSERVATION` or `INSTRUCTION`). On commit:

- Each nested content item is validated against its **component OPT** (looked up by `archetype_node_id`).
- **ADL rules** on the composition OPT and on each nested component OPT are evaluated.
- **Context constraints** under `/context` are always validated on the composition OPT, including wrapper templates with nested openEHR archetype content.

Slot `archetype_node_id` values are normalized before validation (local slot ids such as `id8` are resolved to full archetype ids such as `openEHR-EHR-INSTRUCTION.medication.v1`).

### Commit validation (ADL2 templates)

When a composition references an uploaded ADL2 template, `ValidationServiceImp` runs (in order):

1. **RM mandatory properties** — name, language, category, composer, template id, etc.
2. **WebTemplate validation** — cardinality, required nodes, path coverage, extra nodes (`check-for-extra-nodes`).
3. **ADL2 OPT validation** (`validate-adl2-opt-on-commit`, default `true`):
   - **Commit normalizer** — fixes common example/commit mismatches (category `431` → `at1`, slot id resolution).
   - **Component OPT validation** — Archie OPT constraints on each nested content archetype.
   - **Composition OPT validation** — Archie path validation on the full composition (skipped for wrapper templates where nested content uses full openEHR archetype ids, to avoid duplicate slot noise).
   - **Context constraint validation** — explicit walk of `/context` ADL constraints not fully covered by full-composition Archie validate: `CString` value lists (e.g. endorsement names) and `COrdered` quantity magnitude ranges (e.g. weight in kg/lb).
4. **ADL2 rule validation** (`validate-adl2-rules-on-commit`, default `true`) — rule assertions from the template OPT and nested component OPTs.
5. **Terminology validation** — external terminology checks for coded values.

Violations from steps 3–4 return `ConstraintViolationException` with RM paths and messages.

**Configuration** (`ehrbase.validation` in `application.yml` or environment):

| Property | Default | Purpose |
|----------|---------|---------|
| `validate-rm-constraints` | `true` | Strict Archie RM invariant checks during WebTemplate validation |
| `check-for-extra-nodes` | `true` | Reject compositions containing nodes not in the WebTemplate |
| `validate-adl2-opt-on-commit` | `true` | ADL2 OPT + context constraint validation on commit |
| `validate-adl2-rules-on-commit` | `true` | ADL rule assertion validation on commit |

Example (Spring YAML):

```yaml
ehrbase:
  validation:
    validate-rm-constraints: true
    check-for-extra-nodes: true
    validate-adl2-opt-on-commit: true
    validate-adl2-rules-on-commit: true
```

**Note:** `GET …/definition/template/{templateId}/example` uses the generic example generator, which may produce placeholder values that violate ADL2 value-list or range constraints. Clients should adjust example data to match OPT constraints before commit, or build compositions from validated fixtures.

## Prerequisites

- Java 21 (EhrBase v2.29.0)
- Maven 3.9+
- Gradle 8+ (for Archie submodule)
- Docker & Docker Compose (optional, for local stack)

## Build

```powershell
# 1. Publish Archie 3.18.0 to local Maven repository
.\scripts\publish-archie.ps1

# 2. Build ADL2 CDR (EhrBase fork)
.\scripts\build-ehrbase.ps1
```

Or on Unix:

```bash
./scripts/publish-archie.sh
./scripts/build-ehrbase.sh
```

## Run locally

Quick start after [building](#build):

```powershell
cd ehrbase
Copy-Item .env.ehrbase.example .env.ehrbase   # first time only
docker compose up -d
# or from repo root:
.\scripts\deploy-docker.ps1
```

| Service | URL |
|---------|-----|
| openEHR REST API | http://localhost:8080/ehrbase/rest/openehr/v1 |
| Swagger UI | http://localhost:8080/ehrbase/swagger-ui.html |
| Health / status | http://localhost:8080/ehrbase/rest/status |

For JAR-only development (no Docker), see [Deployment → JAR on a host](#jar-on-a-host-no-docker).

---

## Deployment

EhrBase stores all clinical data in **PostgreSQL**. Deployment means: provision Postgres, point the app at it with JDBC settings, then start the server so **Flyway** creates/migrates schema `ehr`.

### Configuration files

| File | Purpose |
|------|---------|
| `ehrbase/.env.ehrbase.example` | Documented template — copy to `.env.ehrbase` |
| `ehrbase/.env.ehrbase` | Active Docker env (DB URLs, credentials, auth) |
| `ehrbase/docker-compose.yml` | Bundled **EhrBase + PostgreSQL + Keycloak** |
| `ehrbase/docker-compose.external-db.yml` | **EhrBase only** — external PostgreSQL |
| `ehrbase/configuration/.../application-docker.yml` | Spring `docker` profile (container entrypoint) |
| `ehrbase/configuration/.../application-production.yml` | Spring `production` profile (JAR / VM / K8s) |

### PostgreSQL connection variables

Set these in `.env.ehrbase` (Docker) or as environment variables (JAR / Kubernetes):

| Variable | Role | Example |
|----------|------|---------|
| `DB_URL` | JDBC URL | `jdbc:postgresql://ehrdb:5432/ehrbase` |
| `DB_USER` | Runtime DB user (DML) | `ehrbase_restricted` |
| `DB_PASS` | Runtime password | *(secret)* |
| `DB_USER_ADMIN` | Flyway migration user (DDL) | `ehrbase` |
| `DB_PASS_ADMIN` | Migration password | *(secret)* |

Optional JDBC parameters: `?sslmode=require` for managed cloud databases.

**Two-user model:** Flyway connects as `DB_USER_ADMIN` to apply migrations; the application uses `DB_USER` for normal queries. The bundled Postgres image creates both users and database `ehrbase` with schemas `ehr` and `ext` on first start.

When using **bundled Postgres**, also keep these aligned with `DB_*` (used by the `ehrdb` container init):

| Variable | Purpose |
|----------|---------|
| `EHRBASE_USER` / `EHRBASE_PASSWORD` | Same as `DB_USER` / `DB_PASS` |
| `EHRBASE_USER_ADMIN` / `EHRBASE_PASSWORD_ADMIN` | Same as `DB_USER_ADMIN` / `DB_PASS_ADMIN` |
| `POSTGRES_PASSWORD` | Superuser `postgres` inside the container |

### Option A — Docker Compose with bundled PostgreSQL (recommended)

```powershell
cd ehrbase
Copy-Item .env.ehrbase.example .env.ehrbase
docker compose up -d
```

- **`ehrbase`** reads `.env.ehrbase`, activates Spring profile `docker`, waits for healthy **`ehrdb`**, runs Flyway, then serves on port `8080`.
- **`ehrdb`** persists data in Docker volume `ehrdb-data`.
- **`keycloak`** on port `8081` (optional OAuth testing).

Verify:

```powershell
curl -u ehrbase-user:SuperSecretPassword http://localhost:8080/ehrbase/rest/status
```

Stop and keep data: `docker compose down`  
Stop and wipe DB volume: `docker compose down -v`

Change host ports via `.env.ehrbase` or shell:

```bash
EHRBASE_PORT=9080 POSTGRES_PORT=5433 docker compose up -d
```

### Option B — Docker Compose with external PostgreSQL

Use when Postgres is already running (RDS, Azure Database, on-prem cluster, etc.).

1. **Prepare the database** (once). Either:
   - Run the official init image briefly, or
   - Execute `ehrbase/createdb-docker.sql` against your instance (creates database `ehrbase`, users, schemas `ehr`/`ext`, extension `uuid-ossp`).

2. **Configure** `.env.ehrbase`:

```bash
DB_URL=jdbc:postgresql://db.example.com:5432/ehrbase
DB_USER=ehrbase_restricted
DB_PASS=<runtime-secret>
DB_USER_ADMIN=ehrbase
DB_PASS_ADMIN=<admin-secret>
```

3. **Start EhrBase only:**

```powershell
cd ehrbase
docker compose -f docker-compose.external-db.yml up -d
# or from repo root:
.\scripts\deploy-docker.ps1 -ExternalDb
```

Use `host.docker.internal` instead of `localhost` when Postgres runs on the Docker host (Windows/macOS).

### Option C — JAR on a host (no Docker)

Build the JAR ([Build](#build)), ensure PostgreSQL is reachable, then:

**Development (auth off, fixed localhost DB)** — default `local` profile:

```powershell
java -jar application/target/ehrbase-2.29.0.jar
```

Uses `application-local.yml` → `jdbc:postgresql://localhost:5432/ehrbase`.

**Production-style (env-driven DB + BASIC auth)** — `production` profile:

```powershell
$env:SPRING_PROFILES_ACTIVE = "production"
$env:DB_URL = "jdbc:postgresql://db-server:5432/ehrbase"
$env:DB_USER = "ehrbase_restricted"
$env:DB_PASS = "<secret>"
$env:DB_USER_ADMIN = "ehrbase"
$env:DB_PASS_ADMIN = "<secret>"
$env:SECURITY_AUTHTYPE = "BASIC"
java -jar application/target/ehrbase-2.29.0.jar
```

Spring Boot also accepts `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, etc. if you prefer standard names.

### Option D — Custom / cloud Kubernetes

1. Deploy PostgreSQL (or use a managed service).
2. Create secrets from the [PostgreSQL connection variables](#postgresql-connection-variables).
3. Run the EhrBase container with:
   - `env` from secrets
   - Spring profile `docker` (container) or `production` (generic)
   - Liveness probe: `GET /ehrbase/rest/status`
4. Mount nothing for DB — migrations run automatically on startup via Flyway.

Example manifest fragment:

```yaml
env:
  - name: SPRING_PROFILES_ACTIVE
    value: docker
  - name: DB_URL
    value: jdbc:postgresql://postgres.default.svc:5432/ehrbase
  - name: DB_USER
    valueFrom: { secretKeyRef: { name: ehrbase-db, key: runtime-user } }
  - name: DB_PASS
    valueFrom: { secretKeyRef: { name: ehrbase-db, key: runtime-password } }
  - name: DB_USER_ADMIN
    valueFrom: { secretKeyRef: { name: ehrbase-db, key: admin-user } }
  - name: DB_PASS_ADMIN
    valueFrom: { secretKeyRef: { name: ehrbase-db, key: admin-password } }
  - name: SECURITY_AUTHTYPE
    value: BASIC
```

### Associating an already-running deployment with a new database

PostgreSQL cannot be swapped at runtime. To point a deployment at a different database:

1. Prepare the new PostgreSQL instance (empty or restored from backup).
2. Update `DB_*` in `.env.ehrbase` or your orchestrator secrets.
3. **Restart** EhrBase (Flyway runs on startup).
4. Confirm `GET /ehrbase/rest/status` and create a test EHR.

To **migrate existing data**, backup/restore the `ehrbase` PostgreSQL database; changing JDBC URL alone does not move clinical content.

### Deployment troubleshooting

| Symptom | Likely cause | Fix |
|---------|--------------|-----|
| `Connection refused` on startup | Wrong `DB_URL` host/port | Use service name `ehrdb` inside compose network; FQDN/IP externally |
| Flyway / permission errors | Runtime user used for migrations | Ensure `DB_USER_ADMIN` has DDL on schema `ehr` |
| App starts but empty API errors | Migrations failed silently | Check container logs for Flyway; connect as admin and inspect schema `ehr` |
| Auth 401 after deploy | `SECURITY_AUTHTYPE=BASIC` | Send `Authorization: Basic …` (see [Authentication](#authentication)) |
| SSL errors to cloud Postgres | Missing SSL in JDBC URL | Add `?sslmode=require` to `DB_URL` |

**Docker Compose** uses the `docker` Spring profile with **HTTP Basic authentication** enabled by default. The `local` profile (default for `java -jar` without extra config) keeps auth disabled for development and integration tests.

---

## REST API guide

This section documents how clients connect to the CDR, authenticate, and exchange openEHR Reference Model (RM) data as JSON. The canonical specification is the [openEHR ITS-REST](https://specifications.openehr.org/releases/ITS-REST/latest/) release; EhrBase implements that API under the base path below.

### Base URL and headers

```
Base URL:  http://localhost:8080/ehrbase/rest/openehr/v1
```

| Header | When required | Values |
|--------|---------------|--------|
| `Authorization` | When `security.authType` is `BASIC` or `OAUTH` | `Basic base64(user:password)` or `Bearer <jwt>` |
| `Content-Type` | POST/PUT with body | `application/json`, `application/xml`, `application/openehr.wt.flat.schema+json`, `application/openehr.wt.structured.schema+json`, `text/plain` (ADL2 templates) |
| `Accept` | Optional | Same as Content-Type for response format |
| `Prefer` | Optional | `return=representation` (include body) or `return=minimal` |
| `If-Match` | PUT composition / versioned update | Preceding version UID (`OBJECT_VERSION_ID.value`) |
| `openEHR-VERSION` | Optional | openEHR release version string |
| `openEHR-AUDIT_DETAILS` | Optional | URL-encoded audit details for create operations |

Query parameter `format` on composition endpoints: `JSON`, `XML`, `FLAT`, `STRUCTURED`.

### Authentication

Authentication is implemented with Spring Security. Configure via `security.authType` (environment variable `SECURITY_AUTHTYPE`).

| Mode | Config | Behaviour |
|------|--------|-----------|
| `NONE` | `SECURITY_AUTHTYPE=NONE` | No credentials required (used by `local` profile / integration tests) |
| `BASIC` | `SECURITY_AUTHTYPE=BASIC` | HTTP Basic on all routes except `/`, `/img/**`. **Default for Docker Compose.** |
| `OAUTH` | `SECURITY_AUTHTYPE=OAUTH` + JWT issuer | Bearer token; roles from Keycloak `realm_access.roles` or JWT `scope` |

**Default BASIC credentials** (`ehrbase/configuration/src/main/resources/application.yml`):

| Role | Username | Password | Access |
|------|----------|----------|--------|
| Clinical user | `ehrbase-user` | `SuperSecretPassword` | openEHR REST API |
| Admin | `ehrbase-admin` | `EvenMoreSecretPassword` | REST API + `/rest/admin/**` (when admin API enabled) |

Docker Compose sets these via `ehrbase/.env.ehrbase` and enables `SECURITY_AUTHTYPE=BASIC`.

**PowerShell example (Basic auth):**

```powershell
$user = "ehrbase-user"
$pass = "SuperSecretPassword"
$token = [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes("${user}:${pass}"))
$headers = @{ Authorization = "Basic $token" }

Invoke-RestMethod -Uri "http://localhost:8080/ehrbase/rest/openehr/v1/ehr" `
  -Method POST -Headers $headers -ContentType "application/json"
```

**curl example:**

```bash
curl -u ehrbase-user:SuperSecretPassword \
  -H "Prefer: return=representation" \
  -H "Content-Type: application/json" \
  -X POST "http://localhost:8080/ehrbase/rest/openehr/v1/ehr"
```

**OAuth2 (Keycloak):** Docker Compose starts Keycloak on port `8081`. Set:

```yaml
security:
  authType: OAUTH
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: http://localhost:8081/auth/realms/ehrbase
```

**CORS** (browser clients): set allowed origins in configuration:

```yaml
web:
  cors:
    allowedOrigins: "https://your-frontend.example.com"
```

---

### Clinical data workflow

Typical sequence: **create EHR → upload template → create composition → query with AQL**.

#### 1. Create EHR

```http
POST /ehrbase/rest/openehr/v1/ehr
Prefer: return=representation
Content-Type: application/json
```

Optional body: `EHR_STATUS` JSON (see schema below). Empty body creates a default EHR.

**Response (`201 Created`):** `Ehr` wrapper with assigned `ehr_id`.

#### 2. Get EHR

```http
GET /ehrbase/rest/openehr/v1/ehr/{ehr_id}
```

Or by subject:

```http
GET /ehrbase/rest/openehr/v1/ehr?subject_id={id}&subject_namespace={ns}
```

#### 3. Upload ADL2 template

```http
POST /ehrbase/rest/openehr/v1/definition/template/adl2
Content-Type: text/plain
Prefer: return=representation

<operational_template ADL or OPT JSON>
```

List templates: `GET /definition/template/adl2`  
Get one: `GET /definition/template/adl2/{template_id}/{version_pattern}`

#### 4. Create composition (PUT clinical data)

```http
POST /ehrbase/rest/openehr/v1/ehr/{ehr_id}/composition?templateId={template_id}
Content-Type: application/json
Prefer: return=representation

{ ... COMPOSITION JSON ... }
```

**Response headers:** `Location`, `ETag` (version UID), optional body when `Prefer: return=representation`.

#### 5. Get composition

```http
GET /ehrbase/rest/openehr/v1/ehr/{ehr_id}/composition/{versioned_object_uid}?format=JSON
```

Optional: `version_at_time` (ISO-8601) for historical version.

#### 6. Update composition

```http
PUT /ehrbase/rest/openehr/v1/ehr/{ehr_id}/composition/{versioned_object_uid}
If-Match: {preceding_version_uid}
Content-Type: application/json

{ ... COMPOSITION JSON ... }
```

#### 7. Create contribution (batch of versions)

```http
POST /ehrbase/rest/openehr/v1/ehr/{ehr_id}/contribution
Content-Type: application/json

{ ... CONTRIBUTION JSON ... }
```

#### 8. AQL query

```http
GET /ehrbase/rest/openehr/v1/query/aql?q=SELECT+c+FROM+COMPOSITION+c+WHERE+e/ehr_id/value+=+:ehrId&query_parameters.ehrId={uuid}
```

Or POST with JSON body:

```json
{
  "q": "SELECT c FROM EHR e CONTAINS COMPOSITION c WHERE e/ehr_id/value = :ehrId",
  "query_parameters": { "ehrId": "00000000-0000-0000-0000-000000000001" }
}
```

Stored queries: `GET|POST /query/{qualified_query_name}[/{version}]`

#### Manual test script

```powershell
$env:EHRBASE_USER = "ehrbase-user"
$env:EHRBASE_PASSWORD = "SuperSecretPassword"
.\scripts\test-adl2-compositions.ps1
```

---

### RM JSON schemas (required fields)

JSON representations use Jackson polymorphism: every RM object includes `"_type": "<RM_TYPE_NAME>"`. Schemas below list **attributes required by the openEHR RM 1.0.4 specification** and **additional checks enforced by this CDR** on create/update. Template-specific constraints (cardinality, value ranges, coded text lists) are enforced via WebTemplate validation at commit. For ADL2 templates, see [ADL2 template support](#adl2-template-support) for supported types and the full commit validation pipeline (`validate-adl2-opt-on-commit` and `validate-adl2-rules-on-commit`, both default `true`).

Shared definitions used by multiple schemas:

```json
{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "$id": "https://adl2-cdr.local/schemas/openehr-common.json",
  "$defs": {
    "RmObject": {
      "type": "object",
      "required": ["_type"],
      "properties": {
        "_type": { "type": "string" }
      }
    },
    "DvText": {
      "allOf": [
        { "$ref": "#/$defs/RmObject" },
        {
          "properties": {
            "_type": { "const": "DV_TEXT" },
            "value": { "type": "string" }
          },
          "required": ["value"]
        }
      ]
    },
    "CodePhrase": {
      "allOf": [
        { "$ref": "#/$defs/RmObject" },
        {
          "properties": {
            "_type": { "const": "CODE_PHRASE" },
            "terminology_id": { "$ref": "#/$defs/TerminologyId" },
            "code_string": { "type": "string" }
          },
          "required": ["terminology_id", "code_string"]
        }
      ]
    },
    "TerminologyId": {
      "allOf": [
        { "$ref": "#/$defs/RmObject" },
        {
          "properties": {
            "_type": { "const": "TERMINOLOGY_ID" },
            "value": { "type": "string" }
          },
          "required": ["value"]
        }
      ]
    },
    "DvCodedText": {
      "allOf": [
        { "$ref": "#/$defs/RmObject" },
        {
          "properties": {
            "_type": { "const": "DV_CODED_TEXT" },
            "value": { "type": "string" },
            "defining_code": { "$ref": "#/$defs/CodePhrase" }
          },
          "required": ["defining_code"]
        }
      ]
    },
    "DvQuantity": {
      "allOf": [
        { "$ref": "#/$defs/RmObject" },
        {
          "properties": {
            "_type": { "const": "DV_QUANTITY" },
            "magnitude": { "type": "number" },
            "units": { "type": "string" }
          },
          "required": ["magnitude"]
        }
      ]
    },
    "DvDateTime": {
      "allOf": [
        { "$ref": "#/$defs/RmObject" },
        {
          "properties": {
            "_type": { "const": "DV_DATE_TIME" },
            "value": { "type": "string", "format": "date-time" }
          },
          "required": ["value"]
        }
      ]
    },
    "DvBoolean": {
      "allOf": [
        { "$ref": "#/$defs/RmObject" },
        {
          "properties": {
            "_type": { "const": "DV_BOOLEAN" },
            "value": { "type": "boolean" }
          },
          "required": ["value"]
        }
      ]
    },
    "DvCount": {
      "allOf": [
        { "$ref": "#/$defs/RmObject" },
        {
          "properties": {
            "_type": { "const": "DV_COUNT" },
            "magnitude": { "type": "integer" }
          },
          "required": ["magnitude"]
        }
      ]
    },
    "DvProportion": {
      "allOf": [
        { "$ref": "#/$defs/RmObject" },
        {
          "properties": {
            "_type": { "const": "DV_PROPORTION" },
            "numerator": { "type": "number" },
            "denominator": { "type": "number" },
            "type": { "type": "integer" },
            "precision": { "type": "integer" }
          },
          "required": ["numerator", "denominator"]
        }
      ]
    },
    "DvDate": {
      "allOf": [
        { "$ref": "#/$defs/RmObject" },
        {
          "properties": {
            "_type": { "const": "DV_DATE" },
            "value": { "type": "string" }
          },
          "required": ["value"]
        }
      ]
    },
    "DvTime": {
      "allOf": [
        { "$ref": "#/$defs/RmObject" },
        {
          "properties": {
            "_type": { "const": "DV_TIME" },
            "value": { "type": "string" }
          },
          "required": ["value"]
        }
      ]
    },
    "DvDuration": {
      "allOf": [
        { "$ref": "#/$defs/RmObject" },
        {
          "properties": {
            "_type": { "const": "DV_DURATION" },
            "value": { "type": "string" }
          },
          "required": ["value"]
        }
      ]
    },
    "DvOrdinal": {
      "allOf": [
        { "$ref": "#/$defs/RmObject" },
        {
          "properties": {
            "_type": { "const": "DV_ORDINAL" },
            "value": { "type": "integer" },
            "symbol": { "$ref": "#/$defs/DvCodedText" }
          },
          "required": ["value"]
        }
      ]
    },
    "DvUri": {
      "allOf": [
        { "$ref": "#/$defs/RmObject" },
        {
          "properties": {
            "_type": { "const": "DV_URI" },
            "value": { "type": "string" }
          },
          "required": ["value"]
        }
      ]
    },
    "DvEhrUri": {
      "allOf": [
        { "$ref": "#/$defs/RmObject" },
        {
          "properties": {
            "_type": { "const": "DV_EHR_URI" },
            "value": { "type": "string" }
          },
          "required": ["value"]
        }
      ]
    },
    "DvMultimedia": {
      "allOf": [
        { "$ref": "#/$defs/RmObject" },
        {
          "properties": {
            "_type": { "const": "DV_MULTIMEDIA" },
            "media_type": { "$ref": "#/$defs/CodePhrase" },
            "size": { "type": "integer" },
            "data": { "type": "string" }
          },
          "required": ["media_type"]
        }
      ]
    },
    "DvParsable": {
      "allOf": [
        { "$ref": "#/$defs/RmObject" },
        {
          "properties": {
            "_type": { "const": "DV_PARSABLE" },
            "value": { "type": "string" },
            "formalism": { "type": "string" }
          },
          "required": ["value", "formalism"]
        }
      ]
    },
    "DvState": {
      "allOf": [
        { "$ref": "#/$defs/RmObject" },
        {
          "properties": {
            "_type": { "const": "DV_STATE" },
            "value": { "$ref": "#/$defs/DvCodedText" },
            "is_terminal": { "type": "boolean" }
          },
          "required": ["value"]
        }
      ]
    },
    "DvIdentifier": {
      "allOf": [
        { "$ref": "#/$defs/RmObject" },
        {
          "properties": {
            "_type": { "const": "DV_IDENTIFIER" },
            "issuer": { "type": "string" },
            "assigner": { "type": "string" },
            "id": { "type": "string" },
            "type": { "type": "string" }
          },
          "required": ["issuer", "assigner", "id", "type"]
        }
      ]
    },
    "ObjectVersionId": {
      "allOf": [
        { "$ref": "#/$defs/RmObject" },
        {
          "properties": {
            "_type": { "const": "OBJECT_VERSION_ID" },
            "value": { "type": "string" }
          },
          "required": ["value"]
        }
      ]
    },
    "ObjectId": {
      "allOf": [
        { "$ref": "#/$defs/RmObject" },
        {
          "properties": {
            "_type": { "enum": ["HIER_OBJECT_ID", "OBJECT_VERSION_ID", "GENERIC_ID"] },
            "value": { "type": "string" }
          },
          "required": ["value"]
        }
      ]
    },
    "PartySelf": {
      "allOf": [
        { "$ref": "#/$defs/RmObject" },
        { "properties": { "_type": { "const": "PARTY_SELF" } } }
      ]
    },
    "PartyIdentified": {
      "allOf": [
        { "$ref": "#/$defs/RmObject" },
        {
          "properties": {
            "_type": { "const": "PARTY_IDENTIFIED" },
            "name": { "type": "string" },
            "identifiers": {
              "type": "array",
              "items": { "$ref": "#/$defs/DvIdentifier" }
            }
          }
        }
      ]
    },
    "TemplateId": {
      "allOf": [
        { "$ref": "#/$defs/RmObject" },
        {
          "properties": {
            "_type": { "const": "TEMPLATE_ID" },
            "value": { "type": "string" }
          },
          "required": ["value"]
        }
      ]
    },
    "Archetyped": {
      "allOf": [
        { "$ref": "#/$defs/RmObject" },
        {
          "properties": {
            "_type": { "const": "ARCHETYPED" },
            "rm_version": { "type": "string" },
            "template_id": { "$ref": "#/$defs/TemplateId" }
          },
          "required": ["rm_version", "template_id"]
        }
      ]
    },
    "AuditDetails": {
      "allOf": [
        { "$ref": "#/$defs/RmObject" },
        {
          "properties": {
            "_type": { "const": "AUDIT_DETAILS" },
            "system_id": { "type": "string" },
            "committer": { "$ref": "#/$defs/PartyIdentified" },
            "change_type": {
              "allOf": [{ "$ref": "#/$defs/DvCodedText" }],
              "description": "openEHR terminology: creation, amendment, modification, deletion, ..."
            },
            "description": { "$ref": "#/$defs/DvText" }
          },
          "required": ["system_id", "committer", "change_type"]
        }
      ]
    },
    "Locatable": {
      "description": "Abstract base; all subtypes require name.",
      "allOf": [
        { "$ref": "#/$defs/RmObject" },
        {
          "properties": {
            "name": { "$ref": "#/$defs/DvText" },
            "uid": { "$ref": "#/$defs/ObjectId" },
            "links": { "type": "array" },
            "archetype_details": { "$ref": "#/$defs/Archetyped" },
            "feeder_audit": { "type": "object" }
          },
          "required": ["name"]
        }
      ]
    }
  }
}
```

#### EHR (API response wrapper)

Returned by `POST/GET /ehr`. Not a pure RM class; wraps system metadata and `EHR_STATUS`.

```json
{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "title": "Ehr",
  "type": "object",
  "properties": {
    "system_id": { "$ref": "openehr-common.json#/$defs/ObjectId" },
    "ehr_id": { "$ref": "openehr-common.json#/$defs/ObjectId" },
    "ehr_status": { "$ref": "#/$defs/EhrStatus" },
    "time_created": { "$ref": "openehr-common.json#/$defs/DvDateTime" }
  },
  "required": ["ehr_id", "ehr_status"]
}
```

#### EHR_STATUS

Used in `POST/PUT /ehr` body and nested in EHR responses. **CDR-enforced required fields:** `subject`, `is_queryable`, `is_modifiable`, plus `_type` and `archetype_node_id` in JSON.

```json
{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "title": "EHR_STATUS",
  "allOf": [{ "$ref": "openehr-common.json#/$defs/Locatable" }],
  "properties": {
    "_type": { "const": "EHR_STATUS" },
    "archetype_node_id": { "type": "string" },
    "subject": { "$ref": "openehr-common.json#/$defs/PartySelf" },
    "is_queryable": { "type": "boolean" },
    "is_modifiable": { "type": "boolean" },
    "other_details": { "type": "object" }
  },
  "required": ["_type", "archetype_node_id", "name", "subject", "is_queryable", "is_modifiable"]
}
```

**Minimal valid example:**

```json
{
  "_type": "EHR_STATUS",
  "archetype_node_id": "openEHR-EHR-EHR_STATUS.generic.v1",
  "name": { "_type": "DV_TEXT", "value": "EHR status" },
  "subject": { "_type": "PARTY_SELF" },
  "is_queryable": true,
  "is_modifiable": true
}
```

#### COMPOSITION

Root document for `POST /ehr/{ehr_id}/composition`. **CDR-enforced on create:** `name`, `archetype_node_id`, `language`, `category`, `composer`, `archetype_details`, `archetype_details.template_id`, plus WebTemplate validation for the uploaded template.

```json
{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "title": "COMPOSITION",
  "allOf": [{ "$ref": "openehr-common.json#/$defs/Locatable" }],
  "properties": {
    "_type": { "const": "COMPOSITION" },
    "archetype_node_id": { "type": "string" },
    "language": { "$ref": "openehr-common.json#/$defs/CodePhrase" },
    "territory": { "$ref": "openehr-common.json#/$defs/CodePhrase" },
    "category": { "$ref": "openehr-common.json#/$defs/DvCodedText" },
    "composer": { "$ref": "openehr-common.json#/$defs/PartyIdentified" },
    "context": { "$ref": "#/$defs/EventContext" },
    "content": {
      "type": "array",
      "items": { "$ref": "#/$defs/ContentItem" }
    }
  },
  "required": [
    "_type", "name", "archetype_node_id", "language", "territory",
    "category", "composer", "archetype_details"
  ]
}
```

`archetype_details.template_id.value` must match an uploaded template ID.

#### EVENT_CONTEXT (composition context)

```json
{
  "title": "EVENT_CONTEXT",
  "allOf": [{ "$ref": "openehr-common.json#/$defs/Locatable" }],
  "properties": {
    "_type": { "const": "EVENT_CONTEXT" },
    "start_time": { "$ref": "openehr-common.json#/$defs/DvDateTime" },
    "end_time": { "$ref": "openehr-common.json#/$defs/DvDateTime" },
    "setting": { "$ref": "openehr-common.json#/$defs/DvCodedText" },
    "health_care_facility": { "$ref": "openehr-common.json#/$defs/PartyIdentified" },
    "participations": { "type": "array" },
    "other_context": { "$ref": "#/$defs/ItemStructure" }
  },
  "required": ["_type", "name", "start_time"]
}
```

#### Content items (COMPOSITION.content)

Each entry inherits RM **ENTRY** requirements when present in content.

| RM class | Required attributes (RM + typical template) |
|----------|-----------------------------------------------|
| **OBSERVATION** | `name`, `archetype_node_id`, `language`, `encoding`, `subject`, `data` (HISTORY) |
| **EVALUATION** | Same as OBSERVATION (`data` is ITEM_TREE, not HISTORY) |
| **INSTRUCTION** | `name`, `archetype_node_id`, `language`, `encoding`, `subject`, `narrative`, `activities` |
| **ACTION** | `name`, `archetype_node_id`, `language`, `encoding`, `subject`, `time`, `description`, `ism_transition` |
| **ADMIN_ENTRY** | `name`, `archetype_node_id`, `language`, `encoding`, `subject`, `data` (ITEM_TREE) |
| **SECTION** | `name`, `archetype_node_id` (contains nested content) |

```json
{
  "title": "OBSERVATION",
  "properties": {
    "_type": { "const": "OBSERVATION" },
    "name": { "$ref": "openehr-common.json#/$defs/DvText" },
    "archetype_node_id": { "type": "string" },
    "language": { "$ref": "openehr-common.json#/$defs/CodePhrase" },
    "encoding": { "$ref": "openehr-common.json#/$defs/CodePhrase" },
    "subject": { "$ref": "openehr-common.json#/$defs/PartySelf" },
    "data": { "$ref": "#/$defs/History" }
  },
  "required": ["_type", "name", "archetype_node_id", "language", "encoding", "subject", "data"]
}
```

#### HISTORY / EVENT (observation data)

```json
{
  "title": "HISTORY",
  "properties": {
    "_type": { "const": "HISTORY" },
    "name": { "$ref": "openehr-common.json#/$defs/DvText" },
    "origin": { "$ref": "openehr-common.json#/$defs/DvDateTime" },
    "events": {
      "type": "array",
      "minItems": 1,
      "items": { "$ref": "#/$defs/Event" }
    }
  },
  "required": ["_type", "name", "origin", "events"]
}
```

```json
{
  "title": "EVENT",
  "properties": {
    "_type": { "enum": ["POINT_EVENT", "INTERVAL_EVENT"] },
    "name": { "$ref": "openehr-common.json#/$defs/DvText" },
    "time": { "$ref": "openehr-common.json#/$defs/DvDateTime" },
    "data": { "$ref": "#/$defs/ItemStructure" },
    "math_function": { "$ref": "openehr-common.json#/$defs/DvCodedText" }
  },
  "required": ["_type", "name", "time", "data"]
}
```

#### ITEM_TREE / CLUSTER / ELEMENT

```json
{
  "title": "ITEM_TREE",
  "properties": {
    "_type": { "const": "ITEM_TREE" },
    "name": { "$ref": "openehr-common.json#/$defs/DvText" },
    "items": { "type": "array", "items": { "$ref": "#/$defs/ClusterOrElement" } }
  },
  "required": ["_type", "name", "items"]
}
```

```json
{
  "title": "CLUSTER",
  "properties": {
    "_type": { "const": "CLUSTER" },
    "name": { "$ref": "openehr-common.json#/$defs/DvText" },
    "archetype_node_id": { "type": "string" },
    "items": { "type": "array", "items": { "$ref": "#/$defs/ClusterOrElement" } }
  },
  "required": ["_type", "name", "items"]
}
```

```json
{
  "title": "ELEMENT",
  "properties": {
    "_type": { "const": "ELEMENT" },
    "name": { "$ref": "openehr-common.json#/$defs/DvText" },
    "archetype_node_id": { "type": "string" },
    "value": {
      "description": "One of the supported ADL2 data value types (see ADL2 template support).",
      "oneOf": [
        { "$ref": "openehr-common.json#/$defs/DvText" },
        { "$ref": "openehr-common.json#/$defs/DvCodedText" },
        { "$ref": "openehr-common.json#/$defs/DvQuantity" },
        { "$ref": "openehr-common.json#/$defs/DvDateTime" },
        { "$ref": "openehr-common.json#/$defs/DvIdentifier" },
        { "$ref": "openehr-common.json#/$defs/DvBoolean" },
        { "$ref": "openehr-common.json#/$defs/DvCount" },
        { "$ref": "openehr-common.json#/$defs/DvProportion" },
        { "$ref": "openehr-common.json#/$defs/DvDate" },
        { "$ref": "openehr-common.json#/$defs/DvTime" },
        { "$ref": "openehr-common.json#/$defs/DvDuration" },
        { "$ref": "openehr-common.json#/$defs/DvOrdinal" },
        { "$ref": "openehr-common.json#/$defs/DvUri" },
        { "$ref": "openehr-common.json#/$defs/DvEhrUri" },
        { "$ref": "openehr-common.json#/$defs/DvMultimedia" },
        { "$ref": "openehr-common.json#/$defs/DvParsable" },
        { "$ref": "openehr-common.json#/$defs/DvState" }
      ]
    }
  },
  "required": ["_type", "name", "value"]
}
```

#### CONTRIBUTION

```json
{
  "title": "CONTRIBUTION",
  "type": "object",
  "properties": {
    "versions": {
      "type": "array",
      "minItems": 1,
      "items": { "$ref": "#/$defs/Version" }
    },
    "audit": { "$ref": "openehr-common.json#/$defs/AuditDetails" }
  },
  "required": ["versions"]
}
```

**CDR rules on create:** `versions` must not be empty; `audit.time_committed` must be **null** (server sets it); each `version.contribution` must be **null**; `version.data` is not validated on contribution ingest.

#### VERSION (inside CONTRIBUTION)

```json
{
  "title": "VERSION",
  "properties": {
    "_type": { "const": "VERSION" },
    "commit_audit": { "$ref": "openehr-common.json#/$defs/AuditDetails" },
    "data": {
      "description": "COMPOSITION, EHR_STATUS, or other VERSION_OBJECT",
      "type": "object"
    },
    "lifecycle_state": { "$ref": "openehr-common.json#/$defs/DvCodedText" }
  },
  "required": ["_type", "commit_audit", "data", "lifecycle_state"]
}
```

`commit_audit.time_committed` must be **null** on create.

#### Validation summary

| Resource | Validator on commit | Key required fields |
|----------|---------------------|---------------------|
| EHR / EHR_STATUS | RM + DTO checks | `subject`, `is_queryable`, `is_modifiable` |
| COMPOSITION | RM + WebTemplate + terminology + ADL2 OPT/rules (ADL2 templates) | See COMPOSITION schema; template must exist |
| CONTRIBUTION | RM (partial) | Non-empty `versions`; server-managed audit timestamps |
| ADL2 OPT constraints | Archie + context walk on commit | Value lists (`CString`), quantity ranges (`COrdered`); component OPTs for nested archetypes; disable with `ehrbase.validation.validate-adl2-opt-on-commit=false` |
| ADL2 rules | Archie rule engine on commit | Rule assertions in ADL on composition and nested OPTs; disable with `ehrbase.validation.validate-adl2-rules-on-commit=false` |

**ADL2 OPT constraint examples** (from integration fixtures):

| Constraint | ADL pattern | Commit behaviour |
|------------|-------------|------------------|
| Endorsement value list | `DV_TEXT` with `value matches {"Robert", "Rick", "Clara"}` | Rejected if value not in list |
| Weight range | `DV_QUANTITY` with `[units, magnitude]` tuples | Rejected if magnitude outside allowed interval for the unit |
| Blood pressure rules | ADL `rules` section with assertions | Rejected if assertion evaluates to false (e.g. systolic ≤ diastolic) |
| Observation wrapper | Composition slot → observation OPT | Component OPT + rules validated on nested content |

For machine-readable API discovery use **Swagger UI** at `/ehrbase/swagger-ui.html`. For the full RM metamodel see `archie/referencemodels/src/main/resources/bmm/openEHR/` (BMM files) and the [openEHR RM specification](https://specifications.openehr.org/releases/RM/latest/).

---

## ADL2 template upload

Upload ADL (`.adls`) or serialized OPT JSON. The server parses with Archie, stores the OPT JSON, builds a WebTemplate, and validates supported RM types (see [ADL2 template support](#adl2-template-support)).

```powershell
$headers = @{
  Authorization = "Basic " + [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes("ehrbase-user:SuperSecretPassword"))
  Prefer = "return=representation"
}
Invoke-RestMethod -Uri "http://localhost:8080/ehrbase/rest/openehr/v1/definition/template/adl2" `
  -Method POST -ContentType "text/plain" -Headers $headers `
  -InFile "template.adls"
```

## Integration with Archie Platform

After deploying this CDR, point the Archie platform tenant CDR URL here and sync via `/definition/template/adl2` instead of `/definition/template/adl1.4`. See [docs/PLATFORM_MIGRATION.md](docs/PLATFORM_MIGRATION.md).

## License

Apache License 2.0. See [NOTICE](NOTICE) for upstream attribution (Archie, EhrBase, openEHR SDK).
