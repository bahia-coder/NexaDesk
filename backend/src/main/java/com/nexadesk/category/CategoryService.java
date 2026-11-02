package com.nexadesk.category;

import com.nexadesk.audit.AuditAction;
import com.nexadesk.audit.AuditService;
import com.nexadesk.category.dto.CategoryDtos.CategoryDto;
import com.nexadesk.category.dto.CategoryDtos.CategoryRequest;
import com.nexadesk.category.dto.CategoryDtos.SubCategoryDto;
import com.nexadesk.category.dto.CategoryDtos.SubCategoryRequest;
import com.nexadesk.exception.BusinessException;
import com.nexadesk.exception.ErrorCode;
import com.nexadesk.exception.ResourceNotFoundException;
import com.nexadesk.ticket.TicketRepository;
import com.nexadesk.ticket.TicketStatus;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ticket categories and subcategories. They are deactivated rather than deleted because tickets keep
 * pointing to them; inactive entries are hidden from ticket creation but stay readable on old tickets.
 */
@Service
@RequiredArgsConstructor
public class CategoryService {

    static final String DEFAULT_ICON = "tag";

    private final TicketCategoryRepository categoryRepository;
    private final TicketSubCategoryRepository subCategoryRepository;
    private final TicketRepository ticketRepository;
    private final CategoryMapper mapper;
    private final AuditService audit;

    private static final String AUDITED = "CATEGORY";

    /**
     * @param includeInactive admin view: also returns deactivated categories and subcategories
     */
    @Transactional(readOnly = true)
    public List<CategoryDto> list(boolean includeInactive) {
        List<TicketCategory> categories = categoryRepository.findAllByOrderByNameAsc();
        if (includeInactive) {
            return categories.stream().map(mapper::toDto).toList();
        }
        return categories.stream().filter(TicketCategory::isActive).map(mapper::toActiveView).toList();
    }

    @Transactional
    public CategoryDto create(CategoryRequest request) {
        String name = request.name().trim();
        if (categoryRepository.existsByNameIgnoreCase(name)) {
            throw new BusinessException(ErrorCode.DUPLICATE_NAME, "Une catégorie « " + name + " » existe déjà.");
        }
        TicketCategory category = new TicketCategory(name, blankToNull(request.description()), icon(request.icon()),
                normalizeKeywords(request.keywords()));
        categoryRepository.save(category);
        audit.record(AuditAction.CATEGORY_CREATED, AUDITED, category.getId(), "Création de la catégorie " + name,
                null, snapshot(category));
        return mapper.toDto(category);
    }

    @Transactional
    public CategoryDto update(Long id, CategoryRequest request) {
        TicketCategory category = findCategory(id);
        String name = request.name().trim();
        if (categoryRepository.existsByNameIgnoreCaseAndIdNot(name, id)) {
            throw new BusinessException(ErrorCode.DUPLICATE_NAME, "Une catégorie « " + name + " » existe déjà.");
        }
        Map<String, Object> before = snapshot(category);
        category.setName(name);
        category.setDescription(blankToNull(request.description()));
        category.setIcon(icon(request.icon()));
        category.setKeywords(normalizeKeywords(request.keywords()));
        audit.record(AuditAction.CATEGORY_UPDATED, AUDITED, id, "Modification de la catégorie " + name, before,
                snapshot(category));
        return mapper.toDto(category);
    }

    /** A category still used by open tickets cannot be deactivated (they would point to a hidden category). */
    @Transactional
    public CategoryDto setActive(Long id, boolean active) {
        TicketCategory category = findCategory(id);
        if (!active) {
            long openTickets = ticketRepository.countByCategory_IdAndStatusIn(id, TicketStatus.OPEN);
            if (openTickets > 0) {
                throw new BusinessException(ErrorCode.CATEGORY_IN_USE, "La catégorie « " + category.getName()
                        + " » est utilisée par " + openTickets + " ticket(s) en cours.");
            }
        }
        if (category.isActive() != active) {
            audit.record(AuditAction.CATEGORY_STATUS_CHANGED, AUDITED, id,
                    (active ? "Réactivation" : "Désactivation") + " de la catégorie " + category.getName());
        }
        category.setActive(active);
        return mapper.toDto(category);
    }

    private static Map<String, Object> snapshot(TicketCategory c) {
        return AuditService.values("name", c.getName(), "description", c.getDescription(), "icon", c.getIcon(),
                "keywords", c.getKeywords());
    }

    @Transactional
    public SubCategoryDto addSubcategory(Long categoryId, SubCategoryRequest request) {
        TicketCategory category = findCategory(categoryId);
        String name = request.name().trim();
        if (subCategoryRepository.existsByCategory_IdAndNameIgnoreCase(categoryId, name)) {
            throw duplicateSubcategory(name, category);
        }
        if (!category.isActive()) {
            throw new BusinessException(ErrorCode.INACTIVE_REFERENCE,
                    "Impossible d'ajouter une sous-catégorie à une catégorie désactivée.");
        }
        return mapper.toDto(subCategoryRepository.save(category.addSubcategory(name)));
    }

    @Transactional
    public SubCategoryDto renameSubcategory(Long id, SubCategoryRequest request) {
        TicketSubCategory subcategory = findSubcategory(id);
        String name = request.name().trim();
        Long categoryId = subcategory.getCategory().getId();
        if (subCategoryRepository.existsByCategory_IdAndNameIgnoreCaseAndIdNot(categoryId, name, id)) {
            throw duplicateSubcategory(name, subcategory.getCategory());
        }
        subcategory.setName(name);
        return mapper.toDto(subcategory);
    }

    @Transactional
    public SubCategoryDto setSubcategoryActive(Long id, boolean active) {
        TicketSubCategory subcategory = findSubcategory(id);
        subcategory.setActive(active);
        return mapper.toDto(subcategory);
    }

    private TicketCategory findCategory(Long id) {
        return categoryRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Catégorie", id));
    }

    private TicketSubCategory findSubcategory(Long id) {
        return subCategoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Sous-catégorie", id));
    }

    private static BusinessException duplicateSubcategory(String name, TicketCategory category) {
        return new BusinessException(ErrorCode.DUPLICATE_NAME,
                "La sous-catégorie « " + name + " » existe déjà dans « " + category.getName() + " ».");
    }

    private static String icon(String icon) {
        return icon == null || icon.isBlank() ? DEFAULT_ICON : icon;
    }

    /** "  VPN, wifi ,, Réseau " -> "vpn, wifi, réseau": lower-case, trimmed, without empty entries. */
    static String normalizeKeywords(String keywords) {
        if (keywords == null || keywords.isBlank()) {
            return null;
        }
        List<String> words = Arrays.stream(keywords.split(","))
                .map(String::trim)
                .filter(word -> !word.isEmpty())
                .map(word -> word.toLowerCase(Locale.ROOT))
                .distinct()
                .toList();
        return words.isEmpty() ? null : String.join(", ", words);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
