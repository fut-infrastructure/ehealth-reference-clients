# clinician-client

Clinician-facing web app. Authenticates via the FUT `ehealth` Keycloak realm.

## Features

**Login and CareTeam selection**
The clinician logs in via OAuth2 and is prompted to select a CareTeam profile before
proceeding. The selected team is held in the session and scoped into every subsequent FHIR
access token via the FUT refresh-token extension.

**Create citizen**
The clinician enters a CPR number. The app calls the `$createPatient` operation on fut-patient,
which looks up the citizen in the national CPR registry and creates or refreshes the Patient
record in one round-trip.

**Episodes of care**
Lists the CareTeam's planned and active episodes, grouped by patient. Each episode has a detail
view showing its diagnosis, managing organisation, care team, and attached care plans.

**Create episode of care**
The clinician picks a diagnosis from a terminology-backed list. The app calls
`$create-episode-of-care` on fut-careplan with a transaction bundle containing the Condition
and EpisodeOfCare entries.

**Care plans**
Care plan detail view showing the plan's activities. New plans are created by selecting a
published PlanDefinition and calling `$apply`.

**Activate and close**
Episodes and care plans can be activated and closed from their detail pages via status-update
operations on the careplan server.

## FHIR servers used

- `fut-patient` (patient lookup and creation)
- `fut-careplan` (episodes of care and care plans)
- `fut-plan` (PlanDefinitions for care plan creation)
- `fut-organization` (organisation hierarchy, used to resolve CareTeam context)
- `fut-terminology` (diagnosis code lookup)
