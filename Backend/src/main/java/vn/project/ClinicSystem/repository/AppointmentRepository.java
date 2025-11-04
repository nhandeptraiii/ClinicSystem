package vn.project.ClinicSystem.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.project.ClinicSystem.model.Appointment;
import vn.project.ClinicSystem.model.enums.AppointmentLifecycleStatus;

@Repository
public interface AppointmentRepository extends JpaRepository<Appointment, Long> {

        List<Appointment> findByDoctorIdOrderByScheduledAtAsc(Long doctorId);

        List<Appointment> findByPatientIdOrderByScheduledAtDesc(Long patientId);

        List<Appointment> findByStatus(AppointmentLifecycleStatus status);

        @Query("""
                        SELECT a FROM Appointment a
                        LEFT JOIN a.patient p
                        LEFT JOIN a.doctor d
                        LEFT JOIN d.account acc
                        WHERE (:keyword IS NULL OR
                            LOWER(p.fullName) LIKE LOWER(CONCAT('%', :keyword, '%'))
                            OR LOWER(p.code) LIKE LOWER(CONCAT('%', :keyword, '%'))
                            OR LOWER(acc.fullName) LIKE LOWER(CONCAT('%', :keyword, '%'))
                            OR LOWER(a.reason) LIKE LOWER(CONCAT('%', :keyword, '%'))
                            OR LOWER(a.notes) LIKE LOWER(CONCAT('%', :keyword, '%')))
                        AND (:status IS NULL OR a.status = :status)
                        ORDER BY a.scheduledAt DESC
                        """)
        Page<Appointment> search(@Param("keyword") String keyword, @Param("status") AppointmentLifecycleStatus status,
                        Pageable pageable);

        @Query(value = """
                        SELECT EXISTS (
                                SELECT 1
                                FROM appointments a
                                WHERE a.status <> 'CANCELLED'
                                  AND a.doctor_id = :doctorId
                                  AND (:ignoreAppointmentId IS NULL OR a.id <> :ignoreAppointmentId)
                                  AND a.scheduled_at < :endAt
                                  AND DATE_ADD(a.scheduled_at, INTERVAL a.duration MINUTE) > :startAt
                        )
                        """, nativeQuery = true)
        int existsDoctorOverlap(@Param("doctorId") Long doctorId,
                        @Param("startAt") LocalDateTime startAt,
                        @Param("endAt") LocalDateTime endAt,
                        @Param("ignoreAppointmentId") Long ignoreAppointmentId);

        @Query(value = """
                        SELECT EXISTS (
                                SELECT 1
                                FROM appointments a
                                WHERE a.status <> 'CANCELLED'
                                  AND a.clinic_room_id = :clinicRoomId
                                  AND (:ignoreAppointmentId IS NULL OR a.id <> :ignoreAppointmentId)
                                  AND a.scheduled_at < :endAt
                                  AND DATE_ADD(a.scheduled_at, INTERVAL a.duration MINUTE) > :startAt
                        )
                        """, nativeQuery = true)
        int existsClinicRoomOverlap(@Param("clinicRoomId") Long clinicRoomId,
                        @Param("startAt") LocalDateTime startAt,
                        @Param("endAt") LocalDateTime endAt,
                        @Param("ignoreAppointmentId") Long ignoreAppointmentId);

}
