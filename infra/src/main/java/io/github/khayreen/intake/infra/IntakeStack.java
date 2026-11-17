package io.github.khayreen.intake.infra;

import software.amazon.awscdk.CfnOutput;
import software.amazon.awscdk.Duration;
import software.amazon.awscdk.RemovalPolicy;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.services.cloudwatch.Alarm;
import software.amazon.awscdk.services.cloudwatch.ComparisonOperator;
import software.amazon.awscdk.services.cloudwatch.Dashboard;
import software.amazon.awscdk.services.cloudwatch.GraphWidget;
import software.amazon.awscdk.services.cloudwatch.MathExpression;
import software.amazon.awscdk.services.cloudwatch.Metric;
import software.amazon.awscdk.services.cloudwatch.SingleValueWidget;
import software.amazon.awscdk.services.cloudwatch.TextWidget;
import software.amazon.awscdk.services.cloudwatch.TreatMissingData;
import software.amazon.awscdk.services.dynamodb.Attribute;
import software.amazon.awscdk.services.dynamodb.AttributeType;
import software.amazon.awscdk.services.dynamodb.BillingMode;
import software.amazon.awscdk.services.dynamodb.Table;
import software.amazon.awscdk.services.dynamodb.TableEncryption;
import software.amazon.awscdk.services.events.EventField;
import software.amazon.awscdk.services.events.EventPattern;
import software.amazon.awscdk.services.events.Match;
import software.amazon.awscdk.services.events.Rule;
import software.amazon.awscdk.services.events.RuleTargetInput;
import software.amazon.awscdk.services.events.targets.SfnStateMachine;
import software.amazon.awscdk.services.events.targets.SfnStateMachineProps;
import software.amazon.awscdk.services.iam.Effect;
import software.amazon.awscdk.services.iam.PolicyStatement;
import software.amazon.awscdk.services.lambda.Architecture;
import software.amazon.awscdk.services.lambda.Code;
import software.amazon.awscdk.services.lambda.Function;
import software.amazon.awscdk.services.lambda.Runtime;
import software.amazon.awscdk.services.logs.LogGroup;
import software.amazon.awscdk.services.logs.RetentionDays;
import software.amazon.awscdk.services.s3.BlockPublicAccess;
import software.amazon.awscdk.services.s3.Bucket;
import software.amazon.awscdk.services.s3.BucketEncryption;
import software.amazon.awscdk.services.s3.LifecycleRule;
import software.amazon.awscdk.services.stepfunctions.CatchProps;
import software.amazon.awscdk.services.stepfunctions.Choice;
import software.amazon.awscdk.services.stepfunctions.Condition;
import software.amazon.awscdk.services.stepfunctions.DefinitionBody;
import software.amazon.awscdk.services.stepfunctions.Fail;
import software.amazon.awscdk.services.stepfunctions.IntegrationPattern;
import software.amazon.awscdk.services.stepfunctions.JsonPath;
import software.amazon.awscdk.services.stepfunctions.LogLevel;
import software.amazon.awscdk.services.stepfunctions.LogOptions;
import software.amazon.awscdk.services.stepfunctions.RetryProps;
import software.amazon.awscdk.services.stepfunctions.StateMachine;
import software.amazon.awscdk.services.stepfunctions.TaskInput;
import software.amazon.awscdk.services.stepfunctions.Timeout;
import software.amazon.awscdk.services.stepfunctions.tasks.DynamoAttributeValue;
import software.amazon.awscdk.services.stepfunctions.tasks.DynamoPutItem;
import software.amazon.awscdk.services.stepfunctions.tasks.LambdaInvoke;
import software.constructs.Construct;

import java.util.List;
import java.util.Map;

/**
 * S3 upload → EventBridge → Step Functions:
 *
 * <pre>
 * ReadText (Textract) → Extract (Bedrock + checks) → accepted? ─ yes → StoreResult
 *                                                             └ no  → WaitForReview (task token, up to 7 days) → StoreResult
 * </pre>
 *
 * Each Lambda has its own role with only the actions it uses. Nothing is public.
 */
public class IntakeStack extends Stack {

    /**
     * @param modelId  Bedrock model or inference profile, for example global.anthropic.claude-haiku-4-5-20251001-v1:0
     * @param codePath the shaded Lambda jar built by {@code mvnw package}
     */
    public record Settings(String modelId, String codePath) {
    }

    static final String NAMESPACE = "DocumentIntake";

    public IntakeStack(Construct scope, String id, StackProps props, Settings settings) {
        super(scope, id, props);

        // ---- Storage -----------------------------------------------------------------------
        Bucket bucket = Bucket.Builder.create(this, "Uploads")
                .blockPublicAccess(BlockPublicAccess.BLOCK_ALL)
                .encryption(BucketEncryption.S3_MANAGED)
                .enforceSsl(true)
                .eventBridgeEnabled(true)
                .lifecycleRules(List.of(LifecycleRule.builder().expiration(Duration.days(30)).build()))
                .removalPolicy(RemovalPolicy.DESTROY)
                .autoDeleteObjects(true)
                .build();

        Table results = Table.Builder.create(this, "Results")
                .partitionKey(Attribute.builder().name("documentId").type(AttributeType.STRING).build())
                .billingMode(BillingMode.PAY_PER_REQUEST)
                .encryption(TableEncryption.AWS_MANAGED)
                .removalPolicy(RemovalPolicy.DESTROY)
                .build();

        Table review = Table.Builder.create(this, "ReviewQueue")
                .partitionKey(Attribute.builder().name("documentId").type(AttributeType.STRING).build())
                .billingMode(BillingMode.PAY_PER_REQUEST)
                .encryption(TableEncryption.AWS_MANAGED)
                .timeToLiveAttribute("expiresAt")
                .removalPolicy(RemovalPolicy.DESTROY)
                .build();

        // ---- Lambdas -----------------------------------------------------------------------
        Function readText = function("ReadText", "ReadTextHandler", 512, 30, Map.of(), settings.codePath());
        Function extract = function("Extract", "ExtractHandler", 1024, 90, Map.of("BEDROCK_MODEL_ID", settings.modelId()), settings.codePath());
        Function reviewTask = function("QueueForReview", "ReviewTaskHandler", 512, 30, Map.of("REVIEW_TABLE", review.getTableName()), settings.codePath());
        Function reviewDecision = function("ReviewDecision", "ReviewDecisionHandler", 512, 30, Map.of("REVIEW_TABLE", review.getTableName()), settings.codePath());

        // ReadText: read uploads and call Textract. Textract has no resource-level permissions, so its resource is "*".
        bucket.grantRead(readText, "incoming/*");
        readText.addToRolePolicy(PolicyStatement.Builder.create()
                .actions(List.of("textract:DetectDocumentText")).resources(List.of("*")).build());

        // Extract: call one Bedrock model and nothing else.
        bedrockStatements(settings.modelId()).forEach(extract::addToRolePolicy);

        // Review queue: write entries; decisions: read, update once, release the waiting workflow.
        review.grant(reviewTask, "dynamodb:PutItem");
        review.grant(reviewDecision, "dynamodb:GetItem", "dynamodb:UpdateItem");

        // ---- Workflow ----------------------------------------------------------------------
        DynamoPutItem storeFailed = DynamoPutItem.Builder.create(this, "StoreFailed")
                .table(results)
                .item(Map.of(
                        "documentId", DynamoAttributeValue.fromString(JsonPath.stringAt("$.key")),
                        "status", DynamoAttributeValue.fromString("FAILED"),
                        "errorJson", DynamoAttributeValue.fromString(JsonPath.jsonToString(JsonPath.objectAt("$.error")))))
                .resultPath(JsonPath.DISCARD)
                .build();
        Fail fail = Fail.Builder.create(this, "Failed").cause("A step failed; see StoreFailed and the execution history").build();
        storeFailed.next(fail);

        LambdaInvoke readTask = LambdaInvoke.Builder.create(this, "ReadTextStep")
                .lambdaFunction(readText).payloadResponseOnly(true).build();
        LambdaInvoke extractTask = LambdaInvoke.Builder.create(this, "ExtractStep")
                .lambdaFunction(extract).payloadResponseOnly(true).build();
        for (LambdaInvoke t : List.of(readTask, extractTask)) {
            t.addRetry(RetryProps.builder().errors(List.of("States.TaskFailed")).maxAttempts(2)
                    .interval(Duration.seconds(5)).backoffRate(2).build());
            t.addCatch(storeFailed, CatchProps.builder().resultPath("$.error").build());
        }

        LambdaInvoke waitForReview = LambdaInvoke.Builder.create(this, "WaitForReview")
                .lambdaFunction(reviewTask)
                .integrationPattern(IntegrationPattern.WAIT_FOR_TASK_TOKEN)
                .payload(TaskInput.fromObject(Map.of("taskToken", JsonPath.getTaskToken(), "extraction", JsonPath.objectAt("$"))))
                .taskTimeout(Timeout.duration(Duration.days(7)))
                .resultPath("$.review")
                .build();
        waitForReview.addCatch(storeFailed, CatchProps.builder().resultPath("$.error").build());

        DynamoPutItem storeAccepted = DynamoPutItem.Builder.create(this, "StoreAccepted")
                .table(results)
                .item(Map.of(
                        "documentId", DynamoAttributeValue.fromString(JsonPath.stringAt("$.documentId")),
                        "status", DynamoAttributeValue.fromString("ACCEPTED"),
                        "formType", DynamoAttributeValue.fromString(JsonPath.stringAt("$.formType")),
                        "resultJson", DynamoAttributeValue.fromString(JsonPath.stringAt("$.resultJson")),
                        "decidedBy", DynamoAttributeValue.fromString("pipeline")))
                .resultPath(JsonPath.DISCARD)
                .build();

        DynamoPutItem storeReviewed = DynamoPutItem.Builder.create(this, "StoreReviewed")
                .table(results)
                .item(Map.of(
                        "documentId", DynamoAttributeValue.fromString(JsonPath.stringAt("$.documentId")),
                        "status", DynamoAttributeValue.fromString(JsonPath.stringAt("$.review.status")),
                        "formType", DynamoAttributeValue.fromString(JsonPath.stringAt("$.formType")),
                        "resultJson", DynamoAttributeValue.fromString(JsonPath.stringAt("$.resultJson")),
                        "reviewJson", DynamoAttributeValue.fromString(JsonPath.jsonToString(JsonPath.objectAt("$.review"))),
                        "decidedBy", DynamoAttributeValue.fromString("reviewer")))
                .resultPath(JsonPath.DISCARD)
                .build();

        Choice accepted = Choice.Builder.create(this, "AcceptedAutomatically").build()
                .when(Condition.stringEquals("$.decision", "AUTO_ACCEPT"), storeAccepted)
                .otherwise(waitForReview.next(storeReviewed));

        LogGroup workflowLogs = logGroup("WorkflowLogs");
        StateMachine workflow = StateMachine.Builder.create(this, "Workflow")
                .definitionBody(DefinitionBody.fromChainable(readTask.next(extractTask).next(accepted)))
                .timeout(Duration.days(8))
                .tracingEnabled(true)
                .logs(LogOptions.builder().destination(workflowLogs).level(LogLevel.ERROR).includeExecutionData(false).build())
                .build();
        workflow.grantTaskResponse(reviewDecision);

        Rule.Builder.create(this, "OnUpload")
                .eventPattern(EventPattern.builder()
                        .source(List.of("aws.s3"))
                        .detailType(List.of("Object Created"))
                        .detail(Map.of("bucket", Map.of("name", List.of(bucket.getBucketName())),
                                "object", Map.of("key", Match.prefix("incoming/"))))
                        .build())
                .targets(List.of(new SfnStateMachine(workflow, SfnStateMachineProps.builder()
                        .input(RuleTargetInput.fromObject(Map.of(
                                "bucket", EventField.fromPath("$.detail.bucket.name"),
                                "key", EventField.fromPath("$.detail.object.key"))))
                        .build())))
                .build();

        // ---- Dashboard ---------------------------------------------------------------------
        Metric docs = metric("DocumentsProcessed", "Sum");
        Metric toReview = metric("SentToReview", "Sum");
        Metric injections = metric("InjectionFlagged", "Sum");
        Metric modelErrors = metric("ModelErrors", "Sum");
        Metric cost = metric("EstimatedCostUsd", "Sum");

        MathExpression costPerDoc = MathExpression.Builder.create().label("Estimated cost per document (US$)")
                .expression("IF(docs > 0, cost / docs, 0)").usingMetrics(Map.of("cost", cost, "docs", docs)).period(Duration.hours(1)).build();
        MathExpression reviewRate = MathExpression.Builder.create().label("Sent to review (%)")
                .expression("IF(docs > 0, 100 * review / docs, 0)").usingMetrics(Map.of("review", toReview, "docs", docs)).period(Duration.hours(1)).build();

        Metric fieldAccuracy = Metric.Builder.create().namespace(NAMESPACE).metricName("FieldAccuracy")
                .statistic("Maximum").period(Duration.days(1)).label("Field accuracy (%) from the last evaluation run").build();

        Dashboard.Builder.create(this, "Dashboard").dashboardName("DocumentIntake")
                .widgets(List.of(
                        List.of(TextWidget.Builder.create().width(24).height(2).markdown(
                                "## Bank Contoh document intake\nSynthetic forms only. Accuracy comes from `aws-eval` runs against labelled forms; "
                                        + "cost is estimated from tokens and pages at list prices, so check the billing console for the real figure.").build()),
                        List.of(
                                SingleValueWidget.Builder.create().title("Field accuracy").metrics(List.of(fieldAccuracy)).width(6).build(),
                                SingleValueWidget.Builder.create().title("Cost per document").metrics(List.of(costPerDoc)).width(6).build(),
                                SingleValueWidget.Builder.create().title("Review rate").metrics(List.of(reviewRate)).width(6).build(),
                                SingleValueWidget.Builder.create().title("Injections flagged").metrics(List.of(injections.with(
                                        software.amazon.awscdk.services.cloudwatch.MetricOptions.builder().period(Duration.days(1)).build()))).width(6).build()),
                        List.of(
                                GraphWidget.Builder.create().title("Documents").width(12)
                                        .left(List.of(docs, toReview, injections, modelErrors)).build(),
                                GraphWidget.Builder.create().title("Workflow").width(12)
                                        .left(List.of(workflow.metricSucceeded(), workflow.metricFailed(), workflow.metricTimedOut())).build())))
                .build();

        Alarm.Builder.create(this, "ModelErrorsAlarm")
                .alarmDescription("The model failed on 3 or more documents in 5 minutes")
                .metric(modelErrors.with(software.amazon.awscdk.services.cloudwatch.MetricOptions.builder().period(Duration.minutes(5)).build()))
                .threshold(3).evaluationPeriods(1)
                .comparisonOperator(ComparisonOperator.GREATER_THAN_OR_EQUAL_TO_THRESHOLD)
                .treatMissingData(TreatMissingData.NOT_BREACHING)
                .build();

        output("BucketName", bucket.getBucketName());
        output("ResultsTable", results.getTableName());
        output("ReviewTable", review.getTableName());
        output("ReviewFunction", reviewDecision.getFunctionName());
        output("StateMachine", workflow.getStateMachineArn());
    }

    private Function function(String name, String handler, int memory, int timeoutSeconds, Map<String, String> env, String codePath) {
        return Function.Builder.create(this, name)
                .runtime(Runtime.JAVA_21)
                .architecture(Architecture.ARM_64)
                .handler("io.github.khayreen.intake.lambda." + handler + "::handleRequest")
                .code(Code.fromAsset(codePath))
                .memorySize(memory)
                .timeout(Duration.seconds(timeoutSeconds))
                .environment(env)
                .logGroup(logGroup(name + "Logs"))
                .build();
    }

    private LogGroup logGroup(String id) {
        return LogGroup.Builder.create(this, id).retention(RetentionDays.ONE_WEEK).removalPolicy(RemovalPolicy.DESTROY).build();
    }

    private static Metric metric(String name, String statistic) {
        return Metric.Builder.create().namespace(NAMESPACE).metricName(name).statistic(statistic).period(Duration.minutes(5)).build();
    }

    private void output(String id, String value) {
        CfnOutput.Builder.create(this, id).value(value).build();
    }

    /**
     * Least privilege for Bedrock. A geographic or global inference profile (an id starting
     * "global.", "apac.", "us." and so on) needs the profile plus the underlying model, and only
     * when the call comes through that profile; a plain model id needs only the model.
     */
    List<PolicyStatement> bedrockStatements(String modelId) {
        String region = getRegion();
        String account = getAccount();
        int dot = modelId.indexOf('.');
        boolean profile = dot > 0 && List.of("global", "apac", "us", "eu", "jp", "au", "ca").contains(modelId.substring(0, dot));
        if (!profile) {
            return List.of(PolicyStatement.Builder.create().effect(Effect.ALLOW).actions(List.of("bedrock:InvokeModel"))
                    .resources(List.of("arn:aws:bedrock:" + region + "::foundation-model/" + modelId)).build());
        }
        String baseModel = modelId.substring(dot + 1);
        String profileArn = "arn:aws:bedrock:" + region + ":" + account + ":inference-profile/" + modelId;
        Map<String, Object> viaProfile = Map.of("StringEquals", Map.of("bedrock:InferenceProfileArn", profileArn));
        return List.of(
                PolicyStatement.Builder.create().effect(Effect.ALLOW).actions(List.of("bedrock:InvokeModel"))
                        .resources(List.of(profileArn)).build(),
                PolicyStatement.Builder.create().effect(Effect.ALLOW).actions(List.of("bedrock:InvokeModel"))
                        .resources(List.of("arn:aws:bedrock:*::foundation-model/" + baseModel, "arn:aws:bedrock:::foundation-model/" + baseModel))
                        .conditions(viaProfile).build());
    }
}
