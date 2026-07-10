package dk.sundhed.ehealth.referenceclients.clinician.api;

import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.gclient.ReferenceClientParam;
import dk.sundhed.ehealth.referenceclients.common.infrastructure.fhir.BundleUtil;
import dk.sundhed.ehealth.referenceclients.common.infrastructure.fhir.FhirClientFactory;
import dk.sundhed.ehealth.referenceclients.common.infrastructure.fhir.FhirServer;
import dk.sundhed.ehealth.referenceclients.common.infrastructure.fhir.JsonPatch;
import dk.sundhed.ehealth.referenceclients.common.infrastructure.security.EHealthContext;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.Task;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Raw FHIR wrapper around {@code Task} operations on {@link FhirServer#TASK}.
 *
 * <p>Task is the mechanism through which a clinician acknowledges measurement submissions: the
 * measurement server creates a Task for each submission; the clinician lists and processes them
 * here.
 */
@Component
public class TaskAPI {

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
}
