package vn.project.ClinicSystem.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityNotFoundException;
import vn.project.ClinicSystem.model.Appointment;
import vn.project.ClinicSystem.model.AppointmentRequest;
import vn.project.ClinicSystem.model.AppointmentRequest.RequestStatus;
import vn.project.ClinicSystem.model.User;
import vn.project.ClinicSystem.repository.AppointmentRequestRepository;
import vn.project.ClinicSystem.repository.UserRepository;

@Service
@Transactional(readOnly = true)
public class AppointmentRequestService {
    private final AppointmentRequestRepository appointmentRequestRepository;
    private final UserRepository userRepository;
    private final AppointmentService appointmentService;

    public AppointmentRequestService(AppointmentRequestRepository appointmentRequestRepository,
            UserRepository userRepository,
            AppointmentService appointmentService) {
        this.appointmentRequestRepository = appointmentRequestRepository;
        this.userRepository = userRepository;
        this.appointmentService = appointmentService;
    }

    public List<AppointmentRequest> findAll() {
        return appointmentRequestRepository.findAllByOrderByCreatedAtDesc();
    }

    public List<AppointmentRequest> getPendingRequests() {
        return appointmentRequestRepository.findByStatusOrderByCreatedAtAsc(RequestStatus.PENDING);
    }

    public AppointmentRequest getById(Long id) {
        return appointmentRequestRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Appointment request not found with id: " + id));
    }

    @Transactional
    public AppointmentRequest create(AppointmentRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Appointment request payload must not be null");
        }
        request.setStatus(RequestStatus.PENDING);
        return appointmentRequestRepository.save(request);
    }

    @Transactional
    public AppointmentRequest updateStatus(Long requestId, RequestStatus status, Long processedByUserId, String note) {
        if (status == null) {
            throw new IllegalArgumentException("Status must not be null");
        }
        AppointmentRequest request = getById(requestId);
        request.setStatus(status);
        if (processedByUserId != null) {
            request.setProcessedBy(loadUser(processedByUserId));
        }
        if (note != null) {
            request.setStaffNote(note);
        }
        return appointmentRequestRepository.save(request);
    }

    @Transactional
    public Appointment approveAndSchedule(Long requestId,
            Long patientId,
            Long doctorId,
            Long clinicRoomId,
            LocalDateTime scheduledAt,
            Long processedByUserId,
            String staffNote) {
        AppointmentRequest request = getById(requestId);
        Appointment appointment = appointmentService.scheduleAppointment(patientId, doctorId, clinicRoomId,
                processedByUserId, scheduledAt, request.getSymptomDescription(), staffNote);
        request.setStatus(RequestStatus.CONFIRMED);
        request.setPreferredAt(scheduledAt);
        request.setStaffNote(staffNote);
        if (processedByUserId != null) {
            request.setProcessedBy(loadUser(processedByUserId));
        }
        appointmentRequestRepository.save(request);
        return appointment;
    }

    @Transactional
    public AppointmentRequest reject(Long requestId, Long processedByUserId, String reason) {
        AppointmentRequest request = getById(requestId);
        request.setStatus(RequestStatus.REJECTED);
        request.setStaffNote(reason);
        if (processedByUserId != null) {
            request.setProcessedBy(loadUser(processedByUserId));
        }
        return appointmentRequestRepository.save(request);
    }

    @Transactional
    public void delete(Long requestId) {
        if (!appointmentRequestRepository.existsById(requestId)) {
            throw new EntityNotFoundException("Appointment request not found with id: " + requestId);
        }
        appointmentRequestRepository.deleteById(requestId);
    }

    private User loadUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found with id: " + userId));
    }
}
