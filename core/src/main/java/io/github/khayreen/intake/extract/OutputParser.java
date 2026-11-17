package io.github.khayreen.intake.extract;

import io.github.khayreen.intake.model.Flag;
import io.github.khayreen.intake.model.FlagCode;
import io.github.khayreen.intake.model.FormType;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reads the model's JSON. In strict mode anything outside the agreed shape (an extra key, a wrong
 * type, a different form type, prose around the JSON) throws the whole answer away and sends the
 * document to review. Lenient mode exists only so tests can show what strict mode prevents.
 */
public final class OutputParser {

    public record Parsed(Map<String, String> fields, boolean suspectedInjection, List<Flag> problems) {

        public boolean usable() {
            return problems.isEmpty();
        }
    }

    private static final Set<String> TOP_LEVEL = Set.of("form_type", "fields", "suspected_injection");
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final boolean strict;

    public OutputParser(boolean strict) {
        this.strict = strict;
    }

    public Parsed parse(FormType form, String text) {
        return strict ? parseStrict(form, text) : parseLenient(form, text);
    }

    private Parsed parseStrict(FormType form, String text) {
        JsonNode root;
        try {
            root = JSON.readTree(stripFence(text));
        } catch (JacksonException e) {
            return rejected("reply is not a single JSON object");
        }
        if (root == null || !root.isObject()) {
            return rejected("reply is not a JSON object");
        }
        for (String key : root.propertyNames()) {
            if (!TOP_LEVEL.contains(key)) {
                return rejected("unexpected key \"" + key + "\"");
            }
        }
        JsonNode type = root.get("form_type");
        if (type == null || !type.isString() || !type.asString().equals(form.name())) {
            return rejected("form_type must be " + form.name());
        }
        JsonNode injection = root.get("suspected_injection");
        if (injection != null && !injection.isBoolean()) {
            return rejected("suspected_injection must be true or false");
        }
        JsonNode fields = root.get("fields");
        if (fields == null || !fields.isObject()) {
            return rejected("fields must be an object");
        }
        Map<String, String> values = new LinkedHashMap<>();
        for (String name : form.fieldNames()) {
            values.put(name, null);
        }
        for (String key : fields.propertyNames()) {
            if (!values.containsKey(key)) {
                return rejected("unknown field \"" + key + "\"");
            }
            JsonNode v = fields.get(key);
            if (v.isNull()) {
                continue;
            }
            if (!v.isString() && !v.isNumber()) {
                return rejected("field \"" + key + "\" must be text, a number or null");
            }
            String s = v.isString() ? v.asString() : v.numberValue().toString();
            if (s.length() > 300) {
                return rejected("field \"" + key + "\" is longer than any value on the form");
            }
            values.put(key, s);
        }
        return new Parsed(values, injection != null && injection.booleanValue(), List.of());
    }

    /** Takes whatever looks like a field, wherever it is. Do not use outside tests. */
    private Parsed parseLenient(FormType form, String text) {
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return rejected("no JSON found");
        }
        JsonNode root;
        try {
            root = JSON.readTree(text.substring(start, end + 1));
        } catch (JacksonException e) {
            return rejected("no JSON found");
        }
        Map<String, String> values = new LinkedHashMap<>();
        for (String name : form.fieldNames()) {
            values.put(name, null);
        }
        JsonNode fields = root.has("fields") ? root.get("fields") : root;
        for (String key : fields.propertyNames()) {
            JsonNode v = fields.get(key);
            if (values.containsKey(key) && v != null && !v.isNull() && v.isValueNode()) {
                values.put(key, v.asString());
            }
        }
        JsonNode injection = root.get("suspected_injection");
        return new Parsed(values, injection != null && injection.asBoolean(), List.of());
    }

    private static Parsed rejected(String why) {
        return new Parsed(Map.of(), false, List.of(Flag.document(FlagCode.SCHEMA_VIOLATION, why)));
    }

    /** Models often wrap JSON in a ```json fence. Removing it changes no content. */
    static String stripFence(String text) {
        String t = text.strip();
        if (t.startsWith("```")) {
            int firstNewline = t.indexOf('\n');
            int lastFence = t.lastIndexOf("```");
            if (firstNewline > 0 && lastFence > firstNewline) {
                return t.substring(firstNewline + 1, lastFence).strip();
            }
        }
        return t;
    }

    /** Builds JSON in the agreed shape. Used by the rules baseline and by test stubs. */
    public static String toJson(FormType form, Map<String, String> fields, boolean suspectedInjection) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("form_type", form.name());
        root.put("fields", fields);
        root.put("suspected_injection", suspectedInjection);
        return JSON.writeValueAsString(root);
    }

    static List<String> keys(JsonNode node) {
        return new ArrayList<>(node.propertyNames());
    }
}
