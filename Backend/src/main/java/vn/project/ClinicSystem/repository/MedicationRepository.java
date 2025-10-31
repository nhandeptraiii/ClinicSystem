package vn.project.ClinicSystem.repository;

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

    @Query("""
            SELECT m FROM Medication m
            WHERE (:keyword IS NULL OR LOWER(m.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR LOWER(m.activeIngredient) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR LOWER(m.manufacturer) LIKE LOWER(CONCAT('%', :keyword, '%')))
            """)
    Page<Medication> search(@Param("keyword") String keyword, Pageable pageable);
}
