package vn.project.ClinicSystem.model.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class StaffDoctorSummary {
    private Long id;
    private String specialty;
    private String licenseNumber;
    private String biography;
}
