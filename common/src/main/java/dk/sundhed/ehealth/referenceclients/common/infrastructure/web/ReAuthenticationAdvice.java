package dk.sundhed.ehealth.referenceclients.common.infrastructure.web;

import dk.sundhed.ehealth.referenceclients.common.infrastructure.security.StaleAuthenticationException;
import org.springframework.security.oauth2.client.ClientAuthorizationRequiredException;
import org.springframework.security.oauth2.core.OAuth2AuthorizationException;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

/**
 * Shows a clear "your session expired" page instead of silently redirecting to {@code /logout},
 * which would just land on Spring Security's default logout confirmation page and confuse the
 * user. Logging back in still works normally via the "Log out" button in the header.
 */
@ControllerAdvice
public class ReAuthenticationAdvice {

    @ExceptionHandler({
            StaleAuthenticationException.class,
            ClientAuthorizationRequiredException.class,
            OAuth2AuthorizationException.class
    })
    public String handle(Exception exception, Model model) {
        model.addAttribute("message", exception.getMessage());
        return "error/session-expired";
    }
}
