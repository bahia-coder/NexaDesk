package com.nexadesk.ticket;

public enum TicketType {
    INCIDENT("INC"),
    SERVICE_REQUEST("REQ");

    private final String referencePrefix;

    TicketType(String referencePrefix) {
        this.referencePrefix = referencePrefix;
    }

    public String referencePrefix() {
        return referencePrefix;
    }
}
