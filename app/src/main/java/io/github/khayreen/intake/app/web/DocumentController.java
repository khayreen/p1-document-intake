package io.github.khayreen.intake.app.web;

import io.github.khayreen.intake.app.ocr.PdfTextReader;
import io.github.khayreen.intake.model.Decision;
import io.github.khayreen.intake.model.FormType;
import io.github.khayreen.intake.model.IntakeResult;
import io.github.khayreen.intake.model.TextExtraction;
import io.github.khayreen.intake.pipeline.IntakePipeline;
import io.github.khayreen.intake.review.ReviewCorrections;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private static final Logger log = LoggerFactory.getLogger(DocumentController.class);
    private static final long MAX_BYTES = 5 * 1024 * 1024;

    private final IntakePipeline pipeline;
    private final PdfTextReader reader;
    private final DocumentStore store;

    public DocumentController(IntakePipeline pipeline, PdfTextReader reader, DocumentStore store) {
        this.pipeline = pipeline;
        this.reader = reader;
        this.store = store;
    }

    @PostMapping(consumes = "multipart/form-data")
    public StoredDocument upload(@RequestParam("file") MultipartFile file) throws IOException {
        byte[] bytes = file.getBytes();
        if (bytes.length == 0 || bytes.length > MAX_BYTES || !startsWith(bytes, "%PDF")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "upload a PDF under 5 MB");
        }
        String id = UUID.randomUUID().toString();
        TextExtraction text = reader.read(bytes);
        IntakeResult result = pipeline.process(id, text);
        StoredDocument.Status status = result.decision() == Decision.AUTO_ACCEPT
                ? StoredDocument.Status.ACCEPTED : StoredDocument.Status.PENDING_REVIEW;
        StoredDocument doc = new StoredDocument(id, safeName(file.getOriginalFilename()), Instant.now(), text.text(), result,
                status, status == StoredDocument.Status.ACCEPTED ? result.fields() : null, null);
        store.save(doc);
        // Field values are customer data, so the log carries only the id, verdict and flag codes.
        log.info("document={} form={} decision={} flags={}", id, result.formType(), result.decision(),
                result.flags().stream().map(f -> f.code().name()).distinct().toList());
        return doc;
    }

    @GetMapping
    public List<StoredDocument> list() {
        return store.all();
    }

    @GetMapping("/{id}")
    public StoredDocument get(@PathVariable String id) {
        return store.find(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    @PostMapping("/{id}/review")
    public StoredDocument review(@PathVariable String id, @Valid @RequestBody ReviewRequest request) {
        StoredDocument doc = get(id);
        if (doc.status() != StoredDocument.Status.PENDING_REVIEW) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "document is not waiting for review");
        }
        if (request.decision() == ReviewRequest.Decision.REJECT) {
            return save(doc, doc.reviewed(StoredDocument.Status.REJECTED, null, request.note()));
        }
        FormType form = Optional.ofNullable(doc.result().formType())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "unknown form type: reject it instead"));

        ReviewCorrections.Outcome outcome = ReviewCorrections.apply(form, doc.result().fields(), request.fields());
        if (!outcome.ok()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, String.join("; ", outcome.problems()));
        }
        return save(doc, doc.reviewed(StoredDocument.Status.APPROVED, outcome.fields(), request.note()));
    }

    private StoredDocument save(StoredDocument before, StoredDocument after) {
        if (!store.replace(before, after)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "document was reviewed by someone else");
        }
        log.info("document={} reviewed status={}", after.id(), after.status());
        return after;
    }

    private static boolean startsWith(byte[] bytes, String prefix) {
        if (bytes.length < prefix.length()) {
            return false;
        }
        for (int i = 0; i < prefix.length(); i++) {
            if (bytes[i] != prefix.charAt(i)) {
                return false;
            }
        }
        return true;
    }

    private static String safeName(String name) {
        if (name == null) {
            return "upload.pdf";
        }
        String base = name.replaceAll(".*[/\\\\]", "");
        return base.replaceAll("[^A-Za-z0-9._-]", "_");
    }
}
