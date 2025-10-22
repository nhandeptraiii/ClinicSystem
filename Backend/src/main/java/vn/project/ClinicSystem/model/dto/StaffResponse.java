package vn.project.ClinicSystem.model.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class StaffResponse {
    private Long id;
    private String fullName;
    private String email;
    private String phone;
    private String gender;
    private LocalDate dateOfBirth;
    private String status;
    private Set<String> roles;
    private Instant createdAt;
    private Instant updatedAt;
    private StaffDoctorSummary doctor;
}
