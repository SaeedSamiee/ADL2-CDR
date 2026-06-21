# Archie Platform Migration to ADL2 CDR

When the ADL2 CDR fork is deployed, update the Archie platform (separate repo) as follows:

## Template sync

Change `EhrBaseTemplateSyncClient` to post ADL2 OPT source to:

```
POST {cdrBaseUrl}/definition/template/adl2
Content-Type: text/plain
```

Body: ADL2 operational template (`.adls` text) or serialized OPT JSON from platform `OperationalTemplateEntity.optJson`.

## Composition submit

Remove `CompositionAdl14Adapter` from the submit path when the tenant CDR is ADL2-capable. Compositions validated against ADL2 OPT use native Archie node IDs.

## Sync DTO

Drop `optXmlAdl14` requirement from template sync requests. The platform can sync directly from `optJson` or generated ADL2 ADL.

## Feature detection

Optional: probe `GET {cdrBaseUrl}/definition/template/adl2` — HTTP 200 with empty list means ADL2 support; HTTP 501 means legacy EhrBase.

## Environment

Set tenant CDR URL to the ADL2 CDR instance, e.g.:

```
http://localhost:8080/ehrbase/rest/openehr/v1
```
