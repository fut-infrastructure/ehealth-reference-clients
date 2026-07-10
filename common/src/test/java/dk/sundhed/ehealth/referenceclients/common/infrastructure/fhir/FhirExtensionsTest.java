package dk.sundhed.ehealth.referenceclients.common.infrastructure.fhir;

import org.hl7.fhir.r4.model.CarePlan;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.StringType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FhirExtensionsTest {

    @Test
    void referenceValue_returnsReferenceOfMatchingExtension() {
        CarePlan carePlan = new CarePlan();
        carePlan.addExtension(new Extension(
                FhirExtensions.EPISODE_OF_CARE, new Reference("EpisodeOfCare/656899")));

        assertThat(FhirExtensions.referenceValue(carePlan, FhirExtensions.EPISODE_OF_CARE))
                .isEqualTo("EpisodeOfCare/656899");
    }

    @Test
    void referenceValue_isNullWhenExtensionAbsent() {
        assertThat(FhirExtensions.referenceValue(new CarePlan(), FhirExtensions.EPISODE_OF_CARE))
                .isNull();
    }

    @Test
    void referenceValue_isNullWhenExtensionValueIsNotAReference() {
        CarePlan carePlan = new CarePlan();
        carePlan.addExtension(new Extension(FhirExtensions.EPISODE_OF_CARE, new StringType("nope")));

        assertThat(FhirExtensions.referenceValue(carePlan, FhirExtensions.EPISODE_OF_CARE))
                .isNull();
    }
}
