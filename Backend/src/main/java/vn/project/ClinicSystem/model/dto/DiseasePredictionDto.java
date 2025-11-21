package vn.project.ClinicSystem.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DiseasePredictionDto {

    private String disease;
    private Double probability;
    private String severity;
    @JsonProperty("should_book_appointment") // <--- Thêm dòng này để map đúng key từ Python
    private Boolean shouldBookAppointment;
}
