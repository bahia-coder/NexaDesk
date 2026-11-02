package com.nexadesk.equipment;

public enum EquipmentStatus {
    /** Available in the IT storage room; held by nobody. */
    IN_STOCK,
    /** Deployed: assigned to a user, or shared at a location (printer, server). */
    IN_USE,
    IN_REPAIR,
    /** Out of the inventory for good (scrapped, lost, sold). Read-only. */
    RETIRED
}
