package io.github.khayreen.intake.lambda;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.services.textract.TextractClient;
import software.amazon.awssdk.services.textract.model.Block;
import software.amazon.awssdk.services.textract.model.BlockType;
import software.amazon.awssdk.services.textract.model.DetectDocumentTextRequest;
import software.amazon.awssdk.services.textract.model.DetectDocumentTextResponse;
import software.amazon.awssdk.services.textract.model.Document;
import software.amazon.awssdk.services.textract.model.S3Object;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Step 1: OCR with Textract. Input {@code {"bucket": "...", "key": "incoming/..."}}.
 * Output adds the text, the mean line confidence and the page count.
 */
public class ReadTextHandler implements RequestHandler<Map<String, Object>, Map<String, Object>> {

    private final TextractClient textract;

    public ReadTextHandler() {
        this(TextractClient.builder().httpClient(UrlConnectionHttpClient.create()).build());
    }

    ReadTextHandler(TextractClient textract) {
        this.textract = textract;
    }

    @Override
    public Map<String, Object> handleRequest(Map<String, Object> input, Context context) {
        String bucket = (String) input.get("bucket");
        String key = (String) input.get("key");
        if (bucket == null || key == null || !key.startsWith("incoming/")) {
            throw new IllegalArgumentException("expected a bucket and a key under incoming/");
        }
        DetectDocumentTextResponse response = textract.detectDocumentText(DetectDocumentTextRequest.builder()
                .document(Document.builder().s3Object(S3Object.builder().bucket(bucket).name(key).build()).build())
                .build());

        List<Block> lines = response.blocks().stream().filter(b -> b.blockType() == BlockType.LINE).toList();
        String text = String.join("\n", lines.stream().map(Block::text).toList());
        double confidence = lines.stream().mapToDouble(b -> b.confidence() == null ? 0 : b.confidence()).average().orElse(0);
        int pages = response.documentMetadata() == null || response.documentMetadata().pages() == null ? 1 : response.documentMetadata().pages();

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("documentId", key);
        out.put("bucket", bucket);
        out.put("key", key);
        out.put("text", text);
        out.put("confidence", confidence);
        out.put("pages", pages);
        return out;
    }
}
