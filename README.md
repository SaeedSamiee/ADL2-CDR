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

Swagger UI: http://localhost:8080/ehrbase/swagger-ui.html

## ADL2 template upload

```powershell
curl -X POST "http://localhost:8080/ehrbase/rest/openehr/v1/definition/template/adl2" `
  -H "Content-Type: text/plain" `
  -H "Prefer: return=representation" `
  --data-binary "@template.adls"
```

## Integration with Archie Platform

After deploying this CDR, point the Archie platform tenant CDR URL here and sync via `/definition/template/adl2` instead of `/definition/template/adl1.4`. See [docs/PLATFORM_MIGRATION.md](docs/PLATFORM_MIGRATION.md).

## License

Apache License 2.0. See [NOTICE](NOTICE) for upstream attribution (Archie, EhrBase, openEHR SDK).
