package com.nexadesk.equipment.dto;

import com.nexadesk.equipment.Equipment;
import com.nexadesk.equipment.EquipmentStatus;
import com.nexadesk.equipment.EquipmentType;
import com.nexadesk.ticket.dto.TicketDtos.UserRefDto;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;

public final class EquipmentDtos {

    private EquipmentDtos() {
    }

    public record EquipmentDto(Long id, String inventoryCode, String name, EquipmentType type, String brand,
                               String model, String serialNumber, LocalDate acquisitionDate,
                               LocalDate warrantyEndDate, boolean underWarranty, EquipmentStatus status,
                               String location, UserRefDto assignedUser, boolean shared, String notes, int version,
                               Instant createdAt, Instant updatedAt) {
    }

    /** Compact form used inside tickets and in the ticket form's equipment picker. */
    public record EquipmentRefDto(Long id, String inventoryCode, String name, EquipmentType type, String location) {

        public static EquipmentRefDto from(Equipment e) {
            return e == null ? null
                    : new EquipmentRefDto(e.getId(), e.getInventoryCode(), e.getName(), e.getType(), e.getLocation());
        }
    }

    public record AssignmentDto(Long id, UserRefDto user, Instant assignedAt, Instant returnedAt) {
    }

    public record EquipmentFilter(String search, EquipmentType type, EquipmentStatus status, Long assignedUserId) {
    }

    public record EquipmentRequest(
            @NotBlank(message = "Le nom est obligatoire.") @Size(max = 120) String name,
            @NotNull(message = "Le type est obligatoire.") EquipmentType type,
            @Size(max = 60) String brand,
            @Size(max = 80) String model,
            @Size(max = 80) String serialNumber,
            LocalDate acquisitionDate,
            LocalDate warrantyEndDate,
            @Size(max = 120) String location,
            @Size(max = 1000) String notes) {
    }

    /** {@code userId} null returns the device to stock. */
    public record AssignRequest(Long userId) {
    }

    public record StatusRequest(@NotNull(message = "Le statut est obligatoire.") EquipmentStatus status) {
    }
}
