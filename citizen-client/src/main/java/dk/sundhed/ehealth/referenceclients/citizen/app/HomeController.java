package dk.sundhed.ehealth.referenceclients.citizen.app;

import dk.sundhed.ehealth.referenceclients.citizen.api.CitizenCarePlanAPI;
import dk.sundhed.ehealth.referenceclients.citizen.api.ProcedureBundle;
import dk.sundhed.ehealth.referenceclients.common.infrastructure.fhir.FhirServer;
import dk.sundhed.ehealth.referenceclients.common.infrastructure.fhir.BaseUrlResolver;
import dk.sundhed.ehealth.referenceclients.common.infrastructure.security.EHealthContext;
import org.hl7.fhir.r4.model.Patient;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.DayOfWeek;
import java.time.LocalDate;

/**
 * Citizen landing page.
 *
 * <p>Unauthenticated requests get the login card. Authenticated requests get a Monday-anchored
 * {@link WeekView} built from {@code $get-patient-procedures} for the {@code [Monday, Sunday]}
 * window. Defaults to the current week; navigable via the optional {@code ?week=YYYY-MM-DD} query
 * parameter.
 *
 * <p>The {@link EHealthContext} is built inline (mirroring
 * {@code CitizenEHealthContextArgumentResolver}) rather than via the resolver because the same
 * route serves anonymous visitors and the resolver throws on missing auth.
 */
@Controller
public class HomeController {

    private static final String USER_ID_CLAIM = "user_id";

    private final CitizenCarePlanAPI carePlanAPI;
    private final WeeklyActivitiesMapper weeklyActivitiesMapper;
    private final BaseUrlResolver baseUrlResolver;

    public HomeController(
            CitizenCarePlanAPI carePlanAPI,
            WeeklyActivitiesMapper weeklyActivitiesMapper,
            BaseUrlResolver baseUrlResolver) {
        this.carePlanAPI = carePlanAPI;
        this.weeklyActivitiesMapper = weeklyActivitiesMapper;
        this.baseUrlResolver = baseUrlResolver;
    }

    @GetMapping("/")
    public String home(
            @RequestParam(name = "week", required = false) String weekParam, Model model) {
        EHealthContext context = currentCitizenContext();
        if (context == null) {
            return "home";
        }
        LocalDate weekStart = parseWeekStart(weekParam);
        LocalDate weekEnd = weekStart.plusDays(6);
        ProcedureBundle bundle = carePlanAPI.getPatientProcedures(weekStart, weekEnd, context);
        WeekView week = weeklyActivitiesMapper.map(weekStart, bundle);
        model.addAttribute("week", week);
        model.addAttribute("today", LocalDate.now());
        return "home";
    }

    private EHealthContext currentCitizenContext() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (!(auth instanceof OAuth2AuthenticationToken token)) {
            return null;
        }
        OAuth2User principal = token.getPrincipal();
        String userId = principal.getAttribute(USER_ID_CLAIM);
        if (userId == null || userId.isBlank()) {
            return null;
        }
        // The FUT nemlogin realm emits a fully-qualified Patient URL; BaseUrlResolver would
        // double-prefix it. Pass through when already qualified.
        String patientId = userId.startsWith("http")
                ? userId
                : baseUrlResolver.createId(FhirServer.PATIENT, Patient.class, userId);
        return EHealthContext.empty().withPatient(patientId);
    }

    private static LocalDate parseWeekStart(String weekParam) {
        LocalDate base = (weekParam == null || weekParam.isBlank())
                ? LocalDate.now()
                : LocalDate.parse(weekParam);
        return base.with(DayOfWeek.MONDAY);
    }
}
