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
import vn.project.ClinicSystem.model.Doctor;
import vn.project.ClinicSystem.model.dto.DoctorAccountAssignmentRequest;
import vn.project.ClinicSystem.service.DoctorService;

@RestController
@RequestMapping("/doctors")
public class DoctorController {
    private final DoctorService doctorService;

    public DoctorController(DoctorService doctorService) {
        this.doctorService = doctorService;
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public ResponseEntity<Doctor> createDoctor(@Valid @RequestBody Doctor doctor) {
        Doctor created = doctorService.create(doctor);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping
    public ResponseEntity<List<Doctor>> getDoctors(@RequestParam(value = "specialty", required = false) String specialty) {
        return ResponseEntity.ok(doctorService.searchBySpecialty(specialty));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Doctor> getDoctorById(@PathVariable Long id) {
        return ResponseEntity.ok(doctorService.getById(id));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}")
    public ResponseEntity<Doctor> updateDoctor(@PathVariable Long id, @Valid @RequestBody Doctor doctor) {
        Doctor updated = doctorService.update(id, doctor);
        return ResponseEntity.ok(updated);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteDoctor(@PathVariable Long id) {
        doctorService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/{id}/assign-account")
    public ResponseEntity<Doctor> assignAccount(@PathVariable Long id,
            @Valid @RequestBody DoctorAccountAssignmentRequest request) {
        Doctor doctor = doctorService.assignAccount(id, request.getUserId());
        return ResponseEntity.ok(doctor);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/{id}/detach-account")
    public ResponseEntity<Doctor> detachAccount(@PathVariable Long id) {
        Doctor doctor = doctorService.detachAccount(id);
        return ResponseEntity.ok(doctor);
    }
}