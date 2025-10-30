package vn.project.ClinicSystem.controller;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
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
import vn.project.ClinicSystem.model.IndicatorTemplate;
import vn.project.ClinicSystem.model.dto.IndicatorTemplatePageResponse;
import vn.project.ClinicSystem.model.dto.IndicatorTemplateRequest;
import vn.project.ClinicSystem.service.IndicatorTemplateService;

@RestController
@RequestMapping("/indicator-templates")
public class IndicatorTemplateController {

    private final IndicatorTemplateService templateService;

    public IndicatorTemplateController(IndicatorTemplateService templateService) {
        this.templateService = templateService;
    }

    @GetMapping
    public ResponseEntity<IndicatorTemplatePageResponse> getAllIndicatorTemplates(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "10") int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 50);
        Pageable pageable = PageRequest.of(safePage, safeSize);

        IndicatorTemplatePageResponse response = templateService.findAll(keyword, pageable);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<IndicatorTemplate> getTemplate(@PathVariable("id") Long id) {
        return ResponseEntity.ok(templateService.getById(id));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public ResponseEntity<IndicatorTemplate> createTemplate(
            @Valid @RequestBody IndicatorTemplateRequest request) {
        IndicatorTemplate created = templateService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}")
    public ResponseEntity<IndicatorTemplate> updateTemplate(
            @PathVariable("id") Long id,
            @Valid @RequestBody IndicatorTemplateRequest request) {
        IndicatorTemplate updated = templateService.update(id, request);
        return ResponseEntity.ok(updated);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTemplate(@PathVariable("id") Long id) {
        templateService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
