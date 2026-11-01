package com.nexadesk.category;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class PriorityMatrixTest {

    @ParameterizedTest(name = "impact {0} × urgence {1} → {2}")
    @CsvSource({
            "HIGH,   LOW,    MEDIUM",
            "HIGH,   MEDIUM, HIGH",
            "HIGH,   HIGH,   CRITICAL",
            "MEDIUM, LOW,    LOW",
            "MEDIUM, MEDIUM, MEDIUM",
            "MEDIUM, HIGH,   HIGH",
            "LOW,    LOW,    LOW",
            "LOW,    MEDIUM, LOW",
            "LOW,    HIGH,   MEDIUM"
    })
    void matchesTheDocumentedMatrix(Level impact, Level urgency, PriorityCode expected) {
        assertThat(PriorityMatrix.priorityFor(impact, urgency)).isEqualTo(expected);
    }

    @Test
    void entriesCoverAllNineCombinations() {
        assertThat(PriorityMatrix.entries()).hasSize(9);
    }

    @Test
    void criticalRequiresBothHighImpactAndHighUrgency() {
        assertThat(PriorityMatrix.entries())
                .filteredOn(e -> e.priority() == PriorityCode.CRITICAL)
                .singleElement()
                .satisfies(e -> {
                    assertThat(e.impact()).isEqualTo(Level.HIGH);
                    assertThat(e.urgency()).isEqualTo(Level.HIGH);
                });
    }

    @Test
    void missingInputIsRejected() {
        assertThatThrownBy(() -> PriorityMatrix.priorityFor(null, Level.LOW)).isInstanceOf(NullPointerException.class);
    }
}
