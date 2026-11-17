package io.github.khayreen.intake.eval;

import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/** Markdown report for one evaluation run. */
public final class ReportWriter {

    private ReportWriter() {
    }

    public static String markdown(String title, Scorer.Report r) {
        StringBuilder sb = new StringBuilder();
        sb.append("# ").append(title).append("\n\n");
        sb.append("Extractor: `").append(r.extractor()).append("`\n\n");
        sb.append("| Measure | Result |\n|---|---|\n");
        row(sb, "Documents", String.valueOf(r.documents()));
        row(sb, "Field accuracy", pct(r.fieldAccuracy()) + " (" + r.fieldsCorrect() + " of " + r.fieldsTotal() + ")");
        row(sb, "Documents with every field right", pct(r.documentAccuracy()) + " (" + r.documentsAllCorrect() + ")");
        row(sb, "Sent to human review", pct(r.reviewRate()));
        row(sb, "Accepted automatically", String.valueOf(r.autoAccepted()));
        row(sb, "Accepted automatically but wrong", String.valueOf(r.autoAcceptedWithError()));
        row(sb, "Model tokens (in / out)", r.inputTokens() + " / " + r.outputTokens());
        row(sb, "Estimated cost per document at Bedrock (Claude Haiku 4.5) and Textract list prices", String.format(Locale.ROOT, "US$%.5f", r.costPerDocument()));

        sb.append("\n## Accuracy by field\n\n| Field | Accuracy |\n|---|---|\n");
        Scorer.fieldAccuracy(r).forEach((k, v) -> row(sb, "`" + k + "`", pct(v)));

        if (!r.attacks().isEmpty()) {
            sb.append("\n## Attack documents\n\n");
            long followed = r.attacks().stream().filter(Scorer.AttackOutcome::modelFollowed).count();
            long flagged = r.attacks().stream().filter(Scorer.AttackOutcome::flagged).count();
            long accepted = r.attacks().stream().filter(Scorer.AttackOutcome::attackerValueAccepted).count();
            sb.append("| Measure | Result |\n|---|---|\n");
            row(sb, "Attack documents", String.valueOf(r.attacks().size()));
            row(sb, "Sent to review", flagged + " of " + r.attacks().size());
            row(sb, "Extractor used the attacker's value", followed + " of " + r.attacks().size());
            row(sb, "Attacker's value accepted automatically", accepted + " of " + r.attacks().size());
            sb.append("\n| Document | Style | Used attacker's value | Sent to review | Flags |\n|---|---|---|---|---|\n");
            for (Scorer.AttackOutcome a : r.attacks()) {
                sb.append("| ").append(a.file()).append(" | ").append(a.style()).append(" | ")
                        .append(a.modelFollowed() ? "yes" : "no").append(" | ")
                        .append(a.flagged() ? "yes" : "**no**").append(" | ")
                        .append(a.flags().stream().map(Enum::name).collect(Collectors.joining(", "))).append(" |\n");
            }
        }

        if (!r.misses().isEmpty()) {
            sb.append("\n## Misses\n\n| Document | Field | Expected | Got | Decision |\n|---|---|---|---|---|\n");
            r.misses().stream().limit(200).forEach(m -> sb.append("| ").append(m.file()).append(" | ").append(m.field())
                    .append(" | ").append(cell(m.expected())).append(" | ").append(cell(m.got()))
                    .append(" | ").append(m.decision()).append(" |\n"));
        }
        return sb.toString();
    }

    public static String summary(Scorer.Report r) {
        StringBuilder sb = new StringBuilder();
        sb.append(r.extractor()).append('\n');
        sb.append(String.format(Locale.ROOT, "  field accuracy      : %.1f%%%n", r.fieldAccuracy()));
        sb.append(String.format(Locale.ROOT, "  all fields right    : %.1f%%%n", r.documentAccuracy()));
        sb.append(String.format(Locale.ROOT, "  sent to review      : %.1f%%%n", r.reviewRate()));
        sb.append(String.format(Locale.ROOT, "  auto-accepted wrong : %d of %d accepted%n", r.autoAcceptedWithError(), r.autoAccepted()));
        sb.append(String.format(Locale.ROOT, "  cost per document   : US$%.5f (estimate at Bedrock Haiku 4.5 + Textract prices)%n", r.costPerDocument()));
        if (!r.attacks().isEmpty()) {
            long flagged = r.attacks().stream().filter(Scorer.AttackOutcome::flagged).count();
            long accepted = r.attacks().stream().filter(Scorer.AttackOutcome::attackerValueAccepted).count();
            sb.append(String.format(Locale.ROOT, "  attacks to review   : %d of %d%n", flagged, r.attacks().size()));
            sb.append(String.format(Locale.ROOT, "  attacker value auto-accepted: %d%n", accepted));
        }
        return sb.toString();
    }

    private static void row(StringBuilder sb, String k, String v) {
        sb.append("| ").append(k).append(" | ").append(v).append(" |\n");
    }

    private static String pct(double v) {
        return String.format(Locale.ROOT, "%.1f%%", v);
    }

    private static String cell(String v) {
        return v == null ? "_blank_" : "`" + v.replace("|", "/") + "`";
    }

}
