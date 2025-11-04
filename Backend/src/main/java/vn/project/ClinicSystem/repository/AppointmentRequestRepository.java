package vn.project.ClinicSystem.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import vn.project.ClinicSystem.model.AppointmentRequest;
import vn.project.ClinicSystem.model.enums.AppointmentLifecycleStatus;

@Repository
public interface AppointmentRequestRepository extends JpaRepository<AppointmentRequest, Long> {

        @Query("""
                        SELECT ar FROM AppointmentRequest ar
                        LEFT JOIN FETCH ar.patient
                        LEFT JOIN FETCH ar.processedBy
                        WHERE ar.status = :status
                        ORDER BY ar.createdAt ASC
                        """)
        List<AppointmentRequest> findByStatusOrderByCreatedAtAsc(@Param("status") AppointmentLifecycleStatus status);

        @Query("""
                        SELECT ar FROM AppointmentRequest ar
                        LEFT JOIN FETCH ar.patient
                        LEFT JOIN FETCH ar.processedBy
                        ORDER BY ar.createdAt DESC
                        """)
        List<AppointmentRequest> findAllByOrderByCreatedAtDesc();

        Optional<AppointmentRequest> findByIdAndStatus(Long id, AppointmentLifecycleStatus status);

        @Query("""
                        SELECT DISTINCT ar FROM AppointmentRequest ar
                        LEFT JOIN FETCH ar.patient
                        LEFT JOIN FETCH ar.processedBy
                        WHERE (:keyword IS NULL OR LOWER(ar.fullName) LIKE LOWER(CONCAT('%', :keyword, '%'))
                            OR LOWER(ar.phone) LIKE LOWER(CONCAT('%', :keyword, '%'))
                            OR LOWER(ar.email) LIKE LOWER(CONCAT('%', :keyword, '%'))
                            OR (ar.patient IS NOT NULL AND LOWER(ar.patient.code) LIKE LOWER(CONCAT('%', :keyword, '%')))
                            OR (ar.patient IS NOT NULL AND LOWER(ar.patient.fullName) LIKE LOWER(CONCAT('%', :keyword, '%'))))
                        AND (:status IS NULL OR ar.status = :status)
                        ORDER BY ar.createdAt DESC
                        """)
        Page<AppointmentRequest> search(
                        @Param("keyword") String keyword,
                        @Param("status") AppointmentLifecycleStatus status,
                        Pageable pageable);
}
