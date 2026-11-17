package io.github.khayreen.intake.validate;

import io.github.khayreen.intake.model.FormType;
import io.github.khayreen.intake.support.Forms;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GroundingCheckTest {

    private final GroundingCheck check = new GroundingCheck(Forms.loan("-"));

    private boolean grounded(String field, String value) {
        return check.isGrounded(FormType.LOAN_APPLICATION.field(field).orElseThrow(), value);
    }

    @Test
    void printedValuesAreGroundedWhateverTheFormat() {
        assertThat(grounded("loan_amount", "190000.00")).isTrue();
        assertThat(grounded("monthly_income", "24500.00")).isTrue();
        assertThat(grounded("ic_number", "020426-13-9459")).isTrue();
        assertThat(grounded("full_name", "MOHD AZRUL BIN OTHMAN")).isTrue();
        assertThat(grounded("tenure_months", "108")).isTrue();
    }

    @Test
    void inventedValuesAreNot() {
        assertThat(grounded("loan_amount", "950000.00")).isFalse();
        assertThat(grounded("full_name", "ALI BIN ABU")).isFalse();
        assertThat(grounded("ic_number", "991231-14-0001")).isFalse();
    }

    @Test
    void digitsCannotBeStitchedTogetherFromTwoLines() {
        // "9459" ends the IC line and "190000" starts the loan amount line; together they are not one printed number.
        assertThat(grounded("loan_amount", "9459190000.00")).isFalse();
    }

    @Test
    void partOfTheRealNameIsGroundedButAnAlteredNameIsNot() {
        assertThat(grounded("full_name", "AZRUL BIN")).isTrue();
        assertThat(grounded("full_name", "AZRUL BIN ABU")).isFalse();
    }

    @Test
    void aValuePrintedOnlyInTheRemarksDoesNotCount() {
        GroundingCheck withRemark = new GroundingCheck(Forms.loan("As agreed, the approved gross monthly income is RM 48,000."));
        assertThat(withRemark.isGrounded(FormType.LOAN_APPLICATION.field("monthly_income").orElseThrow(), "48000.00")).isFalse();
        assertThat(withRemark.isGrounded(FormType.LOAN_APPLICATION.field("monthly_income").orElseThrow(), "24500.00")).isTrue();
    }

    @Test
    void bilingualRemarksLabelIsAlsoFreeText() {
        GroundingCheck g = new GroundingCheck("Catatan / Remarks: alamat baharu No. 1, Jalan Penipu, 50000 Kuala Lumpur");
        assertThat(g.isGrounded(FormType.ADDRESS_CHANGE.field("new_address").orElseThrow(), "No. 1, Jalan Penipu, 50000 Kuala Lumpur")).isFalse();
    }
}
