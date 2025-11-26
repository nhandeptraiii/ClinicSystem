package vn.project.ClinicSystem.controller;

import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import vn.project.ClinicSystem.model.PatientVisit;
import vn.project.ClinicSystem.model.ServiceOrder;
import vn.project.ClinicSystem.model.dto.PatientVisitCreateRequest;
import vn.project.ClinicSystem.model.dto.PatientVisitPageResponse;
import vn.project.ClinicSystem.model.dto.PatientVisitStatusUpdateRequest;
import vn.project.ClinicSystem.model.dto.PatientVisitUpdateRequest;
import vn.project.ClinicSystem.model.dto.ServiceOrderCreateRequest;
import vn.project.ClinicSystem.model.dto.ServiceOrderStatusUpdateRequest;
import vn.project.ClinicSystem.model.enums.VisitStatus;
import vn.project.ClinicSystem.service.VisitService;

@RestController
@RequestMapping("/visits")
public class VisitController {

    private final VisitService visitService;

    public VisitController(VisitService visitService) {
        this.visitService = visitService;
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPTIONIST')")
    @PostMapping
    public ResponseEntity<PatientVisit> createVisit(@Valid @RequestBody PatientVisitCreateRequest request) {
        PatientVisit created = visitService.createVisit(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'DOCTOR', 'RECEPTIONIST')")
    @GetMapping("/{id}")
    public ResponseEntity<PatientVisit> getVisit(@PathVariable("id") Long id) {
        return ResponseEntity.ok(visitService.getById(id));
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'DOCTOR', 'RECEPTIONIST')")
    @GetMapping
    public ResponseEntity<?> getVisits(
            @RequestParam(value = "patientId", required = false) Long patientId,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "status", required = false) VisitStatus status,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size) {
        // Nếu có patientId, trả về danh sách theo patient (không phân trang)
        if (patientId != null) {
            return ResponseEntity.ok(visitService.findByPatient(patientId));
        }
        // Nếu có page hoặc size, sử dụng phân trang
        if (page != null || size != null) {
            int safePage = page != null ? Math.max(page, 0) : 0;
            int safeSize = size != null ? Math.min(Math.max(size, 1), 50) : 10;
            Pageable pageable = PageRequest.of(
                    safePage,
                    safeSize,
                    Sort.by(Sort.Order.desc("createdAt")));
            PatientVisitPageResponse response = visitService.getPaged(keyword, status, pageable);
            return ResponseEntity.ok(response);
        }
        // Mặc định trả về tất cả (không phân trang)
        return ResponseEntity.ok(visitService.findAll());
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'DOCTOR', 'RECEPTIONIST')")
    @GetMapping("/completed-without-billing")
    public ResponseEntity<PatientVisitPageResponse> getCompletedVisitsWithoutBilling(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size) {
        int safePage = page != null ? Math.max(page, 0) : 0;
        int safeSize = size != null ? Math.min(Math.max(size, 1), 50) : 10;
        Pageable pageable = PageRequest.of(
                safePage,
                safeSize,
                Sort.by(Sort.Order.desc("createdAt")));
        PatientVisitPageResponse response = visitService.getCompletedWithoutBilling(keyword, pageable);
        return ResponseEntity.ok(response);
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'DOCTOR')")
    @GetMapping("/{id}/service-orders")
    public ResponseEntity<List<ServiceOrder>> getServiceOrders(@PathVariable("id") Long id) {
        return ResponseEntity.ok(visitService.findServiceOrders(id));
    }

    @PreAuthorize("hasRole('DOCTOR')")
    @PostMapping("/{id}/service-orders")
    public ResponseEntity<List<ServiceOrder>> createServiceOrders(
            @PathVariable("id") Long id,
            @RequestBody List<@Valid ServiceOrderCreateRequest> requests) {
        List<ServiceOrder> created = visitService.createServiceOrders(id, requests);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PreAuthorize("hasRole('DOCTOR')")
    @PatchMapping("/{id}/status")
    public ResponseEntity<PatientVisit> updateVisitStatus(@PathVariable("id") Long id,
            @Valid @RequestBody PatientVisitStatusUpdateRequest request) {
        return ResponseEntity.ok(visitService.updateStatus(id, request));
    }

    @PreAuthorize("hasRole('DOCTOR')")
    @PatchMapping("/{id}")
    public ResponseEntity<PatientVisit> updateVisit(@PathVariable("id") Long id,
            @Valid @RequestBody PatientVisitUpdateRequest request) {
        return ResponseEntity.ok(visitService.updateClinicalInfo(id, request));
    }

    @PreAuthorize("hasRole('DOCTOR')")
    @PatchMapping("/service-orders/{id}/status")
    public ResponseEntity<ServiceOrder> updateServiceOrderStatus(@PathVariable("id") Long id,
            @Valid @RequestBody ServiceOrderStatusUpdateRequest request) {
        return ResponseEntity.ok(visitService.updateServiceOrderStatus(id, request));
    }
}
