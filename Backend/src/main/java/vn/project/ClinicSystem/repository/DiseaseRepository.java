package vn.project.ClinicSystem.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import vn.project.ClinicSystem.model.Disease;

public interface DiseaseRepository extends JpaRepository<Disease, Long> {

    Optional<Disease> findByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCase(String code);

    @Query("""
            SELECT d FROM Disease d
            WHERE (:keyword IS NULL OR
                LOWER(d.code) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR LOWER(d.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
            )
            ORDER BY d.name ASC, d.code ASC
            """)
    Page<Disease> search(
            @Param("keyword") String keyword,
            Pageable pageable);
}
