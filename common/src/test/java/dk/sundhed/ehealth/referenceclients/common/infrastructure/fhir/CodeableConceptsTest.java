package dk.sundhed.ehealth.referenceclients.common.infrastructure.fhir;

import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CodeableConceptsTest {

    @Test
    void displayOf_prefersFirstCodingDisplay() {
        CodeableConcept concept = new CodeableConcept().setText("ignored");
        concept.addCoding(new Coding().setCode("NPU03011"));
        concept.addCoding(new Coding().setDisplay("Heart rate"));

        assertThat(CodeableConcepts.displayOf(concept)).isEqualTo("Heart rate");
    }

    @Test
    void displayOf_fallsBackToTextWhenNoCodingHasDisplay() {
        CodeableConcept concept = new CodeableConcept().setText("Heart rate");
        concept.addCoding(new Coding().setCode("NPU03011"));

        assertThat(CodeableConcepts.displayOf(concept)).isEqualTo("Heart rate");
    }

    @Test
    void displayOf_isNullWhenConceptIsNull() {
        assertThat(CodeableConcepts.displayOf(null)).isNull();
    }

    @Test
    void displayOf_isNullWhenNeitherDisplayNorText() {
        CodeableConcept concept = new CodeableConcept();
        concept.addCoding(new Coding().setCode("NPU03011"));

        assertThat(CodeableConcepts.displayOf(concept)).isNull();
    }
}
