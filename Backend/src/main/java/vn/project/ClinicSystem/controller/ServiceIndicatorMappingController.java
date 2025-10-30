package vn.project.ClinicSystem.controller;

import java.util.List;

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
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import vn.project.ClinicSystem.model.ServiceIndicatorMapping;
import vn.project.ClinicSystem.model.dto.ServiceIndicatorMappingRequest;
import vn.project.ClinicSystem.model.dto.ServiceIndicatorMappingUpdateRequest;
import vn.project.ClinicSystem.service.ServiceIndicatorMappingService;

@RestController
@RequestMapping("/medical-services/{serviceId}/indicators")
public class ServiceIndicatorMappingController {

    private final ServiceIndicatorMappingService mappingService;

    public ServiceIndicatorMappingController(ServiceIndicatorMappingService mappingService) {
        this.mappingService = mappingService;
    }

    @PreAuthorize("hasRole('ADMIN') or hasRole('DOCTOR')")
    @GetMapping
    public ResponseEntity<List<ServiceIndicatorMapping>> listMappings(@PathVariable("serviceId") Long serviceId) {
        return ResponseEntity.ok(mappingService.listByMedicalService(serviceId));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public ResponseEntity<ServiceIndicatorMapping> createMapping(
            @PathVariable("serviceId") Long serviceId,
            @Valid @RequestBody ServiceIndicatorMappingRequest request) {
        ServiceIndicatorMapping created = mappingService.create(serviceId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{mappingId}")
    public ResponseEntity<ServiceIndicatorMapping> updateMapping(
            @PathVariable("serviceId") Long serviceId,
            @PathVariable("mappingId") Long mappingId,
            @Valid @RequestBody ServiceIndicatorMappingUpdateRequest request) {
        ServiceIndicatorMapping existing = mappingService.getById(mappingId);
        if (!existing.getMedicalService().getId().equals(serviceId)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        ServiceIndicatorMapping updated = mappingService.update(mappingId, request);
        return ResponseEntity.ok(updated);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{mappingId}")
    public ResponseEntity<Void> deleteMapping(
            @PathVariable("serviceId") Long serviceId,
            @PathVariable("mappingId") Long mappingId) {
        ServiceIndicatorMapping existing = mappingService.getById(mappingId);
        if (!existing.getMedicalService().getId().equals(serviceId)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        mappingService.delete(mappingId);
        return ResponseEntity.noContent().build();
    }
}
