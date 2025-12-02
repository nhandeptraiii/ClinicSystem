package vn.project.ClinicSystem.model.dto;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PatientVisitUpdateRequest {

    @Size(max = 500, message = "Chẩn đoán tạm thời không được vượt quá 500 ký tự")
    private String provisionalDiagnosis;

    private java.util.List<Long> diseaseIds;
}
