package com.nexadesk.category;

import com.nexadesk.audit.AuditAction;
import com.nexadesk.audit.AuditService;
import com.nexadesk.category.dto.CategoryDtos.MatrixEntryDto;
import com.nexadesk.category.dto.CategoryDtos.PriorityDto;
import com.nexadesk.category.dto.CategoryDtos.PriorityRequest;
import com.nexadesk.exception.ResourceNotFoundException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PriorityService {

    private final PriorityRepository repository;
    private final CategoryMapper mapper;
    private final AuditService audit;

    @Transactional(readOnly = true)
    public List<PriorityDto> list() {
        return repository.findAllByOrderByRankAsc().stream().map(mapper::toDto).toList();
    }

    @Transactional
    public PriorityDto update(Long id, PriorityRequest request) {
        Priority priority = repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Priorité", id));
        Map<String, Object> before = AuditService.values("label", priority.getLabel(), "color", priority.getColor());
        priority.setLabel(request.label().trim());
        priority.setColor(request.color().toUpperCase(Locale.ROOT));
        audit.record(AuditAction.PRIORITY_UPDATED, "PRIORITY", priority.getCode(),
                "Modification de la priorité " + priority.getCode(), before,
                AuditService.values("label", priority.getLabel(), "color", priority.getColor()));
        return mapper.toDto(priority);
    }

    public List<MatrixEntryDto> matrix() {
        return PriorityMatrix.entries().stream()
                .map(e -> new MatrixEntryDto(e.impact(), e.urgency(), e.priority()))
                .toList();
    }
}
