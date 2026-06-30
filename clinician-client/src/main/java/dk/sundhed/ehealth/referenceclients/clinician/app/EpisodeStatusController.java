package dk.sundhed.ehealth.referenceclients.clinician.app;

import dk.sundhed.ehealth.referenceclients.clinician.api.ConsentAPI;
import dk.sundhed.ehealth.referenceclients.clinician.api.EpisodeOfCareAPI;
import dk.sundhed.ehealth.referenceclients.common.infrastructure.fhir.FhirServer;
import dk.sundhed.ehealth.referenceclients.common.infrastructure.fhir.IdFactory;
import dk.sundhed.ehealth.referenceclients.common.infrastructure.security.EHealthContext;
import org.hl7.fhir.r4.model.EpisodeOfCare;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Status changes for an {@link EpisodeOfCare}.
 *
 * <p>Single route: {@code POST /episodes/{id}/status} with a {@code target} form param. Performed
 * as a JSON Patch {@code replace /status} via {@link EpisodeOfCareAPI#changeEpisodeStatus}.
 * Redirects to {@code /episodes/{id}} so the user lands back on the episode page. The form itself
 * lives inline in {@code episode-detail.html}.
 */
@Controller
public class EpisodeStatusController {

    private final EpisodeOfCareAPI episodeOfCareAPI;
    private final ConsentAPI consentAPI;
    private final IdFactory idFactory;

    public EpisodeStatusController(
            EpisodeOfCareAPI episodeOfCareAPI, ConsentAPI consentAPI, IdFactory idFactory) {
        this.episodeOfCareAPI = episodeOfCareAPI;
        this.consentAPI = consentAPI;
        this.idFactory = idFactory;
    }

    @PostMapping("/episodes/{id}/status")
    public String changeStatus(
            @PathVariable("id") String id,
            @RequestParam("target") String target,
            EHealthContext context) {
        String qualifiedId = idFactory.createId(FhirServer.CARE_PLAN, EpisodeOfCare.class, id);
        EpisodeOfCare.EpisodeOfCareStatus status = EpisodeOfCare.EpisodeOfCareStatus.fromCode(target);
        if (status == null) {
            throw new IllegalArgumentException("Unsupported EpisodeOfCare status: " + target);
        }
        if (status == EpisodeOfCare.EpisodeOfCareStatus.ACTIVE) {
            // careplan rejects the activation patch until a PITEOC enrollment consent exists for
            // the episode. Read the episode to learn its patient, then record the consent (scoped
            // to the patient and episode) before flipping the status.
            EpisodeOfCare episode =
                    episodeOfCareAPI.fetchEpisodeOfCareById(id, context).episodes().getFirst();
            String patientReference = episode.getPatient().getReference();
            EHealthContext consentContext =
                    context.withPatient(patientReference).withEpisodeOfCare(qualifiedId);
            consentAPI.ensureEnrollmentConsent(
                    qualifiedId, patientReference, context.organizationId(), consentContext);
        }
        episodeOfCareAPI.changeEpisodeStatus(qualifiedId, status, context);
        return "redirect:/episodes/" + id;
    }
}
