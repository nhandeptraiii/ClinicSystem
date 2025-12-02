package vn.project.ClinicSystem.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DiseaseRequest {

    @NotBlank(message = "Mã bệnh không được để trống")
    @Size(max = 50, message = "Mã bệnh không được vượt quá 50 ký tự")
    private String code;

    @NotBlank(message = "Tên bệnh không được để trống")
    @Size(max = 255, message = "Tên bệnh không được vượt quá 255 ký tự")
    private String name;

    @Size(max = 2000, message = "Mô tả không được vượt quá 2000 ký tự")
    private String description;
}
