package io.github.khayreen.intake.lambda;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import io.github.khayreen.intake.model.FormType;
import io.github.khayreen.intake.review.ReviewCorrections;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;
import software.amazon.awssdk.services.sfn.SfnClient;
import software.amazon.awssdk.services.sfn.model.SendTaskSuccessRequest;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A reviewer's decision. Called directly with {@code lambda:InvokeFunction} by someone allowed to
 * review: {@code {"documentId": "...", "decision": "APPROVE", "fields": {...}, "note": "..."}}.
 * Corrections are checked with the pipeline's own rules, the queue entry can only be decided once,
 * and the waiting workflow is then released.
 */
public class ReviewDecisionHandler implements RequestHandler<Map<String, Object>, Map<String, Object>> {

    private final DynamoDbClient dynamo;
    private final SfnClient sfn;
    private final String table;

    public ReviewDecisionHandler() {
        this(DynamoDbClient.builder().httpClient(UrlConnectionHttpClient.create()).build(),
                SfnClient.builder().httpClient(UrlConnectionHttpClient.create()).build(), System.getenv("REVIEW_TABLE"));
    }

    ReviewDecisionHandler(DynamoDbClient dynamo, SfnClient sfn, String table) {
        this.dynamo = dynamo;
        this.sfn = sfn;
        this.table = table;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> handleRequest(Map<String, Object> input, Context context) {
        String id = (String) input.get("documentId");
        String decision = String.valueOf(input.get("decision"));
        if (id == null || !(decision.equals("APPROVE") || decision.equals("REJECT"))) {
            return error("send documentId and a decision of APPROVE or REJECT");
        }
        String note = input.get("note") == null ? "" : String.valueOf(input.get("note"));
        if (note.length() > 500) {
            return error("note is longer than 500 characters");
        }

        Map<String, AttributeValue> item = dynamo.getItem(GetItemRequest.builder().tableName(table)
                .key(Map.of("documentId", AttributeValue.fromS(id))).consistentRead(true).build()).item();
        if (item == null || item.isEmpty()) {
            return error("no document " + id + " in the review queue");
        }
        if (!"PENDING_REVIEW".equals(item.get("status").s())) {
            return error("document is not waiting for review");
        }

        Map<String, Object> output = new LinkedHashMap<>();
        output.put("decision", decision);
        output.put("status", decision.equals("APPROVE") ? "APPROVED" : "REJECTED");
        output.put("note", note);
        if (decision.equals("APPROVE")) {
            FormType form;
            try {
                form = FormType.valueOf(item.get("formType").s());
            } catch (IllegalArgumentException e) {
                return error("unknown form type: reject it instead");
            }
            Map<String, Object> result = Json.read(item.get("resultJson").s());
            ReviewCorrections.Outcome outcome = ReviewCorrections.apply(form,
                    (Map<String, String>) result.get("fields"), (Map<String, String>) input.get("fields"));
            if (!outcome.ok()) {
                return error(String.join("; ", outcome.problems()));
            }
            output.put("fields", outcome.fields());
        }

        try {
            dynamo.updateItem(UpdateItemRequest.builder().tableName(table)
                    .key(Map.of("documentId", AttributeValue.fromS(id)))
                    .updateExpression("SET #s = :new, decidedAt = :at, reviewNote = :note")
                    .conditionExpression("#s = :pending")
                    .expressionAttributeNames(Map.of("#s", "status"))
                    .expressionAttributeValues(Map.of(":new", AttributeValue.fromS(decision.equals("APPROVE") ? "APPROVED" : "REJECTED"),
                            ":pending", AttributeValue.fromS("PENDING_REVIEW"),
                            ":at", AttributeValue.fromS(Instant.now().toString()),
                            ":note", AttributeValue.fromS(note)))
                    .build());
        } catch (ConditionalCheckFailedException e) {
            return error("document was reviewed by someone else");
        }
        sfn.sendTaskSuccess(SendTaskSuccessRequest.builder().taskToken(item.get("taskToken").s()).output(Json.write(output)).build());
        return Map.of("ok", true, "documentId", id, "status", decision.equals("APPROVE") ? "APPROVED" : "REJECTED");
    }

    private static Map<String, Object> error(String why) {
        return Map.of("ok", false, "error", why);
    }
}
