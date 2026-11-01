package com.nexadesk.category;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nexadesk.category.dto.CategoryDtos.CategoryDto;
import com.nexadesk.category.dto.CategoryDtos.CategoryRequest;
import com.nexadesk.category.dto.CategoryDtos.SubCategoryDto;
import com.nexadesk.category.dto.CategoryDtos.SubCategoryRequest;
import com.nexadesk.exception.ErrorCode;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    @Mock
    private TicketCategoryRepository categoryRepository;
    @Mock
    private TicketSubCategoryRepository subCategoryRepository;

    private CategoryService service;

    @BeforeEach
    void setUp() {
        service = new CategoryService(categoryRepository, subCategoryRepository, new CategoryMapperImpl());
    }

    private static TicketCategory category(long id, String name, boolean active) {
        TicketCategory category = new TicketCategory(name, null, "tag", null);
        ReflectionTestUtils.setField(category, "id", id);
        category.setActive(active);
        return category;
    }

    @Test
    void createRejectsANameThatDiffersOnlyByCase() {
        when(categoryRepository.existsByNameIgnoreCase("matériel")).thenReturn(true);

        assertThatThrownBy(() -> service.create(new CategoryRequest(" matériel ", null, null, null)))
                .extracting("code").isEqualTo(ErrorCode.DUPLICATE_NAME);
        verify(categoryRepository, never()).save(any());
    }

    @Test
    void createNormalizesKeywordsAndDefaultsTheIcon() {
        when(categoryRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        CategoryDto created = service.create(new CategoryRequest("Téléphonie", "  ", null, " VoIP, ,Standard , voip"));

        assertThat(created.icon()).isEqualTo(CategoryService.DEFAULT_ICON);
        assertThat(created.description()).isNull();
        assertThat(created.keywords()).isEqualTo("voip, standard");
    }

    @Test
    void nonAdminListHidesInactiveCategoriesAndSubcategories() {
        TicketCategory active = category(1, "Matériel", true);
        active.addSubcategory("Écran");
        active.addSubcategory("Ancien modèle").setActive(false);
        TicketCategory inactive = category(2, "Fax", false);
        when(categoryRepository.findAllByOrderByNameAsc()).thenReturn(List.of(active, inactive));

        List<CategoryDto> visible = service.list(false);

        assertThat(visible).extracting(CategoryDto::name).containsExactly("Matériel");
        assertThat(visible.getFirst().subcategories()).extracting(SubCategoryDto::name).containsExactly("Écran");
        assertThat(service.list(true)).hasSize(2);
    }

    @Test
    void subcategoryNamesAreUniqueWithinTheirCategory() {
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category(1, "Impression", true)));
        when(subCategoryRepository.existsByCategory_IdAndNameIgnoreCase(1L, "Scanner")).thenReturn(true);

        assertThatThrownBy(() -> service.addSubcategory(1L, new SubCategoryRequest("Scanner")))
                .extracting("code").isEqualTo(ErrorCode.DUPLICATE_NAME);
    }

    @Test
    void cannotAddASubcategoryToADeactivatedCategory() {
        when(categoryRepository.findById(2L)).thenReturn(Optional.of(category(2, "Fax", false)));

        assertThatThrownBy(() -> service.addSubcategory(2L, new SubCategoryRequest("Télécopieur")))
                .extracting("code").isEqualTo(ErrorCode.INACTIVE_REFERENCE);
    }

    @Test
    void unknownCategoryIsNotFound() {
        when(categoryRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.setActive(99L, false))
                .extracting("code").isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
    }
}
