package dk.sundhed.ehealth.referenceclients.common.infrastructure.fhir;

import ca.uhn.fhir.rest.client.api.IGenericClient;
import org.hl7.fhir.r4.model.Bundle;

/**
 * Helpers for paging through HAPI search results.
 *
 * <p>HAPI returns search results as a series of {@link Bundle} pages linked by {@code
 * Bundle.link[relation=next]}. Use {@link #loadAllPages} to collect all pages into a single bundle
 * before processing.
 */
public final class SearchUtil {

    private SearchUtil() {
    }

    /**
     * Walks all {@code next} pages starting from {@code firstPage} and returns a single {@link
     * Bundle} whose entries are the union of every page.
     *
     * <p>{@code firstPage} is not mutated. A shallow copy is created via {@link Bundle#copy()} and
     * subsequent pages' entries are appended to it.
     *
     * @param client    the authenticated HAPI client used to fetch subsequent pages
     * @param firstPage the initial bundle returned by the search call
     * @return a bundle containing all entries from all pages; identical to {@code firstPage} if there
     * are no further pages
     */
    public static Bundle loadAllPages(IGenericClient client, Bundle firstPage) {
        Bundle result = firstPage.copy();
        Bundle current = firstPage;

        while (current.getLink(Bundle.LINK_NEXT) != null) {
            Bundle next = client.loadPage().next(current).execute();
            result.getEntry().addAll(next.getEntry());
            current = next;
        }

        return result;
    }
}
