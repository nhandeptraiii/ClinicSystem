package vn.project.ClinicSystem.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import vn.project.ClinicSystem.model.dto.DashboardSummaryResponse;
import vn.project.ClinicSystem.service.DashboardService;

@RestController
@RequestMapping("/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/summary")
    public ResponseEntity<DashboardSummaryResponse> getSummary() {
        return ResponseEntity.ok(dashboardService.getTodaySummary());
    }
}
