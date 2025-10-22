package vn.project.ClinicSystem.model.dto;

import java.time.LocalDate;
import java.util.Set;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class StaffUpdateRequest {

    private String fullName;

    @Email(message = "Email không hợp lệ")
    private String email;

    @Pattern(regexp = "^\\d{10}$", message = "Số điện thoại phải gồm đúng 10 chữ số")
    private String phone;

    private String gender;

    private LocalDate dateOfBirth;

    private String password;

    private Set<String> roles;

    private String status;

    @Valid
    private StaffDoctorInfo doctor;
}
