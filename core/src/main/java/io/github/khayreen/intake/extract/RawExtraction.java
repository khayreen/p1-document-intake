package io.github.khayreen.intake.extract;

import io.github.khayreen.intake.model.Usage;

/** What an extractor returned before any checks: JSON text, or an error. */
public record RawExtraction(String json, Usage usage, String error) {

    public static RawExtraction ok(String json, Usage usage) {
        return new RawExtraction(json, usage, null);
    }

    public static RawExtraction failed(String error, Usage usage) {
        return new RawExtraction(null, usage, error);
    }
}
