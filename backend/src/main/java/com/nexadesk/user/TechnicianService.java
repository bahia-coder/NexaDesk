package com.nexadesk.user;

import com.nexadesk.category.CategoryMapper;
import com.nexadesk.category.TicketCategory;
import com.nexadesk.category.TicketCategoryRepository;
import com.nexadesk.category.dto.CategoryDtos.CategoryRefDto;
import com.nexadesk.exception.BusinessException;
import com.nexadesk.exception.ErrorCode;
import com.nexadesk.exception.ResourceNotFoundException;
import com.nexadesk.user.dto.TechnicianDtos.TechnicianDto;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Technician specialties and the list of technicians available for assignment. */
@Service
@RequiredArgsConstructor
public class TechnicianService {

    private final UserRepository userRepository;
    private final TicketCategoryRepository categoryRepository;
    private final CategoryMapper categoryMapper;

    @Transactional(readOnly = true)
    public List<TechnicianDto> technicians(Long categoryId) {
        List<User> technicians = categoryId == null
                ? userRepository.findActiveTechnicians()
                : userRepository.findActiveTechniciansWithSpecialty(categoryId);
        return technicians.stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public List<CategoryRefDto> specialties(Long userId) {
        return sortedRefs(find(userId).getSpecialties());
    }

    /**
     * Replaces the specialties of a technician. A deactivated category cannot be newly assigned, but one
     * the technician already had is kept (deactivating a category must not silently strip specialties).
     */
    @Transactional
    public List<CategoryRefDto> replaceSpecialties(Long userId, List<Long> categoryIds) {
        User user = find(userId);
        if (user.getRoleCode() != RoleCode.TECHNICIAN) {
            throw new BusinessException(ErrorCode.SPECIALTIES_TECHNICIAN_ONLY);
        }
        Set<Long> requested = new LinkedHashSet<>(categoryIds);
        List<TicketCategory> categories = categoryRepository.findAllByIdIn(List.copyOf(requested));
        if (categories.size() != requested.size()) {
            Set<Long> found = new HashSet<>();
            categories.forEach(c -> found.add(c.getId()));
            Long missing = requested.stream().filter(id -> !found.contains(id)).findFirst().orElseThrow();
            throw new ResourceNotFoundException("Catégorie", missing);
        }
        for (TicketCategory category : categories) {
            if (!category.isActive() && !user.hasSpecialty(category.getId())) {
                throw new BusinessException(ErrorCode.INACTIVE_REFERENCE,
                        "La catégorie « " + category.getName() + " » est désactivée.");
            }
        }
        user.replaceSpecialties(categories);
        return sortedRefs(user.getSpecialties());
    }

    private User find(Long userId) {
        return userRepository.findById(userId).orElseThrow(() -> new ResourceNotFoundException("Utilisateur", userId));
    }

    private TechnicianDto toDto(User user) {
        return new TechnicianDto(user.getId(), user.getFullName(), user.getEmail(), user.getJobTitle(),
                user.getAvatarColor(), sortedRefs(user.getSpecialties()));
    }

    private List<CategoryRefDto> sortedRefs(Set<TicketCategory> categories) {
        return categories.stream()
                .sorted(Comparator.comparing(TicketCategory::getName))
                .map(categoryMapper::toRef)
                .toList();
    }
}
