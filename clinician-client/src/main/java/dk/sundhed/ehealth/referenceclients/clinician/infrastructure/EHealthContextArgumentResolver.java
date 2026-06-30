package dk.sundhed.ehealth.referenceclients.clinician.infrastructure;

import dk.sundhed.ehealth.referenceclients.clinician.app.SelectContextController;
import dk.sundhed.ehealth.referenceclients.common.infrastructure.connect.CareTeamOption;
import dk.sundhed.ehealth.referenceclients.common.infrastructure.security.EHealthContext;
import dk.sundhed.ehealth.referenceclients.common.infrastructure.security.StaleAuthenticationException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.core.MethodParameter;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * Injects an immutable {@link EHealthContext} into controller methods that declare it as a
 * parameter. The selected {@link CareTeamOption} is read from session attribute
 * {@value SelectContextController#SELECTED_CONTEXT_ATTRIBUTE}. {@code ehealth-connect} already
 * returns fully-qualified FHIR URLs for the care team id and the affiliation organization id, so
 * they are passed straight through to the downstream FHIR layer.
 */
@Component
public class EHealthContextArgumentResolver implements HandlerMethodArgumentResolver {

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
        HttpServletRequest request = webRequest.getNativeRequest(HttpServletRequest.class);
        HttpSession session = request == null ? null : request.getSession(false);
        Object selected =
                session == null
                        ? null
                        : session.getAttribute(SelectContextController.SELECTED_CONTEXT_ATTRIBUTE);
        if (!(selected instanceof CareTeamOption careTeam)) {
            throw new StaleAuthenticationException("No selected eHealth context on session");
        }
        EHealthContext context = EHealthContext.empty();
        if (careTeam.id() != null) {
            context = context.withCareTeam(careTeam.id());
        }
        if (careTeam.affiliation() != null && careTeam.affiliation().id() != null) {
            context = context.withOrganization(careTeam.affiliation().id());
        }
        return context;
    }
}
