package io.github.khayreen.intake.lambda;

import tools.jackson.databind.json.JsonMapper;

import java.util.Map;

/** Shared JSON mapper. */
final class Json {

    static final JsonMapper MAPPER = JsonMapper.builder().build();

    private Json() {
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> toMap(Object value) {
        return MAPPER.convertValue(value, Map.class);
    }

    static String write(Object value) {
        return MAPPER.writeValueAsString(value);
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> read(String json) {
        return MAPPER.readValue(json, Map.class);
    }
}
