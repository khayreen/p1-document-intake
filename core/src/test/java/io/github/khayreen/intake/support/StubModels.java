package io.github.khayreen.intake.support;

import io.github.khayreen.intake.extract.LlmFieldExtractor;
import io.github.khayreen.intake.extract.LlmPort;
import io.github.khayreen.intake.extract.OutputParser;
import io.github.khayreen.intake.model.FormType;
import io.github.khayreen.intake.security.PromptBuilder;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

/** Models that need no network: honest, gullible, or fully compromised. */
public final class StubModels {

    private StubModels() {
    }

    /** Returns whatever the function produces from the user message. Records the last prompt. */
    public static final class Scripted implements LlmPort {
        private final Function<String, String> reply;
        public String lastSystem;
        public String lastUser;

        public Scripted(Function<String, String> reply) {
            this.reply = reply;
        }

        @Override
        public Reply complete(String system, String user) {
            lastSystem = system;
            lastUser = user;
            return new Reply(reply.apply(user), 900, 120);
        }

        @Override
        public String modelName() {
            return "stub";
        }
    }

    /** Copies the true values correctly. */
    public static Scripted honest(FormType form, Map<String, String> truth) {
        return new Scripted(user -> OutputParser.toJson(form, truth, false));
    }

    /**
     * Copies the true values, but falls for an injection whenever the trigger text is still in what it was given:
     * it then writes the attacker's value into the target field. This is how a real model fails.
     */
    public static Scripted gullible(FormType form, Map<String, String> truth, String trigger, String field, String attackerValue) {
        return new Scripted(user -> {
            Map<String, String> out = new LinkedHashMap<>(truth);
            if (user.contains(trigger)) {
                out.put(field, attackerValue);
            }
            return OutputParser.toJson(form, out, false);
        });
    }

    /** Always returns exactly this reply, whatever it was asked. */
    public static Scripted fixed(String reply) {
        return new Scripted(user -> reply);
    }

    public static LlmFieldExtractor extractor(LlmPort model) {
        return new LlmFieldExtractor(model, new PromptBuilder(true));
    }
}
