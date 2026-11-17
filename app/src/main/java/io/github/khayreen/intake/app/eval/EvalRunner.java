package io.github.khayreen.intake.app.eval;

import io.github.khayreen.intake.app.config.IntakeProperties;
import io.github.khayreen.intake.app.llm.SpringAiLlm;
import io.github.khayreen.intake.app.ocr.PdfTextReader;
import io.github.khayreen.intake.eval.CostModel;
import io.github.khayreen.intake.eval.LabelledDocument;
import io.github.khayreen.intake.eval.ReportWriter;
import io.github.khayreen.intake.eval.Scorer;
import io.github.khayreen.intake.model.FlagCode;
import io.github.khayreen.intake.model.FormType;
import io.github.khayreen.intake.model.IntakeResult;
import io.github.khayreen.intake.pipeline.IntakePipeline;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@code java -jar intake-app.jar eval --data=data/forms/main}
 *
 * <p>Runs every labelled form through the pipeline and writes {@code results/<set>-<extractor>.md}.
 * With the LLM extractor it first makes one test call and stops with a plain reason if it fails,
 * and it stops if three documents in a row get no answer from the model, so an outage is never
 * reported as an accuracy figure.
 */
@Component
public class EvalRunner implements ApplicationRunner, ExitCodeGenerator {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final IntakePipeline pipeline;
    private final PdfTextReader reader;
    private final IntakeProperties props;
    private final ObjectProvider<ChatModel> chatModel;
    private int exitCode;

    public EvalRunner(IntakePipeline pipeline, PdfTextReader reader, IntakeProperties props, ObjectProvider<ChatModel> chatModel) {
        this.pipeline = pipeline;
        this.reader = reader;
        this.props = props;
        this.chatModel = chatModel;
    }

    @Override
    public void run(ApplicationArguments args) throws IOException {
        if (args.getNonOptionArgs().isEmpty() || !args.getNonOptionArgs().getFirst().equals("eval")) {
            return;
        }
        Path data = Path.of(option(args, "data", "data/forms/main"));
        String set = data.getFileName().toString();
        long delayMs = Long.parseLong(option(args, "delay-ms", "0"));

        if (props.usesLlm() && !preflight()) {
            exitCode = 2;
            return;
        }

        List<LabelledDocument> docs = readLabels(data.resolve("labels.jsonl"));
        List<IntakeResult> results = new ArrayList<>();
        int consecutiveErrors = 0;
        int modelErrors = 0;
        for (LabelledDocument doc : docs) {
            IntakeResult r = pipeline.process(doc.file(), reader.read(Files.readAllBytes(data.resolve(doc.file()))));
            results.add(r);
            if (r.hasFlag(FlagCode.MODEL_ERROR)) {
                modelErrors++;
                if (++consecutiveErrors >= 3) {
                    System.err.println("Stopped: the model failed on three documents in a row. Last error: "
                            + r.flags().stream().filter(f -> f.code() == FlagCode.MODEL_ERROR).findFirst().orElseThrow().detail());
                    System.err.println("No results were written. Check the key, the model name and your rate limit, then run again.");
                    exitCode = 3;
                    return;
                }
            } else {
                consecutiveErrors = 0;
            }
            System.out.print("\r" + results.size() + "/" + docs.size());
            if (delayMs > 0) {
                sleep(delayMs);
            }
        }
        System.out.println();

        Scorer.Report report = Scorer.score(pipeline.extractorName(), docs, results, CostModel.BEDROCK_HAIKU_TEXTRACT);
        String name = set + "-" + (props.usesLlm() ? "llm" : "rules");
        Path out = Path.of("results");
        Files.createDirectories(out);
        String title = "Evaluation: " + set + " set, " + (props.usesLlm() ? "LLM (" + props.model() + ")" : "rules baseline");
        String md = ReportWriter.markdown(title, report);
        if (modelErrors > 0) {
            md = md.replaceFirst("\n\n", "\n\n> " + modelErrors + " document(s) got no answer from the model and were sent to review.\n\n");
        }
        Files.writeString(out.resolve(name + ".md"), md);
        Files.writeString(out.resolve(name + ".json"), JSON.writerWithDefaultPrettyPrinter().writeValueAsString(summary(report, modelErrors)));
        System.out.print(ReportWriter.summary(report));
        if (modelErrors > 0) {
            System.out.println("  model errors        : " + modelErrors);
        }
        System.out.println("  report              : results/" + name + ".md");
    }

    private boolean preflight() {
        ChatModel model = chatModel.getIfAvailable();
        if (model == null) {
            System.err.println("Stopped: intake.extractor=llm but no chat model is configured. Set SPRING_AI_MODEL_CHAT=openai and GROQ_API_KEY.");
            return false;
        }
        try {
            String reply = new SpringAiLlm(model, props.model()).complete("Reply with the single word OK.", "Ready?").text();
            if (reply == null || reply.isBlank()) {
                System.err.println("Stopped: the test call to the model returned no text.");
                return false;
            }
            return true;
        } catch (RuntimeException e) {
            System.err.println("Stopped: the test call to the model failed: " + e.getMessage());
            System.err.println("Check the API key in .env, the model name (LLM_MODEL) and the provider's model list.");
            return false;
        }
    }

    public static List<LabelledDocument> readLabels(Path file) throws IOException {
        List<LabelledDocument> docs = new ArrayList<>();
        for (String line : Files.readAllLines(file)) {
            if (line.isBlank()) {
                continue;
            }
            JsonNode n = JSON.readTree(line);
            Map<String, String> fields = new LinkedHashMap<>();
            n.get("fields").propertyNames().forEach(k -> fields.put(k, n.get("fields").get(k).isNull() ? null : n.get("fields").get(k).asString()));
            Map<String, String> target = new LinkedHashMap<>();
            if (n.has("attack_target")) {
                n.get("attack_target").propertyNames().forEach(k -> target.put(k, n.get("attack_target").get(k).asString()));
            }
            docs.add(new LabelledDocument(n.get("file").asString(), FormType.valueOf(n.get("form_type").asString()), fields,
                    n.has("attack_style") ? n.get("attack_style").asString() : null, target));
        }
        return docs;
    }

    private static Map<String, Object> summary(Scorer.Report r, int modelErrors) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("extractor", r.extractor());
        m.put("documents", r.documents());
        m.put("field_accuracy", round(r.fieldAccuracy()));
        m.put("document_accuracy", round(r.documentAccuracy()));
        m.put("review_rate", round(r.reviewRate()));
        m.put("auto_accepted", r.autoAccepted());
        m.put("auto_accepted_with_error", r.autoAcceptedWithError());
        m.put("input_tokens", r.inputTokens());
        m.put("output_tokens", r.outputTokens());
        m.put("cost_per_document_usd", r.costPerDocument());
        m.put("model_errors", modelErrors);
        if (!r.attacks().isEmpty()) {
            m.put("attacks", r.attacks().size());
            m.put("attacks_sent_to_review", r.attacks().stream().filter(Scorer.AttackOutcome::flagged).count());
            m.put("attacks_model_followed", r.attacks().stream().filter(Scorer.AttackOutcome::modelFollowed).count());
            m.put("attacker_value_auto_accepted", r.attacks().stream().filter(Scorer.AttackOutcome::attackerValueAccepted).count());
        }
        return m;
    }

    private static double round(double v) {
        return Math.round(v * 10) / 10.0;
    }

    private static String option(ApplicationArguments args, String name, String fallback) {
        return args.containsOption(name) ? args.getOptionValues(name).getFirst() : fallback;
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public int getExitCode() {
        return exitCode;
    }
}
