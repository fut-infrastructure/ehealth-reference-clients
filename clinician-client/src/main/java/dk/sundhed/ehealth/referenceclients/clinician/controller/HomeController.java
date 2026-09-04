package dk.sundhed.ehealth.referenceclients.clinician.controller;

import dk.sundhed.ehealth.referenceclients.clinician.fhir.CarePlanAPI;
import dk.sundhed.ehealth.referenceclients.clinician.fhir.PatientAPI;
import dk.sundhed.ehealth.referenceclients.clinician.view.PatientPlanSummaryView;
import dk.sundhed.ehealth.referenceclients.common.fhir.CareTeamOption;
import dk.sundhed.ehealth.referenceclients.common.fhir.PatientDemographics;
import dk.sundhed.ehealth.referenceclients.common.security.EHealthContext;
import jakarta.servlet.http.HttpSession;
import org.hl7.fhir.r4.model.CarePlan;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Reference;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Landing page.
 *
 * <p>For an authenticated clinician with a selected care team, the home page shows the patients who
 * have a care plan updated within the recency window ({@link #ROSTER_WINDOW_DAYS} days) on the
 * selected team. This is the de-facto roster, since FUT has no standing patient list on the
 * CareTeam itself. The window is necessary because the team can own tens of thousands of episodes and care
 * plans; scoping to recent activity keeps the list to the handful of patients actually being worked
 * on, and surfaces a freshly-created citizen (their new plan falls inside the window). Rows link to
 * the patient view.
 *
 * <p>Anonymous users and users who have not yet picked a care team see the login / select-team cards
 * instead, so this controller reads the selected context from the session directly rather than via
 * {@link EHealthContext} injection (the argument resolver throws when none is selected).
 */
@Controller
public class HomeController {

    /**
     * Size of the recency window for the roster. The home page states this number to the user so the
     * scope of the list is never ambiguous; keep the template label in sync if this changes.
     */
    static final int ROSTER_WINDOW_DAYS = 30;

    private final CarePlanAPI carePlanAPI;
    private final PatientAPI patientAPI;

    public HomeController(CarePlanAPI carePlanAPI, PatientAPI patientAPI) {
        this.carePlanAPI = carePlanAPI;
        this.patientAPI = patientAPI;
    }

    @GetMapping("/")
    public String home(
            HttpSession session,
            Model model,
            @RequestParam(required = false) String term) {
        EHealthContext context = selectedContext(session);
        if (context != null && context.careTeamId() != null) {
            boolean searchActive = term != null && !term.isBlank();
            model.addAttribute("searchActive", searchActive);
            model.addAttribute("term", term);
            if (searchActive) {
                PatientAPI.PatientSearchResult result = patientAPI.searchPatients(term, context);
                model.addAttribute("patients", searchRows(result.patients(), context));
                model.addAttribute("truncated", result.truncated());
            } else {
                model.addAttribute("patients", roster(context));
                model.addAttribute("rosterWindowDays", ROSTER_WINDOW_DAYS);
            }
        }
        return "home";
    }

    /**
     * Builds search result rows, narrowed down to the ones on the clinician's care team via
     * {@link CarePlanAPI#filterPatientIdsOnCareTeam}, mirroring the roster's implicit scoping. There
     * is no "all citizens" mode: fut-patient's authorization narrowing confines every non-{@code _id}
     * Patient search under a care-team-scoped practitioner token to that same care-team compartment
     * regardless of client intent.
     */
    private List<PatientPlanSummaryView> searchRows(List<Patient> matches, EHealthContext context) {
        Set<String> candidateIds = matches.stream()
                .map(patient -> patient.getIdElement().getIdPart())
                .filter(id -> id != null && !id.isBlank())
                .collect(Collectors.toSet());

        Set<String> onTeam = carePlanAPI.filterPatientIdsOnCareTeam(context, candidateIds);
        List<Patient> scoped = matches.stream()
                .filter(patient -> onTeam.contains(patient.getIdElement().getIdPart()))
                .toList();

        return scoped.stream()
                .map(patient -> new PatientPlanSummaryView(
                        patient.getIdElement().getIdPart(),
                        PatientDemographics.displayName(patient),
                        PatientDemographics.cpr(patient),
                        null))
                .toList();
    }

    /**
     * Patients with at least one care plan updated in the last {@link #ROSTER_WINDOW_DAYS} days on
     * the selected care team, one row per patient with the count of their plans in the window.
     * Insertion order follows the care plans as returned by the server (ascending by id).
     */
    private List<PatientPlanSummaryView> roster(EHealthContext context) {
        LocalDate since = LocalDate.now().minusDays(ROSTER_WINDOW_DAYS);
        List<CarePlan> carePlans = carePlanAPI.findRecentCarePlansByCareTeam(context, since);

        // Distinct patients in first-seen order, with how many of their plans fall in the window.
        Map<String, Integer> planCountByPatient = new LinkedHashMap<>();
        for (CarePlan carePlan : carePlans) {
            String patientId = idPart(carePlan.getSubject());
            if (patientId != null) {
                planCountByPatient.merge(patientId, 1, Integer::sum);
            }
        }

        Map<String, Patient> patientsById =
                patientAPI.findPatientsById(planCountByPatient.keySet(), context).stream()
                        .filter(patient -> patient.getIdElement().getIdPart() != null)
                        .collect(Collectors.toMap(
                                patient -> patient.getIdElement().getIdPart(),
                                Function.identity(),
                                (existing, replacement) -> existing));

        List<PatientPlanSummaryView> rows = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : planCountByPatient.entrySet()) {
            Patient patient = patientsById.get(entry.getKey());
            rows.add(new PatientPlanSummaryView(
                    entry.getKey(),
                    patient == null ? null : PatientDemographics.displayName(patient),
                    patient == null ? null : PatientDemographics.cpr(patient),
                    entry.getValue()));
        }
        return rows;
    }

    private static EHealthContext selectedContext(HttpSession session) {
        Object selected = session == null
                ? null
                : session.getAttribute(SelectContextController.SELECTED_CONTEXT_ATTRIBUTE);
        if (!(selected instanceof CareTeamOption careTeam)) {
            return null;
        }
        return careTeam.toEHealthContext();
    }

    private static String idPart(Reference ref) {
        if (ref == null || ref.isEmpty()) {
            return null;
        }
        String idPart = ref.getReferenceElement().getIdPart();
        return idPart != null && !idPart.isBlank() ? idPart : null;
    }

}
