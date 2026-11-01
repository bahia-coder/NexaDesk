package com.nexadesk.ticket;

import java.util.Optional;

/**
 * Where a ticket's documented solution can come from when the handler resolves it without retyping one.
 * Declared here and implemented by the intervention module (dependency inversion): the ticket module does
 * not depend on the intervention module, which already depends on it.
 */
public interface SolutionSource {

    Optional<String> latestSolution(Long ticketId);
}
