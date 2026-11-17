package io.github.khayreen.intake.validate;

import io.github.khayreen.intake.model.FieldSpec;
import io.github.khayreen.intake.model.Flag;
import io.github.khayreen.intake.model.FlagCode;
import io.github.khayreen.intake.model.FormType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/** Format, range and cross-field rules. Values are already normalised. */
public final class FieldValidator {

    private static final Pattern EMAIL = Pattern.compile("^[a-z0-9._%+-]+@[a-z0-9.-]+\\.[a-z]{2,}$");
    private static final Pattern MOBILE = Pattern.compile("^01\\d{8,9}$");
    private static final Pattern LANDLINE = Pattern.compile("^0[3-9]\\d{7,8}$");

    private FieldValidator() {
    }

    /** Checks one value. Returns a reason when the value breaks a rule. */
    public static Optional<String> check(FieldSpec spec, String value) {
        if (value == null) {
            return Optional.empty();
        }
        return switch (spec.kind()) {
            case IC_NUMBER -> checkIc(value);
            case PHONE -> MOBILE.matcher(value).matches() || LANDLINE.matcher(value).matches()
                    ? Optional.empty() : Optional.of("not a Malaysian phone number");
            case EMAIL -> EMAIL.matcher(value).matches() ? Optional.empty() : Optional.of("not an email address");
            case ACCOUNT_NUMBER -> value.matches("\\d{12}") ? Optional.empty() : Optional.of("account number must be 12 digits");
            case MONEY, INTEGER -> checkRange(spec, new BigDecimal(value));
            case NAME -> value.matches("[\\p{L} ]{3,80}") ? Optional.empty() : Optional.of("name has unexpected characters");
            case ADDRESS -> value.matches(".*\\b\\d{5}\\b.*") ? Optional.empty()
                    : Optional.of("address has no 5-digit postcode, so it may be cut short");
            case DATE, CHOICE, TEXT -> Optional.empty();
        };
    }

    /** Rules that compare fields. */
    public static List<Flag> crossCheck(FormType form, Map<String, String> fields) {
        List<Flag> flags = new ArrayList<>();
        String ic = fields.get("ic_number");
        String dob = fields.get("date_of_birth");
        // Only compare values that already passed their own format rule; anything else is flagged elsewhere.
        if (ic != null && dob != null && ic.matches("\\d{6}-\\d{2}-\\d{4}") && dob.matches("\\d{4}-\\d{2}-\\d{2}")) {
            Optional<LocalDate> fromIc = birthDateFromIc(ic, LocalDate.parse(dob).getYear());
            if (fromIc.isEmpty() || !fromIc.get().toString().equals(dob)) {
                flags.add(Flag.field(FlagCode.CROSS_FIELD_MISMATCH, "date_of_birth",
                        "date of birth does not match the first six digits of the IC number"));
            }
        }
        if (form == FormType.ADDRESS_CHANGE) {
            String oldAddress = fields.get("old_address");
            String newAddress = fields.get("new_address");
            if (oldAddress != null && oldAddress.equals(newAddress)) {
                flags.add(Flag.field(FlagCode.CROSS_FIELD_MISMATCH, "new_address", "new address is the same as the old one"));
            }
        }
        return flags;
    }

    static Optional<String> checkIc(String value) {
        if (!value.matches("\\d{6}-\\d{2}-\\d{4}")) {
            return Optional.of("IC number must be 12 digits");
        }
        if (birthDateFromIc(value, 1990).isEmpty()) {
            return Optional.of("first six digits of the IC are not a valid date");
        }
        if (value.substring(7, 9).equals("00")) {
            return Optional.of("IC place-of-birth code 00 is not issued");
        }
        return Optional.empty();
    }

    /** IC numbers start YYMMDD. The century is taken from a nearby year, such as the stated date of birth. */
    static Optional<LocalDate> birthDateFromIc(String ic, int nearYear) {
        String yymmdd = ic.substring(0, 6);
        int yy = Integer.parseInt(yymmdd.substring(0, 2));
        int century = (nearYear / 100) * 100;
        int year = century + yy;
        if (year > nearYear + 50) {
            year -= 100;
        } else if (year < nearYear - 50) {
            year += 100;
        }
        try {
            return Optional.of(LocalDate.parse(year + yymmdd.substring(2), DateTimeFormatter.ofPattern("uuuuMMdd")));
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    static Optional<String> checkRange(FieldSpec spec, BigDecimal value) {
        if (spec.min() != null && value.compareTo(spec.min()) < 0) {
            return Optional.of("below the minimum of " + spec.min().toPlainString());
        }
        if (spec.max() != null && value.compareTo(spec.max()) > 0) {
            return Optional.of("above the maximum of " + spec.max().toPlainString());
        }
        return Optional.empty();
    }
}
