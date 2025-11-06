package vn.project.ClinicSystem.model.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ServiceOrderCreateRequest {

    @NotNull(message = "Cần chọn dịch vụ chuyên khoa")
    private Long medicalServiceId;

    @Size(max = 500)
    private String note;
}
