# Activate and close care plans and episodes

A clinician can activate a draft care plan, which flips it and all its activities to active in one step, and can change the status of both care plans and episodes of care through their allowed transitions.

## User flow

**Care plan:**
- On the care-plan detail page, clinician clicks "Activate" when the plan is in `draft` status.
- The plan and all its activities move to active atomically via a transaction bundle.
- A status dropdown on the same page allows further transitions (`active`, `on-hold`, `completed`, `revoked`, `entered-in-error`).

**Episode of care:**
- On the episode detail page, an inline `<select>` lists the valid status transitions (`planned`, `waitlist`, `active`, `onhold`, `finished`, `cancelled`), minus the current status.
- Submitting the form posts to `POST /episodes/{id}/status`.
- Moving to `active` needs one extra step first: the app checks whether a PITEOC enrollment consent exists for the episode, and creates one if it doesn't, before it patches the status.

## Sequence

Activating a care plan reads the ActivityDefinitions its ServiceRequests instantiate, so a
recurring one keeps its schedule (see Notes):

```mermaid
sequenceDiagram
    participant Browser
    participant CarePlansController
    participant CarePlanAPI
    participant careplan as fut-careplan
    participant plan as fut-plan
    Browser ->> CarePlansController: POST /episodes/{eoc}/care-plans/{id}/activate
    CarePlansController ->> CarePlanAPI: activateCarePlan(id, eoc, context)
    CarePlanAPI ->> careplan: GET CarePlan?_id=&_include=CarePlan:activity-reference
    careplan -->> CarePlanAPI: CarePlan + ServiceRequests
    CarePlanAPI ->> plan: GET ActivityDefinition?_id=...
    plan -->> CarePlanAPI: ActivityDefinitions, by canonical URL
    CarePlanAPI ->> careplan: POST CarePlan transaction (PUT CarePlan + each activity, active)
    careplan -->> CarePlanAPI: 200 OK
    CarePlanAPI -->> CarePlansController: done
    CarePlansController -->> Browser: Redirect to /episodes/{eoc}/care-plans/{id}
```

Moving an episode to `active` is the more involved of the two remaining transitions, since it needs
a consent check first:

```mermaid
sequenceDiagram
    participant Browser
    participant EpisodeStatusController
    participant EpisodeOfCareAPI
    participant ConsentAPI
    participant careplan as fut-careplan
    Browser ->> EpisodeStatusController: POST /episodes/{id}/status (target=active)
    EpisodeStatusController ->> careplan: GET EpisodeOfCare/{id}
    careplan -->> EpisodeStatusController: EpisodeOfCare (to read its patient)
    EpisodeStatusController ->> ConsentAPI: ensureEnrollmentConsent(episode, patient, org, context)
    ConsentAPI ->> careplan: GET Consent?data={episodeId}
    careplan -->> ConsentAPI: existing Consent, or none
    opt no consent found
        ConsentAPI ->> careplan: POST Consent
        careplan -->> ConsentAPI: Consent created
    end
    ConsentAPI -->> EpisodeStatusController: done
    EpisodeStatusController ->> EpisodeOfCareAPI: changeEpisodeStatus(id, active, context)
    EpisodeOfCareAPI ->> careplan: PATCH EpisodeOfCare/{id} (replace /status)
    careplan -->> EpisodeOfCareAPI: 200 OK
    EpisodeOfCareAPI -->> EpisodeStatusController: done
    EpisodeStatusController -->> Browser: Redirect to /episodes/{id}
```

## Key files

- `clinician-client/src/main/java/.../clinician/controller/CarePlansController.java`: `POST /episodes/{eoc}/care-plans/{id}/activate` and `POST /episodes/{eoc}/care-plans/{id}/status`
- `clinician-client/src/main/java/.../clinician/controller/EpisodeStatusController.java`: `POST /episodes/{id}/status`; calls `ConsentAPI.ensureEnrollmentConsent` before activating
- `clinician-client/src/main/java/.../clinician/fhir/CarePlanAPI.java`: `activateCarePlan(id, eoc, context)` (fetch the plan, its ServiceRequests, and the ActivityDefinitions they instantiate; build transaction; execute); `changeCarePlanStatus(id, eoc, target, context)` (read → mutate status → PUT)
- `clinician-client/src/main/java/.../clinician/fhir/EpisodeOfCareAPI.java`: `changeEpisodeStatus(id, target, context)` issues a JSON Patch `replace /status`
- `clinician-client/src/main/java/.../clinician/fhir/ConsentAPI.java`: `ensureEnrollmentConsent(...)` checks for an existing `PITEOC` consent and creates one if absent
- `clinician-client/src/main/java/.../clinician/fhir/CarePlanActivationUtil.java`: `buildActivationBundle(carePlan, serviceRequests, activityDefinitionsByCanonicalUrl)` builds the transaction bundle that flips the plan and each activity to its type-specific active state; for a `ServiceRequest`, copies a recurring `Timing` from its `ActivityDefinition` when one exists (see Notes), rather than always defaulting to a one-shot slot
- `common/src/main/java/.../common/fhir/JsonPatch.java`: RFC 6902 JSON Patch builder used by `changeEpisodeStatus`
- `clinician-client/src/main/resources/templates/careplan-detail.html`: Activate button (shown when `canActivate`) and status dropdown (shown when `allowedTransitions` is non-empty)
- `clinician-client/src/main/resources/templates/episode-detail.html`: inline episode status form

## FHIR operations

- `GET ActivityDefinition?_id=...` on `FhirServer.PLAN`: bulk-reads the ActivityDefinitions the
  care plan's ServiceRequests instantiate, ahead of the activation transaction
- `POST /CarePlan` transaction on `FhirServer.CARE_PLAN` (episode-scoped token): activation bundle with PUT entries for the CarePlan (`active`) and each activity (Task → `READY`, Appointment → `BOOKED`, ServiceRequest → `ACTIVE`)
- `GET CarePlan/{id}` + `PUT CarePlan/{id}` on `FhirServer.CARE_PLAN`: status change
- `PATCH /EpisodeOfCare/{id}` on `FhirServer.CARE_PLAN` (episode-scoped token): JSON Patch `replace /status`
- `GET Consent?data={episodeId}` + `POST Consent` on `FhirServer.CARE_PLAN`: consent check and creation before episode activation

## Notes

- The careplan server rejects an episode activation with HTTP 422 (`EPISODEOFCARE_PATCH_NO_CONSENT`) unless a `PITEOC` enrollment consent already exists for the episode. `ConsentAPI.ensureEnrollmentConsent` checks first and only creates a consent when none exists, so calling it twice is safe.
- All CarePlan writes, both activate and status change, need the token to carry the owning episode. Both `CarePlansController` methods set that with `context.withEpisodeOfCare(...)` before calling the API.
- The episode PATCH needs the episode in the token context too, or the server returns HTTP 403. `EpisodeOfCareAPI.changeEpisodeStatus` sets it internally with `context.withEpisodeOfCare(id)`.
- `$apply` leaves a ServiceRequest's `occurrence[x]` empty on the draft it creates,
  `GET`ing a freshly-`$apply`'d ServiceRequest shows neither `occurrencePeriod` nor
  `occurrenceTiming` set. `activateCarePlan` bulk-reads the
  ActivityDefinitions its ServiceRequests instantiate first, and `CarePlanActivationUtil` copies
  `ActivityDefinition.timingTiming` onto the ServiceRequest when present, falling back to a
  one-shot `Period` only when there's no ActivityDefinition to ask, or it doesn't specify a timing
  itself.
