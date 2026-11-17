package io.github.khayreen.intake.validate;

import io.github.khayreen.intake.model.FieldSpec;
import io.github.khayreen.intake.model.FormType;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class NormaliserTest {

    private static FieldSpec field(FormType form, String name) {
        return form.field(name).orElseThrow();
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "RM 5,000.00 | 5000.00", "RM5,000 | 5000.00", "5,000.00 | 5000.00", "5000 | 5000.00", "RM 940,000 | 940000.00",
            "myr 12.5 | 12.50", "RM 1O,000 | 10000.00"})
    void money(String printed, String expected) {
        assertThat(Normaliser.normalise(field(FormType.LOAN_APPLICATION, "loan_amount"), printed)).contains(expected);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {"84 months | 84", "60 bulan | 60", "108 | 108", "6O months | 60", "1 month | 1"})
    void tenureKeepsTheWordMonthsIntact(String printed, String expected) {
        assertThat(Normaliser.normalise(field(FormType.LOAN_APPLICATION, "tenure_months"), printed)).contains(expected);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {"9OO101-14-5678 | 900101-14-5678", "900101145678 | 900101-14-5678", "90010l-14-5678 | 900101-14-5678"})
    void icRepairsOcrSlips(String printed, String expected) {
        assertThat(Normaliser.normalise(field(FormType.LOAN_APPLICATION, "ic_number"), printed)).contains(expected);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {"+60 12-345 6789 | 0123456789", "012-345 6789 | 0123456789", "011 23456789 | 01123456789"})
    void phone(String printed, String expected) {
        assertThat(Normaliser.normalise(field(FormType.ACCOUNT_OPENING, "phone"), printed)).contains(expected);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {"01/02/1990 | 1990-02-01", "1 Feb 1990 | 1990-02-01", "1990-02-01 | 1990-02-01",
            "01-02-1990 | 1990-02-01", "01.02.1990 | 1990-02-01"})
    void dates(String printed, String expected) {
        assertThat(Normaliser.normalise(field(FormType.ACCOUNT_OPENING, "date_of_birth"), printed)).contains(expected);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {"Savings Account | SAVINGS", "savings | SAVINGS", "CURRENT | CURRENT"})
    void choices(String printed, String expected) {
        assertThat(Normaliser.normalise(field(FormType.ACCOUNT_OPENING, "account_type"), printed)).contains(expected);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {"lots", "12 years", "-5", "99999999999999", "RM 5,000 or more"})
    void unreadableValuesAreEmpty(String printed) {
        assertThat(Normaliser.normalise(field(FormType.LOAN_APPLICATION, "tenure_months"), printed)).isEmpty();
        assertThat(Normaliser.normalise(field(FormType.LOAN_APPLICATION, "loan_amount"), printed)).isEmpty();
    }
}
