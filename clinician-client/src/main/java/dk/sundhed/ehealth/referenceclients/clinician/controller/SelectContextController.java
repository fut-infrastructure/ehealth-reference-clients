package dk.sundhed.ehealth.referenceclients.clinician.controller;

import dk.sundhed.ehealth.referenceclients.clinician.security.LoginSuccessHandler;
import dk.sundhed.ehealth.referenceclients.common.exceptions.StaleAuthenticationException;
import dk.sundhed.ehealth.referenceclients.common.fhir.CareTeamOption;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * Care team picker. Reads the available care teams from the session and stores the chosen one
 * back on the session for later requests to use. If the session doesn't have the list at all
 * (e.g. an expired session), that's treated as a stale session rather than an empty list.
 */
@Controller
@RequestMapping("/select-context")
public class SelectContextController {

    public static final String SELECTED_CONTEXT_ATTRIBUTE = "selected-context";

    @GetMapping
    public String show(HttpSession session, Model model) {
        @SuppressWarnings("unchecked")
        List<CareTeamOption> available =
                (List<CareTeamOption>)
                        session.getAttribute(LoginSuccessHandler.AVAILABLE_CONTEXTS_ATTRIBUTE);
        if (available == null) {
            throw new StaleAuthenticationException("No available CareTeams on session");
        }
        if (session.getAttribute(SELECTED_CONTEXT_ATTRIBUTE) != null) {
            return "redirect:/";
        }
        model.addAttribute("careTeams", available);
        return "select-context";
    }

    @PostMapping
    public String select(@RequestParam("careTeamId") String careTeamId, HttpSession session) {
        @SuppressWarnings("unchecked")
        List<CareTeamOption> available =
                (List<CareTeamOption>)
                        session.getAttribute(LoginSuccessHandler.AVAILABLE_CONTEXTS_ATTRIBUTE);
        if (available == null) {
            throw new StaleAuthenticationException("No available CareTeams on session");
        }
        CareTeamOption chosen =
                available.stream()
                        .filter(careTeam -> careTeamId.equals(careTeam.careTeamId()))
                        .findFirst()
                        .orElse(null);
        if (chosen == null) {
            return "redirect:/select-context";
        }
        session.setAttribute(SELECTED_CONTEXT_ATTRIBUTE, chosen);
        return "redirect:/";
    }
}
