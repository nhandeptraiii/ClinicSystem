package vn.project.ClinicSystem.model.dto;

import java.time.DayOfWeek;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class WorkScheduleDayDto {
    @NotNull
    private DayOfWeek dayOfWeek;

    private boolean morning;

    private boolean afternoon;

    private Long clinicRoomId;

    private String clinicRoomName;

    private String clinicRoomCode;
}
