# clinician-client

Clinician-facing web app. Authenticates via the FUT `ehealth` Keycloak realm.

## Features

| Feature                           | Description                                                                                                                                                                                             | Doc                                                                                         |
|-----------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------|
| Login + CareTeam selection        | The clinician logs in via OAuth2 and picks a CareTeam profile. The selected team is held in the session and scoped into every subsequent FHIR access token via the FUT refresh-token extension.         | [authentication.md](../docs/authentication.md), [select-context.md](docs/select-context.md) |
| Search citizens                   | The home page lists citizens with a recent care plan on the CareTeam, and can search fut-patient directly by name or CPR for anyone outside that recent window.                                         | [search-citizens.md](docs/search-citizens.md)                                               |
| Open or create citizen            | The clinician enters a CPR number. The app calls `$createPatient` on fut-patient, which looks up the citizen in the national CPR registry and either returns the existing citizen or creates a new one. | [create-citizen.md](docs/create-citizen.md)                                                 |
| Episodes of care (list + detail)  | Lists the CareTeam's planned and active episodes, grouped by patient. Each episode has a detail view showing its diagnosis, managing organisation, care team, and attached care plans.                  | [episodes.md](docs/episodes.md)                                                             |
| Create episode of care            | The clinician picks a diagnosis from a terminology-backed list. The app calls `$create-episode-of-care` on fut-careplan with a transaction bundle containing the Condition and EpisodeOfCare entries.   | [create-episode.md](docs/create-episode.md)                                                 |
| Care plan detail + create         | Care plan detail view showing the plan's activities. New plans are created by selecting a published PlanDefinition and calling `$apply`.                                                                | [care-plans.md](docs/care-plans.md)                                                         |
| Assign a task to the citizen      | From an active care plan, the clinician can create a task asking the citizen to submit a measurement, which then shows up on the citizen's task list.                                                   | [assign-task.md](docs/assign-task.md)                                                       |
| Activate / close plans + episodes | Episodes and care plans can be activated and closed from their detail pages via status-update operations on the careplan server.                                                                        | [activate-close.md](docs/activate-close.md)                                                 |
| Measurement list                  | Full measurement history for one episode of care, with a free-text search across date, measurement name, and value.                                                                                     | [measurements.md](docs/measurements.md)                                                     |
| Task list + status update         | The clinician's view of tasks linked to one episode of care, joined to the measurement each task is about, with inline status updates.                                                                  | [tasks.md](docs/tasks.md)                                                                   |

## FHIR servers used

- `fut-patient` (patient lookup, search, and creation)
- `fut-careplan` (episodes of care and care plans)
- `fut-plan` (PlanDefinitions for care plan creation)
- `fut-organization` (organisation hierarchy, used to resolve CareTeam context)
- `fut-terminology` (diagnosis code lookup)
- `fut-task` (assigning tasks to citizens)
