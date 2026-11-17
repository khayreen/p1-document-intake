package io.github.khayreen.intake.app.web;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Map;

/**
 * A reviewer's decision.
 *
 * @param decision APPROVE or REJECT
 * @param fields   corrected values; fields left out keep the extracted value
 * @param note     why, for the audit trail
 */
public record ReviewRequest(@NotNull Decision decision, Map<String, String> fields, @Size(max = 500) String note) {

    public enum Decision { APPROVE, REJECT }
}
