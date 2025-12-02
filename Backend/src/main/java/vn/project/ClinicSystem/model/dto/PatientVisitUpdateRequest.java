package vn.project.ClinicSystem.model.dto;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PatientVisitUpdateRequest {

    @Size(max = 500, message = "Chẩn đoán tạm thời không được vượt quá 500 ký tự")
    private String provisionalDiagnosis;

    @Size(max = 2000, message = "Ghi chú lâm sàng không được vượt quá 2000 ký tự")
    private String clinicalNote;

    private Long diseaseId;

    @Size(max = 2000, message = "Ghi chú chẩn đoán không được vượt quá 2000 ký tự")
    private String diagnosisNote;
}
