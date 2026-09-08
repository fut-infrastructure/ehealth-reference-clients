package dk.sundhed.ehealth.referenceclients.clinician.view;

import jakarta.annotation.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One care plan's {@link TaskView}s, grouped for the episode task list page so a submission's
 * several tasks (the platform can create more than one per submission, see
 * {@link TaskView#carePlanId()}) read as one row of work instead of unrelated-looking entries.
 *
 * @param carePlanId    bare CarePlan id shared by every task in {@code tasks}, or {@code null} for
 *                      the group of tasks whose {@code ehealth-reference-careplan} extension was
 *                      absent
 * @param carePlanTitle the CarePlan's title, or its bare id when it has none; {@code null} only for
 *                      the no-{@code carePlanId} group
 * @param tasks         this care plan's tasks, in the order they appeared in the source list
 */
public record TaskGroupView(
        @Nullable String carePlanId, @Nullable String carePlanTitle, List<TaskView> tasks) {

    /**
     * Groups by {@link TaskView#carePlanId()}, preserving the input order both across groups (a
     * group appears where its first task did) and within a group. {@code carePlanTitleById} supplies
     * each group's display title, falling back to the bare id when a CarePlan isn't in the map.
     */
    public static List<TaskGroupView> groupByCarePlan(
            List<TaskView> taskViews, Map<String, String> carePlanTitleById) {
        Map<String, List<TaskView>> byCarePlanId = new LinkedHashMap<>();
        for (TaskView task : taskViews) {
            byCarePlanId.computeIfAbsent(task.carePlanId(), key -> new ArrayList<>()).add(task);
        }
        return byCarePlanId.entrySet().stream()
                .map(entry -> new TaskGroupView(
                        entry.getKey(),
                        titleOf(entry.getKey(), carePlanTitleById),
                        List.copyOf(entry.getValue())))
                .toList();
    }

    private static String titleOf(String carePlanId, Map<String, String> carePlanTitleById) {
        if (carePlanId == null) {
            return null;
        }
        return carePlanTitleById.getOrDefault(carePlanId, carePlanId);
    }
}
