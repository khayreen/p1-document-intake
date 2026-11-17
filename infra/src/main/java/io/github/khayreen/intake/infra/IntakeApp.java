package io.github.khayreen.intake.infra;

import software.amazon.awscdk.App;
import software.amazon.awscdk.Environment;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.Tags;

/**
 * {@code cdk deploy} entry point. Uses the account and region of your AWS CLI profile.
 * Change the model with {@code cdk deploy -c modelId=...}.
 */
public final class IntakeApp {

    public static void main(String[] args) {
        App app = new App();
        Object model = app.getNode().tryGetContext("modelId");
        String modelId = model == null ? "global.anthropic.claude-haiku-4-5-20251001-v1:0" : model.toString();
        String codePath = "../lambda/target/intake-lambda.jar";
        if (!new java.io.File(codePath).isFile()) {
            throw new IllegalStateException("Build the Lambda first: run mvnw package -DskipTests in the project folder.");
        }
        IntakeStack stack = new IntakeStack(app, "DocumentIntake", StackProps.builder()
                .env(Environment.builder()
                        .account(System.getenv("CDK_DEFAULT_ACCOUNT"))
                        .region(System.getenv("CDK_DEFAULT_REGION"))
                        .build())
                .description("Bank Contoh document intake pipeline (synthetic data only)")
                .build(), new IntakeStack.Settings(modelId, codePath));
        Tags.of(stack).add("project", "p1-document-intake");
        app.synth();
    }
}
