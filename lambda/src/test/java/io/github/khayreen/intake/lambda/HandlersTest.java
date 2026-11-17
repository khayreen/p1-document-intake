package io.github.khayreen.intake.lambda;

import io.github.khayreen.intake.extract.LlmPort;
import io.github.khayreen.intake.extract.OutputParser;
import io.github.khayreen.intake.model.FormType;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.PutItemResponse;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemResponse;
import software.amazon.awssdk.services.sfn.SfnClient;
import software.amazon.awssdk.services.sfn.model.SendTaskSuccessRequest;
import software.amazon.awssdk.services.sfn.model.SendTaskSuccessResponse;
import software.amazon.awssdk.services.textract.TextractClient;
import software.amazon.awssdk.services.textract.model.Block;
import software.amazon.awssdk.services.textract.model.BlockType;
import software.amazon.awssdk.services.textract.model.DetectDocumentTextRequest;
import software.amazon.awssdk.services.textract.model.DetectDocumentTextResponse;
import software.amazon.awssdk.services.textract.model.DocumentMetadata;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The Lambda handlers with in-memory fakes of Textract, DynamoDB and Step Functions. */
class HandlersTest {

    static final String FORM = """
            BANK CONTOH BERHAD
            LOAN APPLICATION FORM
            Full Name: MOHD AZRUL BIN OTHMAN
            MyKad No.: 020426-13-9459
            Loan Type: [ ] Personal [ ] Housing [X] Car
            Loan Amount: RM 190,000.00
            Tenure (months): 108 months
            Gross Monthly Income: 24,500.00
            Employer: Sekolah Kebangsaan Taman Permai
            Remarks: -""";

    static Map<String, String> truth() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("full_name", "MOHD AZRUL BIN OTHMAN");
        m.put("ic_number", "020426-13-9459");
        m.put("loan_type", "CAR");
        m.put("loan_amount", "190000");
        m.put("tenure_months", "108");
        m.put("monthly_income", "24500");
        m.put("employer_name", "Sekolah Kebangsaan Taman Permai");
        return m;
    }

    // ---- Textract ----------------------------------------------------------------------

    @Test
    void readTextJoinsLinesAndAveragesConfidence() {
        TextractClient textract = new TextractClient() {
            @Override
            public DetectDocumentTextResponse detectDocumentText(DetectDocumentTextRequest request) {
                assertThat(request.document().s3Object().name()).isEqualTo("incoming/a.pdf");
                return DetectDocumentTextResponse.builder()
                        .documentMetadata(DocumentMetadata.builder().pages(1).build())
                        .blocks(Block.builder().blockType(BlockType.PAGE).build(),
                                Block.builder().blockType(BlockType.LINE).text("BANK CONTOH BERHAD").confidence(99f).build(),
                                Block.builder().blockType(BlockType.WORD).text("BANK").confidence(10f).build(),
                                Block.builder().blockType(BlockType.LINE).text("LOAN APPLICATION FORM").confidence(91f).build())
                        .build();
            }

            @Override
            public String serviceName() {
                return "textract";
            }

            @Override
            public void close() {
            }
        };
        Map<String, Object> out = new ReadTextHandler(textract).handleRequest(Map.of("bucket", "b", "key", "incoming/a.pdf"), null);
        assertThat(out.get("text")).isEqualTo("BANK CONTOH BERHAD\nLOAN APPLICATION FORM");
        assertThat((double) out.get("confidence")).isEqualTo(95.0);
        assertThat(out.get("documentId")).isEqualTo("incoming/a.pdf");
    }

    @Test
    void readTextOnlyAcceptsTheIncomingPrefix() {
        assertThatThrownBy(() -> new ReadTextHandler(null).handleRequest(Map.of("bucket", "b", "key", "results/x.pdf"), null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ---- Extraction --------------------------------------------------------------------

    private static LlmPort honest() {
        return new LlmPort() {
            @Override
            public Reply complete(String system, String user) {
                return new Reply(OutputParser.toJson(FormType.LOAN_APPLICATION, truth(), false), 950, 140);
            }

            @Override
            public String modelName() {
                return "stub";
            }
        };
    }

    @Test
    void extractRunsTheSamePipelineAndWritesMetricsWithoutCustomerData() {
        ByteArrayOutputStream log = new ByteArrayOutputStream();
        Map<String, Object> out = new ExtractHandler(honest(), new PrintStream(log))
                .handleRequest(Map.of("documentId", "incoming/a.pdf", "text", FORM, "confidence", 98.5, "pages", 1), null);

        assertThat(out.get("decision")).isEqualTo("AUTO_ACCEPT");
        assertThat(out.get("formType")).isEqualTo("LOAN_APPLICATION");
        assertThat(out.get("inputTokens")).isEqualTo(950L);

        String metrics = log.toString();
        Map<String, Object> emf = Json.read(metrics.strip());
        assertThat(emf).containsEntry("DocumentsProcessed", 1).containsEntry("SentToReview", 0).containsKey("_aws");
        assertThat(metrics).doesNotContain("020426").doesNotContain("AZRUL").doesNotContain("24500");
    }

    @Test
    void extractSendsLowConfidenceScansToReview() {
        Map<String, Object> out = new ExtractHandler(honest(), new PrintStream(new ByteArrayOutputStream()))
                .handleRequest(Map.of("documentId", "incoming/a.pdf", "text", FORM, "confidence", 55.0, "pages", 1), null);
        assertThat(out.get("decision")).isEqualTo("HUMAN_REVIEW");
    }

    // ---- Review queue ------------------------------------------------------------------

    /** A one-table DynamoDB that honours the "status = PENDING_REVIEW" condition. */
    static final class FakeDynamo implements DynamoDbClient {
        final Map<String, Map<String, AttributeValue>> items = new HashMap<>();

        @Override
        public PutItemResponse putItem(PutItemRequest r) {
            items.put(r.item().get("documentId").s(), new HashMap<>(r.item()));
            return PutItemResponse.builder().build();
        }

        @Override
        public GetItemResponse getItem(GetItemRequest r) {
            return GetItemResponse.builder().item(items.get(r.key().get("documentId").s())).build();
        }

        @Override
        public UpdateItemResponse updateItem(UpdateItemRequest r) {
            Map<String, AttributeValue> item = items.get(r.key().get("documentId").s());
            if (!item.get("status").s().equals(r.expressionAttributeValues().get(":pending").s())) {
                throw ConditionalCheckFailedException.builder().message("condition").build();
            }
            item.put("status", r.expressionAttributeValues().get(":new"));
            return UpdateItemResponse.builder().build();
        }

        @Override
        public String serviceName() {
            return "dynamodb";
        }

        @Override
        public void close() {
        }
    }

    static final class FakeSfn implements SfnClient {
        final List<SendTaskSuccessRequest> sent = new ArrayList<>();

        @Override
        public SendTaskSuccessResponse sendTaskSuccess(SendTaskSuccessRequest r) {
            sent.add(r);
            return SendTaskSuccessResponse.builder().build();
        }

        @Override
        public String serviceName() {
            return "sfn";
        }

        @Override
        public void close() {
        }
    }

    private static Map<String, Object> queuedExtraction() {
        Map<String, String> fields = truth();
        fields.put("monthly_income", null);
        Map<String, Object> e = new LinkedHashMap<>();
        e.put("documentId", "incoming/a.pdf");
        e.put("formType", "LOAN_APPLICATION");
        e.put("resultJson", Json.write(Map.of("formType", "LOAN_APPLICATION", "fields", fields, "flags", List.of())));
        return e;
    }

    @Test
    void reviewQueueAndDecisionReleaseTheWorkflowOnce() {
        FakeDynamo dynamo = new FakeDynamo();
        FakeSfn sfn = new FakeSfn();
        new ReviewTaskHandler(dynamo, "review").handleRequest(Map.of("taskToken", "token-123", "extraction", queuedExtraction()), null);
        assertThat(dynamo.items.get("incoming/a.pdf").get("status").s()).isEqualTo("PENDING_REVIEW");

        ReviewDecisionHandler decide = new ReviewDecisionHandler(dynamo, sfn, "review");

        Map<String, Object> missing = decide.handleRequest(Map.of("documentId", "incoming/a.pdf", "decision", "APPROVE"), null);
        assertThat(missing).containsEntry("ok", false);
        assertThat(String.valueOf(missing.get("error"))).contains("monthly_income is required");

        Map<String, Object> invented = decide.handleRequest(Map.of("documentId", "incoming/a.pdf", "decision", "APPROVE",
                "fields", Map.of("monthly_income", "24500", "credit_limit", "500000")), null);
        assertThat(String.valueOf(invented.get("error"))).contains("unknown field credit_limit");
        assertThat(sfn.sent).isEmpty();

        Map<String, Object> ok = decide.handleRequest(Map.of("documentId", "incoming/a.pdf", "decision", "APPROVE",
                "fields", Map.of("monthly_income", "RM 24,500"), "note", "checked"), null);
        assertThat(ok).containsEntry("ok", true).containsEntry("status", "APPROVED");
        assertThat(sfn.sent).hasSize(1);
        assertThat(sfn.sent.getFirst().taskToken()).isEqualTo("token-123");
        assertThat(sfn.sent.getFirst().output()).contains("\"monthly_income\":\"24500.00\"");

        Map<String, Object> again = decide.handleRequest(Map.of("documentId", "incoming/a.pdf", "decision", "REJECT"), null);
        assertThat(again).containsEntry("ok", false);
        assertThat(sfn.sent).hasSize(1);
    }

    @Test
    void decisionMustBeApproveOrReject() {
        Map<String, Object> r = new ReviewDecisionHandler(new FakeDynamo(), new FakeSfn(), "review")
                .handleRequest(Map.of("documentId", "x", "decision", "AUTO_APPROVE_ALL"), null);
        assertThat(r).containsEntry("ok", false);
    }
}
