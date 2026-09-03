# citizen-client

Citizen-facing web app. Authenticates via the FUT `nemlogin` Keycloak realm (NemLogin).

## Features

**Weekly activity view**
The home page shows a week grid of the citizen's scheduled activities, derived from the
careplan server's `$get-patient-procedures` operation. The citizen can navigate between weeks
and submit a measurement directly from an activity card.

**Task list**
Lists tasks assigned to the citizen across their active episodes (e.g. a clinician asking for a
measurement outside the regular schedule). Each open task can be marked complete inline.

**Citizen profile**
The profile page shows the citizen's own Patient record (name, CPR, birth date, address) along
with their active episodes of care and care plans.

## FHIR servers used

- `fut-patient` (citizen's own Patient record)
- `fut-careplan` (episodes of care, care plans, and the `$get-patient-procedures` operation)
- `fut-measurement` (submitting measurements)
- `fut-task` (the citizen's task list)
