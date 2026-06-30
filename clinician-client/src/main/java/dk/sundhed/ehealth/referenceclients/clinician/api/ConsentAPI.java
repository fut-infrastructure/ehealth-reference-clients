package dk.sundhed.ehealth.referenceclients.clinician.api;

import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.gclient.ReferenceClientParam;
import dk.sundhed.ehealth.referenceclients.common.infrastructure.fhir.FhirClientFactory;
import dk.sundhed.ehealth.referenceclients.common.infrastructure.fhir.FhirServer;
import dk.sundhed.ehealth.referenceclients.common.infrastructure.security.EHealthContext;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Consent;
import org.hl7.fhir.r4.model.Reference;
import org.springframework.stereotype.Component;

import java.util.Date;

/**
 * Raw FHIR wrapper around {@code Consent} operations on {@code fut-careplan}.
 *
 * <p>The only consent this client deals with is the enrollment consent (category {@code PITEOC},
 * "Participate in Telemedical EpisodeOfCare"). The careplan server refuses to move an
 * {@code EpisodeOfCare} to {@code active} until such a consent exists for it (HTTP 422
 * {@code EPISODEOFCARE_PATCH_NO_CONSENT}: "No valid Episode Of Care related consent"). The resource
 * shape mirrors the consents the server itself attaches to enrolled episodes: scope
 * {@code patient-privacy}, the patient and managing organization as performers, the episode URL as
 * the policy rule, and the patient as the {@code PRIMAUTH} provision actor over the episode.
 */
@Component
public class ConsentAPI {

    private static final String CONSENT_PROFILE =
            "http://ehealth.sundhed.dk/fhir/StructureDefinition/ehealth-consent";
    private static final String CONSENT_CATEGORY_SYSTEM =
            "http://ehealth.sundhed.dk/cs/consent-category";
    private static final String PITEOC = "PITEOC";
    private static final ReferenceClientParam DATA = new ReferenceClientParam("data");

    private final FhirClientFactory fhirClientFactory;

    public ConsentAPI(FhirClientFactory fhirClientFactory) {
        this.fhirClientFactory = fhirClientFactory;
    }

    /**
     * Ensures a {@code PITEOC} enrollment {@link Consent} exists for the given episode, creating one
     * if absent. Idempotent so re-activating an episode does not pile up duplicate consents.
     *
     * @param episodeQualifiedId    fully-qualified EpisodeOfCare URL the consent is in force for
     * @param patientReference      fully-qualified Patient URL (must match the episode's patient)
     * @param organizationReference fully-qualified managing Organization URL
     * @param context               security context, scoped to the patient and episode
     */
    public void ensureEnrollmentConsent(
            String episodeQualifiedId,
            String patientReference,
            String organizationReference,
            EHealthContext context) {
        IGenericClient client = fhirClientFactory.createClient(FhirServer.CARE_PLAN, context);

        Bundle existing = client.search()
                .forResource(Consent.class)
                .where(DATA.hasId(episodeQualifiedId))
                .returnBundle(Bundle.class)
                .execute();
        if (existing.hasEntry()) {
            return;
        }

        Consent consent = new Consent();
        consent.getMeta().addProfile(CONSENT_PROFILE);
        consent.setStatus(Consent.ConsentState.ACTIVE);
        consent.getScope().addCoding()
                .setSystem("http://terminology.hl7.org/CodeSystem/consentscope")
                .setCode("patient-privacy");
        consent.addCategory().addCoding().setSystem(CONSENT_CATEGORY_SYSTEM).setCode(PITEOC);
        consent.getPatient().setReference(patientReference);
        consent.addPerformer(new Reference(patientReference));
        consent.addPerformer(new Reference(organizationReference));
        consent.getPolicyRule().addCoding()
                .setSystem("urn:ietf:rfc:3986")
                .setCode(episodeQualifiedId);

        Consent.ProvisionComponent provision = consent.getProvision();
        provision.getPeriod().setStart(new Date());
        provision.addActor()
                .setRole(new org.hl7.fhir.r4.model.CodeableConcept().addCoding(
                        new org.hl7.fhir.r4.model.Coding()
                                .setSystem("http://terminology.hl7.org/CodeSystem/contractsignertypecodes")
                                .setCode("PRIMAUTH")))
                .setReference(new Reference(patientReference));
        provision.addData()
                .setMeaning(Consent.ConsentDataMeaning.AUTHOREDBY)
                .setReference(new Reference(episodeQualifiedId));

        client.create().resource(consent).execute();
    }
}
