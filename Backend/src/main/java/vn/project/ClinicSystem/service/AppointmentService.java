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
import vn.project.ClinicSystem.model.Patient;
import vn.project.ClinicSystem.model.User;
import vn.project.ClinicSystem.model.dto.AppointmentCreateRequest;
import vn.project.ClinicSystem.model.dto.AppointmentStatusUpdateRequest;
import vn.project.ClinicSystem.model.dto.AppointmentUpdateRequest;
import vn.project.ClinicSystem.model.enums.AppointmentStatus;
import vn.project.ClinicSystem.repository.AppointmentRepository;
import vn.project.ClinicSystem.repository.ClinicRoomRepository;
import vn.project.ClinicSystem.repository.DoctorRepository;
import vn.project.ClinicSystem.repository.PatientRepository;
import vn.project.ClinicSystem.repository.UserRepository;

@Service
@Transactional(readOnly = true)
public class AppointmentService {

    private final AppointmentRepository appointmentRepository;
    private final PatientRepository patientRepository;
    private final DoctorRepository doctorRepository;
    private final ClinicRoomRepository clinicRoomRepository;
    private final UserRepository userRepository;
    private final Validator validator;

    public AppointmentService(AppointmentRepository appointmentRepository,
            PatientRepository patientRepository,
            DoctorRepository doctorRepository,
            ClinicRoomRepository clinicRoomRepository,
            UserRepository userRepository,
            Validator validator) {
        this.appointmentRepository = appointmentRepository;
        this.patientRepository = patientRepository;
        this.doctorRepository = doctorRepository;
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
        ClinicRoom clinicRoom = loadClinicRoom(request.getClinicRoomId());

        appointment.setPatient(patient);
        appointment.setDoctor(doctor);
        appointment.setClinicRoom(clinicRoom);
        appointment.setScheduledAt(request.getScheduledAt());
        appointment.setDuration(resolveDuration(request.getDuration()));
        appointment.setReason(request.getReason());
        appointment.setNotes(request.getNotes());
        appointment.setPatientDateOfBirth(patient.getDateOfBirth());
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
            Long staffUserId,
            Integer duration) {

        Patient patient = loadPatient(patientId);
        Doctor doctor = loadDoctor(doctorId);
        ClinicRoom clinicRoom = loadClinicRoom(clinicRoomId);

        Appointment appointment = new Appointment();
        appointment.setPatient(patient);
        appointment.setDoctor(doctor);
        appointment.setClinicRoom(clinicRoom);
        appointment.setScheduledAt(scheduledAt);
        appointment.setDuration(resolveDuration(duration));
        appointment.setReason(requestEntity.getSymptomDescription());
        if (patient.getDateOfBirth() != null) {
            appointment.setPatientDateOfBirth(patient.getDateOfBirth());
        } else {
            appointment.setPatientDateOfBirth(requestEntity.getDateOfBirth());
        }
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
            appointment.setPatientDateOfBirth(appointment.getPatient().getDateOfBirth());
        }

        if (request.getDoctorId() != null && !request.getDoctorId().equals(appointment.getDoctor().getId())) {
            appointment.setDoctor(loadDoctor(request.getDoctorId()));
        }

        if (request.getClinicRoomId() != null) {
            if (appointment.getClinicRoom() == null
                    || !request.getClinicRoomId().equals(appointment.getClinicRoom().getId())) {
                appointment.setClinicRoom(loadClinicRoom(request.getClinicRoomId()));
            }
        }

        if (request.getScheduledAt() != null) {
            appointment.setScheduledAt(request.getScheduledAt());
        }

        if (request.getDuration() != null) {
            appointment.setDuration(resolveDuration(request.getDuration()));
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
        LocalDateTime start = appointment.getScheduledAt();
        LocalDateTime end = start.plusMinutes(resolveDuration(appointment.getDuration()));
        LocalDateTime dayStart = start.toLocalDate().atStartOfDay();
        LocalDateTime dayEnd = dayStart.plusDays(1);

        boolean doctorBusy = appointmentRepository
                .findByDoctorIdAndScheduledAtBetween(appointment.getDoctor().getId(), dayStart, dayEnd)
                .stream()
                .filter(a -> ignoreId == null || !a.getId().equals(ignoreId))
                .anyMatch(existing -> isOverlapping(start, end,
                        existing.getScheduledAt(),
                        existing.getScheduledAt().plusMinutes(resolveDuration(existing.getDuration()))));

        if (doctorBusy) {
            throw new IllegalStateException("Bác sĩ đã có lịch trong khoảng thời gian này");
        }

        boolean roomBusy = appointmentRepository
                .findByClinicRoomIdAndScheduledAtBetween(appointment.getClinicRoom().getId(), dayStart, dayEnd)
                .stream()
                .filter(a -> ignoreId == null || !a.getId().equals(ignoreId))
                .anyMatch(existing -> isOverlapping(start, end,
                        existing.getScheduledAt(),
                        existing.getScheduledAt().plusMinutes(resolveDuration(existing.getDuration()))));

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

    private boolean isOverlapping(LocalDateTime start1, LocalDateTime end1,
            LocalDateTime start2, LocalDateTime end2) {
        return start1.isBefore(end2) && start2.isBefore(end1);
    }

    private int resolveDuration(Integer duration) {
        if (duration == null || duration <= 0) {
            return 30;
        }
        return duration;
    }

    private ClinicRoom loadClinicRoom(Long clinicRoomId) {
        return clinicRoomRepository.findById(clinicRoomId)
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy phòng với id: " + clinicRoomId));
    }

    private Patient loadPatient(Long patientId) {
        return patientRepository.findById(patientId)
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy bệnh nhân với id: " + patientId));
    }

    private Doctor loadDoctor(Long doctorId) {
        return doctorRepository.findById(doctorId)
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy bác sĩ với id: " + doctorId));
    }

    private User loadUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy người dùng với id: " + userId));
    }
}
