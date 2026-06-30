package dk.sundhed.ehealth.referenceclients.clinician.infrastructure;

import dk.sundhed.ehealth.referenceclients.clinician.app.SelectContextController;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.PathMatcher;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.List;

/**
 * Redirects authenticated requests to {@code /select-context} when no care team has been picked
 * yet, so controllers that take an {@code EHealthContext} parameter can treat it as non-null.
 *
 * <p>Allowed without a selection: {@code /}, {@code /select-context}, {@code /logout},
 * {@code /error}, static assets, actuator, and the Spring Security OAuth2 endpoints.
 */
public class SelectedContextInterceptor implements HandlerInterceptor {

    private static final List<String> ALLOWED_PATTERNS =
            List.of(
                    "/",
                    "/select-context",
                    "/logout",
                    "/error",
                    "/css/**",
                    "/fonts/**",
                    "/webjars/**",
                    "/actuator/**",
                    "/login/**",
                    "/oauth2/**");

    private final PathMatcher pathMatcher = new AntPathMatcher();

    @Override
    public boolean preHandle(
            HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        if (isAllowed(request.getRequestURI(), request.getContextPath())) {
            return true;
        }
        if (!(SecurityContextHolder.getContext().getAuthentication()
                instanceof OAuth2AuthenticationToken)) {
            return true;
        }
        HttpSession session = request.getSession(false);
        if (session != null
                && session.getAttribute(SelectContextController.SELECTED_CONTEXT_ATTRIBUTE)
                        != null) {
            return true;
        }
        response.sendRedirect(request.getContextPath() + "/select-context");
        return false;
    }

    private boolean isAllowed(String requestUri, String contextPath) {
        String path =
                contextPath == null || contextPath.isEmpty()
                        ? requestUri
                        : requestUri.substring(contextPath.length());
        for (String pattern : ALLOWED_PATTERNS) {
            if (pathMatcher.match(pattern, path)) {
                return true;
            }
        }
        return false;
    }
}
