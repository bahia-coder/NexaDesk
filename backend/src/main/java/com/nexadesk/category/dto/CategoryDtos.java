package com.nexadesk.category.dto;

import com.nexadesk.category.Level;
import com.nexadesk.category.PriorityCode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

/** Request and response records of the category / priority reference data. */
public final class CategoryDtos {

    private static final String ICON_REGEX = "^[a-z0-9]+(-[a-z0-9]+)*$";
    private static final String ICON_MESSAGE = "L'icône doit être un nom d'icône Lucide (ex. « wifi », « key-round »).";
    private static final String COLOR_REGEX = "^#[0-9A-Fa-f]{6}$";

    private CategoryDtos() {
    }

    public record SubCategoryDto(Long id, String name, boolean active) {
    }

    public record CategoryDto(Long id, String name, String description, String icon, String keywords,
                              boolean active, List<SubCategoryDto> subcategories) {
    }

    /** Lightweight reference used inside other resources (specialties, tickets). */
    public record CategoryRefDto(Long id, String name, String icon) {
    }

    public record CategoryRequest(
            @NotBlank(message = "Le nom est obligatoire.") @Size(max = 80) String name,
            @Size(max = 255) String description,
            @Size(max = 40) @Pattern(regexp = ICON_REGEX, message = ICON_MESSAGE) String icon,
            @Size(max = 1000) String keywords) {
    }

    public record SubCategoryRequest(@NotBlank(message = "Le nom est obligatoire.") @Size(max = 80) String name) {
    }

    public record PriorityDto(Long id, PriorityCode code, String label, int rank, String color) {
    }

    public record PriorityRequest(
            @NotBlank(message = "Le libellé est obligatoire.") @Size(max = 50) String label,
            @NotBlank @Pattern(regexp = COLOR_REGEX, message = "La couleur doit être au format #RRGGBB.") String color) {
    }

    public record MatrixEntryDto(Level impact, Level urgency, PriorityCode priority) {
    }
}
