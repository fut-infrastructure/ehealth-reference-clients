package dk.sundhed.ehealth.referenceclients.clinician.app.util;

import org.hl7.fhir.r4.model.*;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Builds a FHIR transaction {@link Bundle} that flips a {@link CarePlan} and all its referenced
 * activities from {@code draft} to {@code active} in a single atomic call.
 *
 * <p>For each activity resource the matching status enum is applied:
 * <ul>
 *   <li>{@link ServiceRequest} → {@link ServiceRequest.ServiceRequestStatus#ACTIVE}</li>
 *   <li>{@link Task} → {@link Task.TaskStatus#READY} (the FHIR R4 "active" equivalent for Task)</li>
 *   <li>{@link Appointment} → {@link Appointment.AppointmentStatus#BOOKED}</li>
 * </ul>
 * The CarePlan is given a {@link Period} start of "now" if it does not already have one.
 *
 * <p>This util produces the bundle ready for
 * {@code client.transaction().withBundle(bundle).execute()}.
 */
public final class CarePlanActivationUtil {

    private CarePlanActivationUtil() {
    }

    /**
     * Builds a transaction Bundle that activates the given care plan and activities.
     *
     * @param carePlan   the draft care plan to activate
     * @param activities the activities (Task / Appointment / ServiceRequest) to activate alongside
     * @return a {@link Bundle.BundleType#TRANSACTION} bundle ready to {@code execute()}
     */
    public static Bundle buildActivationBundle(CarePlan carePlan, List<? extends Resource> activities) {
        Date now = new Date();

        carePlan.setStatus(CarePlan.CarePlanStatus.ACTIVE);
        if (!carePlan.hasPeriod() || carePlan.getPeriod().getStart() == null) {
            carePlan.setPeriod(new Period().setStart(now));
        }

        List<Resource> activatedActivities = new ArrayList<>();
        for (Resource activity : activities) {
            activateActivity(activity, now);
            activatedActivities.add(activity);
        }

        Bundle bundle = new Bundle().setType(Bundle.BundleType.TRANSACTION);
        bundle.addEntry(putEntry(carePlan));
        for (Resource activity : activatedActivities) {
            bundle.addEntry(putEntry(activity));
        }
        return bundle;
    }

    private static void activateActivity(Resource activity, Date now) {
        switch (activity) {
            case ServiceRequest serviceRequest -> {
                serviceRequest.setStatus(ServiceRequest.ServiceRequestStatus.ACTIVE);
                if (!serviceRequest.hasOccurrence()) {
                    serviceRequest.setOccurrence(new Period().setStart(now));
                }
            }
            case Task task -> task.setStatus(Task.TaskStatus.READY);
            case Appointment appointment -> appointment.setStatus(Appointment.AppointmentStatus.BOOKED);
            default -> {
                // Unknown activity type: bundle it back as-is. The server will reject it loudly
                // if the type is unexpected, which is the right failure mode for a reference client.
            }
        }
    }

    private static Bundle.BundleEntryComponent putEntry(Resource resource) {
        String fullUrl = resource.getIdElement().toVersionless().getValueAsString();
        String requestUrl = resource.getIdElement().toUnqualifiedVersionless().getValueAsString();
        return new Bundle.BundleEntryComponent()
                .setFullUrl(fullUrl)
                .setResource(resource)
                .setRequest(new Bundle.BundleEntryRequestComponent()
                        .setMethod(Bundle.HTTPVerb.PUT)
                        .setUrl(requestUrl));
    }
}
