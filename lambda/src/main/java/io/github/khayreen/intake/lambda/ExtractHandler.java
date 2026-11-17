package io.github.khayreen.intake.lambda;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import io.github.khayreen.intake.eval.CostModel;
import io.github.khayreen.intake.extract.LlmFieldExtractor;
import io.github.khayreen.intake.extract.LlmPort;
import io.github.khayreen.intake.model.Decision;
import io.github.khayreen.intake.model.FlagCode;
import io.github.khayreen.intake.model.IntakeResult;
import io.github.khayreen.intake.model.TextExtraction;
import io.github.khayreen.intake.pipeline.IntakePipeline;
import io.github.khayreen.intake.security.PromptBuilder;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;

import java.io.PrintStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Step 2: the same pipeline as the local service, with Bedrock as the model. Writes CloudWatch
 * metrics as embedded-metric-format log lines, so it needs no CloudWatch permission. Field values
 * are never logged.
 */
public class ExtractHandler implements RequestHandler<Map<String, Object>, Map<String, Object>> {

    static final String NAMESPACE = "DocumentIntake";

    private final IntakePipeline pipeline;
    private final PrintStream metricsOut;

    public ExtractHandler() {
        this(new BedrockLlm(BedrockRuntimeClient.builder().httpClient(UrlConnectionHttpClient.create()).build(),
                System.getenv("BEDROCK_MODEL_ID")), System.out);
    }

    ExtractHandler(LlmPort llm, PrintStream metricsOut) {
        this.pipeline = new IntakePipeline(new LlmFieldExtractor(llm, new PromptBuilder(true)));
        this.metricsOut = metricsOut;
    }

    @Override
    public Map<String, Object> handleRequest(Map<String, Object> input, Context context) {
        String id = (String) input.get("documentId");
        TextExtraction text = new TextExtraction((String) input.get("text"),
                ((Number) input.get("confidence")).doubleValue(), ((Number) input.get("pages")).intValue());

        IntakeResult result = pipeline.process(id, text);
        emitMetrics(result);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("documentId", id);
        out.put("formType", result.formType() == null ? "UNKNOWN" : result.formType().name());
        out.put("decision", result.decision().name());
        out.put("fields", result.fields());
        out.put("flags", result.flags().stream().map(f -> Map.of("code", f.code().name(),
                "field", f.field() == null ? "" : f.field(), "detail", f.detail())).toList());
        Map<String, Object> stored = new LinkedHashMap<>();
        stored.put("formType", out.get("formType"));
        stored.put("decision", out.get("decision"));
        stored.put("fields", result.fields());
        stored.put("flags", out.get("flags"));
        stored.put("model", result.usage().model());
        stored.put("inputTokens", result.usage().inputTokens());
        stored.put("outputTokens", result.usage().outputTokens());
        stored.put("ocrPages", result.ocrPages());
        out.put("resultJson", Json.write(stored));
        out.put("inputTokens", result.usage().inputTokens());
        out.put("outputTokens", result.usage().outputTokens());
        return out;
    }

    /** One EMF line per document: CloudWatch turns the numbers into metrics. */
    void emitMetrics(IntakeResult r) {
        double cost = CostModel.BEDROCK_HAIKU_TEXTRACT.cost(r);
        Map<String, Object> metric = new LinkedHashMap<>();
        metric.put("_aws", Map.of("Timestamp", System.currentTimeMillis(),
                "CloudWatchMetrics", List.of(Map.of("Namespace", NAMESPACE, "Dimensions", List.of(List.of()),
                        "Metrics", List.of(
                                Map.of("Name", "DocumentsProcessed", "Unit", "Count"),
                                Map.of("Name", "SentToReview", "Unit", "Count"),
                                Map.of("Name", "InjectionFlagged", "Unit", "Count"),
                                Map.of("Name", "ModelErrors", "Unit", "Count"),
                                Map.of("Name", "InputTokens", "Unit", "Count"),
                                Map.of("Name", "OutputTokens", "Unit", "Count"),
                                Map.of("Name", "EstimatedCostUsd", "Unit", "None"))))));
        metric.put("DocumentsProcessed", 1);
        metric.put("SentToReview", r.decision() == Decision.HUMAN_REVIEW ? 1 : 0);
        metric.put("InjectionFlagged", r.hasFlag(FlagCode.INJECTION_SUSPECTED) || r.hasFlag(FlagCode.MODEL_REPORTED_INJECTION) ? 1 : 0);
        metric.put("ModelErrors", r.hasFlag(FlagCode.MODEL_ERROR) ? 1 : 0);
        metric.put("InputTokens", r.usage().inputTokens());
        metric.put("OutputTokens", r.usage().outputTokens());
        metric.put("EstimatedCostUsd", cost);
        metric.put("flagCodes", r.flags().stream().map(f -> f.code().name()).distinct().toList());
        metricsOut.println(Json.write(metric));
    }
}
