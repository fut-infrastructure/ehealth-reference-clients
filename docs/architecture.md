# Architecture

This document is a short tour for first-time readers.

## Two independent web applications

The repository contains two Spring Boot 3.4 web applications:

- **clinician-client** is used by healthcare professionals. It authenticates against the FUT
  Keycloak `clinician` realm and lets a clinician select a CareTeam profile, create citizens,
  and manage episodes of care and care plans.
- **citizen-client** is used by patients. It authenticates against the FUT Keycloak `citizen`
  realm (NemLogin) and shows a citizen their planned weekly activities.

Each app is an independent deployment unit with its own `Dockerfile`, Helm chart, and
`application.yaml`. They share no runtime state and expose no API to each other. The split
reflects the real security boundary: clinician tokens carry CareTeam and organization context,
citizen tokens carry only the patient's own identity.

## Shared common module

Both apps depend on a single `common` Maven module that holds three categories of shared code:

1. **FHIR client plumbing**: `FhirClientFactory`, `FhirServer` enum, `BaseUrlResolver`,
   `IdFactory`, `BundleUtil`, `SearchUtil`. Every outbound FHIR call goes through
   `FhirClientFactory.createClient(server, context)`, which attaches the correct bearer token
   and base URL before the call is made.
2. **OAuth2 refresh extension**: `EHealthRefreshTokenGrantRequest`,
   `EHealthRefreshTokenResponseClient`, and the request-scoped `EHealthUser` bean. The FUT
   Keycloak accepts extra parameters on the `refresh_token` grant that scope the issued access
   token to a specific organization, CareTeam, patient, and episode-of-care tuple. Spring
   Security does not support this out of the box; this extension adds the required form
   parameters to the refresh request.
3. **Calm CSS**: a hand-written `calm.css` that defines the six component patterns used across
   all templates (`chip`, `card`, `data-row`, `button`, `form`, `layout`). No external CSS
   framework; no CDN dependency at runtime.

## Authentication and security context

Both apps authenticate via Spring Security OAuth2 against different Keycloak realms: clinicians
log in through the `ehealth` realm; citizens log in through the `nemlogin` realm.

FUT's FHIR APIs require the access token to carry context beyond the user identity:
organization, CareTeam, patient, and episode of care. The apps use the `EHealthContext` record
to hold that context and pass it through to every outbound FHIR call. Controllers never read
session state or token claims directly; the context is injected as a typed method parameter.

## FHIR layering

Each app's `infrastructure/fhir/` tree is split into two sub-packages with a strict
responsibility rule:

- `api/` holds interfaces that are raw FHIR wrappers. Each method corresponds to one FHIR
  operation (a search, a read, a create, a custom operation). No business logic, no derivation,
  no convenience composition. The surface area of this layer should be scannable in one sitting
  and recognisable as "FHIR, plus auth."
- `service/` holds the HAPI implementations of those interfaces.

Business logic (finding the top parent of an organization hierarchy, grouping activities by
week, mapping a FHIR `EpisodeOfCare` to a display record) lives in `app/`, in mappers or
application services that call the raw `api/` methods and transform the results.

This separation is intentional: vendors reading the code should find it easy to locate where
FHIR calls happen and easy to locate where the clinical logic happens, without the two being
tangled.

