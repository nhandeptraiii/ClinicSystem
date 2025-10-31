package vn.project.ClinicSystem.model.dto;

import java.util.List;

import org.springframework.data.domain.Page;

import lombok.Getter;
import lombok.Setter;
import vn.project.ClinicSystem.model.Medication;

@Getter
@Setter
public class MedicationPageResponse {
    private List<Medication> items;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;
    private boolean hasNext;
    private boolean hasPrevious;

    public static MedicationPageResponse from(Page<Medication> pageData) {
        MedicationPageResponse response = new MedicationPageResponse();
        response.setItems(pageData.getContent());
        response.setPage(pageData.getNumber());
        response.setSize(pageData.getSize());
        response.setTotalElements(pageData.getTotalElements());
        response.setTotalPages(pageData.getTotalPages());
        response.setHasNext(pageData.hasNext());
        response.setHasPrevious(pageData.hasPrevious());
        return response;
    }
}
