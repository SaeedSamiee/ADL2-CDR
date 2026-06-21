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

```powershell
cd ehrbase
docker compose up -d
java -jar application/target/ehrbase-2.29.0.jar
```

| Service | URL |
|---------|-----|
| openEHR REST API | http://localhost:8080/ehrbase/rest/openehr/v1 |
| Swagger UI | http://localhost:8080/ehrbase/swagger-ui.html |
| Health / status | http://localhost:8080/ehrbase/rest/status |

**Docker Compose** uses the `docker` Spring profile with **HTTP Basic authentication enabled** (see [Authentication](#authentication)). The `local` profile (default for `java -jar` without extra config) keeps auth disabled for development and integration tests.

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

JSON representations use Jackson polymorphism: every RM object includes `"_type": "<RM_TYPE_NAME>"`. Schemas below list **attributes required by the openEHR RM 1.0.4 specification** and **additional checks enforced by this CDR** on create/update. Template-specific constraints (cardinality, value ranges, coded text lists) are enforced via WebTemplate validation at commit; full ADL2 OPT constraints are available through Archie but not yet wired into every commit path for ADL2 templates.

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
      "oneOf": [
        { "$ref": "openehr-common.json#/$defs/DvText" },
        { "$ref": "openehr-common.json#/$defs/DvCodedText" },
        { "$ref": "openehr-common.json#/$defs/DvQuantity" },
        { "$ref": "openehr-common.json#/$defs/DvDateTime" }
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
| COMPOSITION | RM + WebTemplate + terminology | See COMPOSITION schema; template must exist |
| CONTRIBUTION | RM (partial) | Non-empty `versions`; server-managed audit timestamps |
| ADL2 OPT constraints | Archie (not yet on all commit paths) | Value lists, ranges in ADL — use `/definition/template/adl2` + client-side validation |

For machine-readable API discovery use **Swagger UI** at `/ehrbase/swagger-ui.html`. For the full RM metamodel see `archie/referencemodels/src/main/resources/bmm/openEHR/` (BMM files) and the [openEHR RM specification](https://specifications.openehr.org/releases/RM/latest/).

---

## ADL2 template upload

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
