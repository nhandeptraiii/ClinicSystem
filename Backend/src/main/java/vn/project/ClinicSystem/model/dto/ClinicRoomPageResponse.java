package vn.project.ClinicSystem.model.dto;

import java.util.List;

import org.springframework.data.domain.Page;

import lombok.Getter;
import lombok.Setter;
import vn.project.ClinicSystem.model.ClinicRoom;

@Getter
@Setter
public class ClinicRoomPageResponse {
    private List<ClinicRoom> items;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;
    private boolean hasNext;
    private boolean hasPrevious;

    public static ClinicRoomPageResponse from(Page<ClinicRoom> pageData) {
        ClinicRoomPageResponse response = new ClinicRoomPageResponse();
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
