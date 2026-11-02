package com.nexadesk.ai.llm;

import java.util.List;
import java.util.Map;

/**
 * A connected language model. Implementations only transport the request; prompts, JSON validation and the
 * heuristic fallback live in the AI service, so switching provider is a configuration change.
 */
public interface LlmClient extends AutoCloseable {

    enum Role { USER, ASSISTANT }

    record Message(Role role, String content) {
    }

    /**
     * @param schema JSON Schema of the expected answer: the model must reply with one JSON object matching it
     */
    record Request(String system, List<Message> messages, Map<String, Object> schema, int maxTokens) {
    }

    /** Provider name shown to users ("Anthropic", "OpenAI-compatible"). */
    String provider();

    String model();

    /**
     * @return the raw text of the answer (expected to be a JSON object)
     * @throws LlmException on any transport, quota, refusal or truncation problem
     */
    String complete(Request request);

    @Override
    default void close() {
    }
}
