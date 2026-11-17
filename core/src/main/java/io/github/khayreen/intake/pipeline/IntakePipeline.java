package io.github.khayreen.intake.pipeline;

import io.github.khayreen.intake.extract.FieldExtractor;
import io.github.khayreen.intake.extract.FormClassifier;
import io.github.khayreen.intake.extract.OutputParser;
import io.github.khayreen.intake.extract.RawExtraction;
import io.github.khayreen.intake.extract.RulesExtractor;
import io.github.khayreen.intake.model.Decision;
import io.github.khayreen.intake.model.FieldSpec;
import io.github.khayreen.intake.model.Flag;
import io.github.khayreen.intake.model.FlagCode;
import io.github.khayreen.intake.model.FormType;
import io.github.khayreen.intake.model.IntakeResult;
import io.github.khayreen.intake.model.TextExtraction;
import io.github.khayreen.intake.model.Usage;
import io.github.khayreen.intake.security.InjectionScreen;
import io.github.khayreen.intake.validate.FieldValidator;
import io.github.khayreen.intake.validate.GroundingCheck;
import io.github.khayreen.intake.validate.Normaliser;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * OCR text in, verdict out. The same code runs locally and inside the AWS Lambda.
 *
 * <pre>
 * screen → classify form → extract fields → strict parse → validate → ground → second opinion → decide
 * </pre>
 *
 * The model only fills in values. Whether a document is accepted is decided here, from the flags.
 */
public final class IntakePipeline {

    /** Below this mean OCR confidence a person checks the document. */
    public static final double MIN_OCR_CONFIDENCE = 80.0;

    private final FieldExtractor extractor;
    private final Set<Defence> defences;
    private final InjectionScreen screen = new InjectionScreen();
    private final OutputParser parser;
    private final RulesExtractor rules = new RulesExtractor();

    public IntakePipeline(FieldExtractor extractor, Set<Defence> defences) {
        this.extractor = extractor;
        this.defences = Set.copyOf(defences);
        this.parser = new OutputParser(defences.contains(Defence.STRICT_SCHEMA));
    }

    public IntakePipeline(FieldExtractor extractor) {
        this(extractor, Defence.all());
    }

    public IntakeResult process(String documentId, TextExtraction ocr) {
        List<Flag> flags = new ArrayList<>();

        if (ocr.confidence() < MIN_OCR_CONFIDENCE) {
            flags.add(Flag.document(FlagCode.LOW_OCR_CONFIDENCE,
                    "OCR confidence %.0f%% is below %.0f%%".formatted(ocr.confidence(), MIN_OCR_CONFIDENCE)));
        }

        // 1. Screen the text for instructions aimed at the model.
        String text = ocr.text();
        String cleaned = text;
        if (defences.contains(Defence.INJECTION_SCREEN) || defences.contains(Defence.SANITISE_TEXT)) {
            InjectionScreen.Result screened = screen.screen(text);
            if (defences.contains(Defence.INJECTION_SCREEN)) {
                screened.lines().forEach(line -> flags.add(Flag.document(FlagCode.INJECTION_SUSPECTED,
                        "line " + line.lineNumber() + " " + line.reason() + ": \"" + abbreviate(line.text()) + "\"")));
                if (screened.hiddenCharacters() > 0) {
                    flags.add(Flag.document(FlagCode.HIDDEN_CHARACTERS,
                            screened.hiddenCharacters() + " invisible characters removed"));
                }
            }
            if (defences.contains(Defence.SANITISE_TEXT)) {
                cleaned = screened.cleanedText();
            }
        }

        // 2. Which form is it? Decided by code from the printed heading.
        Optional<FormType> detected = FormClassifier.classify(cleaned);
        if (detected.isEmpty()) {
            flags.add(Flag.document(FlagCode.UNKNOWN_FORM, "heading does not match a Bank Contoh form"));
            return result(documentId, null, Map.of(), flags, Usage.NONE, ocr.pages());
        }
        FormType form = detected.get();

        // 3. Ask the extractor, then read its answer.
        RawExtraction raw = extractor.extract(form, cleaned);
        if (raw.error() != null) {
            flags.add(Flag.document(FlagCode.MODEL_ERROR, raw.error()));
            return result(documentId, form, emptyFields(form), flags, raw.usage(), ocr.pages());
        }
        OutputParser.Parsed parsed = parser.parse(form, raw.json());
        flags.addAll(parsed.problems());
        if (!parsed.usable()) {
            return result(documentId, form, emptyFields(form), flags, raw.usage(), ocr.pages());
        }
        if (parsed.suspectedInjection()) {
            flags.add(Flag.document(FlagCode.MODEL_REPORTED_INJECTION, "the model reported instructions inside the document"));
        }

        // 4. Normalise, validate and ground every value.
        GroundingCheck grounding = new GroundingCheck(cleaned);
        boolean secondOpinion = defences.contains(Defence.SECOND_OPINION) && !(extractor instanceof RulesExtractor);
        Map<String, String> rulesRead = secondOpinion ? rules.read(form, cleaned) : Map.of();
        Map<String, String> values = new LinkedHashMap<>();
        for (FieldSpec spec : form.fields()) {
            String rawValue = parsed.fields().get(spec.name());
            Optional<String> value = safeNormalise(spec, rawValue);

            if (value.isEmpty()) {
                values.put(spec.name(), rawValue == null || rawValue.isBlank() ? null : rawValue.strip());
                if (rawValue != null && !rawValue.isBlank() && defences.contains(Defence.FIELD_VALIDATION)) {
                    flags.add(Flag.field(FlagCode.INVALID_VALUE, spec.name(), "could not read \"" + abbreviate(rawValue) + "\""));
                } else if (spec.required()) {
                    flags.add(Flag.field(FlagCode.MISSING_REQUIRED, spec.name(), "required field is empty"));
                }
                continue;
            }
            values.put(spec.name(), value.get());

            if (defences.contains(Defence.FIELD_VALIDATION)) {
                FieldValidator.check(spec, value.get())
                        .ifPresent(why -> flags.add(Flag.field(FlagCode.INVALID_VALUE, spec.name(), why)));
            }
            if (defences.contains(Defence.GROUNDING_CHECK) && !grounding.isGrounded(spec, value.get())) {
                flags.add(Flag.field(FlagCode.NOT_GROUNDED, spec.name(),
                        "\"" + abbreviate(value.get()) + "\" is not printed in the document"));
            }
            if (secondOpinion) {
                Optional<String> fromRules = Normaliser.normalise(spec, rulesRead.get(spec.name()))
                        .filter(v -> FieldValidator.check(spec, v).isEmpty());
                if (fromRules.isPresent() && !fromRules.get().equals(value.get())) {
                    flags.add(Flag.field(FlagCode.EXTRACTORS_DISAGREE, spec.name(),
                            "model read \"" + abbreviate(value.get()) + "\", the labelled box reads \"" + fromRules.get() + "\""));
                }
            }
        }
        if (defences.contains(Defence.FIELD_VALIDATION)) {
            flags.addAll(FieldValidator.crossCheck(form, values));
        }

        return result(documentId, form, values, flags, raw.usage(), ocr.pages());
    }

    private IntakeResult result(String id, FormType form, Map<String, String> fields, List<Flag> flags, Usage usage, int pages) {
        boolean review = !flags.isEmpty() && defences.contains(Defence.REVIEW_ROUTING);
        return new IntakeResult(id, form, fields, flags, review ? Decision.HUMAN_REVIEW : Decision.AUTO_ACCEPT, usage, pages);
    }

    /** A value the normaliser cannot read is reported as invalid; hostile input never stops the pipeline. */
    private static Optional<String> safeNormalise(FieldSpec spec, String raw) {
        try {
            return Normaliser.normalise(spec, raw);
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    private static Map<String, String> emptyFields(FormType form) {
        Map<String, String> m = new LinkedHashMap<>();
        form.fieldNames().forEach(n -> m.put(n, null));
        return m;
    }

    private static String abbreviate(String s) {
        String one = s.replaceAll("\\s+", " ").strip();
        return one.length() <= 80 ? one : one.substring(0, 77) + "...";
    }

    public String extractorName() {
        return extractor.name();
    }
}
