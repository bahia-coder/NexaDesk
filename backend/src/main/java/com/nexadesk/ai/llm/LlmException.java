package com.nexadesk.ai.llm;

/** The model could not give a usable answer; callers fall back to the heuristic. */
public class LlmException extends RuntimeException {

    public LlmException(String message) {
        super(message);
    }

    public LlmException(String message, Throwable cause) {
        super(message, cause);
    }
}
