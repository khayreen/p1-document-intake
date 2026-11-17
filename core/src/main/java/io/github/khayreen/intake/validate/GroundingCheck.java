package io.github.khayreen.intake.validate;

import io.github.khayreen.intake.model.FieldSpec;

import java.time.LocalDate;
import java.util.Locale;

/**
 * Checks that an extracted value is actually printed in the document. A model that invents a value,
 * or copies one from a line the injection screen removed, fails this check.
 *
 * <p>Values must come from the form's boxes. The free-text Remarks line is written by the customer
 * and is where a hidden instruction usually sits, so a value printed only there does not count.
 *
 * <p>Grounding proves a value is in a box, not that it is in the right box: a form that prints two
 * amounts can still have the wrong one chosen. Choice fields are weak here because both options are
 * usually printed.
 */
public final class GroundingCheck {

    private final String words;
    private final java.util.List<String> lineDigits;
    private final String lower;
    private final java.util.List<LocalDate> dates;

    private static final java.util.regex.Pattern FREE_TEXT =
            java.util.regex.Pattern.compile("^\\s*(remarks|catatan|notes?|comments?)\\b", java.util.regex.Pattern.CASE_INSENSITIVE);

    public GroundingCheck(String fullText) {
        String documentText = withoutFreeText(fullText);
        this.words = " " + Normaliser.words(documentText) + " ";
        this.lineDigits = documentText.lines()
                .map(line -> Normaliser.digits(line.replaceAll("[A-Za-z]{3,}", " ")))
                .filter(d -> !d.isEmpty())
                .toList();
        this.lower = documentText.toLowerCase(Locale.ROOT).replace(" ", "");
        this.dates = Normaliser.datesIn(documentText);
    }

    public boolean isGrounded(FieldSpec spec, String value) {
        if (value == null) {
            return true;
        }
        return switch (spec.kind()) {
            case NAME, TEXT, ADDRESS, CHOICE -> words.contains(" " + Normaliser.words(value) + " ");
            case EMAIL -> lower.contains(value);
            case IC_NUMBER, ACCOUNT_NUMBER -> onOneLine(value.replaceAll("\\D", ""));
            case PHONE -> onOneLine(value) || onOneLine("6" + value);
            case INTEGER -> onOneLine(value);
            case MONEY -> onOneLine(moneyDigits(value));
            case DATE -> dates.contains(LocalDate.parse(value));
        };
    }

    /** Drops the customer's free-text lines. */
    static String withoutFreeText(String text) {
        return String.join("\n", text.lines().filter(l -> !FREE_TEXT.matcher(l).find()).toList());
    }

    /** Digits must sit on one printed line, so a value cannot be stitched together from two fields. */
    private boolean onOneLine(String wanted) {
        return lineDigits.stream().anyMatch(d -> d.contains(wanted));
    }

    /** 5000.00 is printed as 5,000 or 5,000.00; 5000.50 only as 5,000.50. */
    static String moneyDigits(String value) {
        return value.endsWith(".00") ? value.substring(0, value.length() - 3) : value.replace(".", "");
    }
}
