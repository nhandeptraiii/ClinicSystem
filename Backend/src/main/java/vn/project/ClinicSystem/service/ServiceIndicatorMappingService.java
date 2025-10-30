package vn.project.ClinicSystem.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityExistsException;
import jakarta.persistence.EntityNotFoundException;
import vn.project.ClinicSystem.model.IndicatorTemplate;
import vn.project.ClinicSystem.model.MedicalService;
import vn.project.ClinicSystem.model.ServiceIndicatorMapping;
import vn.project.ClinicSystem.model.dto.ServiceIndicatorMappingRequest;
import vn.project.ClinicSystem.model.dto.ServiceIndicatorMappingUpdateRequest;
import vn.project.ClinicSystem.repository.IndicatorTemplateRepository;
import vn.project.ClinicSystem.repository.MedicalServiceRepository;
import vn.project.ClinicSystem.repository.ServiceIndicatorMappingRepository;

@Service
@Transactional(readOnly = true)
public class ServiceIndicatorMappingService {

    private final ServiceIndicatorMappingRepository mappingRepository;
    private final MedicalServiceRepository medicalServiceRepository;
    private final IndicatorTemplateRepository templateRepository;

    public ServiceIndicatorMappingService(
            ServiceIndicatorMappingRepository mappingRepository,
            MedicalServiceRepository medicalServiceRepository,
            IndicatorTemplateRepository templateRepository) {
        this.mappingRepository = mappingRepository;
        this.medicalServiceRepository = medicalServiceRepository;
        this.templateRepository = templateRepository;
    }

    public ServiceIndicatorMapping getById(Long id) {
        return mappingRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy mapping với id: " + id));
    }

    public List<ServiceIndicatorMapping> listByMedicalService(Long medicalServiceId) {
        ensureServiceExists(medicalServiceId);
        return mappingRepository.findByMedicalServiceIdOrderByDisplayOrderAsc(medicalServiceId);
    }

    public List<ServiceIndicatorMapping> listByIndicatorTemplate(Long templateId) {
        ensureTemplateExists(templateId);
        return mappingRepository.findByIndicatorTemplateIdOrderByDisplayOrderAsc(templateId);
    }

    @Transactional
    public ServiceIndicatorMapping create(Long medicalServiceId, ServiceIndicatorMappingRequest request) {
        MedicalService service = loadService(medicalServiceId);
        IndicatorTemplate template = loadTemplate(request.getIndicatorTemplateId());

        if (mappingRepository.existsByMedicalServiceIdAndIndicatorTemplateId(
                medicalServiceId, request.getIndicatorTemplateId())) {
            throw new EntityExistsException("Chỉ số này đã được thêm vào dịch vụ rồi");
        }

        ServiceIndicatorMapping mapping = new ServiceIndicatorMapping();
        mapping.setMedicalService(service);
        mapping.setIndicatorTemplate(template);
        mapping.setRequired(request.getRequired() != null ? request.getRequired() : true);
        mapping.setDisplayOrder(request.getDisplayOrder() != null ? request.getDisplayOrder() : 0);

        return mappingRepository.save(mapping);
    }

    @Transactional
    public ServiceIndicatorMapping update(Long id, ServiceIndicatorMappingUpdateRequest request) {
        ServiceIndicatorMapping mapping = getById(id);

        if (request.getRequired() != null) {
            mapping.setRequired(request.getRequired());
        }
        if (request.getDisplayOrder() != null) {
            mapping.setDisplayOrder(request.getDisplayOrder());
        }

        return mappingRepository.save(mapping);
    }

    @Transactional
    public void delete(Long id) {
        if (!mappingRepository.existsById(id)) {
            throw new EntityNotFoundException("Không tìm thấy mapping với id: " + id);
        }
        mappingRepository.deleteById(id);
    }

    private void ensureServiceExists(Long serviceId) {
        if (!medicalServiceRepository.existsById(serviceId)) {
            throw new EntityNotFoundException("Không tìm thấy dịch vụ với id: " + serviceId);
        }
    }

    private void ensureTemplateExists(Long templateId) {
        if (!templateRepository.existsById(templateId)) {
            throw new EntityNotFoundException("Không tìm thấy template với id: " + templateId);
        }
    }

    private MedicalService loadService(Long serviceId) {
        return medicalServiceRepository.findById(serviceId)
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy dịch vụ với id: " + serviceId));
    }

    private IndicatorTemplate loadTemplate(Long templateId) {
        return templateRepository.findById(templateId)
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy template với id: " + templateId));
    }
}
