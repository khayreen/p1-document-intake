package io.github.khayreen.intake.extract;

import io.github.khayreen.intake.model.FormType;

/** Reads field values out of document text. The LLM and the rules baseline both implement this. */
public interface FieldExtractor {

    RawExtraction extract(FormType form, String documentText);

    String name();
}
