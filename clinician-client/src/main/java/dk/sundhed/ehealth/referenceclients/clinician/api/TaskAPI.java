package dk.sundhed.ehealth.referenceclients.clinician.api;

import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.gclient.ReferenceClientParam;
import dk.sundhed.ehealth.referenceclients.common.infrastructure.fhir.BundleUtil;
import dk.sundhed.ehealth.referenceclients.common.infrastructure.fhir.FhirClientFactory;
import dk.sundhed.ehealth.referenceclients.common.infrastructure.fhir.FhirServer;
import dk.sundhed.ehealth.referenceclients.common.infrastructure.fhir.JsonPatch;
import dk.sundhed.ehealth.referenceclients.common.infrastructure.security.EHealthContext;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Task;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;

/**
 * Raw FHIR wrapper around {@code Task} operations on {@link FhirServer#TASK}.
 *
 * <p>Task is the mechanism through which a clinician acknowledges measurement submissions: the
 * measurement server creates a Task for each submission; the clinician lists and processes them
 * here. {@link #createTaskForPatient} is the exception: applying a PlanDefinition only produces a
 * CarePlan and its ServiceRequest/Appointment activities (see {@code CarePlanAPI#applyPlanDefinition}),
 * nothing on the platform turns that into a citizen-owned Task, so the clinician creates one
 * explicitly to give the citizen something to act on.
 */
@Component
public class TaskAPI {

    private static final String PROFILE = "http://ehealth.sundhed.dk/fhir/StructureDefinition/ehealth-task";
    private static final String EXT_EPISODE_OF_CARE =
            "http://ehealth.sundhed.dk/fhir/StructureDefinition/ehealth-task-episodeOfCare";
    private static final String EXT_TASK_CATEGORY =
            "http://ehealth.sundhed.dk/fhir/StructureDefinition/ehealth-task-category";
    private static final String EXT_RESTRICTION_CATEGORY =
            "http://ehealth.sundhed.dk/fhir/StructureDefinition/ehealth-restriction-category";
    private static final String EXT_TASK_RESPONSIBLE =
            "http://ehealth.sundhed.dk/fhir/StructureDefinition/ehealth-task-responsible";
    private static final String EXT_REFERENCE_CAREPLAN =
            "http://ehealth.sundhed.dk/fhir/StructureDefinition/ehealth-reference-careplan";

    private static final ReferenceClientParam EPISODE_OF_CARE =
            new ReferenceClientParam("episodeOfCare");

    private static final int MAX_TASKS = 200;

    private final FhirClientFactory fhirClientFactory;

    public TaskAPI(FhirClientFactory fhirClientFactory) {
        this.fhirClientFactory = fhirClientFactory;
    }

    /**
     * Returns all {@link Task}s linked to the given episode on the task server, up to
     * {@value MAX_TASKS}.
     *
     * @param episodeOfCareId fully-qualified EpisodeOfCare URL
     * @param context         security context (scoped to episode internally)
     * @return tasks for this episode, most recently updated last
     */
    public List<Task> findTasksByEpisode(String episodeOfCareId, EHealthContext context) {
        IGenericClient client = fhirClientFactory.createClient(FhirServer.TASK, context);

        Bundle bundle = client.search()
                .forResource(Task.class)
                .where(EPISODE_OF_CARE.hasId(episodeOfCareId))
                .count(MAX_TASKS)
                .returnBundle(Bundle.class)
                .execute();

        return BundleUtil.extract(bundle, Task.class);
    }

    /**
     * Issues a JSON-Patch {@code replace /status} against the given {@code Task}. The task server
     * requires the owning episode in the token, so {@code context} must already be episode-scoped.
     *
     * @param taskId  fully-qualified Task URL
     * @param target  desired {@link Task.TaskStatus}
     * @param context episode-scoped security context
     */
    public void changeTaskStatus(String taskId, Task.TaskStatus target, EHealthContext context) {
        IGenericClient client = fhirClientFactory.createClient(FhirServer.TASK, context);

        String patch = JsonPatch.builder()
                .replace("/status", target.toCode())
                .build();

        client.patch().withBody(patch).withId(new IdType(taskId)).execute();
    }

    /**
     * Creates a citizen-owned {@link Task} linked to a {@code CarePlan}, so the citizen has
     * something to act on.
     *
     * <p>The {@code task-category} codes ({@code http://ehealth.sundhed.dk/cs/task-category}) are
     * all clinician-facing assessment reasons.
     * This reuses the closest fit, {@code MeasurementForAssessment},
     * and puts the actual citizen-facing instruction in {@code description}.
     *
     * @param patientId       fully-qualified Patient URL (becomes both {@code for} and
     *                        {@code owner}, matching what the citizen app actually searches on)
     * @param episodeOfCareId fully-qualified EpisodeOfCare URL
     * @param carePlanId      fully-qualified CarePlan URL this task fulfils
     * @param careTeamId      fully-qualified CareTeam URL responsible for the task
     * @param description     citizen-facing instruction text
     * @param context         security context, scoped to the episode internally
     */
    public void createTaskForPatient(
            String patientId,
            String episodeOfCareId,
            String carePlanId,
            String careTeamId,
            String description,
            EHealthContext context) {
        EHealthContext taskContext = context.withEpisodeOfCare(episodeOfCareId).withPatient(patientId);
        IGenericClient client = fhirClientFactory.createClient(FhirServer.TASK, taskContext);

        Task task = new Task();
        task.getMeta().addProfile(PROFILE);
        task.addExtension(EXT_EPISODE_OF_CARE, new Reference(episodeOfCareId));
        task.addExtension(EXT_TASK_CATEGORY, new CodeableConcept().addCoding(new Coding(
                "http://ehealth.sundhed.dk/cs/task-category",
                "MeasurementForAssessment",
                "Need assessment of measurement")));
        task.addExtension(EXT_RESTRICTION_CATEGORY, new CodeableConcept().addCoding(new Coding(
                "http://ehealth.sundhed.dk/cs/restriction-category",
                "measurement-monitoring",
                "Monitoring of measurement(s)")));
        task.addExtension(EXT_TASK_RESPONSIBLE, new Reference(careTeamId));
        task.addExtension(EXT_REFERENCE_CAREPLAN, new Reference(carePlanId));
        task.setStatus(Task.TaskStatus.REQUESTED);
        task.setIntent(Task.TaskIntent.PLAN);
        task.setPriority(Task.TaskPriority.ROUTINE);
        task.setFor(new Reference(patientId));
        task.setOwner(new Reference(patientId));
        task.setAuthoredOn(new Date());
        task.setDescription(description);

        client.create().resource(task).execute();
    }
}
