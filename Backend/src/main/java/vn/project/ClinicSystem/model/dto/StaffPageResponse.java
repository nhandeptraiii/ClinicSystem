package vn.project.ClinicSystem.model.dto;

import java.util.List;
import java.util.Map;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class StaffPageResponse {
    private List<StaffResponse> items;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;
    private boolean hasNext;
    private boolean hasPrevious;
    private long totalStaff;
    private Map<String, Long> roleTotals;
}
