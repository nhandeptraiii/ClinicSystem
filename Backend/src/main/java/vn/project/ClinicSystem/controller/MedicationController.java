package vn.project.ClinicSystem.controller;

import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import vn.project.ClinicSystem.model.Medication;
import vn.project.ClinicSystem.model.dto.MedicationCreateRequest;
import vn.project.ClinicSystem.model.dto.MedicationPageResponse;
import vn.project.ClinicSystem.model.dto.MedicationUpdateRequest;
import vn.project.ClinicSystem.service.MedicationService;

@RestController
@RequestMapping("/medications")
public class MedicationController {

    private final MedicationService medicationService;

    public MedicationController(MedicationService medicationService) {
        this.medicationService = medicationService;
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'PHARMACIST')")
    @PostMapping
    public ResponseEntity<Medication> createMedication(@Valid @RequestBody MedicationCreateRequest request) {
        Medication created = medicationService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping
    public ResponseEntity<?> getMedications(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size) {
        if (page != null || size != null) {
            int safePage = page != null ? Math.max(page, 0) : 0;
            int safeSize = size != null ? Math.min(Math.max(size, 1), 50) : 10;
            Pageable pageable = PageRequest.of(
                    safePage,
                    safeSize,
                    Sort.by(Sort.Order.asc("name")));
            MedicationPageResponse response = medicationService.getPaged(keyword, pageable);
            return ResponseEntity.ok(response);
        }
        if (keyword != null && !keyword.trim().isEmpty()) {
            return ResponseEntity.ok(medicationService.search(keyword));
        }
        return ResponseEntity.ok(medicationService.findAll());
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/{id}")
    public ResponseEntity<Medication> getMedication(@PathVariable("id") Long id) {
        return ResponseEntity.ok(medicationService.getById(id));
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'PHARMACIST')")
    @PutMapping("/{id}")
    public ResponseEntity<Medication> updateMedication(
            @PathVariable("id") Long id,
            @Valid @RequestBody MedicationUpdateRequest request) {
        Medication updated = medicationService.update(id, request);
        return ResponseEntity.ok(updated);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteMedication(@PathVariable("id") Long id) {
        medicationService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
