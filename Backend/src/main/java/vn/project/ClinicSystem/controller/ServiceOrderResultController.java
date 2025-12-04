package vn.project.ClinicSystem.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import vn.project.ClinicSystem.model.ServiceOrderResult;
import vn.project.ClinicSystem.model.dto.ServiceOrderResultRequest;
import vn.project.ClinicSystem.service.ServiceOrderPrintService;
import vn.project.ClinicSystem.service.ServiceOrderResultService;

@RestController
@RequestMapping("/service-orders/{orderId}/results")
public class ServiceOrderResultController {

    private final ServiceOrderResultService resultService;
    private final ServiceOrderPrintService printService;

    public ServiceOrderResultController(ServiceOrderResultService resultService,
            ServiceOrderPrintService printService) {
        this.resultService = resultService;
        this.printService = printService;
    }

    @PreAuthorize("hasRole('ADMIN') or hasRole('DOCTOR')")
    @GetMapping
    public ResponseEntity<List<ServiceOrderResult>> getResults(@PathVariable("orderId") Long orderId) {
        return ResponseEntity.ok(resultService.findResults(orderId));
    }

    @PreAuthorize("hasRole('ADMIN') or hasRole('DOCTOR')")
    @PostMapping
    public ResponseEntity<List<ServiceOrderResult>> recordResults(
            @PathVariable("orderId") Long orderId,
            @Valid @RequestBody ServiceOrderResultRequest request) {
        resultService.recordResults(orderId, request);
        List<ServiceOrderResult> results = resultService.findResults(orderId);
        return ResponseEntity.status(HttpStatus.CREATED).body(results);
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'DOCTOR', 'RECEPTIONIST')")
    @GetMapping("/print")
    public ResponseEntity<byte[]> printServiceOrder(@PathVariable("orderId") Long orderId) {
        byte[] pdfBytes = printService.generateServiceOrderPdf(orderId);
        return ResponseEntity.ok()
                .header("Content-Type", "application/pdf")
                .header("Content-Disposition", "inline; filename=\"service-order-" + orderId + ".pdf\"")
                .header("X-Content-Type-Options", "nosniff")
                .body(pdfBytes);
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'DOCTOR', 'RECEPTIONIST')")
    @GetMapping("/print-result")
    public ResponseEntity<byte[]> printServiceOrderResult(@PathVariable("orderId") Long orderId) {
        byte[] pdfBytes = printService.generateServiceOrderResultPdf(orderId);
        return ResponseEntity.ok()
                .header("Content-Type", "application/pdf")
                .header("Content-Disposition", "inline; filename=\"service-order-result-" + orderId + ".pdf\"")
                .header("X-Content-Type-Options", "nosniff")
                .body(pdfBytes);
    }
}
