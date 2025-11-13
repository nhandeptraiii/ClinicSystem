package vn.project.ClinicSystem.model.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class BookingFunnelResponse {
    private final String month;

    private final long requestsTotal;
    private final long requestsApproved;
    private final long requestsCancelled;

    private final long appointmentsCreated;
    private final long appointmentsFromRequests;
    private final long appointmentsCompleted;
    private final long appointmentsCancelled;
    private final long appointmentsCheckedIn;
}

