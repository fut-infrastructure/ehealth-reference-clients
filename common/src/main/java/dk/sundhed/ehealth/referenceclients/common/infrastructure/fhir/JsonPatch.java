package dk.sundhed.ehealth.referenceclients.common.infrastructure.fhir;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Small builder for RFC 6902 JSON Patch documents.
 *
 * <p>Produces a JSON array of operation objects, e.g. {@code [{"op":"replace","path":"/status",
 * "value":"finished"}]}, ready to hand to HAPI's
 * {@code client.patch().withBody(json).withId(...)} API.
 */
public final class JsonPatch {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final List<Map<String, Object>> operations = new ArrayList<>();

    private JsonPatch() {
    }

    public static JsonPatch builder() {
        return new JsonPatch();
    }

    /**
     * Adds a {@code replace} op. The value is serialized by Jackson, so strings, booleans and
     * numbers all render correctly.
     */
    public JsonPatch replace(String path, Object value) {
        operations.add(Map.of("op", "replace", "path", path, "value", value));
        return this;
    }

    /**
     * Renders the patch as a JSON document string.
     */
    public String build() {
        try {
            return MAPPER.writeValueAsString(operations);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize JSON Patch", e);
        }
    }
}
