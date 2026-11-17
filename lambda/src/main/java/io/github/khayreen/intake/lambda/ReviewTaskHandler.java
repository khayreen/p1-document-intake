package io.github.khayreen.intake.lambda;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;

/**
 * Step 3, only for flagged documents: puts the document in the review queue with the Step Functions
 * task token, then the workflow waits (up to 7 days) for a person to decide.
 */
public class ReviewTaskHandler implements RequestHandler<Map<String, Object>, Map<String, Object>> {

    private final DynamoDbClient dynamo;
    private final String table;

    public ReviewTaskHandler() {
        this(DynamoDbClient.builder().httpClient(UrlConnectionHttpClient.create()).build(), System.getenv("REVIEW_TABLE"));
    }

    ReviewTaskHandler(DynamoDbClient dynamo, String table) {
        this.dynamo = dynamo;
        this.table = table;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> handleRequest(Map<String, Object> input, Context context) {
        String token = (String) input.get("taskToken");
        Map<String, Object> extraction = (Map<String, Object>) input.get("extraction");
        if (token == null || extraction == null) {
            throw new IllegalArgumentException("expected taskToken and extraction");
        }
        Instant now = Instant.now();
        dynamo.putItem(PutItemRequest.builder().tableName(table).item(Map.of(
                "documentId", s((String) extraction.get("documentId")),
                "status", s("PENDING_REVIEW"),
                "formType", s((String) extraction.get("formType")),
                "resultJson", s((String) extraction.get("resultJson")),
                "taskToken", s(token),
                "createdAt", s(now.toString()),
                "expiresAt", AttributeValue.fromN(String.valueOf(now.plus(30, ChronoUnit.DAYS).getEpochSecond()))))
                .build());
        return Map.of("queued", extraction.get("documentId"));
    }

    private static AttributeValue s(String v) {
        return AttributeValue.fromS(v == null ? "" : v);
    }
}
