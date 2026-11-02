package com.nexadesk.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexadesk.ai.llm.AnthropicLlmClient;
import com.nexadesk.ai.llm.LlmClient;
import com.nexadesk.ai.llm.OpenAiCompatibleLlmClient;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Effective AI mode, decided once at startup from {@link AiProperties}. Asking for {@code llm} without the
 * settings it needs never breaks the application: it runs in heuristic mode and says so in the logs.
 */
@Slf4j
@Component
public class AiRuntime implements DisposableBean {

    private final AiSource mode;
    private final LlmClient client;

    @Autowired
    public AiRuntime(AiProperties properties, ObjectMapper mapper) {
        this(properties, createClient(properties, mapper));
    }

    /** For tests: a given client (or none). */
    AiRuntime(AiProperties properties, LlmClient client) {
        this.client = client;
        this.mode = switch (properties.mode()) {
            case DISABLED -> AiSource.DISABLED;
            case HEURISTIC -> AiSource.HEURISTIC;
            case LLM -> client == null ? AiSource.HEURISTIC : AiSource.LLM;
        };
        if (this.mode == AiSource.LLM) {
            log.info("AI mode: LLM ({} – {})", client.provider(), client.model());
        } else {
            log.info("AI mode: {}", this.mode);
        }
    }

    private static LlmClient createClient(AiProperties p, ObjectMapper mapper) {
        if (p.mode() != AiProperties.Mode.LLM) {
            return null;
        }
        return switch (p.provider()) {
            case ANTHROPIC -> {
                if (isBlank(p.apiKey())) {
                    log.warn("AI_MODE=llm with the Anthropic provider but AI_API_KEY is empty: heuristic mode.");
                    yield null;
                }
                yield new AnthropicLlmClient(p.apiKey(), isBlank(p.model()) ? AiProperties.DEFAULT_ANTHROPIC_MODEL
                        : p.model(), p.timeout());
            }
            case OPENAI_COMPATIBLE -> {
                if (isBlank(p.baseUrl()) || isBlank(p.model())) {
                    log.warn("AI_MODE=llm with an OpenAI-compatible provider needs AI_BASE_URL and AI_MODEL: "
                            + "heuristic mode.");
                    yield null;
                }
                yield new OpenAiCompatibleLlmClient(p.baseUrl(), p.apiKey(), p.model(), p.timeout(), mapper);
            }
        };
    }

    public AiSource mode() {
        return mode;
    }

    /** Present only in LLM mode. */
    public Optional<LlmClient> client() {
        return mode == AiSource.LLM ? Optional.of(client) : Optional.empty();
    }

    @Override
    public void destroy() {
        if (client != null) {
            client.close();
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
