package vn.project.ClinicSystem.controller;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import vn.project.ClinicSystem.model.Appointment;
import vn.project.ClinicSystem.model.dto.AppointmentCreateRequest;
import vn.project.ClinicSystem.model.dto.AppointmentPageResponse;
import vn.project.ClinicSystem.model.dto.AppointmentStatusUpdateRequest;
import vn.project.ClinicSystem.model.dto.AppointmentUpdateRequest;
import vn.project.ClinicSystem.model.enums.AppointmentLifecycleStatus;
import vn.project.ClinicSystem.service.AppointmentService;
import vn.project.ClinicSystem.util.SecurityUtil;

@RestController
@RequestMapping("/appointments")
public class AppointmentController {

    private final AppointmentService appointmentService;

    public AppointmentController(AppointmentService appointmentService) {
        this.appointmentService = appointmentService;
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public ResponseEntity<Appointment> createAppointment(
            @Valid @RequestBody AppointmentCreateRequest request) {
        String staffUsername = SecurityUtil.getCurrentUserLogin()
                .orElseThrow(() -> new IllegalStateException("Không thể xác định người dùng đang đăng nhập"));
        Appointment created = appointmentService.createAppointment(request, staffUsername);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PreAuthorize("hasRole('ADMIN') or hasRole('DOCTOR')")
    @GetMapping
    public ResponseEntity<?> getAppointments(
            @RequestParam(value = "doctorId", required = false) Long doctorId,
            @RequestParam(value = "patientId", required = false) Long patientId,
            @RequestParam(value = "status", required = false) AppointmentLifecycleStatus status,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size) {

        // Hỗ trợ phân trang khi có page hoặc size
        if (page != null || size != null) {
            int safePage = page != null ? Math.max(page, 0) : 0;
            int safeSize = size != null ? Math.min(Math.max(size, 1), 50) : 10;
            Pageable pageable = PageRequest.of(
                    safePage,
                    safeSize,
                    Sort.by(Sort.Order.desc("scheduledAt")));
            AppointmentPageResponse response = appointmentService.getPaged(keyword, status, pageable);
            return ResponseEntity.ok(response);
        }

        // Giữ nguyên logic cũ cho các trường hợp filter đặc biệt
        if (doctorId != null) {
            return ResponseEntity.ok(appointmentService.findByDoctor(doctorId));
        }
        if (patientId != null) {
            return ResponseEntity.ok(appointmentService.findByPatient(patientId));
        }
        if (status != null) {
            return ResponseEntity.ok(appointmentService.findByStatus(status));
        }
        return ResponseEntity.ok(appointmentService.findAll());
    }

    @PreAuthorize("hasRole('ADMIN') or hasRole('DOCTOR')")
    @GetMapping("/{id}")
    public ResponseEntity<Appointment> getAppointmentById(@PathVariable("id") Long id) {
        return ResponseEntity.ok(appointmentService.getById(id));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}")
    public ResponseEntity<Appointment> updateAppointment(@PathVariable("id") Long id,
            @Valid @RequestBody AppointmentUpdateRequest request) {
        Appointment updated = appointmentService.updateAppointment(id, request);
        return ResponseEntity.ok(updated);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PatchMapping("/{id}/status")
    public ResponseEntity<Appointment> updateAppointmentStatus(
            @PathVariable("id") Long id,
            @Valid @RequestBody AppointmentStatusUpdateRequest request) {
        Appointment updated = appointmentService.updateStatus(id, request);
        return ResponseEntity.ok(updated);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteAppointment(@PathVariable("id") Long id) {
        appointmentService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
