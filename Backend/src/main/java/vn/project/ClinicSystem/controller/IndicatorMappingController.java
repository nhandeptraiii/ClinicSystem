package vn.project.ClinicSystem.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import vn.project.ClinicSystem.model.ServiceIndicatorMapping;
import vn.project.ClinicSystem.service.ServiceIndicatorMappingService;

@RestController
@RequestMapping("/indicator-mappings")
public class IndicatorMappingController {

    private final ServiceIndicatorMappingService mappingService;

    public IndicatorMappingController(ServiceIndicatorMappingService mappingService) {
        this.mappingService = mappingService;
    }

    @GetMapping("/by-template/{templateId}")
    public ResponseEntity<List<ServiceIndicatorMapping>> getMappingsByTemplate(
            @PathVariable("templateId") Long templateId) {
        return ResponseEntity.ok(mappingService.listByIndicatorTemplate(templateId));
    }
}
