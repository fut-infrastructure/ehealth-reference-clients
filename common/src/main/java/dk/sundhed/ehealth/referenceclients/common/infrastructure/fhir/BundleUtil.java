package dk.sundhed.ehealth.referenceclients.common.infrastructure.fhir;

import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Resource;

import java.util.List;
import java.util.Optional;

/**
 * Reusable helpers for extracting typed resources out of a {@link Bundle}.
 */
public final class BundleUtil {

    private BundleUtil() {
    }

    /**
     * Returns all entries in {@code bundle} that are instances of {@code type}.
     *
     * @param bundle the bundle to search
     * @param type   the resource class to filter by
     * @param <R>    the resource type
     * @return a list of matching resources, possibly empty
     */
    public static <R extends Resource> List<R> extract(Bundle bundle, Class<R> type) {
        return bundle.getEntry().stream()
                .map(Bundle.BundleEntryComponent::getResource)
                .filter(type::isInstance)
                .map(type::cast)
                .toList();
    }

    /**
     * Returns the first entry in {@code bundle} that is an instance of {@code type}, or {@link
     * Optional#empty()} if none exists.
     *
     * @param bundle the bundle to search
     * @param type   the resource class to filter by
     * @param <R>    the resource type
     * @return an {@code Optional} containing the first match, or empty
     */
    public static <R extends Resource> Optional<R> extractFirst(Bundle bundle, Class<R> type) {
        return bundle.getEntry().stream()
                .map(Bundle.BundleEntryComponent::getResource)
                .filter(type::isInstance)
                .map(type::cast)
                .findFirst();
    }

    /**
     * Returns the single entry in {@code bundle} that is an instance of {@code type}.
     *
     * @param bundle the bundle to search
     * @param type   the resource class to filter by
     * @param <R>    the resource type
     * @return the single matching resource
     * @throws IllegalStateException if the number of matching entries is not exactly one
     */
    public static <R extends Resource> R extractOne(Bundle bundle, Class<R> type) {
        List<R> results = extract(bundle, type);
        if (results.size() != 1) {
            throw new IllegalStateException(
                    "Expected exactly one " + type.getSimpleName() + " in bundle, found " + results.size());
        }
        return results.get(0);
    }
}
