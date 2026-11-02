package com.nexadesk.ai;

import com.nexadesk.ai.HeuristicClassifier.CategoryOption;
import com.nexadesk.ai.HeuristicClassifier.SubOption;
import com.nexadesk.category.TicketCategory;
import com.nexadesk.category.TicketCategoryRepository;
import com.nexadesk.category.TicketSubCategory;
import java.util.Arrays;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Active categories and subcategories, as plain records the classifier (heuristic or model) can use. */
@Component
@RequiredArgsConstructor
class AiCatalog {

    private final TicketCategoryRepository categoryRepository;

    @Transactional(readOnly = true)
    List<CategoryOption> categories() {
        return categoryRepository.findAllByOrderByNameAsc().stream()
                .filter(TicketCategory::isActive)
                .map(category -> new CategoryOption(category.getId(), category.getName(), category.getDescription(),
                        category.getIcon(),
                        category.getKeywords() == null ? List.of()
                                : Arrays.stream(category.getKeywords().split(",")).map(String::strip)
                                        .filter(keyword -> !keyword.isEmpty()).toList(),
                        category.getSubcategories().stream().filter(TicketSubCategory::isActive)
                                .map(sub -> new SubOption(sub.getId(), sub.getName())).toList()))
                .toList();
    }
}
