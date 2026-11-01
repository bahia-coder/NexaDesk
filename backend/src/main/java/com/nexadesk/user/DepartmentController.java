package com.nexadesk.user;

import com.nexadesk.user.dto.DepartmentDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Tag(name = "Utilisateurs")
public class DepartmentController {

    private final UserAdminService service;

    @GetMapping("/api/departments")
    @Operation(summary = "Lister les départements")
    public List<DepartmentDto> list() {
        return service.departments();
    }
}
