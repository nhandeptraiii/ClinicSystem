package vn.project.ClinicSystem.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import vn.project.ClinicSystem.model.dto.BookingFunnelResponse;
import vn.project.ClinicSystem.service.AnalyticsService;

@RestController
@RequestMapping("/analytics")
@PreAuthorize("hasRole('ADMIN')")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    public AnalyticsController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @GetMapping("/booking-funnel")
    public ResponseEntity<BookingFunnelResponse> getBookingFunnel(
            @RequestParam(value = "month", required = false) String month) {
        return ResponseEntity.ok(analyticsService.getBookingFunnel(month));
    }
}

