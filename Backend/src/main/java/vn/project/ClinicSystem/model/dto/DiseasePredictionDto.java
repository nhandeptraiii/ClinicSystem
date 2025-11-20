package vn.project.ClinicSystem.model.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DiseasePredictionDto {

    private String disease;
    private Double probability;
    private String severity;
    private Boolean shouldBookAppointment;
}
