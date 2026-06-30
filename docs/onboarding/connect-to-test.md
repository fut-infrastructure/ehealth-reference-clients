# Connecting to a FUT test environment

This walkthrough targets `devenvcgi`, the default environment baked into the compose file. All env vars are overridable, so the same setup works against any non-production FUT environment (test, preprod, …) by supplying a different `.env`.

## Prerequisites

- Docker (Compose v2 or the `docker-compose` plugin)
- Network access to `*.devenvcgi.ehealth.sundhed.dk` (public internet; no VPN required)

JDK 21 is only required if you want to build or run the apps outside Docker.

## OAuth2 clients

`ehealth-reference-clinician-client` and `ehealth-reference-citizen-client` are pre-provisioned on all FUT non-production environments. The compose file defaults to `devenvcgi` with the known client secret; no `.env` file is needed for a first run.

If you are building your own solution you will need your own clients registered. Ask your contact at FUT. See [keycloak-clients.md](keycloak-clients.md) for what a registration request should contain.

## Step 1: configure the environment

For `devenvcgi`, skip this step. The defaults are sufficient.

To target a different environment, copy `.env.example` to `.env`, replace `<env>` with the environment name, and fill in your client IDs and secret.

## Step 2: start the apps

```
docker compose -f deploy/docker-compose.yml up
```

Docker builds both images from source on first run. Subsequent starts are faster because the Maven dependency layer is cached.

- Clinician app: http://localhost:8080
- Citizen app: http://localhost:8090

## Step 3: smoke test

**Clinician login**

1. Open http://localhost:8080 and click "Log ind".
2. Sign in with a test clinician account that has CareTeam membership. Ask your contact at the FUT platform team for test credentials; they are not published here.
3. After redirect you should see the CareTeam picker.

**Citizen login**

1. Open http://localhost:8090 and click "Log ind".
2. You are redirected to the NemLogin test system. Log in using the [MitID test tool](https://pp.mitid.dk/test-tool/frontend/#/view-identity).
3. After redirect you should see the citizen landing page.

## Test CPR

Use CPR `0501792275` (Lars Larsen) for both the clinician "Create citizen" flow and citizen NemLogin. This identity has care plans assigned and resolves via NSP `exttest` on all non-production environments.

## Running the live integration test

`CreatePatientIT` in `clinician-client` hits the live FHIR server and validates that the `$createPatient` operation returns a usable `Patient` resource. It runs under Maven Failsafe at the `verify` phase, not under Surefire.

Configuration lives in `clinician-client/src/test/resources/application.yaml`. All values can be overridden via `EHEALTH_TEST_*` env vars:

| Env var                         | Purpose                                                 |
|---------------------------------|---------------------------------------------------------|
| `EHEALTH_TEST_PATIENT_BASE_URL` | Patient FHIR server base URL                            |
| `EHEALTH_TEST_TOKEN_URL`        | Keycloak token endpoint                                 |
| `EHEALTH_TEST_CLIENT_ID`        | Keycloak client with ROPC enabled                       |
| `EHEALTH_TEST_SCOPE`            | Token scope (default: `openid practitioner`)            |
| `EHEALTH_TEST_CPR`              | CPR to use for `$createPatient` (default: `0501792275`) |
| `EHEALTH_TEST_USERNAME`         | Test user (ROPC)                                        |
| `EHEALTH_TEST_PASSWORD`         | Test user password (ROPC)                               |
| `EHEALTH_TEST_BEARER_TOKEN`     | Pre-obtained token; skips ROPC entirely                 |

Supply credentials via env vars before running:

```
EHEALTH_TEST_USERNAME=... EHEALTH_TEST_PASSWORD=... ./mvnw -pl clinician-client -am verify
```

Or use a pre-obtained bearer token:

```
EHEALTH_TEST_BEARER_TOKEN=<token> ./mvnw -pl clinician-client -am verify
```

## Deploying to Kubernetes

Helm charts are not included in this repository. The images are published at `ghcr.io/fut-infrastructure/clinician-client` and `ghcr.io/fut-infrastructure/citizen-client`.

In-memory sessions log users out on restart. For production deployments, use a persistent session store.
