package dk.sundhed.ehealth.referenceclients.common.infrastructure.web;

import dk.sundhed.ehealth.referenceclients.common.infrastructure.security.StaleAuthenticationException;
import org.springframework.security.oauth2.client.ClientAuthorizationRequiredException;
import org.springframework.security.oauth2.core.OAuth2AuthorizationException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

/**
 * Catches stale-session and OAuth2 re-auth signals and routes the user through {@code /logout}.
 *
 * <p>The OIDC client-initiated logout drives a fresh login and lands the user back at {@code
 * baseUrl}.
 */
@ControllerAdvice
public class ReAuthenticationAdvice {

    @ExceptionHandler({
            StaleAuthenticationException.class,
            ClientAuthorizationRequiredException.class,
            OAuth2AuthorizationException.class
    })
    public String handle(Exception exception) {
        return "redirect:/logout";
    }
}
