package io.github.khayreen.intake.app.llm;

import io.github.khayreen.intake.extract.LlmPort;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.List;

/**
 * Spring AI behind the pipeline's {@link LlmPort}. Which provider answers is set by
 * {@code spring.ai.model.chat}: {@code openai} for any OpenAI-compatible API such as Groq,
 * {@code bedrock-converse} for Amazon Bedrock. No tools are registered.
 */
public final class SpringAiLlm implements LlmPort {

    private final ChatModel model;
    private final String name;

    public SpringAiLlm(ChatModel model, String name) {
        this.model = model;
        this.name = name;
    }

    @Override
    public Reply complete(String system, String user) {
        // Model, temperature 0 and token limits come from application.yml for each provider. Spring AI 2.0's
        // provider models expect their own options class, so none are passed here.
        Prompt prompt = new Prompt(List.of(new SystemMessage(system), new UserMessage(user)));
        ChatResponse response = model.call(prompt);
        String text = response.getResult() == null ? null : response.getResult().getOutput().getText();
        Usage usage = response.getMetadata() == null ? null : response.getMetadata().getUsage();
        long in = usage == null || usage.getPromptTokens() == null ? 0 : usage.getPromptTokens();
        long out = usage == null || usage.getCompletionTokens() == null ? 0 : usage.getCompletionTokens();
        return new Reply(text, in, out);
    }

    @Override
    public String modelName() {
        return name;
    }
}
