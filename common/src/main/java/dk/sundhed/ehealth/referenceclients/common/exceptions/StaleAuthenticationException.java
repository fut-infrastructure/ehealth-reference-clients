package dk.sundhed.ehealth.referenceclients.common.exceptions;

/**
 * Thrown when a request arrives with a valid {@code JSESSIONID} cookie but the in-memory session
 * state required to serve the request is missing (typically after an app restart). Caught by
 * {@code ReAuthenticationAdvice}, which shows a session-expired page prompting a fresh login.
 */
public class StaleAuthenticationException extends RuntimeException {

    public StaleAuthenticationException(String message) {
        super(message);
    }

    public StaleAuthenticationException(String message, Throwable cause) {
        super(message, cause);
    }
}
