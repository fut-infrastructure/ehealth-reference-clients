# citizen-client

Citizen-facing web app. Authenticates via the FUT `nemlogin` Keycloak realm (NemLogin).

## Features

| Feature            | Description                                                                                                                                                                                                                                    | Doc                                                 |
|--------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-----------------------------------------------------|
| Login (NemLogin)   | The citizen logs in via NemLogin OAuth2 against the `nemlogin` Keycloak realm.                                                                                                                                                                 | [authentication.md](../docs/authentication.md)      |
| Weekly activities  | The home page shows a week grid of the citizen's scheduled activities, derived from the careplan server's `$get-patient-procedures` operation. The citizen can navigate between weeks and submit a measurement directly from an activity card. | [weekly-activities.md](docs/weekly-activities.md)   |
| Citizen profile    | Shows the citizen's own Patient record (name, CPR, birth date, address) along with their active episodes of care and care plans.                                                                                                               | [profile.md](docs/profile.md)                       |
| Submit measurement | Submits a measurement for a scheduled or unscheduled activity via fut-measurement's `$submit-measurement` operation.                                                                                                                           | [submit-measurement.md](docs/submit-measurement.md) |

## FHIR servers used

- `fut-patient` (citizen's own Patient record)
- `fut-careplan` (episodes of care, care plans, and the `$get-patient-procedures` operation)
- `fut-measurement` (submitting measurements)
