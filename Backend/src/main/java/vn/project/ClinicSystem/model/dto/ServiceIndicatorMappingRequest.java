package vn.project.ClinicSystem.model.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ServiceIndicatorMappingRequest {

    @NotNull(message = "ID template không được null")
    private Long indicatorTemplateId;

    private Boolean required = Boolean.TRUE;

    private Integer displayOrder = 0;
}
