package vn.project.ClinicSystem.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityNotFoundException;
import vn.project.ClinicSystem.model.Appointment;
import vn.project.ClinicSystem.model.ClinicRoom;
import vn.project.ClinicSystem.model.Doctor;
import vn.project.ClinicSystem.model.Patient;
import vn.project.ClinicSystem.model.User;
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

    public AppointmentService(AppointmentRepository appointmentRepository,
            PatientRepository patientRepository,
            DoctorRepository doctorRepository,
            ClinicRoomRepository clinicRoomRepository,
            UserRepository userRepository) {
        this.appointmentRepository = appointmentRepository;
        this.patientRepository = patientRepository;
        this.doctorRepository = doctorRepository;
        this.clinicRoomRepository = clinicRoomRepository;
        this.userRepository = userRepository;
    }

    public Appointment getById(Long id) {
        return appointmentRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Appointment not found with id: " + id));
    }

    public List<Appointment> findAll() {
        return appointmentRepository.findAll();
    }

    public List<Appointment> getForDoctor(Long doctorId) {
        return appointmentRepository.findByDoctorId(doctorId);
    }

    public List<Appointment> getForDoctorBetween(Long doctorId, LocalDateTime start, LocalDateTime end) {
        return appointmentRepository.findByDoctorIdAndScheduledAtBetween(doctorId, start, end);
    }

    public List<Appointment> getForPatient(Long patientId) {
        return appointmentRepository.findByPatientIdOrderByScheduledAtDesc(patientId);
    }

    public List<Appointment> getByStatus(AppointmentStatus status) {
        return appointmentRepository.findByStatus(status);
    }

    @Transactional
    public Appointment scheduleAppointment(Long patientId,
            Long doctorId,
            Long clinicRoomId,
            Long createdByUserId,
            LocalDateTime scheduledAt,
            String reason,
            String notes) {
        if (scheduledAt == null) {
            throw new IllegalArgumentException("Scheduled time must not be null");
        }
        Patient patient = loadPatient(patientId);
        Doctor doctor = loadDoctor(doctorId);
        ClinicRoom clinicRoom = clinicRoomId != null ? loadClinicRoom(clinicRoomId) : null;
        User createdBy = createdByUserId != null ? loadUser(createdByUserId) : null;

        ensureDoctorAvailability(doctorId, scheduledAt, null);
        if (clinicRoom != null) {
            ensureRoomAvailability(clinicRoom.getId(), scheduledAt, null);
        }

        Appointment appointment = new Appointment();
        appointment.setPatient(patient);
        appointment.setDoctor(doctor);
        appointment.setClinicRoom(clinicRoom);
        appointment.setCreatedBy(createdBy);
        appointment.setScheduledAt(scheduledAt);
        appointment.setReason(reason);
        appointment.setNotes(notes);
        appointment.setStatus(AppointmentStatus.REQUESTED);

        return appointmentRepository.save(appointment);
    }

    @Transactional
    public Appointment reschedule(Long appointmentId, LocalDateTime newScheduledAt, Long clinicRoomId, String notes) {
        if (newScheduledAt == null) {
            throw new IllegalArgumentException("New scheduled time must not be null");
        }
        Appointment appointment = getById(appointmentId);
        ensureDoctorAvailability(appointment.getDoctor().getId(), newScheduledAt, appointment.getId());
        if (clinicRoomId != null) {
            ClinicRoom clinicRoom = loadClinicRoom(clinicRoomId);
            ensureRoomAvailability(clinicRoom.getId(), newScheduledAt, appointment.getId());
            appointment.setClinicRoom(clinicRoom);
        } else {
            appointment.setClinicRoom(null);
        }
        appointment.setScheduledAt(newScheduledAt);
        if (notes != null) {
            appointment.setNotes(notes);
        }
        appointment.setStatus(AppointmentStatus.CONFIRMED);
        return appointmentRepository.save(appointment);
    }

    @Transactional
    public Appointment updateStatus(Long appointmentId, AppointmentStatus status, String note) {
        if (status == null) {
            throw new IllegalArgumentException("Status must not be null");
        }
        Appointment appointment = getById(appointmentId);
        appointment.setStatus(status);
        if (note != null) {
            appointment.setNotes(note);
        }
        return appointmentRepository.save(appointment);
    }

    @Transactional
    public void delete(Long appointmentId) {
        if (!appointmentRepository.existsById(appointmentId)) {
            throw new EntityNotFoundException("Appointment not found with id: " + appointmentId);
        }
        appointmentRepository.deleteById(appointmentId);
    }

    private Patient loadPatient(Long patientId) {
        return patientRepository.findById(patientId)
                .orElseThrow(() -> new EntityNotFoundException("Patient not found with id: " + patientId));
    }

    private Doctor loadDoctor(Long doctorId) {
        return doctorRepository.findById(doctorId)
                .orElseThrow(() -> new EntityNotFoundException("Doctor not found with id: " + doctorId));
    }

    private ClinicRoom loadClinicRoom(Long clinicRoomId) {
        return clinicRoomRepository.findById(clinicRoomId)
                .orElseThrow(() -> new EntityNotFoundException("Clinic room not found with id: " + clinicRoomId));
    }

    private User loadUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found with id: " + userId));
    }

    private void ensureDoctorAvailability(Long doctorId, LocalDateTime scheduledAt, Long ignoreAppointmentId) {
        List<Appointment> existing = appointmentRepository
                .findByDoctorIdAndScheduledAtBetween(doctorId, scheduledAt.minusMinutes(1), scheduledAt.plusMinutes(1));
        for (Appointment appointment : existing) {
            if (ignoreAppointmentId != null && appointment.getId().equals(ignoreAppointmentId)) {
                continue;
            }
            if (appointment.getStatus() != AppointmentStatus.CANCELLED) {
                throw new IllegalStateException("Doctor already has an appointment around " + scheduledAt);
            }
        }
    }

    private void ensureRoomAvailability(Long clinicRoomId, LocalDateTime scheduledAt, Long ignoreAppointmentId) {
        List<Appointment> existing = appointmentRepository.findByClinicRoomIdAndScheduledAtBetween(clinicRoomId,
                scheduledAt.minusMinutes(1), scheduledAt.plusMinutes(1));
        for (Appointment appointment : existing) {
            if (ignoreAppointmentId != null && appointment.getId().equals(ignoreAppointmentId)) {
                continue;
            }
            if (appointment.getStatus() != AppointmentStatus.CANCELLED) {
                throw new IllegalStateException("Clinic room is already booked around " + scheduledAt);
            }
        }
    }
}
