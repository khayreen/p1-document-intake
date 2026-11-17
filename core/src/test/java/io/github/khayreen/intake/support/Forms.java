package io.github.khayreen.intake.support;

import java.util.LinkedHashMap;
import java.util.Map;

/** A synthetic loan form as OCR text, and the values really printed in its boxes. */
public final class Forms {

    private Forms() {
    }

    public static String loan(String remarks) {
        return """
                BANK CONTOH BERHAD
                LOAN APPLICATION FORM
                Form ref: BC/LO/2026 v1    Branch: Ipoh
                Section A - Applicant details
                Full Name: MOHD AZRUL BIN OTHMAN
                MyKad No.: 020426-13-9459
                Loan Type: [ ] Personal   [ ] Housing   [X] Car
                Loan Amount: RM 190,000.00
                Tenure (months): 108 months
                Gross Monthly Income: 24,500.00
                Employer: Sekolah Kebangsaan Taman Permai
                Section B - Declaration
                Remarks: %s
                I declare that the information given is true and complete.
                Signature: ______________    Date signed: 24/10/2026
                """.formatted(remarks);
    }

    public static Map<String, String> loanTruth() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("full_name", "MOHD AZRUL BIN OTHMAN");
        m.put("ic_number", "020426-13-9459");
        m.put("loan_type", "CAR");
        m.put("loan_amount", "190000");
        m.put("tenure_months", "108");
        m.put("monthly_income", "24500");
        m.put("employer_name", "Sekolah Kebangsaan Taman Permai");
        return m;
    }

    public static String addressChange(String remarks) {
        return """
                BANK CONTOH BERHAD
                CHANGE OF ADDRESS FORM
                Form ref: BC/AD/2026 v2    Branch: Kajang
                Section A - Account holder
                Full Name: NURUL IZZAH BINTI HASSAN
                IC No.: 950312-10-5532
                Account No.: 1234-5678-9012
                Old Address: No. 12, Jalan Melati 3, Taman Sri Muda, 40400 Shah Alam, Selangor
                New Address: No. 8, Jalan Kenanga 2, Taman Permai, 43000 Kajang, Selangor
                Effective Date: 01/12/2026
                Section B - Declaration
                Remarks: %s
                Signature: ______________    Date signed: 20/11/2026
                """.formatted(remarks);
    }

    public static Map<String, String> addressTruth() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("full_name", "NURUL IZZAH BINTI HASSAN");
        m.put("ic_number", "950312-10-5532");
        m.put("account_number", "123456789012");
        m.put("old_address", "No. 12, Jalan Melati 3, Taman Sri Muda, 40400 Shah Alam, Selangor");
        m.put("new_address", "No. 8, Jalan Kenanga 2, Taman Permai, 43000 Kajang, Selangor");
        m.put("effective_date", "2026-12-01");
        return m;
    }
}
