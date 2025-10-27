package vn.project.ClinicSystem.model.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateWorkScheduleRequest {
    @NotEmpty(message = "Danh sách ngày làm việc không được để trống")
    private List<@Valid WorkScheduleDayDto> days;

    @Positive(message = "Phòng khám không hợp lệ")
    private Long clinicRoomId;
}
