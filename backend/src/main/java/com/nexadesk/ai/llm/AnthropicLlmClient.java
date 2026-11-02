package com.nexadesk.ai.llm;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.JsonValue;
import com.anthropic.errors.AnthropicException;
import com.anthropic.models.beta.messages.BetaJsonOutputFormat;
import com.anthropic.models.beta.messages.BetaMessage;
import com.anthropic.models.beta.messages.BetaOutputConfig;
import com.anthropic.models.beta.messages.BetaStopReason;
import com.anthropic.models.beta.messages.BetaTextBlock;
import com.anthropic.models.beta.messages.MessageCreateParams;
import java.time.Duration;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Claude through the official Anthropic Java SDK (Messages API).
 *
 * <ul>
 *   <li>Structured outputs: the answer is constrained to the JSON Schema of the request.</li>
 *   <li>Effort {@code low}: short, well-specified tasks (classification, summary) do not need deep reasoning,
 *       and it keeps latency and cost down.</li>
 *   <li>Server-side refusal fallback (beta): if a safety classifier declines, the API retries on a suitable
 *       model instead of failing; a remaining refusal is reported as an error (heuristic fallback).</li>
 *   <li>One retry at most and a short timeout: an interactive screen waits for the answer.</li>
 * </ul>
 */
public class AnthropicLlmClient implements LlmClient {

    static final String FALLBACK_BETA = "server-side-fallback-2026-07-01";

    private final AnthropicClient client;
    private final String model;

    public AnthropicLlmClient(String apiKey, String model, Duration timeout) {
        this(AnthropicOkHttpClient.builder().apiKey(apiKey).timeout(timeout).maxRetries(1).build(), model);
    }

    /** For tests: points the SDK at a local fake server. */
    AnthropicLlmClient(String apiKey, String model, Duration timeout, String baseUrl) {
        this(AnthropicOkHttpClient.builder().apiKey(apiKey).timeout(timeout).maxRetries(0).baseUrl(baseUrl).build(),
                model);
    }

    private AnthropicLlmClient(AnthropicClient client, String model) {
        this.client = client;
        this.model = model;
    }

    @Override
    public String provider() {
        return "Anthropic";
    }

    @Override
    public String model() {
        return model;
    }

    @Override
    public String complete(Request request) {
        MessageCreateParams.Builder params = MessageCreateParams.builder()
                .model(model)
                .maxTokens(request.maxTokens())
                .system(request.system())
                .outputConfig(BetaOutputConfig.builder()
                        .effort(BetaOutputConfig.Effort.LOW)
                        .format(BetaJsonOutputFormat.builder().schema(schema(request.schema())).build())
                        .build())
                .addBeta(FALLBACK_BETA)
                .putAdditionalBodyProperty("fallbacks", JsonValue.from("default"));
        for (Message message : request.messages()) {
            if (message.role() == Role.USER) {
                params.addUserMessage(message.content());
            } else {
                params.addAssistantMessage(message.content());
            }
        }
        BetaMessage response;
        try {
            response = client.beta().messages().create(params.build());
        } catch (AnthropicException e) {
            throw new LlmException("Appel Anthropic en échec : " + e.getMessage(), e);
        }
        BetaStopReason stop = response.stopReason().orElse(null);
        if (BetaStopReason.REFUSAL.equals(stop)) {
            throw new LlmException("Le modèle a refusé de répondre.");
        }
        if (BetaStopReason.MAX_TOKENS.equals(stop)) {
            throw new LlmException("Réponse du modèle tronquée (limite de longueur).");
        }
        return response.content().stream()
                .flatMap(block -> block.text().stream())
                .map(BetaTextBlock::text)
                .collect(Collectors.joining());
    }

    private static BetaJsonOutputFormat.Schema schema(Map<String, Object> schema) {
        BetaJsonOutputFormat.Schema.Builder builder = BetaJsonOutputFormat.Schema.builder();
        schema.forEach((key, value) -> builder.putAdditionalProperty(key, JsonValue.from(value)));
        return builder.build();
    }

    @Override
    public void close() {
        client.close();
    }
}
