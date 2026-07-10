package dk.sundhed.ehealth.referenceclients.clinician.app;

import dk.sundhed.ehealth.referenceclients.clinician.api.MeasurementAPI;
import dk.sundhed.ehealth.referenceclients.common.infrastructure.fhir.FhirServer;
import dk.sundhed.ehealth.referenceclients.common.infrastructure.fhir.IdFactory;
import dk.sundhed.ehealth.referenceclients.common.infrastructure.security.EHealthContext;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.EpisodeOfCare;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Patient;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;

/**
 * Renders the full measurement history for a single {@code EpisodeOfCare}, with a free-text search.
 *
 * <p>Each row corresponds to one {@link Observation} extracted from the inner Bundles returned by
 * {@code $search-measurements-bundle-limit}. The episode-detail page shows a recent preview; this
 * page lists everything (up to the operation's bundle limit) and filters client-side by the
 * optional {@code q} query.
 */
@Controller
@RequestMapping("/episodes")
public class MeasurementsController {

    /**
     * Effectively "all history"; the operation still caps the returned bundle count.
     */
    private static final int LOOKBACK_DAYS = 3650;
    private static final ZoneId ZONE = ZoneId.systemDefault();

    private final MeasurementAPI measurementAPI;
    private final IdFactory idFactory;

    public MeasurementsController(MeasurementAPI measurementAPI, IdFactory idFactory) {
        this.measurementAPI = measurementAPI;
        this.idFactory = idFactory;
    }

    @GetMapping("/{id}/measurements")
    public String measurements(
            @PathVariable("id") String episodeId,
            @RequestParam(value = "patient", required = false) String patientId,
            @RequestParam(value = "q", required = false) String query,
            EHealthContext context,
            Model model) {
        String qualifiedEoc = idFactory.createId(FhirServer.CARE_PLAN, EpisodeOfCare.class, episodeId);
        String qualifiedPatient = (patientId == null || patientId.isBlank())
                ? null
                : idFactory.createId(FhirServer.PATIENT, Patient.class, patientId);
        EHealthContext measurementContext = context.withPatient(qualifiedPatient);
        Date start = Date.from(
                LocalDate.now().minusDays(LOOKBACK_DAYS).atStartOfDay(ZONE).toInstant());
        Bundle outer = measurementAPI.searchMeasurements(qualifiedEoc, start, measurementContext);
        List<MeasurementView> measurements = MeasurementView.from(outer).stream()
                .filter(measurement -> measurement.matches(query))
                .toList();

        model.addAttribute("episodeId", episodeId);
        model.addAttribute("patientId", patientId);
        model.addAttribute("query", query);
        model.addAttribute("measurements", measurements);

        return "measurements";
    }
}
