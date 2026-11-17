package io.github.khayreen.intake.app.web;

import io.github.khayreen.intake.model.IntakeResult;

import java.time.Instant;
import java.util.Map;

/** A processed document and what happened to it after the pipeline. */
public record StoredDocument(String id, String fileName, Instant uploadedAt, String text, IntakeResult result,
                             Status status, Map<String, String> finalFields, String reviewNote) {

    public enum Status {
        /** No flags: accepted by the pipeline. */
        ACCEPTED,
        /** Waiting for a person. */
        PENDING_REVIEW,
        /** A person approved it, possibly after correcting fields. */
        APPROVED,
        /** A person rejected it. */
        REJECTED
    }

    public StoredDocument reviewed(Status status, Map<String, String> fields, String note) {
        return new StoredDocument(id, fileName, uploadedAt, text, result, status, fields, note);
    }
}
