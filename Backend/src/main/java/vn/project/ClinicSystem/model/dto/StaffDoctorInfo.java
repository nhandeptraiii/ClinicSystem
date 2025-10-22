package vn.project.ClinicSystem.model.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class StaffDoctorInfo {

    @NotBlank(message = "Chuyên khoa không được để trống")
    private String specialty;

    @NotBlank(message = "Số giấy phép không được để trống")
    private String licenseNumber;

    private String examinationRoom;

    private String biography;
}
