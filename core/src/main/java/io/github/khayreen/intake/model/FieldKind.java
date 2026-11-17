package io.github.khayreen.intake.model;

/** What a field holds. Decides how a value is normalised, validated and grounded. */
public enum FieldKind {
    NAME,
    IC_NUMBER,
    DATE,
    PHONE,
    EMAIL,
    ADDRESS,
    MONEY,
    INTEGER,
    CHOICE,
    ACCOUNT_NUMBER,
    TEXT;

    /** Kinds whose value is mostly digits, so OCR letter/digit confusions are repaired. */
    public boolean isNumeric() {
        return this == IC_NUMBER || this == PHONE || this == MONEY || this == INTEGER || this == ACCOUNT_NUMBER;
    }
}
