package com.nexadesk.ai;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * @param mode              requested mode; {@code llm} without usable settings falls back to {@code heuristic}
 * @param provider          which connector serves the {@code llm} mode
 * @param apiKey            read from the environment only (never committed); optional for a local Ollama
 * @param baseUrl           OpenAI-compatible server, e.g. {@code http://localhost:11434/v1}
 * @param model             model name; Anthropic defaults to {@link #DEFAULT_ANTHROPIC_MODEL}
 * @param timeout           per call; past it, the heuristic answers instead
 * @param requestsPerMinute LLM calls allowed per user and per minute (cost and abuse control)
 */
@Validated
@ConfigurationProperties("nexadesk.ai")
public record AiProperties(@NotNull Mode mode, @NotNull Provider provider, String apiKey, String baseUrl,
                           String model, @NotNull Duration timeout, @Min(1) @Max(600) int requestsPerMinute) {

    public static final String DEFAULT_ANTHROPIC_MODEL = "claude-opus-5-5";

    public enum Mode { DISABLED, HEURISTIC, LLM }

    public enum Provider { ANTHROPIC, OPENAI_COMPATIBLE }
}
