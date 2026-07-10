package dk.sundhed.ehealth.referenceclients.clinician.app;

import dk.sundhed.ehealth.referenceclients.clinician.infrastructure.LoginSuccessHandler;
import dk.sundhed.ehealth.referenceclients.common.infrastructure.connect.CareTeamOption;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * Care team picker. The list is read from session attribute
 * {@value LoginSuccessHandler#AVAILABLE_CONTEXTS_ATTRIBUTE}; the chosen entry is written to
 * {@value #SELECTED_CONTEXT_ATTRIBUTE}. Empty list or stale session bounces the user through
 * {@code /logout} per ADR 0003.
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
        if (available == null || available.isEmpty()) {
            return "redirect:/logout";
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
            return "redirect:/logout";
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
