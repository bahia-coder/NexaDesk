package com.nexadesk.ticket;

import static org.assertj.core.api.Assertions.assertThat;

import com.nexadesk.TestcontainersConfiguration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.support.TransactionTemplate;

/** Same context configuration as the other integration tests, so Spring's test context cache reuses it. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class TicketReferenceGeneratorIntegrationTest {

    private static final int CONCURRENT_CREATIONS = 20;

    @Autowired
    private TicketReferenceGenerator generator;
    @Autowired
    private TransactionTemplate transactionTemplate;

    @Test
    void concurrentGenerationNeverProducesTheSameReference() throws Exception {
        Callable<String> nextInItsOwnTransaction = () -> transactionTemplate.execute(
                status -> generator.next(TicketType.SERVICE_REQUEST));

        List<Future<String>> futures = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(8)) {
            for (int i = 0; i < CONCURRENT_CREATIONS; i++) {
                futures.add(pool.submit(nextInItsOwnTransaction));
            }
        }
        Set<String> references = new HashSet<>();
        for (Future<String> future : futures) {
            references.add(future.get());
        }

        assertThat(references).hasSize(CONCURRENT_CREATIONS)
                .allMatch(reference -> reference.matches("REQ-\\d{4}-\\d{4}"));
        List<Integer> numbers = references.stream()
                .map(reference -> Integer.parseInt(reference.substring(reference.lastIndexOf('-') + 1)))
                .sorted()
                .toList();
        assertThat(numbers.getLast() - numbers.getFirst())
                .as("numbers are consecutive: no gap, no duplicate")
                .isEqualTo(CONCURRENT_CREATIONS - 1);
    }
}
