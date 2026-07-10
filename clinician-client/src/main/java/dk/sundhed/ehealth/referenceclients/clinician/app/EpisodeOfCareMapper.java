package dk.sundhed.ehealth.referenceclients.clinician.app;

import dk.sundhed.ehealth.referenceclients.clinician.api.EpisodeOfCareAPI;
import dk.sundhed.ehealth.referenceclients.common.infrastructure.security.EHealthContext;
import org.hl7.fhir.r4.model.*;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Translates raw {@link EpisodeOfCareAPI.SearchResult} bundles into the view records the
 * episodes-of-care templates consume.
 *
 * <p>All derivation logic lives here: grouping by patient, picking the primary diagnosis label,
 * resolving referenced {@code Patient} / {@code Condition} / {@code Organization} / {@code
 * CareTeam} resources from the bundle's contained set, and formatting dates.
 */
@Component
public class EpisodeOfCareMapper {

    private static final String CPR_SYSTEM = "urn:oid:1.2.208.176.1.2";

    /**
     * Groups episodes by their referenced patient and returns one {@link PatientEpisodesView} per
     * patient. {@code patientsById} is populated by the controller via {@link
     * dk.sundhed.ehealth.referenceclients.clinician.api.PatientAPI#findPatientsById} because
     * the fut-careplan CapabilityStatement does not allow {@code EpisodeOfCare:patient} as an
     * {@code _include}.
     */
    public List<PatientEpisodesView> toPatientEpisodesViews(
            EpisodeOfCareAPI.SearchResult result, Map<String, Patient> patientsById) {
        Map<String, Condition> conditionsById = indexById(result.conditions());
        for (EpisodeOfCare episode : result.episodes()) {
            addContainedConditions(episode, conditionsById);
        }

        Map<String, List<EpisodeOfCare>> episodesByPatientId = new LinkedHashMap<>();
        for (EpisodeOfCare episode : result.episodes()) {
            String patientId = referenceIdPart(episode.getPatient());
            if (patientId == null) {
                continue;
            }
            episodesByPatientId
                    .computeIfAbsent(patientId, key -> new ArrayList<>())
                    .add(episode);
        }

        List<PatientEpisodesView> views = new ArrayList<>();
        for (Map.Entry<String, List<EpisodeOfCare>> entry : episodesByPatientId.entrySet()) {
            String patientId = entry.getKey();
            Patient patient = patientsById.get(patientId);

            List<PatientEpisodesView.EpisodeSummaryView> summaries = entry.getValue().stream()
                    .map(episodeEntry -> toSummary(episodeEntry, conditionsById))
                    .toList();

            views.add(new PatientEpisodesView(
                    patientId,
                    displayName(patient),
                    cpr(patient),
                    summaries));
        }
        views.sort(Comparator.comparing(PatientEpisodesView::patientName, Comparator.nullsLast(String::compareToIgnoreCase)));
        return views;
    }

    /**
     * Builds the detail view from a single-episode {@link EpisodeOfCareAPI.SearchResult}. The
     * resolved {@code patientsById} / {@code orgsById} / {@code teamsById} maps are supplied by
     * the controller; only diagnosis {@code Condition}s arrive via {@code _include}.
     */
    public EpisodeOfCareDetailView toDetailView(
            EpisodeOfCareAPI.SearchResult result,
            Map<String, Patient> patientsById,
            Map<String, Organization> orgsById,
            Map<String, CareTeam> teamsById) {
        if (result.episodes().isEmpty()) {
            throw new IllegalStateException("Cannot build detail view from empty SearchResult");
        }
        EpisodeOfCare episode = result.episodes().get(0);
        Map<String, Condition> conditionsById = indexById(result.conditions());
        addContainedConditions(episode, conditionsById);

        String patientId = referenceIdPart(episode.getPatient());
        Patient patient = patientId == null ? null : patientsById.get(patientId);

        String orgId = referenceIdPart(episode.getManagingOrganization());
        Organization org = orgId == null ? null : orgsById.get(orgId);

        String teamName = episode.getTeam().stream()
                .map(this::referenceIdPart)
                .filter(java.util.Objects::nonNull)
                .map(teamsById::get)
                .filter(java.util.Objects::nonNull)
                .map(CareTeam::getName)
                .filter(name -> name != null && !name.isBlank())
                .findFirst()
                .orElse(null);

        List<EpisodeOfCareDetailView.DiagnosisView> diagnoses = episode.getDiagnosis().stream()
                .map(diagnosisComponent -> toDiagnosisView(diagnosisComponent, conditionsById))
                .toList();

        return new EpisodeOfCareDetailView(
                episode.getIdElement().getIdPart(),
                episode.hasStatus() ? episode.getStatus().toCode() : null,
                episode.hasPeriod() && episode.getPeriod().hasStart()
                        ? episode.getPeriod().getStartElement().getValueAsString()
                        : null,
                episode.hasPeriod() && episode.getPeriod().hasEnd()
                        ? episode.getPeriod().getEndElement().getValueAsString()
                        : null,
                patientId,
                displayName(patient),
                cpr(patient),
                org == null ? null : org.getName(),
                teamName,
                diagnoses);
    }

    /**
     * Builds a transaction {@link Bundle} that creates the diagnosis {@link Condition} and the
     * {@link EpisodeOfCare} in one atomic call.
     *
     * <p>The IG profile {@code ehealth-episodeofcare} declares {@code diagnosis.condition} with
     * aggregation {@code #referenced}, which forbids the {@code contained}/{@code #c1} shape. We
     * therefore POST both resources, cross-referencing the Condition via a {@code urn:uuid:}
     * placeholder that the FHIR transaction processor rewrites server-side.
     *
     * <p>The Condition carries {@code clinicalStatus = active} and {@code verificationStatus =
     * confirmed} to satisfy {@code con-5}. The EpisodeOfCare carries the mandatory
     * {@code ehealth-episodeofcare-caremanagerOrganization} extension. Status is {@code planned};
     * period start defaults to now (server may overwrite).
     */
    public Bundle toCreateEpisodeTransaction(
            String patientId, ConditionCodeOption diagnosis, EHealthContext context) {
        String conditionFullUrl = "urn:uuid:" + UUID.randomUUID();
        String episodeFullUrl = "urn:uuid:" + UUID.randomUUID();
        String provenanceFullUrl = "urn:uuid:" + UUID.randomUUID();
        Date now = new Date();

        Condition condition = new Condition();
        condition.getMeta().addProfile("http://ehealth.sundhed.dk/fhir/StructureDefinition/ehealth-condition");
        condition.addExtension(new Extension(
                "http://hl7.org/fhir/StructureDefinition/workflow-episodeOfCare",
                new Reference(episodeFullUrl)));
        condition.setClinicalStatus(new CodeableConcept().addCoding(new Coding()
                .setSystem("http://terminology.hl7.org/CodeSystem/condition-clinical")
                .setCode("active")));
        condition.setVerificationStatus(new CodeableConcept().addCoding(new Coding()
                .setSystem("http://terminology.hl7.org/CodeSystem/condition-ver-status")
                .setCode("confirmed")));
        condition.setSubject(new Reference(patientId));
        condition.setCode(new CodeableConcept()
                .addCoding(new Coding()
                        .setSystem(ConditionCodeOption.SYSTEM)
                        .setCode(diagnosis.code())
                        .setDisplay(diagnosis.display())));

        EpisodeOfCare episode = new EpisodeOfCare();
        episode.getMeta().addProfile("http://ehealth.sundhed.dk/fhir/StructureDefinition/ehealth-episodeofcare");
        episode.setStatus(EpisodeOfCare.EpisodeOfCareStatus.PLANNED);
        episode.setPatient(new Reference(patientId));
        if (context.organizationId() != null) {
            Reference orgRef = new Reference(context.organizationId());
            episode.setManagingOrganization(orgRef);
            episode.addExtension()
                    .setUrl("http://ehealth.sundhed.dk/fhir/StructureDefinition/ehealth-episodeofcare-caremanagerOrganization")
                    .setValue(orgRef);
        }
        if (context.careTeamId() != null) {
            episode.addTeam(new Reference(context.careTeamId()));
        }
        episode.setPeriod(new Period().setStart(now));
        episode.addDiagnosis().setCondition(new Reference(conditionFullUrl));

        // A privacy Provenance is mandatory in the $create-episode-of-care payload; it records the
        // legal basis (sundhedsloven) for holding the citizen's episode.
        Provenance provenance = new Provenance();
        provenance.getMeta().addProfile("http://ehealth.sundhed.dk/fhir/StructureDefinition/ehealth-provenance");
        provenance.addAgent().setWho(new Reference(patientId));
        provenance.addTarget(new Reference(episodeFullUrl));
        provenance.setRecorded(now);
        provenance.addPolicy("http://ehealth.sundhed.dk/policy/dk/sundhedsloven");

        Bundle bundle = new Bundle();
        bundle.setType(Bundle.BundleType.TRANSACTION);
        bundle.addEntry(postEntry(provenanceFullUrl, provenance));
        bundle.addEntry(postEntry(episodeFullUrl, episode));
        bundle.addEntry(postEntry(conditionFullUrl, condition));
        return bundle;
    }

    private static Bundle.BundleEntryComponent postEntry(String fullUrl, Resource resource) {
        return new Bundle.BundleEntryComponent()
                .setFullUrl(fullUrl)
                .setResource(resource)
                .setRequest(new Bundle.BundleEntryRequestComponent()
                        .setMethod(Bundle.HTTPVerb.POST)
                        .setUrl(resource.fhirType()));
    }

    private PatientEpisodesView.EpisodeSummaryView toSummary(
            EpisodeOfCare episode, Map<String, Condition> conditionsById) {
        String diagnosisLabel = episode.getDiagnosis().stream()
                .sorted(Comparator.comparingInt(
                        diagnosisComponent -> diagnosisComponent.hasRank()
                                ? diagnosisComponent.getRank() : Integer.MAX_VALUE))
                .map(diagnosisComponent -> {
                    Condition condition = resolveCondition(diagnosisComponent.getCondition(), conditionsById);
                    if (condition != null) {
                        String label = labelFor(condition.getCode());
                        if (label != null) {
                            return label;
                        }
                    }
                    return null;
                })
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .orElse(null);

        String startDate = episode.hasPeriod() && episode.getPeriod().hasStart()
                ? episode.getPeriod().getStartElement().getValueAsString()
                : null;

        return new PatientEpisodesView.EpisodeSummaryView(
                episode.getIdElement().getIdPart(),
                episode.hasStatus() ? episode.getStatus().toCode() : null,
                diagnosisLabel,
                startDate);
    }

    private EpisodeOfCareDetailView.DiagnosisView toDiagnosisView(
            EpisodeOfCare.DiagnosisComponent component, Map<String, Condition> conditionsById) {
        Condition condition = resolveCondition(component.getCondition(), conditionsById);
        String code = null;
        String display = null;
        if (condition != null && condition.hasCode()) {
            Coding coding = condition.getCode().getCodingFirstRep();
            code = coding.getCode();
            display = coding.hasDisplay() ? coding.getDisplay() : condition.getCode().getText();
        }
        Integer rank = component.hasRank() ? component.getRank() : null;
        return new EpisodeOfCareDetailView.DiagnosisView(code, display, rank);
    }

    private Condition resolveCondition(Reference ref, Map<String, Condition> conditionsById) {
        if (ref == null || ref.isEmpty()) {
            return null;
        }
        String refStr = ref.getReference();
        if (refStr != null && refStr.startsWith("#")) {
            // Contained reference; conditionsById was pre-populated from episode.getContained().
            return conditionsById.get(refStr.substring(1));
        }
        String conditionId = referenceIdPart(ref);
        return conditionId == null ? null : conditionsById.get(conditionId);
    }

    private void addContainedConditions(EpisodeOfCare episode, Map<String, Condition> conditionsById) {
        for (org.hl7.fhir.r4.model.Resource contained : episode.getContained()) {
            if (contained instanceof Condition condition && condition.getIdElement().getIdPart() != null) {
                conditionsById.putIfAbsent(condition.getIdElement().getIdPart(), condition);
            }
        }
    }

    private String labelFor(CodeableConcept concept) {
        if (concept == null) {
            return null;
        }
        if (concept.hasCoding()) {
            Coding coding = concept.getCodingFirstRep();
            if (coding.hasDisplay()) {
                return coding.getDisplay();
            }
            if (coding.hasCode()) {
                return coding.getCode();
            }
        }
        return concept.hasText() ? concept.getText() : null;
    }

    private String referenceIdPart(Reference ref) {
        if (ref == null || ref.isEmpty()) {
            return null;
        }
        return ref.getReferenceElement().getIdPart();
    }

    private <R extends org.hl7.fhir.r4.model.Resource> Map<String, R> indexById(List<R> resources) {
        Map<String, R> map = new HashMap<>();
        for (R resource : resources) {
            String idPart = resource.getIdElement().getIdPart();
            if (idPart != null) {
                map.put(idPart, resource);
            }
        }
        return map;
    }

    private String displayName(Patient patient) {
        if (patient == null) {
            return null;
        }
        for (HumanName name : patient.getName()) {
            String given = name.getGivenAsSingleString();
            String family = name.getFamily();
            if (given != null && !given.isBlank() && family != null && !family.isBlank()) {
                return given + " " + family;
            }
            if (family != null && !family.isBlank()) {
                return family;
            }
            if (given != null && !given.isBlank()) {
                return given;
            }
        }
        return patient.getIdElement().getIdPart();
    }

    private String cpr(Patient patient) {
        if (patient == null) {
            return null;
        }
        return patient.getIdentifier().stream()
                .filter(identifier -> CPR_SYSTEM.equals(identifier.getSystem()))
                .map(identifier -> identifier.getValue() != null ? identifier.getValue() : "")
                .findFirst()
                .orElse(null);
    }
}
