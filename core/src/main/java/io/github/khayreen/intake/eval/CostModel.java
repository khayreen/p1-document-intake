package io.github.khayreen.intake.eval;

import io.github.khayreen.intake.model.IntakeResult;

/**
 * Prices in US dollars, as an estimate. Defaults (November 2026): Claude Haiku 4.5 on Bedrock at
 * US$1 / US$5 per million input / output tokens, and Textract DetectDocumentText at US$0.0015 per
 * page, the US West price on AWS's pricing page; the Singapore rate may differ. The billing console
 * after a real run is the figure to quote.
 */
public record CostModel(double inputPerMillionTokens, double outputPerMillionTokens, double ocrPerPage) {

    public static final CostModel BEDROCK_HAIKU_TEXTRACT = new CostModel(1.00, 5.00, 0.0015);

    public double cost(IntakeResult r) {
        return r.usage().inputTokens() * inputPerMillionTokens / 1_000_000.0
                + r.usage().outputTokens() * outputPerMillionTokens / 1_000_000.0
                + r.ocrPages() * ocrPerPage;
    }
}
