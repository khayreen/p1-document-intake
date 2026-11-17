package io.github.khayreen.intake.infra;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import software.amazon.awscdk.App;
import software.amazon.awscdk.Environment;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.assertions.Match;
import software.amazon.awscdk.assertions.Template;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Synthesises the stack and checks the security properties that matter. No AWS account needed. */
class IntakeStackTest {

    static Template template;
    static Map<String, Map<String, Object>> policies;

    @BeforeAll
    static void synth() throws IOException {
        Path jar = Files.createTempFile("intake-lambda", ".jar");
        App app = new App();
        IntakeStack stack = new IntakeStack(app, "Test", StackProps.builder()
                .env(Environment.builder().account("123456789012").region("ap-southeast-1").build()).build(),
                new IntakeStack.Settings("global.anthropic.claude-haiku-4-5-20251001-v1:0", jar.toString()));
        template = Template.fromStack(stack);
        policies = template.findResources("AWS::IAM::Policy");
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> statements(String policyIdPrefix) {
        List<Map<String, Object>> out = new ArrayList<>();
        policies.forEach((id, resource) -> {
            if (!id.startsWith(policyIdPrefix)) {
                return;
            }
            Map<String, Object> doc = (Map<String, Object>) ((Map<String, Object>) resource.get("Properties")).get("PolicyDocument");
            out.addAll((List<Map<String, Object>>) doc.get("Statement"));
        });
        return out;
    }

    private static List<String> list(Object o) {
        if (o instanceof List<?> l) {
            return l.stream().map(String::valueOf).toList();
        }
        return List.of(String.valueOf(o));
    }

    @Test
    void bucketIsPrivateEncryptedAndHttpsOnly() {
        template.hasResourceProperties("AWS::S3::Bucket", Map.of(
                "PublicAccessBlockConfiguration", Map.of("BlockPublicAcls", true, "BlockPublicPolicy", true,
                        "IgnorePublicAcls", true, "RestrictPublicBuckets", true),
                "BucketEncryption", Match.anyValue()));
        template.hasResourceProperties("AWS::S3::BucketPolicy", Map.of("PolicyDocument", Map.of("Statement",
                Match.arrayWith(List.of(Match.objectLike(Map.of("Effect", "Deny",
                        "Condition", Map.of("Bool", Map.of("aws:SecureTransport", "false")))))))));
    }

    @Test
    void noPolicyGrantsEveryAction() {
        policies.keySet().forEach(id -> statements(id).forEach(s ->
                assertThat(list(s.get("Action"))).as(id).noneMatch(a -> a.equals("*") || a.endsWith(":*"))));
    }

    /**
     * Actions that AWS does not let you narrow to one resource: Textract, Step Functions log delivery and
     * X-Ray tracing. Any other action on "*" fails this test.
     */
    private static final List<String> AWS_REQUIRES_WILDCARD = List.of("textract:DetectDocumentText",
            "logs:CreateLogDelivery", "logs:GetLogDelivery", "logs:UpdateLogDelivery", "logs:DeleteLogDelivery",
            "logs:ListLogDeliveries", "logs:PutResourcePolicy", "logs:DescribeResourcePolicies", "logs:DescribeLogGroups",
            "xray:PutTraceSegments", "xray:PutTelemetryRecords", "xray:GetSamplingRules", "xray:GetSamplingTargets");

    @Test
    void wildcardResourcesOnlyWhereAwsRequiresThem() {
        policies.keySet().forEach(id -> statements(id).stream()
                .filter(s -> list(s.get("Resource")).contains("*"))
                .forEach(s -> assertThat(AWS_REQUIRES_WILDCARD).as(id).containsAll(list(s.get("Action")))));
    }

    @Test
    void extractCanOnlyCallTheConfiguredModel() {
        List<Map<String, Object>> extract = statements("ExtractServiceRoleDefaultPolicy");
        assertThat(extract).isNotEmpty();
        extract.forEach(s -> assertThat(list(s.get("Action"))).containsExactly("bedrock:InvokeModel"));
        String all = extract.toString();
        assertThat(all).contains("inference-profile/global.anthropic.claude-haiku-4-5-20251001-v1:0")
                .contains("foundation-model/anthropic.claude-haiku-4-5-20251001-v1:0")
                .contains("bedrock:InferenceProfileArn")
                .doesNotContain("s3:").doesNotContain("dynamodb:");
    }

    @Test
    void reviewDecisionCanReleaseOnlyThisWorkflow() {
        List<Map<String, Object>> decision = statements("ReviewDecisionServiceRoleDefaultPolicy");
        String all = decision.toString();
        assertThat(all).contains("states:SendTaskSuccess").contains("dynamodb:UpdateItem").doesNotContain("dynamodb:DeleteItem")
                .doesNotContain("dynamodb:Scan").doesNotContain("bedrock");
    }

    @Test
    void workflowWaitsForAPersonWithATaskToken() {
        Map<String, Map<String, Object>> machines = template.findResources("AWS::StepFunctions::StateMachine");
        assertThat(machines).hasSize(1);
        assertThat(machines.values().iterator().next().toString()).contains("waitForTaskToken").contains("AUTO_ACCEPT");
    }

    @Test
    void onlyUploadsUnderIncomingStartTheWorkflow() {
        template.hasResourceProperties("AWS::Events::Rule", Map.of("EventPattern", Match.objectLike(Map.of(
                "detail-type", List.of("Object Created"),
                "detail", Match.objectLike(Map.of("object", Map.of("key", List.of(Map.of("prefix", "incoming/")))))))));
    }

    @Test
    void lambdasRunJava21OnArm() {
        template.resourcePropertiesCountIs("AWS::Lambda::Function", Map.of("Runtime", "java21", "Architectures", List.of("arm64")), 4);
    }

    @Test
    void logsAreKeptForAWeekAndExecutionDataIsNotLogged() {
        template.hasResourceProperties("AWS::Logs::LogGroup", Map.of("RetentionInDays", 7));
        template.hasResourceProperties("AWS::StepFunctions::StateMachine", Map.of("LoggingConfiguration",
                Match.objectLike(Map.of("IncludeExecutionData", false))));
    }

    @Test
    void dashboardExists() {
        template.resourceCountIs("AWS::CloudWatch::Dashboard", 1);
    }

    @Test
    void plainModelIdNeedsOnlyTheModel() {
        Path jar;
        try {
            jar = Files.createTempFile("intake-lambda", ".jar");
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        IntakeStack stack = new IntakeStack(new App(), "Plain", StackProps.builder()
                .env(Environment.builder().account("123456789012").region("ap-southeast-1").build()).build(),
                new IntakeStack.Settings("amazon.nova-lite-v1:0", jar.toString()));
        assertThat(stack.bedrockStatements("amazon.nova-lite-v1:0")).hasSize(1);
    }
}
