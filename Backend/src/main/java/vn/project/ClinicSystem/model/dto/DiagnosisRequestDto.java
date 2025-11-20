package vn.project.ClinicSystem.model.dto;

import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DiagnosisRequestDto {

    private List<String> symptoms = new ArrayList<>();
    private Integer topK;
}
