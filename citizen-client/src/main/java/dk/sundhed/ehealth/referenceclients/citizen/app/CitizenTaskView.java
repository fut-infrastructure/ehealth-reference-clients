package dk.sundhed.ehealth.referenceclients.citizen.app;

import jakarta.annotation.Nullable;

/**
 * One task row for the citizen task list page.
 *
 * <p>Derived from a {@code Task} returned by {@code Task?patient=...} on {@code fut-task}.
 */
public record CitizenTaskView(
        String taskId,
        String qualifiedId,
        String status,
        @Nullable String category,
        @Nullable String description,
        @Nullable String priority,
        @Nullable String episodeRef) {
}
