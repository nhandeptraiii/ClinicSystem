package vn.project.ClinicSystem.service;

import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.project.ClinicSystem.model.dto.BookingFunnelResponse;
import vn.project.ClinicSystem.model.enums.AppointmentLifecycleStatus;
import vn.project.ClinicSystem.repository.AppointmentRepository;
import vn.project.ClinicSystem.repository.AppointmentRequestRepository;

@Service
@Transactional(readOnly = true)
public class AnalyticsService {

    private final AppointmentRequestRepository appointmentRequestRepository;
    private final AppointmentRepository appointmentRepository;
    private final ZoneId zoneId = ZoneId.systemDefault();

    public AnalyticsService(AppointmentRequestRepository appointmentRequestRepository,
            AppointmentRepository appointmentRepository) {
        this.appointmentRequestRepository = appointmentRequestRepository;
        this.appointmentRepository = appointmentRepository;
    }

    public BookingFunnelResponse getBookingFunnel(String monthParam) {
        YearMonth targetMonth = parseMonth(monthParam);
        Instant start = targetMonth.atDay(1).atStartOfDay(zoneId).toInstant();
        Instant endExclusive = targetMonth.plusMonths(1).atDay(1).atStartOfDay(zoneId).toInstant();

        long requestsTotal = appointmentRequestRepository
                .countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(start, endExclusive);
        long requestsApproved = appointmentRequestRepository
                .countByStatusAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                        AppointmentLifecycleStatus.CONFIRMED,
                        start,
                        endExclusive);
        long requestsCancelled = appointmentRequestRepository
                .countByStatusAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                        AppointmentLifecycleStatus.CANCELLED,
                        start,
                        endExclusive);

        long appointmentsCreated = appointmentRepository
                .countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(start, endExclusive);
        long appointmentsFromRequests = appointmentRepository
                .countByRequestIsNotNullAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(start, endExclusive);
        long appointmentsCompleted = appointmentRepository
                .countByStatusAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                        AppointmentLifecycleStatus.COMPLETED,
                        start,
                        endExclusive);
        long appointmentsCancelled = appointmentRepository
                .countByStatusAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                        AppointmentLifecycleStatus.CANCELLED,
                        start,
                        endExclusive);
        long appointmentsCheckedIn = appointmentRepository
                .countByStatusAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                        AppointmentLifecycleStatus.CHECKED_IN,
                        start,
                        endExclusive);

        return BookingFunnelResponse.builder()
                .month(targetMonth.toString())
                .requestsTotal(requestsTotal)
                .requestsApproved(requestsApproved)
                .requestsCancelled(requestsCancelled)
                .appointmentsCreated(appointmentsCreated)
                .appointmentsFromRequests(appointmentsFromRequests)
                .appointmentsCompleted(appointmentsCompleted)
                .appointmentsCancelled(appointmentsCancelled)
                .appointmentsCheckedIn(appointmentsCheckedIn)
                .build();
    }

    private YearMonth parseMonth(String monthParam) {
        if (monthParam == null || monthParam.isBlank()) {
            return YearMonth.now(zoneId);
        }
        try {
            return YearMonth.parse(monthParam);
        } catch (Exception ex) {
            return YearMonth.now(zoneId);
        }
    }
}

