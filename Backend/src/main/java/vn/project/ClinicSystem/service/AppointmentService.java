package vn.project.ClinicSystem.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import vn.project.ClinicSystem.model.Appointment;
import vn.project.ClinicSystem.model.AppointmentRequest;
import vn.project.ClinicSystem.model.ClinicRoom;
import vn.project.ClinicSystem.model.Doctor;
import vn.project.ClinicSystem.model.MedicalService;
import vn.project.ClinicSystem.model.Patient;
import vn.project.ClinicSystem.model.User;
import vn.project.ClinicSystem.model.dto.AppointmentCreateRequest;
import vn.project.ClinicSystem.model.dto.AppointmentStatusUpdateRequest;
import vn.project.ClinicSystem.model.dto.AppointmentUpdateRequest;
import vn.project.ClinicSystem.model.enums.AppointmentStatus;
import vn.project.ClinicSystem.repository.AppointmentRepository;
import vn.project.ClinicSystem.repository.ClinicRoomRepository;
import vn.project.ClinicSystem.repository.DoctorRepository;
import vn.project.ClinicSystem.repository.MedicalServiceRepository;
import vn.project.ClinicSystem.repository.PatientRepository;
import vn.project.ClinicSystem.repository.UserRepository;

@Service
@Transactional(readOnly = true)
public class AppointmentService {

    private final AppointmentRepository appointmentRepository;
    private final PatientRepository patientRepository;
    private final DoctorRepository doctorRepository;
    private final MedicalServiceRepository medicalServiceRepository;
    private final ClinicRoomRepository clinicRoomRepository;
    private final UserRepository userRepository;
    private final Validator validator;

    public AppointmentService(AppointmentRepository appointmentRepository,
            PatientRepository patientRepository,
            DoctorRepository doctorRepository,
            MedicalServiceRepository medicalServiceRepository,
            ClinicRoomRepository clinicRoomRepository,
            UserRepository userRepository,
            Validator validator) {
        this.appointmentRepository = appointmentRepository;
        this.patientRepository = patientRepository;
        this.doctorRepository = doctorRepository;
        this.medicalServiceRepository = medicalServiceRepository;
        this.clinicRoomRepository = clinicRoomRepository;
        this.userRepository = userRepository;
        this.validator = validator;
    }

    public Appointment getById(Long id) {
        return appointmentRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy lịch khám với id: " + id));
    }

    public List<Appointment> findAll() {
        return appointmentRepository.findAll();
    }

    public List<Appointment> findByDoctor(Long doctorId) {
        return appointmentRepository.findByDoctorIdOrderByScheduledAtAsc(doctorId);
    }

    public List<Appointment> findByPatient(Long patientId) {
        return appointmentRepository.findByPatientIdOrderByScheduledAtDesc(patientId);
    }

    public List<Appointment> findByStatus(AppointmentStatus status) {
        return appointmentRepository.findByStatus(status);
    }

    @Transactional
    public Appointment createAppointment(AppointmentCreateRequest request, Long createdByUserId) {
        Appointment appointment = new Appointment();
        Patient patient = loadPatient(request.getPatientId());
        Doctor doctor = loadDoctor(request.getDoctorId());
        MedicalService medicalService = loadMedicalService(request.getMedicalServiceId());

        ClinicRoom clinicRoom = resolveClinicRoom(request.getClinicRoomId(), medicalService);

        appointment.setPatient(patient);
        appointment.setDoctor(doctor);
        appointment.setMedicalService(medicalService);
        appointment.setClinicRoom(clinicRoom);
        appointment.setScheduledAt(request.getScheduledAt());
        appointment.setReason(request.getReason());
        appointment.setNotes(request.getNotes());
        appointment.setStatus(AppointmentStatus.CONFIRMED);

        if (createdByUserId != null) {
            appointment.setCreatedBy(loadUser(createdByUserId));
        }

        validateBean(appointment);
        ensureAvailability(appointment, null);
        return appointmentRepository.save(appointment);
    }

    @Transactional
    public Appointment createFromRequest(AppointmentRequest requestEntity,
            Long patientId,
            Long doctorId,
            LocalDateTime scheduledAt,
            Long clinicRoomId,
            Long staffUserId) {

        Patient patient = loadPatient(patientId);
        Doctor doctor = loadDoctor(doctorId);
        MedicalService medicalService = requestEntity.getMedicalService();
        ClinicRoom clinicRoom = resolveClinicRoom(clinicRoomId, medicalService);

        Appointment appointment = new Appointment();
        appointment.setPatient(patient);
        appointment.setDoctor(doctor);
        appointment.setMedicalService(medicalService);
        appointment.setClinicRoom(clinicRoom);
        appointment.setScheduledAt(scheduledAt);
        appointment.setReason(requestEntity.getSymptomDescription());
        appointment.setStatus(AppointmentStatus.CONFIRMED);
        appointment.setRequest(requestEntity);

        if (staffUserId != null) {
            appointment.setCreatedBy(loadUser(staffUserId));
        }

        validateBean(appointment);
        ensureAvailability(appointment, null);
        return appointmentRepository.save(appointment);
    }

    @Transactional
    public Appointment updateAppointment(Long id, AppointmentUpdateRequest request) {
        Appointment appointment = getById(id);

        if (request.getPatientId() != null && !request.getPatientId().equals(appointment.getPatient().getId())) {
            appointment.setPatient(loadPatient(request.getPatientId()));
        }

        if (request.getDoctorId() != null && !request.getDoctorId().equals(appointment.getDoctor().getId())) {
            appointment.setDoctor(loadDoctor(request.getDoctorId()));
        }

        if (request.getMedicalServiceId() != null
                && !request.getMedicalServiceId().equals(appointment.getMedicalService().getId())) {
            MedicalService medicalService = loadMedicalService(request.getMedicalServiceId());
            appointment.setMedicalService(medicalService);
            if (request.getClinicRoomId() == null) {
                appointment.setClinicRoom(resolveClinicRoom(null, medicalService));
            }
        }

        if (request.getClinicRoomId() != null) {
            appointment.setClinicRoom(resolveClinicRoom(request.getClinicRoomId(), appointment.getMedicalService()));
        }

        if (request.getScheduledAt() != null) {
            appointment.setScheduledAt(request.getScheduledAt());
        }

        if (request.getReason() != null) {
            appointment.setReason(request.getReason());
        }

        if (request.getNotes() != null) {
            appointment.setNotes(request.getNotes());
        }

        validateBean(appointment);
        ensureAvailability(appointment, appointment.getId());
        return appointmentRepository.save(appointment);
    }

    @Transactional
    public Appointment updateStatus(Long id, AppointmentStatusUpdateRequest request) {
        Appointment appointment = getById(id);
        appointment.setStatus(request.getStatus());
        if (request.getNote() != null) {
            appointment.setNotes(request.getNote());
        }
        return appointmentRepository.save(appointment);
    }

    @Transactional
    public void delete(Long id) {
        if (!appointmentRepository.existsById(id)) {
            throw new EntityNotFoundException("Không tìm thấy lịch khám với id: " + id);
        }
        appointmentRepository.deleteById(id);
    }

    private void ensureAvailability(Appointment appointment, Long ignoreId) {
        LocalDateTime start = appointment.getScheduledAt().minusMinutes(1);
        LocalDateTime end = appointment.getScheduledAt().plusMinutes(1);

        boolean doctorBusy = appointmentRepository
                .findByDoctorIdAndScheduledAtBetween(appointment.getDoctor().getId(), start, end)
                .stream()
                .anyMatch(a -> ignoreId == null || !a.getId().equals(ignoreId));

        if (doctorBusy) {
            throw new IllegalStateException("Bác sĩ đã có lịch trong khoảng thời gian này");
        }

        boolean roomBusy = appointmentRepository
                .findByClinicRoomIdAndScheduledAtBetween(appointment.getClinicRoom().getId(), start, end)
                .stream()
                .anyMatch(a -> ignoreId == null || !a.getId().equals(ignoreId));

        if (roomBusy) {
            throw new IllegalStateException("Phòng khám đã được đặt trong khoảng thời gian này");
        }
    }

    private void validateBean(Appointment appointment) {
        var violations = validator.validate(appointment);
        if (!violations.isEmpty()) {
            throw new ConstraintViolationException(violations);
        }
    }

    private ClinicRoom resolveClinicRoom(Long clinicRoomId, MedicalService medicalService) {
        if (clinicRoomId != null) {
            return clinicRoomRepository.findById(clinicRoomId)
                    .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy phòng với id: " + clinicRoomId));
        }
        return medicalService.getClinicRoom();
    }

    private Patient loadPatient(Long patientId) {
        return patientRepository.findById(patientId)
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy bệnh nhân với id: " + patientId));
    }

    private Doctor loadDoctor(Long doctorId) {
        return doctorRepository.findById(doctorId)
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy bác sĩ với id: " + doctorId));
    }

    private MedicalService loadMedicalService(Long serviceId) {
        return medicalServiceRepository.findById(serviceId)
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy dịch vụ với id: " + serviceId));
    }

    private User loadUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy người dùng với id: " + userId));
    }
}
