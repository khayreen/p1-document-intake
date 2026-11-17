package io.github.khayreen.intake.model;

import java.util.List;
import java.util.Optional;

import static io.github.khayreen.intake.model.FieldKind.*;

/** The three synthetic Bank Contoh forms the pipeline accepts. */
public enum FormType {

    ACCOUNT_OPENING("ACCOUNT OPENING", List.of(
            FieldSpec.of("full_name", NAME, true, "applicant's full name as on IC"),
            FieldSpec.of("ic_number", IC_NUMBER, true, "Malaysian IC number, YYMMDD-PB-####"),
            FieldSpec.of("date_of_birth", DATE, true, "date of birth, ISO yyyy-MM-dd"),
            FieldSpec.of("phone", PHONE, true, "mobile number"),
            FieldSpec.of("email", EMAIL, false, "email address, null if blank"),
            FieldSpec.of("address", ADDRESS, true, "residential address, one line"),
            FieldSpec.choice("account_type", "the ticked account type", "SAVINGS", "CURRENT"),
            FieldSpec.ranged("initial_deposit", MONEY, 10, 1_000_000, "initial deposit in RM, digits only")
    )),

    LOAN_APPLICATION("LOAN APPLICATION", List.of(
            FieldSpec.of("full_name", NAME, true, "applicant's full name as on IC"),
            FieldSpec.of("ic_number", IC_NUMBER, true, "Malaysian IC number, YYMMDD-PB-####"),
            FieldSpec.choice("loan_type", "the ticked loan type", "PERSONAL", "HOUSING", "CAR"),
            FieldSpec.ranged("loan_amount", MONEY, 1_000, 2_000_000, "amount requested in RM, digits only"),
            FieldSpec.ranged("tenure_months", INTEGER, 12, 420, "repayment period in months"),
            FieldSpec.ranged("monthly_income", MONEY, 500, 1_000_000, "gross monthly income in RM, digits only"),
            FieldSpec.of("employer_name", TEXT, true, "employer's name")
    )),

    ADDRESS_CHANGE("CHANGE OF ADDRESS", List.of(
            FieldSpec.of("full_name", NAME, true, "account holder's full name"),
            FieldSpec.of("ic_number", IC_NUMBER, true, "Malaysian IC number, YYMMDD-PB-####"),
            FieldSpec.of("account_number", ACCOUNT_NUMBER, true, "12-digit Bank Contoh account number"),
            FieldSpec.of("old_address", ADDRESS, true, "previous address, one line"),
            FieldSpec.of("new_address", ADDRESS, true, "new address, one line"),
            FieldSpec.of("effective_date", DATE, true, "date the change takes effect, ISO yyyy-MM-dd")
    ));

    private final String title;
    private final List<FieldSpec> fields;

    FormType(String title, List<FieldSpec> fields) {
        this.title = title;
        this.fields = fields;
    }

    /** Words in the form's printed heading. */
    public String title() {
        return title;
    }

    public List<FieldSpec> fields() {
        return fields;
    }

    public Optional<FieldSpec> field(String name) {
        return fields.stream().filter(f -> f.name().equals(name)).findFirst();
    }

    public List<String> fieldNames() {
        return fields.stream().map(FieldSpec::name).toList();
    }
}
