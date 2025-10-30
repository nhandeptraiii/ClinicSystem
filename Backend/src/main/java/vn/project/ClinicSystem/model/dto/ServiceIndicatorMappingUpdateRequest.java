package vn.project.ClinicSystem.model.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ServiceIndicatorMappingUpdateRequest {

    private Boolean required;

    private Integer displayOrder;
}
