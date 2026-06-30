package dk.sundhed.ehealth.referenceclients.clinician.app;

import jakarta.annotation.Nullable;

/**
 * Lightweight option row for the create-care-plan picker. Carries the bare PlanDefinition id and a
 * display label derived from {@code title} (falling back to {@code name}) plus an optional version.
 */
public record PlanDefinitionOptionView(
        String id,
        String label,
        @Nullable String version) {
}
