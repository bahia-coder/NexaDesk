package com.nexadesk.user.dto;

import com.nexadesk.category.dto.CategoryDtos.CategoryRefDto;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public final class TechnicianDtos {

    private TechnicianDtos() {
    }

    /** A technician as offered for assignment. The open-ticket workload is added by the ticket module. */
    public record TechnicianDto(Long id, String fullName, String email, String jobTitle, String avatarColor,
                                List<CategoryRefDto> specialties) {
    }

    public record SpecialtiesRequest(
            @NotNull(message = "La liste des catégories est obligatoire (vide pour tout retirer).")
            @Size(max = 30)
            List<@NotNull Long> categoryIds) {
    }
}
