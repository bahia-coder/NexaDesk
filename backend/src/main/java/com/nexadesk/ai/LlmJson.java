package com.nexadesk.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexadesk.ai.llm.LlmException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Strict reading of a model's answer: it must be one JSON object that maps onto the expected record and passes
 * its Bean Validation constraints. Anything else is an {@link LlmException}, so the caller falls back to the
 * heuristic instead of showing a half-understood answer.
 */
@Component
public class LlmJson {

    private final ObjectMapper mapper;
    private final Validator validator;

    public LlmJson(ObjectMapper mapper, Validator validator) {
        this.mapper = mapper;
        this.validator = validator;
    }

    public <T> T read(String answer, Class<T> type) {
        int start = answer.indexOf('{');
        int end = answer.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new LlmException("La réponse du modèle n'est pas un objet JSON.");
        }
        T value;
        try {
            // Some local models wrap the object in prose or a Markdown code fence: only the object is read.
            value = mapper.readValue(answer.substring(start, end + 1), type);
        } catch (JsonProcessingException e) {
            throw new LlmException("JSON du modèle illisible : " + e.getOriginalMessage(), e);
        }
        Set<ConstraintViolation<T>> violations = validator.validate(value);
        if (!violations.isEmpty()) {
            ConstraintViolation<T> first = violations.iterator().next();
            throw new LlmException("Réponse du modèle invalide : " + first.getPropertyPath() + " " + first.getMessage());
        }
        return value;
    }
}
