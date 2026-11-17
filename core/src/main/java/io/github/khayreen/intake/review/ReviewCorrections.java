package io.github.khayreen.intake.review;

import io.github.khayreen.intake.model.FieldSpec;
import io.github.khayreen.intake.model.FormType;
import io.github.khayreen.intake.validate.FieldValidator;
import io.github.khayreen.intake.validate.Normaliser;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Applies a reviewer's corrections and checks the result with the same rules as the pipeline.
 * A person can fix a value, but cannot approve a form with a missing or invalid field, or add a
 * field the form does not have. Used by the local review page and by the review Lambda on AWS.
 */
public final class ReviewCorrections {

    public record Outcome(Map<String, String> fields, List<String> problems) {

        public boolean ok() {
            return problems.isEmpty();
        }
    }

    private ReviewCorrections() {
    }

    public static Outcome apply(FormType form, Map<String, String> extracted, Map<String, String> corrections) {
        Map<String, String> fields = new LinkedHashMap<>();
        for (FieldSpec spec : form.fields()) {
            String v = extracted == null ? null : extracted.get(spec.name());
            fields.put(spec.name(), v == null ? null : Normaliser.normalise(spec, v).orElse(v));
        }
        List<String> problems = new ArrayList<>();
        if (corrections != null) {
            for (Map.Entry<String, String> c : corrections.entrySet()) {
                FieldSpec spec = form.field(c.getKey()).orElse(null);
                if (spec == null) {
                    problems.add("unknown field " + c.getKey());
                    continue;
                }
                String raw = c.getValue();
                fields.put(c.getKey(), raw == null || raw.isBlank() ? null : Normaliser.normalise(spec, raw).orElse(raw.strip()));
            }
        }
        for (FieldSpec spec : form.fields()) {
            String v = fields.get(spec.name());
            if (v == null) {
                if (spec.required()) {
                    problems.add(spec.name() + " is required");
                }
            } else if (Normaliser.normalise(spec, v).isEmpty()) {
                problems.add(spec.name() + ": cannot read \"" + v + "\"");
            } else {
                FieldValidator.check(spec, v).ifPresent(why -> problems.add(spec.name() + ": " + why));
            }
        }
        if (problems.isEmpty()) {
            FieldValidator.crossCheck(form, fields).forEach(f -> problems.add(f.field() + ": " + f.detail()));
        }
        return new Outcome(fields, problems);
    }
}
