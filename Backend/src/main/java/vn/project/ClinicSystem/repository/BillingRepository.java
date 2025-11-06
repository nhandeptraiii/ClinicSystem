package vn.project.ClinicSystem.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import vn.project.ClinicSystem.model.Billing;
import vn.project.ClinicSystem.model.enums.BillingStatus;

@Repository
public interface BillingRepository extends JpaRepository<Billing, Long> {

    Optional<Billing> findByVisitId(Long visitId);

    List<Billing> findByPatientIdOrderByIssuedAtDesc(Long patientId);

    @Query("""
            SELECT b FROM Billing b
            LEFT JOIN b.patient p
            WHERE (:keyword IS NULL OR
                LOWER(p.fullName) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR LOWER(p.code) LIKE LOWER(CONCAT('%', :keyword, '%')))
            AND (:status IS NULL OR b.status = :status)
            AND (:patientId IS NULL OR b.patient.id = :patientId)
            """)
    Page<Billing> search(
            @Param("keyword") String keyword,
            @Param("status") BillingStatus status,
            @Param("patientId") Long patientId,
            Pageable pageable);
}
