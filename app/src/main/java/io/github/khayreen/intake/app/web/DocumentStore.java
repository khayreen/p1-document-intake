package io.github.khayreen.intake.app.web;

import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** In-memory store for the local demo. On AWS the results and review queue live in DynamoDB. */
@Component
public class DocumentStore {

    private final ConcurrentHashMap<String, StoredDocument> docs = new ConcurrentHashMap<>();

    public void save(StoredDocument doc) {
        docs.put(doc.id(), doc);
    }

    public Optional<StoredDocument> find(String id) {
        return Optional.ofNullable(docs.get(id));
    }

    public List<StoredDocument> all() {
        return docs.values().stream().sorted(Comparator.comparing(StoredDocument::uploadedAt).reversed()).toList();
    }

    /** Swaps a document only if nobody changed it in the meantime. */
    public boolean replace(StoredDocument expected, StoredDocument updated) {
        return docs.replace(expected.id(), expected, updated);
    }
}
