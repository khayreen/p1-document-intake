package io.github.khayreen.intake.validate;

import io.github.khayreen.intake.model.FlagCode;
import io.github.khayreen.intake.model.FormType;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class FieldValidatorTest {

    private static java.util.Optional<String> check(FormType form, String field, String value) {
        return FieldValidator.check(form.field(field).orElseThrow(), value);
    }

    @Test
    void icMustStartWithARealDate() {
        assertThat(check(FormType.LOAN_APPLICATION, "ic_number", "900101-14-5678")).isEmpty();
        assertThat(check(FormType.LOAN_APPLICATION, "ic_number", "901301-14-5678")).isPresent();
        assertThat(check(FormType.LOAN_APPLICATION, "ic_number", "900101-00-5678")).isPresent();
    }

    @Test
    void amountsHaveRanges() {
        assertThat(check(FormType.LOAN_APPLICATION, "loan_amount", "190000.00")).isEmpty();
        assertThat(check(FormType.LOAN_APPLICATION, "loan_amount", "500.00")).isPresent();
        assertThat(check(FormType.LOAN_APPLICATION, "tenure_months", "600")).isPresent();
    }

    @Test
    void addressNeedsAPostcodeSoACutOffAddressIsCaught() {
        assertThat(check(FormType.ADDRESS_CHANGE, "new_address", "NO 8 JALAN KENANGA 2 TAMAN PERMAI 43000 KAJANG SELANGOR")).isEmpty();
        assertThat(check(FormType.ADDRESS_CHANGE, "new_address", "NO 8 JALAN KENANGA 2 TAMAN PERMAI")).isPresent();
    }

    @Test
    void phoneAndEmail() {
        assertThat(check(FormType.ACCOUNT_OPENING, "phone", "0123456789")).isEmpty();
        assertThat(check(FormType.ACCOUNT_OPENING, "phone", "12345")).isPresent();
        assertThat(check(FormType.ACCOUNT_OPENING, "email", "aina92@mail.example.my")).isEmpty();
        assertThat(check(FormType.ACCOUNT_OPENING, "email", "not-an-email")).isPresent();
    }

    @Test
    void birthDateMustMatchIc() {
        assertThat(FieldValidator.crossCheck(FormType.ACCOUNT_OPENING, Map.of("ic_number", "880514-07-5531", "date_of_birth", "1988-05-14"))).isEmpty();
        assertThat(FieldValidator.crossCheck(FormType.ACCOUNT_OPENING, Map.of("ic_number", "880514-07-5531", "date_of_birth", "1988-05-15")))
                .anyMatch(f -> f.code() == FlagCode.CROSS_FIELD_MISMATCH);
        assertThat(FieldValidator.crossCheck(FormType.ACCOUNT_OPENING, Map.of("ic_number", "020426-13-9459", "date_of_birth", "2002-04-26"))).isEmpty();
    }

    @Test
    void crossCheckIgnoresMalformedValues() {
        assertThat(FieldValidator.crossCheck(FormType.ACCOUNT_OPENING, Map.of("ic_number", "PRANK", "date_of_birth", "soon"))).isEmpty();
    }

    @Test
    void newAddressMustDifferFromOld() {
        assertThat(FieldValidator.crossCheck(FormType.ADDRESS_CHANGE, Map.of("old_address", "A 40400", "new_address", "A 40400")))
                .anyMatch(f -> f.code() == FlagCode.CROSS_FIELD_MISMATCH);
    }
}
