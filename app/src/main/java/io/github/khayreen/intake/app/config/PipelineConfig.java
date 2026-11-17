package io.github.khayreen.intake.app.config;

import io.github.khayreen.intake.app.llm.SpringAiLlm;
import io.github.khayreen.intake.extract.FieldExtractor;
import io.github.khayreen.intake.extract.LlmFieldExtractor;
import io.github.khayreen.intake.extract.RulesExtractor;
import io.github.khayreen.intake.pipeline.IntakePipeline;
import io.github.khayreen.intake.security.PromptBuilder;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PipelineConfig {

    @Bean
    FieldExtractor fieldExtractor(IntakeProperties props, ObjectProvider<ChatModel> chatModel) {
        if (!props.usesLlm()) {
            return new RulesExtractor();
        }
        ChatModel model = chatModel.getIfAvailable();
        if (model == null) {
            throw new IllegalStateException("intake.extractor=llm needs a chat model. Set SPRING_AI_MODEL_CHAT to openai or bedrock-converse.");
        }
        return new LlmFieldExtractor(new SpringAiLlm(model, props.model()), new PromptBuilder(true));
    }

    @Bean
    IntakePipeline intakePipeline(FieldExtractor extractor) {
        return new IntakePipeline(extractor);
    }
}
