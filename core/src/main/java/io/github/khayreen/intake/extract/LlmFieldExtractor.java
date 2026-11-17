package io.github.khayreen.intake.extract;

import io.github.khayreen.intake.model.FormType;
import io.github.khayreen.intake.model.Usage;
import io.github.khayreen.intake.security.PromptBuilder;

/** Asks the model for the fields. Everything it returns is checked afterwards by the pipeline. */
public final class LlmFieldExtractor implements FieldExtractor {

    private final LlmPort llm;
    private final PromptBuilder prompts;

    public LlmFieldExtractor(LlmPort llm, PromptBuilder prompts) {
        this.llm = llm;
        this.prompts = prompts;
    }

    @Override
    public RawExtraction extract(FormType form, String documentText) {
        PromptBuilder.Prompt prompt = prompts.build(form, documentText);
        try {
            LlmPort.Reply reply = llm.complete(prompt.system(), prompt.user());
            Usage usage = new Usage(llm.modelName(), reply.inputTokens(), reply.outputTokens());
            if (reply.text() == null || reply.text().isBlank()) {
                return RawExtraction.failed("model returned no text", usage);
            }
            return RawExtraction.ok(reply.text(), usage);
        } catch (RuntimeException e) {
            return RawExtraction.failed("model call failed: " + e.getClass().getSimpleName(), new Usage(llm.modelName(), 0, 0));
        }
    }

    @Override
    public String name() {
        return "llm:" + llm.modelName();
    }
}
