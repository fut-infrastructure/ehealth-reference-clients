package dk.sundhed.ehealth.referenceclients.common.infrastructure.security;

/**
 * Thrown when a request arrives with a valid {@code JSESSIONID} cookie but the in-memory session
 * state required to serve the request is missing (typically after an app restart). Caught by {@code
 * ReAuthenticationAdvice} which routes the user through {@code /logout} for a fresh OIDC login. See
 * ADR 0003.
 */
public class StaleAuthenticationException extends RuntimeException {

    public StaleAuthenticationException(String message) {
        super(message);
    }

    public StaleAuthenticationException(String message, Throwable cause) {
        super(message, cause);
    }
}
