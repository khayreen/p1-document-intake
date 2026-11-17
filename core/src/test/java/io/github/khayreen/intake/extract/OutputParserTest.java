package io.github.khayreen.intake.extract;

import io.github.khayreen.intake.model.FormType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class OutputParserTest {

    private final OutputParser strict = new OutputParser(true);

    @Test
    void acceptsTheAgreedShape() {
        OutputParser.Parsed p = strict.parse(FormType.LOAN_APPLICATION,
                "{\"form_type\":\"LOAN_APPLICATION\",\"fields\":{\"full_name\":\"TAN WEI MING\",\"tenure_months\":60,\"employer_name\":null},\"suspected_injection\":false}");
        assertThat(p.usable()).isTrue();
        assertThat(p.fields()).containsEntry("full_name", "TAN WEI MING").containsEntry("tenure_months", "60").containsEntry("loan_amount", null);
    }

    @Test
    void acceptsACodeFence() {
        assertThat(strict.parse(FormType.LOAN_APPLICATION, "```json\n{\"form_type\":\"LOAN_APPLICATION\",\"fields\":{}}\n```").usable()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"form_type\":\"LOAN_APPLICATION\",\"fields\":{},\"approved\":true}",
            "{\"form_type\":\"LOAN_APPLICATION\",\"fields\":{\"credit_limit\":\"500000\"}}",
            "{\"form_type\":\"LOAN_APPLICATION\",\"fields\":{\"loan_amount\":{\"value\":\"48000\"}}}",
            "{\"form_type\":\"LOAN_APPLICATION\",\"fields\":{\"loan_amount\":[\"48000\"]}}",
            "{\"form_type\":\"LOAN_APPLICATION\",\"fields\":{\"loan_amount\":true}}",
            "{\"form_type\":\"ACCOUNT_OPENING\",\"fields\":{}}",
            "{\"fields\":{}}",
            "{\"form_type\":\"LOAN_APPLICATION\",\"fields\":{},\"suspected_injection\":\"no\"}",
            "Here you go: {\"form_type\":\"LOAN_APPLICATION\",\"fields\":{}}",
            "not json"})
    void rejectsEverythingElse(String reply) {
        assertThat(strict.parse(FormType.LOAN_APPLICATION, reply).usable()).isFalse();
    }

    @Test
    void rejectsAValueLongerThanAnyBoxOnTheForm() {
        String reply = "{\"form_type\":\"LOAN_APPLICATION\",\"fields\":{\"employer_name\":\"" + "A".repeat(400) + "\"}}";
        assertThat(strict.parse(FormType.LOAN_APPLICATION, reply).usable()).isFalse();
    }

    @Test
    void lenientModeShowsWhatStrictModePrevents() {
        OutputParser.Parsed p = new OutputParser(false).parse(FormType.LOAN_APPLICATION,
                "Sure! {\"form_type\":\"LOAN_APPLICATION\",\"fields\":{\"loan_amount\":\"48000\"},\"approved\":true}");
        assertThat(p.usable()).isTrue();
        assertThat(p.fields()).containsEntry("loan_amount", "48000");
    }
}
