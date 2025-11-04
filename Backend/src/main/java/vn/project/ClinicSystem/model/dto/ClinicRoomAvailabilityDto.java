package vn.project.ClinicSystem.model.dto;

import lombok.Getter;
import lombok.Setter;
import vn.project.ClinicSystem.model.ClinicRoom;

@Getter
@Setter
public class ClinicRoomAvailabilityDto {
    private Long id;
    private String code;
    private String name;
    private String floor;
    private boolean available;

    public ClinicRoomAvailabilityDto(ClinicRoom room, boolean available) {
        this.id = room.getId();
        this.code = room.getCode();
        this.name = room.getName();
        this.floor = room.getFloor();
        this.available = available;
    }
}
