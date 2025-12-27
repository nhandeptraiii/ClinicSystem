package vn.project.ClinicSystem.repository;

import java.time.LocalDate;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import vn.project.ClinicSystem.model.Medication;

@Repository
public interface MedicationRepository extends JpaRepository<Medication, Long> {

    Optional<Medication> findByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCase(String name);

    Optional<Medication> findByBatchNoIgnoreCase(String batchNo);

    boolean existsByBatchNoIgnoreCase(String batchNo);

    @Query("""
            SELECT m FROM Medication m
            WHERE (:keyword IS NULL OR LOWER(m.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR LOWER(m.activeIngredient) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR LOWER(m.manufacturer) LIKE LOWER(CONCAT('%', :keyword, '%')))
            AND (:minExpiry IS NULL OR m.expiryDate >= :minExpiry)
            AND (:maxExpiry IS NULL OR m.expiryDate <= :maxExpiry)
            """)
    Page<Medication> search(@Param("keyword") String keyword,
            @Param("minExpiry") LocalDate minExpiry,
            @Param("maxExpiry") LocalDate maxExpiry,
            Pageable pageable);

    @Query("SELECT COUNT(p) FROM PrescriptionItem p WHERE p.medication.id = :medicationId")
    long countPrescriptionItemsUsingMedication(@Param("medicationId") Long medicationId);

    @org.springframework.data.jpa.repository.Modifying
    @Query("DELETE FROM Medication m WHERE m.id = :id")
    void deleteMedicationById(@Param("id") Long id);
}
