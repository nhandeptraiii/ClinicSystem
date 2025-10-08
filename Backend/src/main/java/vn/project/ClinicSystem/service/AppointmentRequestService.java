package vn.project.ClinicSystem.service;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import vn.project.ClinicSystem.model.Appointment;
import vn.project.ClinicSystem.model.AppointmentRequest;
import vn.project.ClinicSystem.model.MedicalService;
import vn.project.ClinicSystem.model.Patient;
import vn.project.ClinicSystem.model.User;
import vn.project.ClinicSystem.model.dto.AppointmentRequestApproveRequest;
import vn.project.ClinicSystem.model.dto.AppointmentRequestCreateRequest;
import vn.project.ClinicSystem.model.dto.AppointmentRequestRejectRequest;
import vn.project.ClinicSystem.model.enums.AppointmentRequestStatus;
import vn.project.ClinicSystem.repository.AppointmentRequestRepository;
import vn.project.ClinicSystem.repository.MedicalServiceRepository;
import vn.project.ClinicSystem.repository.PatientRepository;
import vn.project.ClinicSystem.repository.UserRepository;

@Service
@Transactional(readOnly = true)
public class AppointmentRequestService {

    private final AppointmentRequestRepository appointmentRequestRepository;
    private final MedicalServiceRepository medicalServiceRepository;
    private final PatientRepository patientRepository;
    private final UserRepository userRepository;
    private final AppointmentService appointmentService;
    private final Validator validator;

    public AppointmentRequestService(AppointmentRequestRepository appointmentRequestRepository,
            MedicalServiceRepository medicalServiceRepository,
            PatientRepository patientRepository,
            UserRepository userRepository,
            AppointmentService appointmentService,
            Validator validator) {
        this.appointmentRequestRepository = appointmentRequestRepository;
        this.medicalServiceRepository = medicalServiceRepository;
        this.patientRepository = patientRepository;
        this.userRepository = userRepository;
        this.appointmentService = appointmentService;
        this.validator = validator;
    }

    public AppointmentRequest getById(Long id) {
        return appointmentRequestRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy yêu cầu đặt lịch với id: " + id));
    }

    public List<AppointmentRequest> findAll() {
        return appointmentRequestRepository.findAllByOrderByCreatedAtDesc();
    }

    public List<AppointmentRequest> findByStatus(AppointmentRequestStatus status) {
        return appointmentRequestRepository.findByStatusOrderByCreatedAtAsc(status);
    }

    @Transactional
    public AppointmentRequest create(AppointmentRequestCreateRequest request) {
        AppointmentRequest entity = new AppointmentRequest();
        entity.setFullName(request.getFullName());
        entity.setPhone(request.getPhone());
        entity.setEmail(request.getEmail());
        entity.setDateOfBirth(request.getDateOfBirth());
        entity.setPreferredAt(request.getPreferredAt());
        entity.setSymptomDescription(request.getSymptomDescription());
        entity.setStatus(AppointmentRequestStatus.PENDING);

        MedicalService medicalService = medicalServiceRepository.findById(request.getMedicalServiceId())
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy dịch vụ với id: "
                        + request.getMedicalServiceId()));
        entity.setMedicalService(medicalService);

        validateBean(entity);
        return appointmentRequestRepository.save(entity);
    }

    @Transactional
    public AppointmentRequest approve(Long id, AppointmentRequestApproveRequest approveRequest, Long staffUserId) {
        AppointmentRequest request = appointmentRequestRepository
                .findByIdAndStatus(id, AppointmentRequestStatus.PENDING)
                .orElseThrow(() -> new EntityNotFoundException("Yêu cầu không tồn tại hoặc đã xử lý"));

        Patient patient = patientRepository.findById(approveRequest.getPatientId())
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy bệnh nhân với id: "
                        + approveRequest.getPatientId()));
        request.setPatient(patient);

        Appointment appointment = appointmentService.createFromRequest(request,
                approveRequest.getPatientId(),
                approveRequest.getDoctorId(),
                approveRequest.getScheduledAt(),
                approveRequest.getClinicRoomId(),
                staffUserId);

        request.setAppointment(appointment);
        request.setStatus(AppointmentRequestStatus.CONFIRMED);
        request.setStaffNote(approveRequest.getStaffNote());
        request.setProcessedAt(Instant.now());

        if (staffUserId != null) {
            User staff = loadUser(staffUserId);
            request.setProcessedBy(staff);
        }

        return appointmentRequestRepository.save(request);
    }

    @Transactional
    public AppointmentRequest reject(Long id, AppointmentRequestRejectRequest rejectRequest, Long staffUserId) {
        AppointmentRequest request = appointmentRequestRepository
                .findByIdAndStatus(id, AppointmentRequestStatus.PENDING)
                .orElseThrow(() -> new EntityNotFoundException("Yêu cầu không tồn tại hoặc đã xử lý"));

        request.setStatus(AppointmentRequestStatus.REJECTED);
        request.setStaffNote(rejectRequest.getStaffNote());
        request.setProcessedAt(Instant.now());

        if (staffUserId != null) {
            request.setProcessedBy(loadUser(staffUserId));
        }

        return appointmentRequestRepository.save(request);
    }

    private void validateBean(AppointmentRequest appointmentRequest) {
        var violations = validator.validate(appointmentRequest);
        if (!violations.isEmpty()) {
            throw new ConstraintViolationException(violations);
        }
    }

    private User loadUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy người dùng với id: " + userId));
    }
}
