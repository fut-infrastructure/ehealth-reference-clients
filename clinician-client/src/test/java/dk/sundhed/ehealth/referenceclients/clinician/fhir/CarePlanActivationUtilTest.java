package dk.sundhed.ehealth.referenceclients.clinician.fhir;

import org.hl7.fhir.r4.model.*;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CarePlanActivationUtilTest {

    private static final String ACTIVITY_DEFINITION_URL =
            "https://plan.example/fhir/ActivityDefinition/486904";

    @Test
    void copiesARecurringTimingFromTheActivityDefinitionWhenOccurrenceIsUnset() {
        ServiceRequest serviceRequest = serviceRequestInstantiating(ACTIVITY_DEFINITION_URL);
        ActivityDefinition activityDefinition = new ActivityDefinition();
        activityDefinition.setId(ACTIVITY_DEFINITION_URL);
        Timing dailyTiming = new Timing();
        dailyTiming.getRepeat().setFrequency(1).setPeriod(1).setPeriodUnit(Timing.UnitsOfTime.D);
        activityDefinition.setTiming(dailyTiming);

        CarePlanActivationUtil.buildActivationBundle(
                new CarePlan(), List.of(serviceRequest), Map.of(ACTIVITY_DEFINITION_URL, activityDefinition));

        assertThat(serviceRequest.getStatus()).isEqualTo(ServiceRequest.ServiceRequestStatus.ACTIVE);
        assertThat(serviceRequest.hasOccurrenceTiming()).isTrue();
        assertThat(serviceRequest.getOccurrenceTiming().getRepeat().getPeriodUnit())
                .isEqualTo(Timing.UnitsOfTime.D);
        // A copy, not the same instance the ActivityDefinition holds.
        assertThat(serviceRequest.getOccurrenceTiming()).isNotSameAs(dailyTiming);
    }

    @Test
    void fallsBackToAOneShotPeriodWhenNoActivityDefinitionIsFound() {
        ServiceRequest serviceRequest = serviceRequestInstantiating(ACTIVITY_DEFINITION_URL);

        CarePlanActivationUtil.buildActivationBundle(new CarePlan(), List.of(serviceRequest), Map.of());

        assertThat(serviceRequest.hasOccurrencePeriod()).isTrue();
        assertThat(serviceRequest.getOccurrencePeriod().getStart()).isNotNull();
    }

    @Test
    void fallsBackToAOneShotPeriodWhenTheActivityDefinitionHasNoTiming() {
        ServiceRequest serviceRequest = serviceRequestInstantiating(ACTIVITY_DEFINITION_URL);
        ActivityDefinition activityDefinition = new ActivityDefinition();
        activityDefinition.setId(ACTIVITY_DEFINITION_URL);

        CarePlanActivationUtil.buildActivationBundle(
                new CarePlan(), List.of(serviceRequest), Map.of(ACTIVITY_DEFINITION_URL, activityDefinition));

        assertThat(serviceRequest.hasOccurrencePeriod()).isTrue();
    }

    @Test
    void leavesAnAlreadySetOccurrenceUntouched() {
        ServiceRequest serviceRequest = serviceRequestInstantiating(ACTIVITY_DEFINITION_URL);
        Period existing = new Period().setStart(new java.util.Date(0));
        serviceRequest.setOccurrence(existing);
        ActivityDefinition activityDefinition = new ActivityDefinition();
        activityDefinition.setId(ACTIVITY_DEFINITION_URL);
        activityDefinition.setTiming(new Timing());

        CarePlanActivationUtil.buildActivationBundle(
                new CarePlan(), List.of(serviceRequest), Map.of(ACTIVITY_DEFINITION_URL, activityDefinition));

        assertThat(serviceRequest.getOccurrencePeriod()).isSameAs(existing);
    }

    @Test
    void activatesTaskAndAppointmentStatuses() {
        Task task = new Task().setStatus(Task.TaskStatus.DRAFT);
        Appointment appointment = new Appointment().setStatus(Appointment.AppointmentStatus.PENDING);

        CarePlanActivationUtil.buildActivationBundle(new CarePlan(), List.of(task, appointment), Map.of());

        assertThat(task.getStatus()).isEqualTo(Task.TaskStatus.READY);
        assertThat(appointment.getStatus()).isEqualTo(Appointment.AppointmentStatus.BOOKED);
    }

    @Test
    void setsCarePlanStatusActiveAndBackfillsAMissingPeriodStart() {
        CarePlan carePlan = new CarePlan();

        CarePlanActivationUtil.buildActivationBundle(carePlan, List.of(), Map.of());

        assertThat(carePlan.getStatus()).isEqualTo(CarePlan.CarePlanStatus.ACTIVE);
        assertThat(carePlan.getPeriod().getStart()).isNotNull();
    }

    private static ServiceRequest serviceRequestInstantiating(String activityDefinitionUrl) {
        ServiceRequest serviceRequest = new ServiceRequest();
        serviceRequest.setId("ServiceRequest/1");
        serviceRequest.addInstantiatesCanonical(activityDefinitionUrl);
        return serviceRequest;
    }
}
