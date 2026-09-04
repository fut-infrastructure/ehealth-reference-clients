package dk.sundhed.ehealth.referenceclients.citizen.controller;

import dk.sundhed.ehealth.referenceclients.citizen.fhir.CitizenMeasurementAPI;
import dk.sundhed.ehealth.referenceclients.citizen.models.ObservationCodes;
import dk.sundhed.ehealth.referenceclients.citizen.view.SubmitMeasurementFormView;
import dk.sundhed.ehealth.referenceclients.common.fhir.BaseUrlResolver;
import dk.sundhed.ehealth.referenceclients.common.fhir.FhirExtensions;
import dk.sundhed.ehealth.referenceclients.common.fhir.FhirServer;
import dk.sundhed.ehealth.referenceclients.common.security.EHealthContext;
import org.hl7.fhir.r4.model.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * Handles the citizen measurement submission flow.
 *
 * <p>GET {@code /measurements/new?serviceRequest={ref}} reads the {@code ServiceRequest} for the
 * activity, extracts code / patient / episode, and renders the submission form.
 *
 * <p>POST {@code /measurements/new} assembles the {@code ehealth-observation} with its required
 * extensions, wraps it in a Bundle, and calls {@code $submit-measurement} on the measurement
 * server. On success the citizen is redirected to the home page.
 *
 * <p>Not every activity is measurable ({@link ObservationCodes}) - e.g. a plain exercise like "do
 * 10 pushups" has no code the platform expects an Observation for. {@code showForm} flags this via
 * {@link SubmitMeasurementFormView#measurable()}; the template swaps the value/unit inputs for a
 * "Mark done" button posting to {@code /measurements/complete} instead.
 */
@Controller
@RequestMapping("/measurements")
public class SubmitMeasurementController {

    private static final String EXT_RESOLVED_TIMING =
            "http://ehealth.sundhed.dk/fhir/StructureDefinition/ehealth-resolved-timing";
    private static final String CS_RESOLVED_TIMING_TYPE =
            "http://ehealth.sundhed.dk/cs/resolved-timing-type";

    private final CitizenMeasurementAPI measurementAPI;
    private final BaseUrlResolver baseUrlResolver;

    public SubmitMeasurementController(
            CitizenMeasurementAPI measurementAPI, BaseUrlResolver baseUrlResolver) {
        this.measurementAPI = measurementAPI;
        this.baseUrlResolver = baseUrlResolver;
    }

    @GetMapping("/new")
    public String showForm(
            @RequestParam(name = "serviceRequest") String serviceRequestUrl,
            @RequestParam(name = "episodeRef", required = false) String episodeRefParam,
            @RequestParam(name = "timingType", required = false) String timingTypeParam,
            @RequestParam(name = "srVersionId", required = false) String srVersionIdParam,
            @RequestParam(name = "slotStart", required = false) String slotStartParam,
            @RequestParam(name = "slotEnd", required = false) String slotEndParam,
            EHealthContext context,
            Model model) {
        EHealthContext readContext = episodeRefParam != null && !episodeRefParam.isBlank()
                ? context.withEpisodeOfCare(episodeRefParam)
                : context;
        ServiceRequest serviceRequest = measurementAPI.readServiceRequest(serviceRequestUrl, readContext);
        ActivityDefinition activityDefinition = null;
        if (!serviceRequest.getInstantiatesCanonical().isEmpty()) {
            String adUrl = serviceRequest.getInstantiatesCanonical().getFirst().getValue();
            activityDefinition = measurementAPI.readActivityDefinition(adUrl, readContext);
        }

        model.addAttribute("form", toFormView(serviceRequest, activityDefinition, episodeRefParam, timingTypeParam, srVersionIdParam, slotStartParam, slotEndParam));

        return "submit-measurement";
    }

    /**
     * Completes a non-measurable activity directly, with no Observation. Only reachable from the
     * "Mark done" button the form renders when {@link SubmitMeasurementFormView#measurable()} is
     * false; a measurable activity always goes through {@link #submit} instead.
     */
    @PostMapping("/complete")
    public String complete(
            @RequestParam("serviceRequestRef") String serviceRequestRef,
            @RequestParam(name = "episodeRef", required = false) String episodeRef,
            EHealthContext context) {
        measurementAPI.completeServiceRequest(serviceRequestRef, episodeRef, context);
        return "redirect:/";
    }

    @PostMapping("/new")
    public String submit(
            @ModelAttribute SubmitMeasurementFormView form,
            EHealthContext context) {
        Observation obs = buildObservation(form);
        Bundle bundle = new Bundle();
        bundle.setType(Bundle.BundleType.TRANSACTION);
        bundle.addEntry()
                .setFullUrl("urn:uuid:" + UUID.randomUUID())
                .setResource(obs)
                .getRequest()
                .setMethod(Bundle.HTTPVerb.POST)
                .setUrl("Observation");

        String episodeRef = form.episodeRef() != null && !form.episodeRef().isBlank()
                ? form.episodeRef()
                : null;
        EHealthContext episodeContext = context
                .withPatient(form.patientRef())
                .withEpisodeOfCare(episodeRef);

        measurementAPI.submitMeasurement(bundle, episodeContext);

        return "redirect:/";
    }

    // --- helpers ---

    private SubmitMeasurementFormView toFormView(
            ServiceRequest serviceRequest, ActivityDefinition activityDefinition, String episodeRefFallback,
            String timingType, String srVersionId, String slotStart, String slotEnd) {
        String bareId = serviceRequest.getIdElement().getIdPart();
        String versionId = serviceRequest.getIdElement().getVersionIdPart();
        String baseRef = baseUrlResolver.resolve(FhirServer.CARE_PLAN)
                + "/ServiceRequest/" + bareId;
        String versionRef = versionId != null ? baseRef + "/_history/" + versionId : baseRef;

        String patientRef = serviceRequest.hasSubject() ? serviceRequest.getSubject().getReference() : null;
        String episodeRef = FhirExtensions.referenceValue(serviceRequest, FhirExtensions.EPISODE_OF_CARE);
        if ((episodeRef == null || episodeRef.isBlank())
                && episodeRefFallback != null && !episodeRefFallback.isBlank()) {
            episodeRef = episodeRefFallback;
        }

        // Observation.code must be from observation-codes; use ActivityDefinition.code which is
        // bound to that ValueSet for measurement activities. Fall back to ServiceRequest.code only
        // if no ActivityDefinition was resolved.
        String codeSystem = null;
        String codeCode = null;
        String codeDisplay = null;
        CodeableConcept codeSource = (activityDefinition != null && activityDefinition.hasCode()) ? activityDefinition.getCode() : serviceRequest.getCode();
        if (codeSource != null) {
            List<Coding> codings = codeSource.getCoding();
            if (!codings.isEmpty()) {
                codeSystem = codings.getFirst().getSystem();
                codeCode = codings.getFirst().getCode();
                codeDisplay = codings.getFirst().hasDisplay() ? codings.getFirst().getDisplay() : codeSource.getText();
            }
        }

        boolean measurable = ObservationCodes.isObservable(codeSystem, codeCode);

        return new SubmitMeasurementFormView(
                baseRef,
                versionRef,
                patientRef,
                episodeRef,
                codeSystem,
                codeCode,
                codeDisplay,
                timingType,
                srVersionId,
                slotStart,
                slotEnd,
                null,
                null,
                measurable);
    }

    private static Date toDate(String isoLocalDateTime) {
        LocalDateTime ldt = LocalDateTime.parse(isoLocalDateTime, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        return Date.from(ldt.atZone(ZoneId.systemDefault()).toInstant());
    }

    private static String extractVersionId(String versionRef) {
        int idx = versionRef.lastIndexOf("/_history/");
        return idx >= 0 ? versionRef.substring(idx + "/_history/".length()) : versionRef;
    }

    private static Observation buildObservation(SubmitMeasurementFormView form) {
        Observation obs = new Observation();
        obs.getMeta().addProfile(
                "http://ehealth.sundhed.dk/fhir/StructureDefinition/ehealth-observation");

        // workflow-episodeOfCare
        if (form.episodeRef() != null) {
            obs.addExtension(new Extension()
                    .setUrl(FhirExtensions.EPISODE_OF_CARE)
                    .setValue(new Reference(form.episodeRef())));
        }

        // ehealth-resolved-timing
        Extension resolvedTiming = new Extension(EXT_RESOLVED_TIMING);

        // Prefer the version ID from $get-patient-procedures (the version the server used when
        // computing slots); fall back to the version read at form-load time.
        String versionId = (form.serviceRequestVersionId() != null && !form.serviceRequestVersionId().isBlank())
                ? form.serviceRequestVersionId()
                : extractVersionId(form.serviceRequestVersionRef());
        resolvedTiming.addExtension(
                new Extension("serviceRequestVersionId", new IdType(versionId)));
        String timingCode = (form.timingType() != null && !form.timingType().isBlank())
                ? form.timingType() : "Adhoc";
        resolvedTiming.addExtension(
                new Extension("type", new CodeableConcept()
                        .addCoding(new Coding()
                                .setSystem(CS_RESOLVED_TIMING_TYPE)
                                .setCode(timingCode))));
        if ("Resolved".equals(timingCode)) {
            if (form.slotStart() != null && !form.slotStart().isBlank()) {
                resolvedTiming.addExtension(new Extension("start",
                        new DateTimeType(toDate(form.slotStart()))));
                // Resolved requires both start and end (server rejects otherwise); an open-period
                // slot (start, no end) collapses to a point, so end falls back to start.
                String slotEnd = (form.slotEnd() != null && !form.slotEnd().isBlank())
                        ? form.slotEnd() : form.slotStart();
                resolvedTiming.addExtension(new Extension("end",
                        new DateTimeType(toDate(slotEnd))));
            }
        }
        obs.addExtension(resolvedTiming);

        obs.addBasedOn(new Reference(form.serviceRequestRef()));
        obs.setStatus(Observation.ObservationStatus.FINAL);

        if (form.codeSystem() != null && form.codeCode() != null) {
            obs.setCode(new CodeableConcept()
                    .addCoding(new Coding()
                            .setSystem(form.codeSystem())
                            .setCode(form.codeCode())
                            .setDisplay(form.codeDisplay())));
        }

        if (form.patientRef() != null) {
            obs.setSubject(new Reference(form.patientRef()));
            obs.addPerformer(new Reference(form.patientRef()));
        }

        // effectivePeriod is when the measurement was actually taken (now), independent of the
        // slot it fulfils. The slot linkage lives in resolved-timing (start/end), not here. The IG
        // $submit-measurement example uses effectivePeriod, not effectiveDateTime.
        Date now = new Date();
        obs.setEffective(new Period().setStart(now).setEnd(now));

        // The citizen types a free-text unit, so we send it as Quantity.unit (display only). A
        // production client would carry a coded UCUM Quantity (system + code), typically taken from
        // the ActivityDefinition rather than free text; the ehealth-observation profile permits
        // either. A blank or non-numeric value is left unset and the server rejects the submission.
        if (form.value() != null && !form.value().isBlank()) {
            try {
                Quantity quantity = new Quantity().setValue(Double.parseDouble(form.value().trim()));
                if (form.unit() != null && !form.unit().isBlank()) {
                    quantity.setUnit(form.unit().trim());
                }
                obs.setValue(quantity);
            } catch (NumberFormatException ignored) {
                // Leave value unset; the server rejects the submission.
            }
        }

        return obs;
    }
}
