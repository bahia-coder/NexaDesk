package com.nexadesk.ai.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Both connectors against a local fake HTTP server: checks the request actually sent on the wire (model,
 * structured output / JSON mode, refusal fallback, headers) and how answers and errors are read.
 */
class LlmClientsTest {

    private static final Map<String, Object> SCHEMA = Map.of("type", "object",
            "properties", Map.of("answer", Map.of("type", "string")),
            "required", List.of("answer"), "additionalProperties", false);
    private static final LlmClient.Request REQUEST = new LlmClient.Request("Consignes système",
            List.of(new LlmClient.Message(LlmClient.Role.USER, "Bonjour"),
                    new LlmClient.Message(LlmClient.Role.ASSISTANT, "Bonjour !"),
                    new LlmClient.Message(LlmClient.Role.USER, "Question")),
            SCHEMA, 1024);

    private final ObjectMapper mapper = new ObjectMapper();
    private final AtomicReference<JsonNode> requestBody = new AtomicReference<>();
    private final AtomicReference<Map<String, String>> requestHeaders = new AtomicReference<>();
    private final AtomicReference<String> requestPath = new AtomicReference<>();
    private HttpServer server;
    private volatile int status = 200;
    private volatile String response = "{}";

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            requestPath.set(exchange.getRequestURI().getPath());
            requestHeaders.set(Map.of(
                    "x-api-key", String.valueOf(exchange.getRequestHeaders().getFirst("x-api-key")),
                    "anthropic-beta", String.valueOf(exchange.getRequestHeaders().getFirst("anthropic-beta")),
                    "authorization", String.valueOf(exchange.getRequestHeaders().getFirst("Authorization"))));
            requestBody.set(mapper.readTree(exchange.getRequestBody()));
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private String url() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private static String anthropicMessage(String stopReason, String text) {
        return """
                {"id":"msg_test","type":"message","role":"assistant","model":"claude-opus-5-5",
                 "content":[{"type":"text","text":%s}],
                 "stop_reason":"%s","stop_sequence":null,
                 "usage":{"input_tokens":10,"output_tokens":5}}""".formatted(quote(text), stopReason);
    }

    private static String quote(String text) {
        return "\"" + text.replace("\"", "\\\"") + "\"";
    }

    @Test
    void anthropicSendsStructuredOutputLowEffortAndRefusalFallback() {
        response = anthropicMessage("end_turn", "{\"answer\":\"ok\"}");
        try (AnthropicLlmClient client = new AnthropicLlmClient("test-key", "claude-opus-5-5", Duration.ofSeconds(5),
                url())) {
            assertThat(client.complete(REQUEST)).isEqualTo("{\"answer\":\"ok\"}");
        }
        JsonNode body = requestBody.get();
        assertThat(requestPath.get()).isEqualTo("/v1/messages");
        assertThat(requestHeaders.get().get("x-api-key")).isEqualTo("test-key");
        assertThat(requestHeaders.get().get("anthropic-beta")).contains(AnthropicLlmClient.FALLBACK_BETA);
        assertThat(body.path("model").asText()).isEqualTo("claude-opus-5-5");
        assertThat(body.path("max_tokens").asInt()).isEqualTo(1024);
        assertThat(body.path("system").asText()).isEqualTo("Consignes système");
        assertThat(body.path("fallbacks").asText()).isEqualTo("default");
        assertThat(body.path("output_config").path("effort").asText()).isEqualTo("low");
        assertThat(body.path("output_config").path("format").path("type").asText()).isEqualTo("json_schema");
        assertThat(body.path("output_config").path("format").path("schema").path("required").get(0).asText())
                .isEqualTo("answer");
        assertThat(body.path("messages")).hasSize(3);
        assertThat(body.path("messages").get(1).path("role").asText()).isEqualTo("assistant");
        assertThat(body.has("thinking")).as("Opus 5.5 always thinks: no thinking parameter").isFalse();
    }

    @Test
    void anthropicRefusalTruncationAndHttpErrorsBecomeLlmExceptions() {
        try (AnthropicLlmClient client = new AnthropicLlmClient("test-key", "claude-opus-5-5", Duration.ofSeconds(5),
                url())) {
            response = anthropicMessage("refusal", "");
            assertThatThrownBy(() -> client.complete(REQUEST)).isInstanceOf(LlmException.class)
                    .hasMessageContaining("refusé");

            response = anthropicMessage("max_tokens", "{\"answer\":");
            assertThatThrownBy(() -> client.complete(REQUEST)).isInstanceOf(LlmException.class)
                    .hasMessageContaining("tronquée");

            status = 401;
            response = "{\"type\":\"error\",\"error\":{\"type\":\"authentication_error\",\"message\":\"invalid x-api-key\"}}";
            assertThatThrownBy(() -> client.complete(REQUEST)).isInstanceOf(LlmException.class);
        }
    }

    @Test
    void openAiCompatibleSendsJsonModeAndTheSchemaInTheSystemPrompt() {
        response = """
                {"choices":[{"index":0,"finish_reason":"stop",
                  "message":{"role":"assistant","content":"{\\"answer\\":\\"ok\\"}"}}]}""";
        OpenAiCompatibleLlmClient client = new OpenAiCompatibleLlmClient(url() + "/v1/", "local-key", "llama3.2",
                Duration.ofSeconds(5), mapper);

        assertThat(client.complete(REQUEST)).isEqualTo("{\"answer\":\"ok\"}");
        JsonNode body = requestBody.get();
        assertThat(requestPath.get()).isEqualTo("/v1/chat/completions");
        assertThat(requestHeaders.get().get("authorization")).isEqualTo("Bearer local-key");
        assertThat(body.path("model").asText()).isEqualTo("llama3.2");
        assertThat(body.path("response_format").path("type").asText()).isEqualTo("json_object");
        assertThat(body.path("messages")).hasSize(4);
        assertThat(body.path("messages").get(0).path("role").asText()).isEqualTo("system");
        assertThat(body.path("messages").get(0).path("content").asText()).contains("Consignes système", "\"answer\"");
        assertThat(body.path("messages").get(2).path("role").asText()).isEqualTo("assistant");
    }

    @Test
    void openAiCompatibleWithoutKeySendsNoAuthorizationAndReportsErrors() {
        OpenAiCompatibleLlmClient client = new OpenAiCompatibleLlmClient(url() + "/v1", null, "llama3.2",
                Duration.ofSeconds(5), mapper);

        response = "{\"choices\":[{\"finish_reason\":\"length\",\"message\":{\"content\":\"{\"}}]}";
        assertThatThrownBy(() -> client.complete(REQUEST)).isInstanceOf(LlmException.class)
                .hasMessageContaining("tronquée");
        assertThat(requestHeaders.get().get("authorization")).isEqualTo("null");

        status = 500;
        response = "{\"error\":\"model not found\"}";
        assertThatThrownBy(() -> client.complete(REQUEST)).isInstanceOf(LlmException.class);
    }
}
