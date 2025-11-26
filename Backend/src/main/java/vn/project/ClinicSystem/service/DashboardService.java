package vn.project.ClinicSystem.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.project.ClinicSystem.model.enums.AppointmentLifecycleStatus;
import vn.project.ClinicSystem.model.enums.VisitStatus;
import vn.project.ClinicSystem.model.dto.DashboardSummaryResponse;
import vn.project.ClinicSystem.repository.AppointmentRepository;
import vn.project.ClinicSystem.repository.AppointmentRequestRepository;
import vn.project.ClinicSystem.repository.PatientVisitRepository;

@Service
@Transactional(readOnly = true)
public class DashboardService {

    private final AppointmentRepository appointmentRepository;
    private final AppointmentRequestRepository appointmentRequestRepository;
    private final PatientVisitRepository patientVisitRepository;
    private final ZoneId zoneId = ZoneId.systemDefault();

    public DashboardService(AppointmentRepository appointmentRepository,
            AppointmentRequestRepository appointmentRequestRepository,
            PatientVisitRepository patientVisitRepository) {
        this.appointmentRepository = appointmentRepository;
        this.appointmentRequestRepository = appointmentRequestRepository;
        this.patientVisitRepository = patientVisitRepository;
    }

    public DashboardSummaryResponse getTodaySummary() {
        LocalDate today = LocalDate.now(zoneId);
        LocalDateTime startOfDayLocal = today.atStartOfDay();
        LocalDateTime endOfDayLocal = today.atTime(LocalTime.MAX);
        Instant startOfDay = startOfDayLocal.atZone(zoneId).toInstant();
        Instant endOfDay = endOfDayLocal.atZone(zoneId).toInstant();

        long appointmentsToday = appointmentRepository.countByScheduledAtBetween(startOfDayLocal, endOfDayLocal);
        long appointmentsConfirmed = appointmentRepository.countByStatusAndScheduledAtBetween(
                AppointmentLifecycleStatus.CONFIRMED, startOfDayLocal, endOfDayLocal);
        long appointmentsCheckedIn = appointmentRepository.countByStatusAndScheduledAtBetween(
                AppointmentLifecycleStatus.CHECKED_IN, startOfDayLocal, endOfDayLocal);

        long pendingRequests = appointmentRequestRepository.countByStatus(AppointmentLifecycleStatus.PENDING);

        long visitsInProgress = patientVisitRepository.countByStatusInAndCreatedAtBetween(
                VisitStatus.inProgressStatuses(), startOfDay, endOfDay);
        long visitsCompleted = patientVisitRepository.countByStatusInAndCreatedAtBetween(
                VisitStatus.completedStatuses(), startOfDay, endOfDay);
        long activeDoctors = patientVisitRepository.countDistinctDoctorByCreatedAtBetween(startOfDay, endOfDay);

        return new DashboardSummaryResponse(
                appointmentsToday,
                appointmentsConfirmed,
                appointmentsCheckedIn,
                pendingRequests,
                visitsInProgress,
                visitsCompleted,
                activeDoctors);
    }
}
