package io.github.khayreen.intake.pipeline;

import java.util.EnumSet;
import java.util.Set;

/**
 * Each defence can be switched off, so tests can show the attack it stops. Production always uses
 * {@link #all()}. Keeping instructions apart from content is built into
 * {@link io.github.khayreen.intake.security.PromptBuilder} and tested there.
 */
public enum Defence {
    /** Look for text addressed to the model and flag the document. */
    INJECTION_SCREEN,
    /** Remove suspicious lines and invisible characters before the model sees the text. */
    SANITISE_TEXT,
    /** Reject any model reply outside the agreed JSON shape. */
    STRICT_SCHEMA,
    /** Format, range and cross-field rules. */
    FIELD_VALIDATION,
    /** Every value must be printed in the cleaned document. */
    GROUNDING_CHECK,
    /**
     * Compare each value with what the rules extractor read from the labelled box. On the test sets the rules
     * extractor never read a wrong value (it leaves unknown labels blank), and a hidden instruction cannot change it.
     */
    SECOND_OPINION,
    /** Any flag sends the document to a person. */
    REVIEW_ROUTING;

    public static Set<Defence> all() {
        return EnumSet.allOf(Defence.class);
    }

    public static Set<Defence> none() {
        return EnumSet.noneOf(Defence.class);
    }

    public static Set<Defence> allExcept(Defence... removed) {
        Set<Defence> s = all();
        for (Defence d : removed) {
            s.remove(d);
        }
        return s;
    }
}
