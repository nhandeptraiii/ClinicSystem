package vn.project.ClinicSystem.repository;

import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import vn.project.ClinicSystem.model.PatientVisit;
import vn.project.ClinicSystem.model.enums.VisitStatus;

public interface PatientVisitRepository extends JpaRepository<PatientVisit, Long> {

    long countByStatusInAndCreatedAtBetween(VisitStatus[] statuses, Instant start, Instant end);

    @Query("SELECT COUNT(DISTINCT pv.primaryAppointment.doctor.id) FROM PatientVisit pv "
            + "WHERE pv.createdAt BETWEEN :start AND :end AND pv.primaryAppointment.doctor.id IS NOT NULL")
    long countDistinctDoctorByCreatedAtBetween(@Param("start") Instant start, @Param("end") Instant end);

    boolean existsByPrimaryAppointmentId(Long appointmentId);

    Page<PatientVisit> findByPatientIdOrderByCreatedAtDesc(Long patientId, Pageable pageable);

    List<PatientVisit> findByPatientIdOrderByCreatedAtDesc(Long patientId);

    boolean existsByDiseases_Id(Long diseaseId);

    @Query("""
            SELECT pv FROM PatientVisit pv
            LEFT JOIN pv.patient p
            LEFT JOIN pv.primaryAppointment pa
            LEFT JOIN pa.doctor d
            LEFT JOIN d.account acc
            LEFT JOIN pv.diseases dis
            WHERE (:keyword IS NULL OR
                LOWER(p.fullName) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR LOWER(p.code) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR LOWER(acc.fullName) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR LOWER(pa.reason) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR LOWER(pa.notes) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR LOWER(dis.code) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR LOWER(dis.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
            )
            AND (:status IS NULL OR pv.status = :status)
            ORDER BY pv.createdAt DESC
            """)
    Page<PatientVisit> search(
            @Param("keyword") String keyword,
            @Param("status") VisitStatus status,
            Pageable pageable);

    @Query("""
            SELECT pv FROM PatientVisit pv
            LEFT JOIN pv.patient p
            WHERE pv.billing IS NULL
              AND pv.status = vn.project.ClinicSystem.model.enums.VisitStatus.COMPLETED
              AND (:keyword IS NULL OR
                LOWER(p.fullName) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR LOWER(p.code) LIKE LOWER(CONCAT('%', :keyword, '%'))
            )
            ORDER BY pv.createdAt DESC
            """)
    Page<PatientVisit> searchCompletedWithoutBilling(
            @Param("keyword") String keyword,
            Pageable pageable);
}
