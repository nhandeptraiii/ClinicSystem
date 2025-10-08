package vn.project.ClinicSystem.model.dto;

import java.time.LocalDateTime;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AppointmentCreateRequest {

    @NotNull(message = "Bệnh nhân không được để trống")
    private Long patientId;

    @NotNull(message = "Bác sĩ không được để trống")
    private Long doctorId;

    @NotNull(message = "Dịch vụ khám không được để trống")
    private Long medicalServiceId;

    @NotNull(message = "Thời gian khám không được để trống")
    private LocalDateTime scheduledAt;

    private Long clinicRoomId; // nếu null sẽ dùng phòng của dịch vụ

    private String reason;
    private String notes;
}
