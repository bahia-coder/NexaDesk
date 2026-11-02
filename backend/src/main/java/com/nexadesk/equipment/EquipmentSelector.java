package com.nexadesk.equipment;

import com.nexadesk.exception.BusinessException;
import com.nexadesk.exception.ErrorCode;
import com.nexadesk.exception.ResourceNotFoundException;
import com.nexadesk.user.RoleCode;
import com.nexadesk.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * RM-14 — which device a user may attach to a ticket: an employee their own devices or shared deployed ones;
 * technicians and admins any device still in the inventory.
 */
@Component
@RequiredArgsConstructor
public class EquipmentSelector {

    private final EquipmentRepository repository;

    /** @return the device, or null when {@code equipmentId} is null */
    public Equipment requireSelectable(Long equipmentId, User user) {
        if (equipmentId == null) {
            return null;
        }
        Equipment equipment = repository.findById(equipmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Équipement", equipmentId));
        if (!isSelectable(equipment, user)) {
            throw new BusinessException(ErrorCode.EQUIPMENT_NOT_SELECTABLE);
        }
        return equipment;
    }

    static boolean isSelectable(Equipment equipment, User user) {
        if (equipment.getStatus() == EquipmentStatus.RETIRED) {
            return false;
        }
        if (user.getRoleCode() != RoleCode.EMPLOYEE) {
            return true;
        }
        return equipment.isShared()
                || (equipment.getAssignedUser() != null && equipment.getAssignedUser().getId().equals(user.getId()));
    }
}
