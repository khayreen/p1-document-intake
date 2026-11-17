package io.github.khayreen.intake.app.aws;

import io.github.khayreen.intake.app.eval.EvalRunner;
import io.github.khayreen.intake.eval.CostModel;
import io.github.khayreen.intake.eval.LabelledDocument;
import io.github.khayreen.intake.eval.ReportWriter;
import io.github.khayreen.intake.eval.Scorer;
import io.github.khayreen.intake.model.Decision;
import io.github.khayreen.intake.model.Flag;
import io.github.khayreen.intake.model.FlagCode;
import io.github.khayreen.intake.model.FormType;
import io.github.khayreen.intake.model.IntakeResult;
import io.github.khayreen.intake.model.Usage;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.cloudformation.CloudFormationClient;
import software.amazon.awssdk.services.cloudformation.model.Output;
import software.amazon.awssdk.services.cloudwatch.CloudWatchClient;
import software.amazon.awssdk.services.cloudwatch.model.MetricDatum;
import software.amazon.awssdk.services.cloudwatch.model.StandardUnit;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.s3.S3Client;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Commands that talk to the deployed stack, using your AWS CLI credentials.
 *
 * <pre>
 * aws-eval   --data=data/forms/holdout        upload labelled forms, wait, score, publish FieldAccuracy
 * aws-review                                  list documents waiting for a person
 * aws-review --id=... --decision=APPROVE [--field.monthly_income=24500] [--note=...]
 * </pre>
 */
@Component
public class AwsCommands implements ApplicationRunner, ExitCodeGenerator {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private int exitCode;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        List<String> commands = args.getNonOptionArgs();
        if (commands.isEmpty()) {
            return;
        }
        switch (commands.getFirst()) {
            case "aws-eval" -> eval(args);
            case "aws-review" -> review(args);
            default -> {
            }
        }
    }

    // ---- aws-eval -----------------------------------------------------------------------

    private void eval(ApplicationArguments args) throws IOException, InterruptedException {
        Path data = Path.of(option(args, "data", "data/forms/holdout"));
        String set = data.getFileName().toString();
        Map<String, String> outputs = stackOutputs(option(args, "stack", "DocumentIntake"));
        Duration timeout = Duration.ofMinutes(Long.parseLong(option(args, "timeout-min", "20")));
        List<LabelledDocument> docs = EvalRunner.readLabels(data.resolve("labels.jsonl"));
        String prefix = "incoming/" + set + "-" + Instant.now().getEpochSecond() + "/";

        try (S3Client s3 = S3Client.create(); DynamoDbClient dynamo = DynamoDbClient.create(); CloudWatchClient cloudWatch = CloudWatchClient.create()) {
            for (LabelledDocument doc : docs) {
                s3.putObject(b -> b.bucket(outputs.get("BucketName")).key(prefix + doc.file()).contentType("application/pdf"),
                        RequestBody.fromFile(data.resolve(doc.file())));
            }
            System.out.println("Uploaded " + docs.size() + " forms to s3://" + outputs.get("BucketName") + "/" + prefix);

            Map<String, IntakeResult> results = new LinkedHashMap<>();
            int failed = 0;
            Instant deadline = Instant.now().plus(timeout);
            while (results.size() < docs.size() && Instant.now().isBefore(deadline)) {
                for (LabelledDocument doc : docs) {
                    String id = prefix + doc.file();
                    if (results.containsKey(id)) {
                        continue;
                    }
                    Map<String, AttributeValue> item = get(dynamo, outputs.get("ResultsTable"), id);
                    if (item == null) {
                        item = get(dynamo, outputs.get("ReviewTable"), id);
                    }
                    if (item == null) {
                        continue;
                    }
                    if ("FAILED".equals(item.get("status").s())) {
                        failed++;
                        results.put(id, failedResult(id));
                    } else {
                        results.put(id, fromStored(id, item.get("resultJson").s()));
                    }
                }
                System.out.print("\r" + results.size() + "/" + docs.size() + " processed");
                if (results.size() < docs.size()) {
                    Thread.sleep(10_000);
                }
            }
            System.out.println();
            if (results.size() < docs.size()) {
                System.err.println("Stopped: only " + results.size() + " of " + docs.size() + " documents finished in " + timeout.toMinutes()
                        + " minutes. Check the Step Functions console for failed executions. No results were written.");
                exitCode = 3;
                return;
            }

            List<IntakeResult> ordered = docs.stream().map(d -> results.get(prefix + d.file())).toList();
            Scorer.Report report = Scorer.score("aws:" + modelOf(ordered), docs, ordered, CostModel.BEDROCK_HAIKU_TEXTRACT);
            Path out = Path.of("results");
            Files.createDirectories(out);
            String md = ReportWriter.markdown("Evaluation on AWS: " + set + " set (Textract + Bedrock)", report);
            if (failed > 0) {
                md = md.replaceFirst("\n\n", "\n\n> " + failed + " execution(s) failed and are counted as wrong.\n\n");
            }
            Files.writeString(out.resolve(set + "-aws.md"), md);
            System.out.print(ReportWriter.summary(report));
            System.out.println("  report              : results/" + set + "-aws.md");

            if (failed >= 3) {
                System.err.println(failed + " executions failed, so the accuracy metric was not published.");
                exitCode = 4;
                return;
            }
            cloudWatch.putMetricData(b -> b.namespace("DocumentIntake").metricData(
                    MetricDatum.builder().metricName("FieldAccuracy").value(report.fieldAccuracy()).unit(StandardUnit.PERCENT).build(),
                    MetricDatum.builder().metricName("EvalReviewRate").value(report.reviewRate()).unit(StandardUnit.PERCENT).build()));
            System.out.println("  published FieldAccuracy to the DocumentIntake dashboard");
        }
    }

    private static Map<String, AttributeValue> get(DynamoDbClient dynamo, String table, String id) {
        Map<String, AttributeValue> item = dynamo.getItem(b -> b.tableName(table).key(Map.of("documentId", AttributeValue.fromS(id))).consistentRead(true)).item();
        return item == null || item.isEmpty() ? null : item;
    }

    @SuppressWarnings("unchecked")
    static IntakeResult fromStored(String id, String json) {
        Map<String, Object> m = JSON.readValue(json, Map.class);
        String type = (String) m.get("formType");
        FormType form = type == null || type.equals("UNKNOWN") ? null : FormType.valueOf(type);
        List<Flag> flags = new ArrayList<>();
        for (Map<String, Object> f : (List<Map<String, Object>>) m.getOrDefault("flags", List.of())) {
            String field = (String) f.get("field");
            flags.add(new Flag(FlagCode.valueOf((String) f.get("code")), field == null || field.isEmpty() ? null : field, (String) f.get("detail")));
        }
        Usage usage = new Usage(String.valueOf(m.getOrDefault("model", "bedrock")),
                ((Number) m.getOrDefault("inputTokens", 0)).longValue(), ((Number) m.getOrDefault("outputTokens", 0)).longValue());
        Decision decision = Decision.valueOf(String.valueOf(m.getOrDefault("decision", flags.isEmpty() ? "AUTO_ACCEPT" : "HUMAN_REVIEW")));
        return new IntakeResult(id, form, (Map<String, String>) m.get("fields"), flags, decision, usage,
                ((Number) m.getOrDefault("ocrPages", 1)).intValue());
    }

    private static IntakeResult failedResult(String id) {
        return new IntakeResult(id, null, Map.of(), List.of(Flag.document(FlagCode.MODEL_ERROR, "execution failed")),
                Decision.HUMAN_REVIEW, Usage.NONE, 1);
    }

    private static String modelOf(List<IntakeResult> results) {
        return results.stream().map(r -> r.usage().model()).filter(m -> !m.equals("none")).findFirst().orElse("bedrock");
    }

    // ---- aws-review ---------------------------------------------------------------------

    private void review(ApplicationArguments args) {
        Map<String, String> outputs = stackOutputs(option(args, "stack", "DocumentIntake"));
        if (!args.containsOption("id")) {
            try (DynamoDbClient dynamo = DynamoDbClient.create()) {
                var items = dynamo.scan(b -> b.tableName(outputs.get("ReviewTable"))
                        .filterExpression("#s = :p").expressionAttributeNames(Map.of("#s", "status"))
                        .expressionAttributeValues(Map.of(":p", AttributeValue.fromS("PENDING_REVIEW")))).items();
                if (items.isEmpty()) {
                    System.out.println("Nothing is waiting for review.");
                }
                for (var item : items) {
                    IntakeResult r = fromStored(item.get("documentId").s(), item.get("resultJson").s());
                    System.out.println(item.get("documentId").s() + "  " + item.get("formType").s());
                    r.flags().forEach(f -> System.out.println("    " + f.code() + (f.field() == null ? "" : " " + f.field()) + ": " + f.detail()));
                }
            }
            return;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("documentId", option(args, "id", null));
        payload.put("decision", option(args, "decision", "").toUpperCase());
        payload.put("note", option(args, "note", ""));
        Map<String, String> fields = new LinkedHashMap<>();
        args.getOptionNames().stream().filter(n -> n.startsWith("field.")).forEach(n -> fields.put(n.substring(6), args.getOptionValues(n).getFirst()));
        payload.put("fields", fields);
        try (LambdaClient lambda = LambdaClient.create()) {
            var response = lambda.invoke(b -> b.functionName(outputs.get("ReviewFunction")).payload(SdkBytes.fromUtf8String(JSON.writeValueAsString(payload))));
            String body = response.payload().asUtf8String();
            System.out.println(body);
            if (!body.contains("\"ok\":true")) {
                exitCode = 1;
            }
        }
    }

    // ---- shared -------------------------------------------------------------------------

    private static Map<String, String> stackOutputs(String stack) {
        try (CloudFormationClient cfn = CloudFormationClient.create()) {
            Map<String, String> out = new LinkedHashMap<>();
            for (Output o : cfn.describeStacks(b -> b.stackName(stack)).stacks().getFirst().outputs()) {
                out.put(o.outputKey(), o.outputValue());
            }
            return out;
        }
    }

    private static String option(ApplicationArguments args, String name, String fallback) {
        return args.containsOption(name) ? args.getOptionValues(name).getFirst() : fallback;
    }

    @Override
    public int getExitCode() {
        return exitCode;
    }
}
