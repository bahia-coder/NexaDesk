package com.nexadesk.ticket;

public enum PendingReason {
    /** Resumes automatically when the requester answers. */
    WAITING_USER,
    WAITING_VENDOR,
    WAITING_PART,
    OTHER
}
