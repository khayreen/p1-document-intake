package io.github.khayreen.intake.app.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param extractor "rules" (no model, no key needed) or "llm" (uses the chat model chosen by spring.ai.model.chat)
 * @param model     name shown in reports
 */
@ConfigurationProperties(prefix = "intake")
public record IntakeProperties(String extractor, String model) {

    public IntakeProperties {
        extractor = extractor == null ? "rules" : extractor;
        model = model == null ? "unknown" : model;
    }

    public boolean usesLlm() {
        return extractor.equalsIgnoreCase("llm");
    }
}
