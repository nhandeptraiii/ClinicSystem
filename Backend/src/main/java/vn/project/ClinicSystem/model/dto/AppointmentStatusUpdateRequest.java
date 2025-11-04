package vn.project.ClinicSystem.model.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import vn.project.ClinicSystem.model.enums.AppointmentLifecycleStatus;

@Getter
@Setter
public class AppointmentStatusUpdateRequest {

    @NotNull(message = "Trạng thái không được để trống")
    private AppointmentLifecycleStatus status;

    private String note;
}
