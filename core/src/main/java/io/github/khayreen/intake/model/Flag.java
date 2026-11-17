package io.github.khayreen.intake.model;

/**
 * One reason for review.
 *
 * @param code   what went wrong
 * @param field  the field it concerns, or null for the whole document
 * @param detail short explanation for the reviewer
 */
public record Flag(FlagCode code, String field, String detail) {

    public static Flag document(FlagCode code, String detail) {
        return new Flag(code, null, detail);
    }

    public static Flag field(FlagCode code, String field, String detail) {
        return new Flag(code, field, detail);
    }
}
