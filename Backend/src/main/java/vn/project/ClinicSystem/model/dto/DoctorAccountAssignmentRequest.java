package vn.project.ClinicSystem.model.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DoctorAccountAssignmentRequest {
    @NotNull(message = "userId không được để trống")
    private Long userId;
}