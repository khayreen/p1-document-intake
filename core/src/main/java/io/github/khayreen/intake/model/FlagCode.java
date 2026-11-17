package io.github.khayreen.intake.model;

/** Why a document was sent to a person instead of being accepted automatically. */
public enum FlagCode {
    /** The injection screen found text addressed to the model. */
    INJECTION_SUSPECTED,
    /** The model itself reported instructions inside the document. */
    MODEL_REPORTED_INJECTION,
    /** Invisible characters were found and removed. */
    HIDDEN_CHARACTERS,
    /** The model's output did not match the strict schema. */
    SCHEMA_VIOLATION,
    /** The model call failed or returned nothing usable. */
    MODEL_ERROR,
    /** The heading did not match a known form. */
    UNKNOWN_FORM,
    /** A value failed its format or range rule. */
    INVALID_VALUE,
    /** Two fields disagree, for example date of birth and IC number. */
    CROSS_FIELD_MISMATCH,
    /** The model's value differs from what the rules extractor read from the labelled box. */
    EXTRACTORS_DISAGREE,
    /** A value does not appear anywhere in the cleaned document text. */
    NOT_GROUNDED,
    /** A required field is empty. */
    MISSING_REQUIRED,
    /** The OCR engine was not confident about the text. */
    LOW_OCR_CONFIDENCE
}
