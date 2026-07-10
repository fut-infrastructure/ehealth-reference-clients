package dk.sundhed.ehealth.referenceclients.common.infrastructure.fhir;

import java.util.ArrayList;
import java.util.List;

/**
 * Small builder for RFC 6902 JSON Patch documents.
 *
 * <p>Produces a JSON array of operation objects, e.g. {@code [{"op":"replace","path":"/status",
 * "value":"finished"}]}, ready to hand to HAPI's
 * {@code client.patch().withBody(json).withId(...)} API.
 *
 * <p>The builder hand-emits JSON to keep the {@code common} module free of an extra Jackson
 * dependency declaration; only a handful of primitive value types are supported. Use
 * {@link #replace(String, String)} / {@link #add(String, String)} for string values and
 * {@link #remove(String)} for path-only ops. Boolean / numeric helpers are provided where useful.
 */
public final class JsonPatch {

    private final List<String> operations = new ArrayList<>();

    private JsonPatch() {
    }

    public static JsonPatch builder() {
        return new JsonPatch();
    }

    /**
     * Adds a {@code replace} op with a string value.
     */
    public JsonPatch replace(String path, String value) {
        operations.add(opWithStringValue("replace", path, value));
        return this;
    }

    /**
     * Adds an {@code add} op with a string value.
     */
    public JsonPatch add(String path, String value) {
        operations.add(opWithStringValue("add", path, value));
        return this;
    }

    /**
     * Adds a {@code remove} op (no value).
     */
    public JsonPatch remove(String path) {
        operations.add("{\"op\":\"remove\",\"path\":\"" + escape(path) + "\"}");
        return this;
    }

    /**
     * Adds a {@code replace} op with a boolean value.
     */
    public JsonPatch replace(String path, boolean value) {
        operations.add("{\"op\":\"replace\",\"path\":\""
                + escape(path)
                + "\",\"value\":" + value + "}");
        return this;
    }

    /**
     * Adds a {@code replace} op with a numeric value.
     */
    public JsonPatch replace(String path, long value) {
        operations.add("{\"op\":\"replace\",\"path\":\""
                + escape(path)
                + "\",\"value\":" + value + "}");
        return this;
    }

    /**
     * Renders the patch as a JSON document string.
     */
    public String build() {
        return "[" + String.join(",", operations) + "]";
    }

    private static String opWithStringValue(String operation, String path, String value) {
        return "{\"op\":\"" + operation + "\",\"path\":\""
                + escape(path)
                + "\",\"value\":\""
                + escape(value)
                + "\"}";
    }

    private static String escape(String input) {
        StringBuilder out = new StringBuilder(input.length() + 8);
        for (int index = 0; index < input.length(); index++) {
            char character = input.charAt(index);
            switch (character) {
                case '\\' -> out.append("\\\\");
                case '"' -> out.append("\\\"");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (character < 0x20) {
                        out.append(String.format("\\u%04x", (int) character));
                    } else {
                        out.append(character);
                    }
                }
            }
        }
        return out.toString();
    }
}
