package dk.sundhed.ehealth.referenceclients.clinician.infrastructure;

import dk.sundhed.ehealth.referenceclients.common.infrastructure.connect.AvailableContexts;
import dk.sundhed.ehealth.referenceclients.common.infrastructure.connect.CareTeamOption;
import dk.sundhed.ehealth.referenceclients.common.infrastructure.connect.EHealthContextOptionsClient;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Runs after a successful OAuth2 login. Calls {@link EHealthContextOptionsClient#contexts(String)}
 * with the freshly issued access token and stores the available care teams on the HTTP session
 * under {@value #AVAILABLE_CONTEXTS_ATTRIBUTE}. On failure of the contexts call, invalidates the
 * session and redirects to {@code /}.
 */
@Component
public class LoginSuccessHandler implements AuthenticationSuccessHandler {

    public static final String AVAILABLE_CONTEXTS_ATTRIBUTE = "availableContexts";

    private static final Logger log = LoggerFactory.getLogger(LoginSuccessHandler.class);

    private final OAuth2AuthorizedClientService authorizedClientService;
    private final EHealthContextOptionsClient contextOptionsClient;
    private final String clientRegistrationId;
    private final SavedRequestAwareAuthenticationSuccessHandler delegate =
            new SavedRequestAwareAuthenticationSuccessHandler();

    public LoginSuccessHandler(
            OAuth2AuthorizedClientService authorizedClientService,
            EHealthContextOptionsClient contextOptionsClient,
            @Value("${ehealth.client-registration-id:clinician}") String clientRegistrationId) {
        this.authorizedClientService = authorizedClientService;
        this.contextOptionsClient = contextOptionsClient;
        this.clientRegistrationId = clientRegistrationId;
        this.delegate.setDefaultTargetUrl("/select-context");
    }

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request, HttpServletResponse response, Authentication authentication)
            throws IOException, ServletException {
        OAuth2AuthorizedClient client =
                authorizedClientService.loadAuthorizedClient(
                        clientRegistrationId, authentication.getName());
        if (client == null) {
            log.warn(
                    "No authorized client for registration {} after successful login",
                    clientRegistrationId);
            invalidateAndRedirect(request, response);
            return;
        }
        try {
            String tokenValue = client.getAccessToken().getTokenValue();
            AvailableContexts contexts =
                    contextOptionsClient.contexts("Bearer " + tokenValue);
            java.util.List<CareTeamOption> careTeams = contexts.careTeamsOrEmpty();
            HttpSession session = request.getSession();
            session.setAttribute(AVAILABLE_CONTEXTS_ATTRIBUTE, new java.util.ArrayList<>(careTeams));
            log.debug("Stored {} available care teams on session", careTeams.size());
        } catch (RuntimeException contextsFetchException) {
            log.warn("Failed to fetch contexts after login", contextsFetchException);
            invalidateAndRedirect(request, response);
            return;
        }
        delegate.onAuthenticationSuccess(request, response, authentication);
    }

    private void invalidateAndRedirect(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        response.sendRedirect(request.getContextPath() + "/");
    }
}
