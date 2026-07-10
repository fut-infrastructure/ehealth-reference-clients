package dk.sundhed.ehealth.referenceclients.citizen.infrastructure;

import dk.sundhed.ehealth.referenceclients.common.infrastructure.fhir.FhirServer;
import dk.sundhed.ehealth.referenceclients.common.infrastructure.fhir.BaseUrlResolver;
import dk.sundhed.ehealth.referenceclients.common.infrastructure.security.EHealthContext;
import dk.sundhed.ehealth.referenceclients.common.infrastructure.security.StaleAuthenticationException;
import org.hl7.fhir.r4.model.Patient;
import org.springframework.core.MethodParameter;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * Builds the citizen-side {@link EHealthContext} from the OIDC {@code user_id} claim, qualified
 * into a fully-qualified Patient URL via {@link BaseUrlResolver}. Citizens always carry exactly one
 * context (themselves) so there is no separate picker step.
 */
@Component
public class CitizenEHealthContextArgumentResolver implements HandlerMethodArgumentResolver {

    private static final String USER_ID_CLAIM = "user_id";

    private final BaseUrlResolver baseUrlResolver;

    public CitizenEHealthContextArgumentResolver(BaseUrlResolver baseUrlResolver) {
        this.baseUrlResolver = baseUrlResolver;
    }

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return EHealthContext.class.equals(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(
            @NonNull MethodParameter parameter,
            ModelAndViewContainer mavContainer,
            @NonNull NativeWebRequest webRequest,
            WebDataBinderFactory binderFactory) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof OAuth2AuthenticationToken token)) {
            throw new StaleAuthenticationException("Citizen request without an OAuth2 token");
        }
        OAuth2User principal = token.getPrincipal();
        String userId = principal.getAttribute(USER_ID_CLAIM);
        if (userId == null || userId.isBlank()) {
            throw new StaleAuthenticationException(
                    "OIDC token missing required '" + USER_ID_CLAIM + "' claim");
        }
        // The `user_id` claim may be a fully-qualified Patient URL or a bare logical id.
        // If it already starts with "http", pass it through unchanged; otherwise qualify it
        // via BaseUrlResolver to build the full resource URL.
        String patientId = userId.startsWith("http")
                ? userId
                : baseUrlResolver.createId(FhirServer.PATIENT, Patient.class, userId);
        return EHealthContext.empty().withPatient(patientId);
    }
}
