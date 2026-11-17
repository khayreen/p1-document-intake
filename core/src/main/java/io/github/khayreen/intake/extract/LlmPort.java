package io.github.khayreen.intake.extract;

/**
 * The only thing the pipeline can ask a model to do: read two messages and return text.
 * There are no tools, no function calls and no way for the model to act on anything.
 * Spring AI implements this locally (OpenAI-compatible) and on AWS (Bedrock Converse).
 */
public interface LlmPort {

    Reply complete(String system, String user);

    String modelName();

    record Reply(String text, long inputTokens, long outputTokens) {
    }
}
