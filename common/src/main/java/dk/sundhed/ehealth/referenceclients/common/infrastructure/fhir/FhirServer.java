package dk.sundhed.ehealth.referenceclients.common.infrastructure.fhir;

/**
 * The FUT FHIR servers the reference clients talk to.
 *
 * <p>Each enum constant maps to a {@code fhir.server.<name>} property containing the base URL of
 * that server. {@link BaseUrlResolver} performs the lookup. Adding a new server here requires the
 * matching property in {@code application.yaml}.
 */
public enum FhirServer {
    PATIENT,
    CARE_PLAN,
    PLAN,
    ORGANIZATION,
    TERMINOLOGY,
    MEASUREMENT,
    TASK
}
