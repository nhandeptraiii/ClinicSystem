package vn.project.ClinicSystem.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import vn.project.ClinicSystem.model.IndicatorTemplate;

@Repository
public interface IndicatorTemplateRepository extends JpaRepository<IndicatorTemplate, Long> {

    Optional<IndicatorTemplate> findByCode(String code);

    boolean existsByCodeIgnoreCase(String code);

    @Query("""
                SELECT it FROM IndicatorTemplate it
                WHERE (:keyword IS NULL OR LOWER(it.name) LIKE LOWER(CONCAT('%', :keyword, '%')) OR LOWER(it.code) LIKE LOWER(CONCAT('%', :keyword, '%')))
                ORDER BY it.name ASC
            """)
    Page<IndicatorTemplate> search(@Param("keyword") String keyword, Pageable pageable);
}
