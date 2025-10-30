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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import vn.project.ClinicSystem.model.IndicatorTemplate;
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
    public ResponseEntity<List<IndicatorTemplate>> listTemplates(
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "activeOnly", defaultValue = "true") boolean activeOnly) {
        if (activeOnly) {
            if (category != null && !category.isBlank()) {
                return ResponseEntity.ok(templateService.findByCategoryActive(category));
            }
            return ResponseEntity.ok(templateService.findAllActive());
        }
        return ResponseEntity.ok(templateService.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<IndicatorTemplate> getTemplate(@PathVariable Long id) {
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
            @PathVariable Long id,
            @Valid @RequestBody IndicatorTemplateRequest request) {
        IndicatorTemplate updated = templateService.update(id, request);
        return ResponseEntity.ok(updated);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTemplate(@PathVariable Long id) {
        templateService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
