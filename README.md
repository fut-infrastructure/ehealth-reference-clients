# ehealth-reference-clients

Reference implementation of a consumer solution on top of the Danish FUT eHealth infrastructure.

The intended use is as a reading and copy-paste resource. Read the code to understand the patterns, then copy what you need into your own application.

Two Spring Boot web apps showing how to integrate with the infrastructure's FHIR APIs and Keycloak-based security:

- **[clinician-client](clinician-client/README.md)** - clinician-facing web app
- **[citizen-client](citizen-client/README.md)** - citizen-facing web app

## What this is

This repository shows a working, end-to-end integration with the FUT platform. It covers:

- OAuth2 login against the FUT Keycloak realms (clinician via the `ehealth` realm; citizen via NemLogin), and the context-aware token refresh FUT's FHIR APIs require, see [`docs/authentication.md`](docs/authentication.md)
- Creating a citizen via the `$createPatient` FHIR operation (CPR lookup + enrichment from the national CPR registry)
- Searching for citizens, managing episodes of care and care plans (create, activate, close), and reviewing/approving the tasks the platform creates on a citizen's measurement submission
- Citizen view of weekly planned activities via the `$get-patient-procedures` operation, and submitting measurements

Both apps are server-side Spring Boot (Java 21, Spring Boot 3.4.x, Thymeleaf). There is no separate SPA bundle and no Node toolchain. The FHIR client is HAPI FHIR R4.

See [`docs/architecture.md`](docs/architecture.md) for a system overview and [`docs/user-guide.md`](docs/user-guide.md) for a screen-by-screen tour.

## What this is not

- **Not feature-complete.** Many real-world features are deliberately out of scope: video, messaging, questionnaires, and appointments as a standalone feature. There is no other reference implementation for those features. Use the [FHIR Implementation Guide](https://github.com/fut-infrastructure/implementation-guide) as the reference.
- **Not a library or framework.** There is no published artifact to depend on. Copy patterns into your own codebase.
- **Not a production system.** No production deployment, no SLA, no PII handling beyond what the test environments already provide.

## Running locally

You need Docker installed.

1. Build: `./mvnw clean verify`
2. Start the apps: `docker compose -f deploy/docker-compose.yml up`
3. Open the clinician app at http://localhost:8080
4. Open the citizen app at http://localhost:8090

See [`docs/onboarding/connect-to-test.md`](docs/onboarding/connect-to-test.md) for logging in and running the integration test.
