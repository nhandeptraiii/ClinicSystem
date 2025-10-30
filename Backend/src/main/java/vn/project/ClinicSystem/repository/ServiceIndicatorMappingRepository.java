package vn.project.ClinicSystem.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import vn.project.ClinicSystem.model.ServiceIndicatorMapping;

@Repository
public interface ServiceIndicatorMappingRepository extends JpaRepository<ServiceIndicatorMapping, Long> {

    List<ServiceIndicatorMapping> findByMedicalServiceIdOrderByDisplayOrderAsc(Long medicalServiceId);

    boolean existsByMedicalServiceIdAndIndicatorTemplateId(Long medicalServiceId, Long indicatorTemplateId);

    List<ServiceIndicatorMapping> findByMedicalServiceIdAndRequiredTrue(Long medicalServiceId);

    void deleteByMedicalServiceIdAndIndicatorTemplateId(Long medicalServiceId, Long indicatorTemplateId);
}
