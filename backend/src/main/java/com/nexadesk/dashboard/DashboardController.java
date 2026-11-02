package com.nexadesk.dashboard;

import com.nexadesk.dashboard.dto.DashboardDtos.AdminDashboard;
import com.nexadesk.dashboard.dto.DashboardDtos.EmployeeDashboard;
import com.nexadesk.dashboard.dto.DashboardDtos.TechnicianDashboard;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
@Tag(name = "Dashboard", description = "Tableaux de bord par rôle, calculés à partir des données réelles")
public class DashboardController {

    private final DashboardService service;

    @GetMapping("/admin")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Dashboard administrateur",
            description = "days = 7, 30, 90 ou 365. Tendances calculées par rapport à la période précédente.")
    public AdminDashboard admin(@RequestParam(defaultValue = "30") int days) {
        return service.admin(days);
    }

    @GetMapping("/technician")
    @PreAuthorize("hasRole('TECHNICIAN')")
    @Operation(summary = "Dashboard technicien")
    public TechnicianDashboard technician() {
        return service.technician();
    }

    @GetMapping("/employee")
    @PreAuthorize("hasRole('EMPLOYEE')")
    @Operation(summary = "Dashboard employé")
    public EmployeeDashboard employee() {
        return service.employee();
    }
}
