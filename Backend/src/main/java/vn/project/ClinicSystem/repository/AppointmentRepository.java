package vn.project.ClinicSystem.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import vn.project.ClinicSystem.model.Appointment;
import vn.project.ClinicSystem.model.enums.AppointmentStatus;

@Repository
public interface AppointmentRepository extends JpaRepository<Appointment, Long> {

    List<Appointment> findByDoctorIdOrderByScheduledAtAsc(Long doctorId);

    List<Appointment> findByPatientIdOrderByScheduledAtDesc(Long patientId);

    List<Appointment> findByStatus(AppointmentStatus status);

    List<Appointment> findByDoctorIdAndScheduledAtBetween(Long doctorId,
            LocalDateTime start, LocalDateTime end);

    List<Appointment> findByClinicRoomIdAndScheduledAtBetween(Long clinicRoomId,
            LocalDateTime start, LocalDateTime end);
}
