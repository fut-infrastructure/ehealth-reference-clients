package dk.sundhed.ehealth.referenceclients.citizen.app;

import java.util.Set;

/**
 * Static mirror of the {@code http://ehealth.sundhed.dk/vs/observation-codes} value set bound to
 * {@code Observation.code} on the {@code ehealth-observation} profile.
 *
 * <p>An activity's {@code ActivityDefinition.code} determines what kind of result the platform
 * expects for it (see the {@code activitydefinition-code-to-measurement-resource-type} ConceptMap in
 * the FUT IG): some codes expect an {@code Observation}, others a {@code QuestionnaireResponse} or
 * {@code Media} which this client doesn't support submitting, and some activities (e.g. a plain
 * exercise like "do 10 pushups") expect nothing measurable at all. A code in this value set is the
 * only case where {@code $submit-measurement} will accept an {@code Observation}; everything else
 * only supports a plain "mark done" completion (see {@link SubmitMeasurementController}).
 */
final class ObservationCodes {

    private ObservationCodes() {
    }

    private static final Set<String> CODES = Set.of(
            "urn:oid:1.2.208.176.2.1|DNK05463",
            "urn:oid:1.2.208.176.2.1|DNK05465",
            "urn:oid:1.2.208.176.2.1|DNK05467",
            "urn:oid:1.2.208.176.2.1|DNK05469",
            "urn:oid:1.2.208.176.2.1|DNK05472",
            "urn:oid:1.2.208.176.2.1|DNK05473",
            "urn:oid:1.2.208.176.2.1|NPU02193",
            "urn:oid:1.2.208.176.2.1|NPU03011",
            "urn:oid:1.2.208.176.2.1|NPU21692",
            "urn:oid:1.2.208.176.2.1|NPU03804",
            "urn:oid:1.2.208.176.2.1|NPU27281",
            "urn:oid:1.2.208.176.2.1|NPU03794",
            "urn:oid:1.2.208.176.2.1|NPU08676",
            "urn:oid:1.2.208.184.100.8|MCS88015",
            "urn:oid:1.2.208.184.100.8|MCS88016",
            "urn:oid:1.2.208.184.100.8|MCS88017",
            "urn:oid:1.2.208.184.100.8|MCS88021",
            "urn:oid:1.2.208.184.100.8|MCS88023",
            "urn:oid:1.2.208.184.100.8|MCS88050",
            "urn:oid:1.2.208.184.100.8|MCS88137",
            "urn:oid:1.2.208.184.100.8|MCS88192",
            "urn:oid:1.2.208.184.100.8|MCS88193",
            "urn:oid:1.2.208.184.100.8|MCS88194",
            "urn:oid:1.2.208.184.100.8|MCS88214",
            "urn:oid:1.2.208.176.2.4|ZZ3170",
            "http://loinc.org|72287-6",
            "http://loinc.org|39126-8",
            "http://loinc.org|39125-0",
            "http://loinc.org|39127-6",
            "http://loinc.org|89260-4",
            "http://loinc.org|94083-3");

    static boolean isObservable(String system, String code) {
        return system != null && code != null && CODES.contains(system + "|" + code);
    }
}
