package io.github.khayreen.intake.extract;

import io.github.khayreen.intake.model.FieldKind;
import io.github.khayreen.intake.model.FieldSpec;
import io.github.khayreen.intake.model.FormType;
import io.github.khayreen.intake.model.Usage;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The no-LLM baseline: finds each field by its printed label and takes what follows it.
 *
 * <p>Each line is split at its first colon into a label and a value. The label is matched against
 * a list of common English labels, and the longest match wins, so "E-mail Address" is an email
 * and not an address. A label printed alone on a line takes the next line as its value, and an
 * address may continue onto one more line. Bilingual labels ("Nama Penuh / Full Name") are read
 * by their English half. Labels outside the list are missed, which is the baseline's real limit.
 *
 * <p>It is also the pipeline's second opinion: a hidden instruction can change what a model
 * writes, but not what this reads from a labelled box.
 */
public final class RulesExtractor implements FieldExtractor {

    private static final Map<String, List<String>> LABELS = Map.ofEntries(
            Map.entry("full_name", List.of("full name", "name of applicant", "applicant name", "account holder name", "name")),
            Map.entry("ic_number", List.of("ic number", "ic no", "nric", "mykad no", "mykad")),
            Map.entry("date_of_birth", List.of("date of birth", "birth date", "dob")),
            Map.entry("phone", List.of("mobile no", "mobile number", "phone", "tel no", "contact no")),
            Map.entry("email", List.of("e-mail address", "email address", "e-mail", "email")),
            Map.entry("address", List.of("residential address", "home address", "address")),
            Map.entry("account_type", List.of("account type", "type of account")),
            Map.entry("initial_deposit", List.of("initial deposit", "opening deposit", "deposit amount")),
            Map.entry("loan_type", List.of("loan type", "type of loan", "financing type")),
            Map.entry("loan_amount", List.of("loan amount", "amount requested", "financing amount")),
            Map.entry("tenure_months", List.of("tenure", "repayment period", "loan period")),
            Map.entry("monthly_income", List.of("gross monthly income", "monthly income", "monthly salary")),
            Map.entry("employer_name", List.of("name of employer", "employer name", "employer", "company")),
            Map.entry("account_number", List.of("account number", "account no")),
            Map.entry("old_address", List.of("old address", "previous address", "current address")),
            Map.entry("new_address", List.of("new address", "new mailing address")),
            Map.entry("effective_date", List.of("effective date", "effective from", "with effect from"))
    );

    private static final Pattern TICKED = Pattern.compile("[\\[(]\\s*[xX✓✔]\\s*[\\])]\\s*([A-Za-z]+)");

    @Override
    public RawExtraction extract(FormType form, String documentText) {
        return RawExtraction.ok(OutputParser.toJson(form, read(form, documentText), false), Usage.NONE);
    }

    @Override
    public String name() {
        return "rules-baseline";
    }

    /** Field name → value as printed, or null when no label was found. */
    public Map<String, String> read(FormType form, String documentText) {
        List<String> lines = documentText.lines().map(String::strip).map(RulesExtractor::englishPart).toList();
        Map<String, String> out = new LinkedHashMap<>();
        form.fieldNames().forEach(n -> out.put(n, null));

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            int colon = line.indexOf(':');
            String label = colon >= 0 ? line.substring(0, colon) : line;
            if (label.length() > 45) {
                continue;
            }
            FieldSpec spec = bestField(form, label);
            if (spec == null || out.get(spec.name()) != null) {
                continue;
            }
            String value = colon >= 0 ? line.substring(colon + 1).strip() : "";
            boolean stacked = value.isEmpty() && i + 1 < lines.size() && !looksLikeLabel(form, lines.get(i + 1));
            if (stacked) {
                value = lines.get(++i).strip();
            }
            if (spec.kind() == FieldKind.ADDRESS && i + 1 < lines.size()
                    && !lines.get(i + 1).contains(":") && !looksLikeLabel(form, lines.get(i + 1)) && !lines.get(i + 1).isBlank()
                    && value.endsWith(",")) {
                value = value + " " + lines.get(++i).strip();
            }
            if (spec.kind() == FieldKind.CHOICE) {
                value = ticked(value);
            }
            out.put(spec.name(), value == null || value.isEmpty() ? null : value);
        }
        return out;
    }

    /** The field whose label is the longest match inside this label text. */
    private static FieldSpec bestField(FormType form, String labelText) {
        // "Name (as per MyKad)" is a name, not an IC number: notes in brackets are not part of the label.
        String l = labelText.replaceAll("\\([^)]*\\)", " ").toLowerCase(Locale.ROOT);
        FieldSpec best = null;
        int bestLength = 0;
        for (FieldSpec spec : form.fields()) {
            for (String synonym : LABELS.getOrDefault(spec.name(), List.of())) {
                if (synonym.length() > bestLength && l.contains(synonym)) {
                    best = spec;
                    bestLength = synonym.length();
                }
            }
        }
        return best;
    }

    private static boolean looksLikeLabel(FormType form, String line) {
        int colon = line.indexOf(':');
        String label = colon >= 0 ? line.substring(0, colon) : line;
        return label.length() <= 45 && bestField(form, label) != null && (colon >= 0 || !line.matches(".*\\d.*"));
    }

    /** "Nama Penuh / Full Name: X" → "Full Name: X". Bilingual forms print the English label after a slash. */
    static String englishPart(String line) {
        int colon = line.indexOf(':');
        int slash = line.indexOf(" / ");
        return slash > 0 && (colon < 0 || slash < colon) ? line.substring(slash + 3) : line;
    }

    /** "[X] Savings [ ] Current" → "Savings". A plain word is returned as it is. */
    private static String ticked(String value) {
        Matcher m = TICKED.matcher(value);
        if (m.find()) {
            return m.group(1);
        }
        return value.contains("[") || value.contains("(") && value.contains("[") ? null : value;
    }
}
