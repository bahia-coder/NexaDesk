package com.nexadesk.category;

import com.nexadesk.category.dto.CategoryDtos.CategoryDto;
import com.nexadesk.category.dto.CategoryDtos.CategoryRefDto;
import com.nexadesk.category.dto.CategoryDtos.PriorityDto;
import com.nexadesk.category.dto.CategoryDtos.SubCategoryDto;
import java.util.List;
import org.mapstruct.Mapper;

@Mapper
public interface CategoryMapper {

    CategoryDto toDto(TicketCategory category);

    SubCategoryDto toDto(TicketSubCategory subcategory);

    CategoryRefDto toRef(TicketCategory category);

    List<CategoryRefDto> toRefs(Iterable<TicketCategory> categories);

    PriorityDto toDto(Priority priority);

    /** Non-admin view: inactive subcategories are not offered when creating a ticket. */
    default CategoryDto toActiveView(TicketCategory category) {
        List<SubCategoryDto> activeSubcategories = category.getSubcategories().stream()
                .filter(TicketSubCategory::isActive)
                .map(this::toDto)
                .toList();
        return new CategoryDto(category.getId(), category.getName(), category.getDescription(), category.getIcon(),
                category.getKeywords(), category.isActive(), activeSubcategories);
    }
}
