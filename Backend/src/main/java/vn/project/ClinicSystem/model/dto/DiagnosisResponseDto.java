package vn.project.ClinicSystem.model.dto;

import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DiagnosisResponseDto {

    private List<DiseasePredictionDto> predictions = new ArrayList<>();
}
