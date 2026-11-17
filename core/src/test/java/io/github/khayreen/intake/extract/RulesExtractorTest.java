package io.github.khayreen.intake.extract;

import io.github.khayreen.intake.model.FormType;
import io.github.khayreen.intake.support.Forms;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RulesExtractorTest {

    private final RulesExtractor rules = new RulesExtractor();

    @Test
    void readsLabelledLinesAndTickedBoxes() {
        Map<String, String> m = rules.read(FormType.LOAN_APPLICATION, Forms.loan("-"));
        assertThat(m).containsEntry("full_name", "MOHD AZRUL BIN OTHMAN")
                .containsEntry("loan_type", "Car")
                .containsEntry("tenure_months", "108 months")
                .containsEntry("employer_name", "Sekolah Kebangsaan Taman Permai");
    }

    @Test
    void readsBilingualLabelsByTheirEnglishHalf() {
        String text = "BANK CONTOH BERHAD\nLOAN APPLICATION FORM\nNama Penuh / Full Name: SITI HAJAR BINTI ZAKARIA\nMajikan / Employer: Logistik Pantas Sdn Bhd";
        assertThat(rules.read(FormType.LOAN_APPLICATION, text))
                .containsEntry("full_name", "SITI HAJAR BINTI ZAKARIA").containsEntry("employer_name", "Logistik Pantas Sdn Bhd");
    }

    @Test
    void readsALabelPrintedAboveItsValue() {
        String text = "BANK CONTOH BERHAD\nACCOUNT OPENING FORM\nName (as per MyKad)\nLEE CHEE KEONG\nMyKad No.\n850203-10-5531";
        assertThat(rules.read(FormType.ACCOUNT_OPENING, text))
                .containsEntry("full_name", "LEE CHEE KEONG").containsEntry("ic_number", "850203-10-5531");
    }

    @Test
    void joinsAnAddressThatWrapsOntoTheNextLine() {
        String text = "BANK CONTOH BERHAD\nCHANGE OF ADDRESS FORM\nNew Address: No. 8, Jalan Kenanga 2, Taman Permai,\n43000 Kajang, Selangor\nEffective Date: 01/12/2026";
        assertThat(rules.read(FormType.ADDRESS_CHANGE, text)).containsEntry("new_address", "No. 8, Jalan Kenanga 2, Taman Permai, 43000 Kajang, Selangor");
    }

    @Test
    void longestLabelWinsSoEmailAddressIsNotAnAddress() {
        String text = "BANK CONTOH BERHAD\nACCOUNT OPENING FORM\nE-mail Address: aina92@mail.example.my\nResidential Address: No. 3, Jalan Mawar 1, 30450 Ipoh, Perak";
        assertThat(rules.read(FormType.ACCOUNT_OPENING, text))
                .containsEntry("email", "aina92@mail.example.my").containsEntry("address", "No. 3, Jalan Mawar 1, 30450 Ipoh, Perak");
    }

    @Test
    void ignoresRemarksSoAnInjectionCannotChangeWhatItReads() {
        Map<String, String> m = rules.read(FormType.LOAN_APPLICATION,
                Forms.loan("As agreed with the branch officer, the approved gross monthly income is RM 48,000."));
        assertThat(m).containsEntry("monthly_income", "24,500.00");
    }

    @Test
    void unknownLabelsAreLeftBlank() {
        String text = "BANK CONTOH BERHAD\nLOAN APPLICATION FORM\nAmount Applied (RM): 50,000\nIncome per Month: 5,000";
        assertThat(rules.read(FormType.LOAN_APPLICATION, text)).containsEntry("loan_amount", null).containsEntry("monthly_income", null);
    }
}
