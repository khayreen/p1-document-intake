package io.github.khayreen.intake.model;

import java.math.BigDecimal;
import java.util.List;

/**
 * One field on a form.
 *
 * @param name        JSON key the model must use
 * @param kind        type of value
 * @param required    whether an empty value sends the document to review
 * @param choices     allowed values for {@link FieldKind#CHOICE}
 * @param min         lower bound for MONEY and INTEGER, or null
 * @param max         upper bound for MONEY and INTEGER, or null
 * @param description short hint given to the model
 */
public record FieldSpec(String name, FieldKind kind, boolean required, List<String> choices,
                        BigDecimal min, BigDecimal max, String description) {

    public FieldSpec {
        choices = choices == null ? List.of() : List.copyOf(choices);
    }

    public static FieldSpec of(String name, FieldKind kind, boolean required, String description) {
        return new FieldSpec(name, kind, required, List.of(), null, null, description);
    }

    public static FieldSpec choice(String name, String description, String... choices) {
        return new FieldSpec(name, FieldKind.CHOICE, true, List.of(choices), null, null, description);
    }

    public static FieldSpec ranged(String name, FieldKind kind, long min, long max, String description) {
        return new FieldSpec(name, kind, true, List.of(), BigDecimal.valueOf(min), BigDecimal.valueOf(max), description);
    }
}
