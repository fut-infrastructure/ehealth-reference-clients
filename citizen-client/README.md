# citizen-client

Citizen-facing web app. Authenticates via the FUT `nemlogin` Keycloak realm (NemLogin).

## Features

**Weekly activity view**
The home page shows a week grid of the citizen's scheduled activities, derived from the
careplan server's `$get-patient-procedures` operation. The citizen can navigate between weeks.
Each activity card shows the activity name, timing type, and submission progress.

**Citizen profile**
The profile page shows the citizen's own Patient record (name, CPR, birth date, address) along
with their active episodes of care and care plans.

## FHIR servers used

- `fut-patient` (citizen's own Patient record)
- `fut-careplan` (episodes of care, care plans, and the `$get-patient-procedures` operation)
