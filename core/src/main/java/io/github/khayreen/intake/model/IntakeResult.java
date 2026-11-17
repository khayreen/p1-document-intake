package io.github.khayreen.intake.model;

import java.util.List;
import java.util.Map;

/**
 * The pipeline's verdict on one document.
 *
 * @param documentId id of the uploaded document
 * @param formType   detected form, or null if unknown
 * @param fields     normalised field values; a field the pipeline could not trust is still shown to the reviewer
 * @param flags      every reason for review; empty means accepted automatically
 * @param decision   AUTO_ACCEPT only when there are no flags
 * @param usage      model tokens used
 * @param ocrPages   pages sent to OCR
 */
public record IntakeResult(String documentId, FormType formType, Map<String, String> fields,
                           List<Flag> flags, Decision decision, Usage usage, int ocrPages) {

    public IntakeResult {
        fields = fields == null ? Map.of() : java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(fields));
        flags = List.copyOf(flags);
    }

    public boolean hasFlag(FlagCode code) {
        return flags.stream().anyMatch(f -> f.code() == code);
    }
}
