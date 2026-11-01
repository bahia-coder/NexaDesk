package com.nexadesk.ticket;

public enum SlaState {
    /** No SLA (rejected ticket, or created before SLA tracking existed). */
    NONE,
    ON_TRACK,
    /** At or above the rule's warning threshold of consumed time. */
    AT_RISK,
    /** Deadline passed while unresolved. */
    BREACHED,
    /** Clock stopped while waiting (user, vendor, part). */
    PAUSED,
    /** Resolved before the deadline. */
    MET,
    /** Resolved after the deadline. */
    MISSED
}
