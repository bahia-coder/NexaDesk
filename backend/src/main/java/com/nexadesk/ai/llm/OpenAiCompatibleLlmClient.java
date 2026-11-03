package com.nexadesk.ai.llm;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Any server implementing the OpenAI "chat completions" API: Ollama (local and free, {@code http://localhost:11434/v1}),
 * OpenAI, Mistral, Groq… The API key is optional (Ollama needs none).
 *
 * <p>JSON mode ({@code response_format: json_object}) is the common denominator of these servers: it guarantees a
 * JSON object but not its shape, so the schema is described in the system prompt and the answer is validated
 * by the caller.
 */
public class OpenAiCompatibleLlmClient implements LlmClient {

    private final RestClient http;
    private final ObjectMapper mapper;
    private final String model;
    /** Shown to users: the server's host (api.groq.com, localhost…), which says who answers. */
    private final String provider;

    public OpenAiCompatibleLlmClient(String baseUrl, String apiKey, String model, Duration timeout,
                                     ObjectMapper mapper) {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
        factory.setReadTimeout(timeout);
        RestClient.Builder builder = RestClient.builder()
                .baseUrl(baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl)
                .requestFactory(factory);
        if (apiKey != null && !apiKey.isBlank()) {
            builder.defaultHeader("Authorization", "Bearer " + apiKey);
        }
        this.http = builder.build();
        this.mapper = mapper;
        this.model = model;
        String host = URI.create(baseUrl).getHost();
        this.provider = host == null || host.isBlank() ? "OpenAI-compatible" : host;
    }

    @Override
    public String provider() {
        return provider;
    }

    @Override
    public String model() {
        return model;
    }

    @Override
    public String complete(Request request) {
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", request.system()
                + "\n\nRéponds uniquement par un objet JSON conforme à ce schéma JSON :\n" + json(request.schema())));
        for (Message message : request.messages()) {
            messages.add(Map.of("role", message.role().name().toLowerCase(Locale.ROOT), "content", message.content()));
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("messages", messages);
        body.put("response_format", Map.of("type", "json_object"));
        body.put("max_tokens", request.maxTokens());

        JsonNode response;
        try {
            response = http.post().uri("/chat/completions").contentType(MediaType.APPLICATION_JSON).body(body)
                    .retrieve().body(JsonNode.class);
        } catch (RestClientException e) {
            throw new LlmException("Appel au serveur LLM en échec : " + e.getMessage(), e);
        }
        JsonNode choice = response == null ? null : response.path("choices").path(0);
        if (choice == null || choice.isMissingNode()) {
            throw new LlmException("Réponse du serveur LLM sans contenu.");
        }
        if ("length".equals(choice.path("finish_reason").asText())) {
            throw new LlmException("Réponse du modèle tronquée (limite de longueur).");
        }
        String content = choice.path("message").path("content").asText("");
        if (content.isBlank()) {
            throw new LlmException("Réponse du serveur LLM vide.");
        }
        return content;
    }

    private String json(Map<String, Object> schema) {
        try {
            return mapper.writeValueAsString(schema);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
