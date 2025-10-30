package vn.project.ClinicSystem.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import vn.project.ClinicSystem.model.IndicatorTemplate;

@Repository
public interface IndicatorTemplateRepository extends JpaRepository<IndicatorTemplate, Long> {

    Optional<IndicatorTemplate> findByCode(String code);

    List<IndicatorTemplate> findByIsActiveTrueOrderByNameAsc();

    List<IndicatorTemplate> findByCategoryAndIsActiveTrueOrderByNameAsc(String category);

    boolean existsByCodeIgnoreCase(String code);

    List<IndicatorTemplate> findAllByOrderByNameAsc();
}
