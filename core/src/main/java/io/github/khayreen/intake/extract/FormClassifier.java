package io.github.khayreen.intake.extract;

import io.github.khayreen.intake.model.FormType;
import io.github.khayreen.intake.validate.Normaliser;

import java.util.Optional;

/**
 * Picks the form from its printed heading. Done in code, not by the model, because the form type
 * decides which schema the model's answer must match.
 */
public final class FormClassifier {

    private FormClassifier() {
    }

    public static Optional<FormType> classify(String text) {
        String head = Normaliser.words(text.lines().limit(4).reduce("", (a, b) -> a + " " + b));
        if (!head.contains("BANK CONTOH")) {
            return Optional.empty();
        }
        for (FormType type : FormType.values()) {
            if (head.contains(type.title())) {
                return Optional.of(type);
            }
        }
        return Optional.empty();
    }
}
