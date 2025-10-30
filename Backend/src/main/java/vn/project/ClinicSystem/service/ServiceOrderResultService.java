package vn.project.ClinicSystem.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityNotFoundException;
import vn.project.ClinicSystem.model.Doctor;
import vn.project.ClinicSystem.model.IndicatorTemplate;
import vn.project.ClinicSystem.model.MedicalService;
import vn.project.ClinicSystem.model.ServiceIndicatorMapping;
import vn.project.ClinicSystem.model.ServiceOrder;
import vn.project.ClinicSystem.model.ServiceOrderResult;
import vn.project.ClinicSystem.model.dto.ServiceOrderResultEntryRequest;
import vn.project.ClinicSystem.model.dto.ServiceOrderResultRequest;
import vn.project.ClinicSystem.model.enums.IndicatorResultLevel;
import vn.project.ClinicSystem.model.enums.ServiceOrderStatus;
import vn.project.ClinicSystem.repository.DoctorRepository;
import vn.project.ClinicSystem.repository.IndicatorTemplateRepository;
import vn.project.ClinicSystem.repository.ServiceIndicatorMappingRepository;
import vn.project.ClinicSystem.repository.ServiceOrderRepository;
import vn.project.ClinicSystem.repository.ServiceOrderResultRepository;

@Service
@Transactional(readOnly = true)
public class ServiceOrderResultService {

    private final ServiceOrderRepository serviceOrderRepository;
    private final ServiceIndicatorMappingRepository mappingRepository;
    private final IndicatorTemplateRepository templateRepository;
    private final ServiceOrderResultRepository resultRepository;
    private final DoctorRepository doctorRepository;

    public ServiceOrderResultService(ServiceOrderRepository serviceOrderRepository,
            ServiceIndicatorMappingRepository mappingRepository,
            IndicatorTemplateRepository templateRepository,
            ServiceOrderResultRepository resultRepository,
            DoctorRepository doctorRepository) {
        this.serviceOrderRepository = serviceOrderRepository;
        this.mappingRepository = mappingRepository;
        this.templateRepository = templateRepository;
        this.resultRepository = resultRepository;
        this.doctorRepository = doctorRepository;
    }

    public List<ServiceOrderResult> findResults(Long serviceOrderId) {
        ensureOrderExists(serviceOrderId);
        return resultRepository.findByServiceOrderIdOrderByIdAsc(serviceOrderId);
    }

    @Transactional
    public ServiceOrder recordResults(Long serviceOrderId, ServiceOrderResultRequest request) {
        ServiceOrder order = loadOrder(serviceOrderId);
        MedicalService medicalService = order.getMedicalService();

        if (order.getStatus() == ServiceOrderStatus.CANCELLED) {
            throw new IllegalStateException("Không thể nhập kết quả cho phiếu dịch vụ đã hủy.");
        }

        Map<Long, IndicatorTemplate> templatesById = loadTemplatesMap(medicalService.getId(), request.getIndicators());
        ensureRequiredTemplatesFilled(medicalService.getId(), request.getIndicators());

        order.clearIndicatorResults();
        request.getIndicators().forEach(entry -> {
            IndicatorTemplate template = templatesById.get(entry.getIndicatorId());
            ServiceOrderResult result = buildResult(template, entry);
            order.addIndicatorResult(result);
        });

        if (request.getOverallConclusion() != null) {
            order.setResultNote(request.getOverallConclusion().trim());
        }

        if (request.getPerformedById() != null) {
            order.setPerformedBy(loadDoctor(request.getPerformedById()));
        } else if (order.getPerformedBy() == null) {
            order.setPerformedBy(order.getAssignedDoctor());
        }

        order.setPerformedAt(request.getPerformedAt() != null ? request.getPerformedAt() : LocalDateTime.now());
        order.setStatus(ServiceOrderStatus.COMPLETED_WITH_RESULT);

        return serviceOrderRepository.save(order);
    }

    private ServiceOrderResult buildResult(IndicatorTemplate template, ServiceOrderResultEntryRequest entry) {
        ServiceOrderResult result = new ServiceOrderResult();
        result.setIndicatorTemplate(template);
        result.setIndicatorNameSnapshot(template.getName());
        result.setUnitSnapshot(template.getUnit());
        result.setMeasuredValue(entry.getValue().stripTrailingZeros());
        result.setEvaluation(evaluate(template, entry.getValue()));
        result.setNote(entry.getNote() != null ? entry.getNote().trim() : null);
        return result;
    }

    private Map<Long, IndicatorTemplate> loadTemplatesMap(Long medicalServiceId,
            List<ServiceOrderResultEntryRequest> entries) {
        // Lấy danh sách template IDs được phép cho dịch vụ này
        List<ServiceIndicatorMapping> mappings = mappingRepository
                .findByMedicalServiceIdOrderByDisplayOrderAsc(medicalServiceId);

        Set<Long> allowedTemplateIds = new HashSet<>();
        for (ServiceIndicatorMapping mapping : mappings) {
            allowedTemplateIds.add(mapping.getIndicatorTemplate().getId());
        }

        // Validate và collect template IDs từ request
        Set<Long> requestedTemplateIds = new HashSet<>();
        for (ServiceOrderResultEntryRequest entry : entries) {
            if (!requestedTemplateIds.add(entry.getIndicatorId())) {
                throw new IllegalArgumentException("Không được nhập trùng một chỉ số hai lần.");
            }
        }

        // Load templates và validate
        Map<Long, IndicatorTemplate> map = new HashMap<>();
        for (Long templateId : requestedTemplateIds) {
            if (!allowedTemplateIds.contains(templateId)) {
                throw new IllegalArgumentException("Chỉ số với id " + templateId
                        + " không thuộc dịch vụ này.");
            }
            IndicatorTemplate template = templateRepository.findById(templateId)
                    .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy template với id: " + templateId));
            map.put(templateId, template);
        }
        return map;
    }

    private void ensureRequiredTemplatesFilled(Long serviceId, List<ServiceOrderResultEntryRequest> entries) {
        // Lấy các mappings có required = true
        List<ServiceIndicatorMapping> requiredMappings = mappingRepository
                .findByMedicalServiceIdAndRequiredTrue(serviceId);

        if (requiredMappings.isEmpty()) {
            return;
        }

        Set<Long> providedTemplateIds = new HashSet<>();
        entries.forEach(entry -> providedTemplateIds.add(entry.getIndicatorId()));

        for (ServiceIndicatorMapping mapping : requiredMappings) {
            Long requiredTemplateId = mapping.getIndicatorTemplate().getId();
            if (!providedTemplateIds.contains(requiredTemplateId)) {
                throw new IllegalArgumentException(
                        "Thiếu kết quả cho chỉ số bắt buộc: " + mapping.getIndicatorTemplate().getName());
            }
        }
    }

    private IndicatorResultLevel evaluate(IndicatorTemplate template, BigDecimal value) {
        BigDecimal min = template.getNormalMin();
        BigDecimal max = template.getNormalMax();

        if (min != null && value.compareTo(min) < 0) {
            return IndicatorResultLevel.LOW;
        }
        if (max != null && value.compareTo(max) > 0) {
            return IndicatorResultLevel.HIGH;
        }
        if (min == null && max == null) {
            return IndicatorResultLevel.UNKNOWN;
        }
        return IndicatorResultLevel.NORMAL;
    }

    private ServiceOrder loadOrder(Long id) {
        return serviceOrderRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy phiếu dịch vụ với id: " + id));
    }

    private void ensureOrderExists(Long id) {
        if (!serviceOrderRepository.existsById(id)) {
            throw new EntityNotFoundException("Không tìm thấy phiếu dịch vụ với id: " + id);
        }
    }

    private Doctor loadDoctor(Long id) {
        return doctorRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy bác sĩ với id: " + id));
    }
}
