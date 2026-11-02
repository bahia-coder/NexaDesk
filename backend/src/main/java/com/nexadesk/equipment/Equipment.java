package com.nexadesk.equipment;

import com.nexadesk.common.BaseEntity;
import com.nexadesk.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "equipment")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Equipment extends BaseEntity {

    @Column(name = "inventory_code", nullable = false, unique = true, length = 20, updatable = false)
    private String inventoryCode;

    @Column(nullable = false, length = 120)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EquipmentType type;

    @Column(length = 60)
    private String brand;

    @Column(length = 80)
    private String model;

    /** Stored upper-case, so uniqueness does not depend on how it was typed. */
    @Column(name = "serial_number", unique = true, length = 80)
    private String serialNumber;

    @Column(name = "acquisition_date")
    private LocalDate acquisitionDate;

    @Column(name = "warranty_end_date")
    private LocalDate warrantyEndDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EquipmentStatus status = EquipmentStatus.IN_STOCK;

    @Column(length = 120)
    private String location;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_user_id")
    private User assignedUser;

    @Column(length = 1000)
    private String notes;

    @Version
    @Column(nullable = false)
    private int version;

    public Equipment(String inventoryCode, String name, EquipmentType type) {
        this.inventoryCode = inventoryCode;
        this.name = name;
        this.type = type;
    }

    public boolean isUnderWarranty(LocalDate today) {
        return warrantyEndDate != null && !warrantyEndDate.isBefore(today);
    }

    /** Shared device: deployed somewhere but held by nobody in particular. */
    public boolean isShared() {
        return status == EquipmentStatus.IN_USE && assignedUser == null;
    }
}
