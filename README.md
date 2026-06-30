# ehealth-reference-clients

Reference implementation of a consumer solution on top of the Danish FUT eHealth infrastructure.

The intended use is as a reading and copy-paste resource. Read the code to understand the patterns, then copy what you need into your own application.

Two Spring Boot web apps showing how to integrate with the infrastructure's FHIR APIs and Keycloak-based security:

- **clinician-client** - clinician-facing web app
- **citizen-client** - citizen-facing web app

## What this is

This repository shows a working, end-to-end integration with the FUT platform. It covers:

- OAuth2 login against the FUT Keycloak realms (clinician via the `ehealth` realm; citizen via NemLogin)
- CareTeam context selection for clinicians (the context-aware token refresh that FUT's FHIR APIs require)
- Creating a citizen via the `$createPatient` FHIR operation (CPR lookup + enrichment from the national CPR registry)
- Managing episodes of care and care plans (create, activate, close)
- Citizen view of weekly planned activities via the `$get-patient-procedures` operation

Both apps are server-side Spring Boot (Java 21, Spring Boot 3.4.x, Thymeleaf). There is no separate SPA bundle and no Node toolchain. The FHIR client is HAPI FHIR R4.


## What this is not

- **Not feature-complete.** Many real-world features are deliberately out of scope: video, messaging, questionnaires, measurements, tasks, and appointments. There is no other reference implementation for those features. Use the [FHIR Implementation Guide](https://github.com/fut-infrastructure/implementation-guide) as the reference.
- **Not a library or framework.** There is no published artifact to depend on. Copy patterns into your own codebase.
- **Not a production system.** No production deployment, no SLA, no PII handling beyond what the test environments already provide.

## Running locally

Build: `./mvnw clean verify`

Running requires registered OAuth2 clients on the target environment. The compose file defaults to `devenvcgi` with pre-provisioned clients; no configuration needed for a first run. See `docs/onboarding/connect-to-test.md`.
