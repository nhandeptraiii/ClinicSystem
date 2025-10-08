package vn.project.ClinicSystem.model.dto;

import java.time.LocalDateTime;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AppointmentRequestApproveRequest {

    @NotNull(message = "Cần gán bệnh nhân cho yêu cầu")
    private Long patientId;

    @NotNull(message = "Cần chỉ định bác sĩ khám")
    private Long doctorId;

    @NotNull(message = "Cần chọn thời gian khám")
    private LocalDateTime scheduledAt;

    private Long clinicRoomId; // nếu null sẽ dùng phòng mặc định của dịch vụ

    private String staffNote;
}
