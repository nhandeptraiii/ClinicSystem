package vn.project.ClinicSystem.controller;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
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
import vn.project.ClinicSystem.model.Disease;
import vn.project.ClinicSystem.model.dto.DiseasePageResponse;
import vn.project.ClinicSystem.model.dto.DiseaseRequest;
import vn.project.ClinicSystem.model.dto.DiseaseUpdateRequest;
import vn.project.ClinicSystem.service.DiseaseService;

@RestController
@RequestMapping("/api/diseases")
public class DiseaseController {

    private final DiseaseService diseaseService;

    public DiseaseController(DiseaseService diseaseService) {
        this.diseaseService = diseaseService;
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public ResponseEntity<Disease> createDisease(@Valid @RequestBody DiseaseRequest request) {
        Disease created = diseaseService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'DOCTOR')")
    @GetMapping
    public ResponseEntity<?> getDiseases(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size) {

        if (page != null || size != null) {
            int safePage = page != null ? Math.max(page, 0) : 0;
            int safeSize = size != null ? Math.min(Math.max(size, 1), 50) : 10;
            Pageable pageable = PageRequest.of(
                    safePage,
                    safeSize,
                    Sort.by(Sort.Order.asc("name"), Sort.Order.asc("code")));
            DiseasePageResponse response = diseaseService.getPaged(keyword, pageable);
            return ResponseEntity.ok(response);
        }

        if (StringUtils.hasText(keyword)) {
            return ResponseEntity.ok(diseaseService.search(keyword));
        }

        return ResponseEntity.ok(diseaseService.findAll());
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'DOCTOR')")
    @GetMapping("/{id}")
    public ResponseEntity<Disease> getDisease(@PathVariable("id") Long id) {
        return ResponseEntity.ok(diseaseService.getById(id));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}")
    public ResponseEntity<Disease> updateDisease(
            @PathVariable("id") Long id,
            @Valid @RequestBody DiseaseUpdateRequest request) {
        Disease updated = diseaseService.update(id, request);
        return ResponseEntity.ok(updated);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteDisease(@PathVariable("id") Long id) {
        diseaseService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
