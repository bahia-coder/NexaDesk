package com.nexadesk.equipment;

public enum EquipmentStatus {
    /** Available in the IT storage room; held by nobody. */
    IN_STOCK("En stock"),
    /** Deployed: assigned to a user, or shared at a location (printer, server). */
    IN_USE("En service"),
    IN_REPAIR("En réparation"),
    /** Out of the inventory for good (scrapped, lost, sold). Read-only. */
    RETIRED("Mis au rebut");

    private final String label;

    EquipmentStatus(String label) {
        this.label = label;
    }

    /** French label, as the interface shows it (audit trail summaries). */
    public String label() {
        return label;
    }
}
