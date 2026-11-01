package com.nexadesk.category;

import com.nexadesk.category.dto.CategoryDtos.MatrixEntryDto;
import com.nexadesk.category.dto.CategoryDtos.PriorityDto;
import com.nexadesk.category.dto.CategoryDtos.PriorityRequest;
import com.nexadesk.exception.ResourceNotFoundException;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PriorityService {

    private final PriorityRepository repository;
    private final CategoryMapper mapper;

    @Transactional(readOnly = true)
    public List<PriorityDto> list() {
        return repository.findAllByOrderByRankAsc().stream().map(mapper::toDto).toList();
    }

    @Transactional
    public PriorityDto update(Long id, PriorityRequest request) {
        Priority priority = repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Priorité", id));
        priority.setLabel(request.label().trim());
        priority.setColor(request.color().toUpperCase(Locale.ROOT));
        return mapper.toDto(priority);
    }

    public List<MatrixEntryDto> matrix() {
        return PriorityMatrix.entries().stream()
                .map(e -> new MatrixEntryDto(e.impact(), e.urgency(), e.priority()))
                .toList();
    }
}
