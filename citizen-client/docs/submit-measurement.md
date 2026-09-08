# Submit a measurement

A citizen can submit a reading (e.g. weight, blood pressure) for a scheduled activity from the
weekly view, or for an unscheduled activity from the "Without time" section on the home page.

## User flow

- Citizen clicks "Submit" (scheduled) on an activity card.
- `GET /measurements/new?serviceRequest=...` reads the underlying `ServiceRequest` and its
  `ActivityDefinition` to pre-fill the measurement code and label, and renders the submission form.
- If the resolved code isn't in the `observation-codes` value set (see `ObservationCodes`), the page
  shows a not-yet-supported notice instead of the value/unit inputs, with no submission action - this
  covers both a plain non-measurable exercise like "do 10 pushups" and a questionnaire activity (code
  `273586006`/"Master Questionnaire" per the FUT IG's
  `activitydefinition-code-to-measurement-resource-type` ConceptMap, which expects a
  `QuestionnaireResponse` this client doesn't yet build).
- Citizen enters a value (and optional free-text unit) and submits, or goes back for a
  non-measurable activity - there is no completion path for those yet.
- `POST /measurements/new` builds an `Observation`, wraps it in a transaction `Bundle`, and submits
  it to the measurement server.
- Citizen is redirected to the home page.

## Key files

- `citizen-client/src/main/java/.../citizen/controller/SubmitMeasurementController.java`: `GET
  /measurements/new` resolves the `ServiceRequest`/`ActivityDefinition` into a form view; `POST
  /measurements/new` builds the `Observation` and submits it
- `citizen-client/src/main/java/.../citizen/fhir/CitizenMeasurementAPI.java`:
  `readServiceRequest(url, context)` (careplan server), `readActivityDefinition(url, context)`
  (plan server), `submitMeasurement(bundle, context)` (measurement server)
- `citizen-client/src/main/java/.../citizen/view/SubmitMeasurementFormView.java`: the form-backing
  record (code, patient/episode refs, timing info, the citizen's typed value and unit, and
  `measurable`)
- `citizen-client/src/main/java/.../citizen/models/ObservationCodes.java`: static mirror of the
  `observation-codes` value set, used to decide `measurable`
- `citizen-client/src/main/resources/templates/submit-measurement.html`: the submission form, or a
  not-yet-supported notice when the activity isn't measurable

## Sequence

```mermaid
sequenceDiagram
    participant Browser
    participant SubmitMeasurementController
    participant CitizenMeasurementAPI
    participant careplan as fut-careplan
    participant plan as fut-plan
    participant measurement as fut-measurement
    Browser ->> SubmitMeasurementController: GET /measurements/new?serviceRequest=...
    SubmitMeasurementController ->> CitizenMeasurementAPI: readServiceRequest(url, context)
    CitizenMeasurementAPI ->> careplan: GET ServiceRequest/{id}
    careplan -->> CitizenMeasurementAPI: ServiceRequest
    CitizenMeasurementAPI -->> SubmitMeasurementController: ServiceRequest
    SubmitMeasurementController ->> CitizenMeasurementAPI: readActivityDefinition(url, context)
    CitizenMeasurementAPI ->> plan: GET ActivityDefinition/{id}
    plan -->> CitizenMeasurementAPI: ActivityDefinition
    CitizenMeasurementAPI -->> SubmitMeasurementController: ActivityDefinition
    SubmitMeasurementController -->> Browser: submission form, pre-filled
    Browser ->> SubmitMeasurementController: POST /measurements/new (value, unit)
    SubmitMeasurementController ->> CitizenMeasurementAPI: submitMeasurement(bundle, context)
    CitizenMeasurementAPI ->> measurement: POST /$submit-measurement
    measurement -->> CitizenMeasurementAPI: 200 OK
    CitizenMeasurementAPI -->> SubmitMeasurementController: 200 OK
    SubmitMeasurementController -->> Browser: redirect to /
```

## FHIR operations

- `GET ServiceRequest/{id}` on `FhirServer.CARE_PLAN`: reads the activity being measured
- `GET ActivityDefinition/{id}` on `FhirServer.PLAN`: reads the measurement code/label the
  ServiceRequest was instantiated from
- `POST /$submit-measurement` on `FhirServer.MEASUREMENT`: body is `Parameters` wrapping a
  transaction `Bundle` containing one `ehealth-observation`

## Notes

- The `ServiceRequest` is the activity the citizen tapped "submit" on. It's read first because it
  carries what everything else needs: its own id/version (used later by the
  `ehealth-resolved-timing` extension), the `subject` reference put on the `Observation`, the
  episode-of-care extension, and a fallback `code`. It also carries `instantiatesCanonical`, which
  points at the `ActivityDefinition` - that's why the `ActivityDefinition` read only happens once
  the `ServiceRequest` comes back: the client needs it to know what to fetch next.
- `Observation.code` is taken from the `ActivityDefinition` when one was resolved, not from the
  `ServiceRequest`, because the `ActivityDefinition.code` is the one bound to the
  `observation-codes` ValueSet for measurement activities. The `ServiceRequest.code` is used only
  as a fallback when no `ActivityDefinition` reference resolves.
- The `ehealth-resolved-timing` extension links the submission back to the specific slot it
  fulfils, carrying the `ServiceRequest`'s version id at the time `$get-patient-procedures`
  resolved the slot (preferred) or at form-load time (fallback). A `Resolved` timing type requires
  both a start and end; an open-ended slot collapses to a point by reusing the start as the end.
- The citizen types a free-text unit rather than picking a coded UCUM unit. A production client
  would carry a coded `Quantity` (system + code), typically taken from the `ActivityDefinition`;
  the `ehealth-observation` profile permits either.
- A blank or non-numeric value is silently left unset on the `Observation`; the server rejects the
  submission rather than the client validating it client-side.
- A questionnaire activity is submittable in principle: the `ehealth-questionnaireresponse` profile
  submits the same way as an Observation via `$submit-measurement`, it just isn't built here yet.
- `ObservationCodes` is a static snapshot of the `observation-codes` value set (IG version
  `2020-03-10T13:13:59`, mirrored from `ValueSet-ehealth-observation-codes.json`). If the platform's
  value set changes, this list goes stale silently - there's no live `$validate-code` call backing it.
