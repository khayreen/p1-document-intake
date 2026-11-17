package io.github.khayreen.intake.app.aws;

import io.github.khayreen.intake.model.Decision;
import io.github.khayreen.intake.model.FlagCode;
import io.github.khayreen.intake.model.FormType;
import io.github.khayreen.intake.model.IntakeResult;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** aws-eval scores what the Lambda stored, so the stored JSON must map back exactly. */
class AwsCommandsTest {

    @Test
    void readsTheResultTheExtractLambdaStores() {
        String stored = """
                {"formType":"LOAN_APPLICATION","decision":"HUMAN_REVIEW",
                 "fields":{"full_name":"TAN WEI MING","monthly_income":"24500.00","loan_type":null},
                 "flags":[{"code":"INJECTION_SUSPECTED","field":"","detail":"line 13"},{"code":"MISSING_REQUIRED","field":"loan_type","detail":"empty"}],
                 "model":"global.anthropic.claude-haiku-4-5-20251001-v1:0","inputTokens":1210,"outputTokens":180,"ocrPages":1}
                """;
        IntakeResult r = AwsCommands.fromStored("incoming/x.pdf", stored);
        assertThat(r.formType()).isEqualTo(FormType.LOAN_APPLICATION);
        assertThat(r.decision()).isEqualTo(Decision.HUMAN_REVIEW);
        assertThat(r.fields()).containsEntry("monthly_income", "24500.00").containsEntry("loan_type", null);
        assertThat(r.flags()).extracting(f -> f.code()).containsExactly(FlagCode.INJECTION_SUSPECTED, FlagCode.MISSING_REQUIRED);
        assertThat(r.flags().getFirst().field()).isNull();
        assertThat(r.usage().inputTokens()).isEqualTo(1210);
    }

    @Test
    void unknownFormHasNoType() {
        IntakeResult r = AwsCommands.fromStored("x", "{\"formType\":\"UNKNOWN\",\"decision\":\"HUMAN_REVIEW\",\"fields\":{},\"flags\":[]}");
        assertThat(r.formType()).isNull();
    }
}
