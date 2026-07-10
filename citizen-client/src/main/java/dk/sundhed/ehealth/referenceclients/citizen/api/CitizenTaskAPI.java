package dk.sundhed.ehealth.referenceclients.citizen.api;

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
 * Citizen-side FHIR wrappers for {@code Task} operations on {@link FhirServer#TASK}.
 *
 * <p>{@link #findTasksByEpisode} returns tasks for one episode of care. The Task server rejects
 * {@code Task?patient=} for citizen tokens (same rule as the careplan server: episodeOfCare must
 * be present in both the search params and the token context). {@link #completeTask} issues a
 * JSON-Patch {@code replace /status completed} for a single task.
 */
@Component
public class CitizenTaskAPI {

    private static final ReferenceClientParam EPISODE_OF_CARE =
            new ReferenceClientParam("episodeOfCare");
    private static final int MAX_TASKS = 200;

    private final FhirClientFactory fhirClientFactory;

    public CitizenTaskAPI(FhirClientFactory fhirClientFactory) {
        this.fhirClientFactory = fhirClientFactory;
    }

    /**
     * Returns all {@link Task}s linked to the given episode, up to {@value MAX_TASKS}.
     *
     * <p>The Task server rejects {@code Task?patient=} for citizen tokens, and further requires a
     * patient to search only for tasks it owns, requested, or is responsible for. We scope by
     * {@code episodeOfCare} (present in both params and token) plus {@code owner}=self to satisfy
     * that rule. Platform assessment tasks ({@code for}=patient, no owner) are therefore not
     * visible to the citizen by design, only tasks the citizen actually owns.
     *
     * @param episodeOfCareId fully-qualified EpisodeOfCare URL
     * @param context         citizen security context scoped to the episode
     * @return tasks for this episode owned by the citizen
     */
    public List<Task> findTasksByEpisode(String episodeOfCareId, EHealthContext context) {
        IGenericClient client = fhirClientFactory.createClient(FhirServer.TASK, context);
        Bundle bundle = client.search()
                .forResource(Task.class)
                .where(EPISODE_OF_CARE.hasId(episodeOfCareId))
                .and(new ReferenceClientParam("owner").hasId(context.patientId()))
                .count(MAX_TASKS)
                .returnBundle(Bundle.class)
                .execute();
        return BundleUtil.extract(bundle, Task.class);
    }

    /**
     * Patches the given {@link Task} status to {@code completed}.
     *
     * @param taskId  fully-qualified Task URL
     * @param context citizen security context
     */
    public void completeTask(String taskId, EHealthContext context) {
        IGenericClient client = fhirClientFactory.createClient(FhirServer.TASK, context);
        String patch = JsonPatch.builder()
                .replace("/status", Task.TaskStatus.COMPLETED.toCode())
                .build();
        client.patch().withBody(patch).withId(new IdType(taskId)).execute();
    }
}
