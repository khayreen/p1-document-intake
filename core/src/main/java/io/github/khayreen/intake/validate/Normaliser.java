package io.github.khayreen.intake.validate;

import io.github.khayreen.intake.model.FieldKind;
import io.github.khayreen.intake.model.FieldSpec;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns a raw value into one canonical string per kind, so that "RM 5,000" and "5000.00",
 * or "01/02/1990" and "1 Feb 1990", compare equal. Returns empty when the value cannot be read.
 */
public final class Normaliser {

    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("d/M/uuuu"),
            DateTimeFormatter.ofPattern("d-M-uuuu"),
            DateTimeFormatter.ofPattern("d.M.uuuu"),
            DateTimeFormatter.ofPattern("d MMM uuuu", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("d MMMM uuuu", Locale.ENGLISH));

    /** Dates as they may be printed on a form. Used to find every date in a document. */
    public static final Pattern DATE_IN_TEXT = Pattern.compile(
            "\\b(\\d{4}-\\d{2}-\\d{2}|\\d{1,2}[/.-]\\d{1,2}[/.-]\\d{4}|\\d{1,2} [A-Za-z]{3,9} \\d{4})\\b");

    private Normaliser() {
    }

    public static Optional<String> normalise(FieldSpec spec, String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        String value = raw.strip();
        return switch (spec.kind()) {
            case NAME, TEXT, ADDRESS -> Optional.of(words(value)).filter(s -> !s.isEmpty());
            case EMAIL -> Optional.of(value.toLowerCase(Locale.ROOT).replace(" ", ""));
            case IC_NUMBER -> ic(value);
            case PHONE -> phone(value);
            case ACCOUNT_NUMBER -> Optional.of(digits(value)).filter(s -> !s.isEmpty());
            case MONEY -> money(value);
            case INTEGER -> integer(value);
            case DATE -> date(value).map(LocalDate::toString);
            case CHOICE -> choice(spec, value);
        };
    }

    /** Upper case, letters and digits only, single spaces. */
    public static String words(String value) {
        return value.toUpperCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{Nd}]+", " ")
                .strip();
    }

    /** Repairs the usual OCR letter/digit swaps, then keeps digits only. */
    public static String digits(String value) {
        return ocrDigits(value).replaceAll("\\D", "");
    }

    /** Repairs O→0, I/l/|→1, S→5, B→8 without removing anything else. */
    public static String ocrDigits(String value) {
        StringBuilder out = new StringBuilder(value.length());
        for (char c : value.toCharArray()) {
            out.append(switch (c) {
                case 'O', 'o', 'D' -> '0';
                case 'I', 'l', '|' -> '1';
                case 'S' -> '5';
                case 'B' -> '8';
                default -> c;
            });
        }
        return out.toString();
    }

    static Optional<String> ic(String value) {
        String d = digits(value);
        if (d.length() != 12) {
            return Optional.empty();
        }
        return Optional.of(d.substring(0, 6) + "-" + d.substring(6, 8) + "-" + d.substring(8));
    }

    static Optional<String> phone(String value) {
        String d = digits(value);
        if (d.startsWith("60")) {
            d = "0" + d.substring(2);
        }
        return d.isEmpty() ? Optional.empty() : Optional.of(d);
    }

    static Optional<String> money(String value) {
        String cleaned = value.strip().replaceFirst("(?i)^(rm|myr)\\s*", "")
                .replace(",", "")
                .replaceAll("\\s", "");
        cleaned = ocrDigits(cleaned);
        Matcher m = Pattern.compile("^\\d{1,12}(\\.\\d{1,2})?$").matcher(cleaned);
        if (!m.matches()) {
            return Optional.empty();
        }
        return Optional.of(new BigDecimal(cleaned).setScale(2, java.math.RoundingMode.UNNECESSARY).toPlainString());
    }

    static Optional<String> integer(String value) {
        // Repair OCR slips in the number only, so the "o" in "months" is left alone.
        Matcher m = Pattern.compile("^\\s*([0-9OoIl|]+)\\s*(months?|bulan)?\\s*$", Pattern.CASE_INSENSITIVE).matcher(value);
        if (!m.matches()) {
            return Optional.empty();
        }
        String digits = ocrDigits(m.group(1));
        return digits.matches("\\d{1,9}") ? Optional.of(String.valueOf(Integer.parseInt(digits))) : Optional.empty();
    }

    public static Optional<LocalDate> date(String value) {
        String v = value.strip().replaceAll("\\s+", " ");
        for (DateTimeFormatter f : DATE_FORMATS) {
            try {
                return Optional.of(LocalDate.parse(v, f));
            } catch (DateTimeParseException ignored) {
                // try the next format
            }
        }
        return Optional.empty();
    }

    static Optional<String> choice(FieldSpec spec, String value) {
        String upper = words(value);
        for (String c : spec.choices()) {
            if (upper.equals(c) || upper.startsWith(c + " ") || upper.endsWith(" " + c)) {
                return Optional.of(c);
            }
        }
        return Optional.empty();
    }

    /** Every date printed in the text, in any supported format. */
    public static List<LocalDate> datesIn(String text) {
        List<LocalDate> found = new ArrayList<>();
        Matcher m = DATE_IN_TEXT.matcher(text);
        while (m.find()) {
            date(m.group(1)).ifPresent(found::add);
        }
        return found;
    }

    /** Convenience for tests and scoring: normalise or return the raw value unchanged. */
    public static String canonical(FieldSpec spec, String raw) {
        return normalise(spec, raw).orElse(raw == null ? null : raw.strip());
    }

    public static boolean isKind(FieldSpec spec, FieldKind kind) {
        return spec.kind() == kind;
    }
}
