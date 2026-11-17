package io.github.khayreen.intake.model;

/** Tokens used by one model call. Zero for the rules baseline. */
public record Usage(String model, long inputTokens, long outputTokens) {

    public static final Usage NONE = new Usage("none", 0, 0);
}
