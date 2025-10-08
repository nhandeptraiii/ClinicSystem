package vn.project.ClinicSystem.model.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AppointmentRequestCreateRequest {

    @NotBlank(message = "Họ tên không được để trống")
    private String fullName;

    @NotBlank(message = "Số điện thoại không được để trống")
    private String phone;

    @Email(message = "Email không hợp lệ")
    private String email;

    @PastOrPresent(message = "Ngày sinh không hợp lệ")
    private LocalDate dateOfBirth;

    private LocalDateTime preferredAt;

    @Size(max = 500)
    private String symptomDescription;

    @NotNull(message = "Cần chọn dịch vụ khám")
    private Long medicalServiceId;
}
