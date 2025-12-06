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
import vn.project.ClinicSystem.model.Billing;
import vn.project.ClinicSystem.model.BillingItem;
import vn.project.ClinicSystem.model.dto.BillingItemCreateRequest;
import vn.project.ClinicSystem.model.dto.BillingItemUpdateRequest;
import vn.project.ClinicSystem.model.dto.BillingPageResponse;
import vn.project.ClinicSystem.model.dto.BillingStatusUpdateRequest;
import vn.project.ClinicSystem.model.enums.BillingStatus;
import vn.project.ClinicSystem.service.BillingService;
import vn.project.ClinicSystem.service.BillingPrintService;

@RestController
@RequestMapping("/billings")
public class BillingController {

    private final BillingService billingService;
    private final BillingPrintService billingPrintService;

    public BillingController(BillingService billingService,
            BillingPrintService billingPrintService) {
        this.billingService = billingService;
        this.billingPrintService = billingPrintService;
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPTIONIST')")
    @GetMapping("/{id}")
    public ResponseEntity<Billing> getBilling(@PathVariable("id") Long id) {
        return ResponseEntity.ok(billingService.getById(id));
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPTIONIST')")
    @GetMapping
    public ResponseEntity<?> getBillings(
            @RequestParam(value = "patientId", required = false) Long patientId,
            @RequestParam(value = "visitId", required = false) Long visitId,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "status", required = false) BillingStatus status,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size) {
        if (visitId != null) {
            return ResponseEntity.ok(List.of(billingService.getByVisit(visitId)));
        }

        int safePage = page != null ? Math.max(page, 0) : 0;
        int safeSize = size != null ? Math.min(Math.max(size, 1), 50) : 10;
        Pageable pageable = PageRequest.of(
                safePage,
                safeSize,
                Sort.by(Sort.Order.desc("issuedAt")));
        BillingPageResponse response = billingService.getPaged(keyword, status, patientId, pageable);
        return ResponseEntity.ok(response);
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPTIONIST')")
    @PostMapping("/visits/{visitId}/generate")
    public ResponseEntity<Billing> generateBilling(@PathVariable("visitId") Long visitId) {
        Billing billing = billingService.generateForVisit(visitId);
        return ResponseEntity.status(HttpStatus.CREATED).body(billing);
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPTIONIST')")
    @PutMapping("/{id}/status")
    public ResponseEntity<Billing> updateStatus(
            @PathVariable("id") Long id,
            @Valid @RequestBody BillingStatusUpdateRequest request) {
        return ResponseEntity.ok(billingService.updateStatus(id, request));
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPTIONIST')")
    @PostMapping("/{id}/items")
    public ResponseEntity<Billing> addManualItem(
            @PathVariable("id") Long id,
            @Valid @RequestBody BillingItemCreateRequest request) {
        Billing billing = billingService.addManualItem(id, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(billing);
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPTIONIST')")
    @PutMapping("/{billingId}/items/{itemId}")
    public ResponseEntity<BillingItem> updateItem(
            @PathVariable("billingId") Long billingId,
            @PathVariable("itemId") Long itemId,
            @Valid @RequestBody BillingItemUpdateRequest request) {
        return ResponseEntity.ok(billingService.updateItem(billingId, itemId, request));
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPTIONIST')")
    @DeleteMapping("/{billingId}/items/{itemId}")
    public ResponseEntity<Void> deleteItem(
            @PathVariable("billingId") Long billingId,
            @PathVariable("itemId") Long itemId) {
        billingService.deleteItem(billingId, itemId);
        return ResponseEntity.noContent().build();
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPTIONIST')")
    @GetMapping("/{id}/print")
    public ResponseEntity<byte[]> printBilling(@PathVariable("id") Long id) {
        byte[] pdfBytes = billingPrintService.generateBillingPdf(id);
        return ResponseEntity.ok()
                .header("Content-Type", "application/pdf")
                .header("Content-Disposition", "inline; filename=\"billing-" + id + ".pdf\"")
                .header("X-Content-Type-Options", "nosniff")
                .body(pdfBytes);
    }
}
