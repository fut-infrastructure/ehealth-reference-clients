package dk.sundhed.ehealth.referenceclients.clinician.app;

import jakarta.annotation.Nullable;

import java.util.List;

/**
 * Lightweight option row for the create-care-plan picker. Carries the bare PlanDefinition id, a
 * display label derived from {@code title} (falling back to {@code name}), an optional version,
 * and a summary of each action so the picker can show a preview.
 */
public record PlanDefinitionOptionView(
        String planDefinitionId,
        String label,
        @Nullable String version,
        @Nullable java.time.LocalDate lastUpdated,
        List<String> actionSummaries) {
}
