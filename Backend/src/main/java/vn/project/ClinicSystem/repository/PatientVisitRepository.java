package vn.project.ClinicSystem.repository;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import vn.project.ClinicSystem.model.PatientVisit;
import vn.project.ClinicSystem.model.enums.VisitStatus;

@Repository
public interface PatientVisitRepository extends JpaRepository<PatientVisit, Long> {
    List<PatientVisit> findByPatientIdOrderByCreatedAtDesc(Long patientId);

    List<PatientVisit> findByStatus(VisitStatus status);

    boolean existsByPrimaryAppointmentId(Long primaryAppointmentId);

    @Query("""
            SELECT v FROM PatientVisit v
            LEFT JOIN v.patient p
            LEFT JOIN v.primaryAppointment pa
            LEFT JOIN pa.doctor d
            LEFT JOIN d.account acc
            WHERE (:keyword IS NULL OR
                LOWER(p.fullName) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR LOWER(p.code) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR LOWER(acc.fullName) LIKE LOWER(CONCAT('%', :keyword, '%')))
            AND (:status IS NULL OR v.status = :status)
            """)
    Page<PatientVisit> search(
            @Param("keyword") String keyword,
            @Param("status") VisitStatus status,
            Pageable pageable);
}
