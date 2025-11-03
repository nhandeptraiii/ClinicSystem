package vn.project.ClinicSystem.model.dto;

import java.util.List;

import org.springframework.data.domain.Page;

import lombok.Getter;
import lombok.Setter;
import vn.project.ClinicSystem.model.AppointmentRequest;

@Getter
@Setter
public class AppointmentRequestPageResponse {
    private List<AppointmentRequest> items;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;
    private boolean hasNext;
    private boolean hasPrevious;

    public static AppointmentRequestPageResponse from(Page<AppointmentRequest> pageData) {
        AppointmentRequestPageResponse response = new AppointmentRequestPageResponse();
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
