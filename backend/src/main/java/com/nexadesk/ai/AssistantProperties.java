package com.nexadesk.ai;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Relevance thresholds of the assistant, on the search score (PostgreSQL {@code ts_rank} + 0.3 × title
 * similarity; 0.6 and more for an article on the exact problem in the demo data).
 *
 * @param candidateMinScore below it, an article only shares a word or two with the question: not even shown to
 *                          the model, which judges the others itself
 * @param reliableScore     from it, an article is trusted without a model (heuristic mode lists only those)
 * @param maxArticles       candidates given to the model
 */
@Validated
@ConfigurationProperties("nexadesk.ai.assistant")
public record AssistantProperties(@DecimalMin("0") @DecimalMax("1") double candidateMinScore,
                                  @DecimalMin("0") @DecimalMax("1") double reliableScore,
                                  @Min(1) @Max(10) int maxArticles) {
}
