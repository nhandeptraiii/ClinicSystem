package vn.project.ClinicSystem.model.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import vn.project.ClinicSystem.model.enums.PrescriptionStatus;

@Getter
@Setter
public class PrescriptionStatusUpdateRequest {

    @NotNull(message = "Trạng thái không được để trống")
    private PrescriptionStatus status;

    @Size(max = 1000, message = "Ghi chú tối đa 1000 ký tự")
    private String pharmacistNote;
}
