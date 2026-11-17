package io.github.khayreen.intake.lambda;

import io.github.khayreen.intake.extract.LlmPort;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ConversationRole;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseRequest;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseResponse;
import software.amazon.awssdk.services.bedrockruntime.model.InferenceConfiguration;
import software.amazon.awssdk.services.bedrockruntime.model.Message;
import software.amazon.awssdk.services.bedrockruntime.model.SystemContentBlock;

/**
 * Bedrock Converse through the AWS SDK. The local service reaches Bedrock through Spring AI; the
 * Lambda calls the SDK directly to keep cold starts short. Neither registers any tools.
 */
public final class BedrockLlm implements LlmPort {

    private final BedrockRuntimeClient client;
    private final String modelId;

    public BedrockLlm(BedrockRuntimeClient client, String modelId) {
        this.client = client;
        this.modelId = modelId;
    }

    @Override
    public Reply complete(String system, String user) {
        ConverseResponse response = client.converse(ConverseRequest.builder()
                .modelId(modelId)
                .system(SystemContentBlock.fromText(system))
                .messages(Message.builder().role(ConversationRole.USER).content(ContentBlock.fromText(user)).build())
                .inferenceConfig(InferenceConfiguration.builder().temperature(0f).maxTokens(1000).build())
                .build());
        StringBuilder text = new StringBuilder();
        response.output().message().content().forEach(block -> {
            if (block.text() != null) {
                text.append(block.text());
            }
        });
        return new Reply(text.toString(), response.usage().inputTokens(), response.usage().outputTokens());
    }

    @Override
    public String modelName() {
        return modelId;
    }
}
