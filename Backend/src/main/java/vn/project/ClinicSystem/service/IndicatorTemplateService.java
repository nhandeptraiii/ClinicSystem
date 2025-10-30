package vn.project.ClinicSystem.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import jakarta.persistence.EntityExistsException;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import vn.project.ClinicSystem.model.IndicatorTemplate;
import vn.project.ClinicSystem.model.dto.IndicatorTemplateRequest;
import vn.project.ClinicSystem.repository.IndicatorTemplateRepository;

@Service
@Transactional(readOnly = true)
public class IndicatorTemplateService {

    private final IndicatorTemplateRepository templateRepository;
    private final Validator validator;

    public IndicatorTemplateService(IndicatorTemplateRepository templateRepository, Validator validator) {
        this.templateRepository = templateRepository;
        this.validator = validator;
    }

    public List<IndicatorTemplate> findAll() {
        return templateRepository.findAllByOrderByNameAsc();
    }

    public List<IndicatorTemplate> findAllActive() {
        return templateRepository.findByIsActiveTrueOrderByNameAsc();
    }

    public List<IndicatorTemplate> findByCategoryActive(String category) {
        return templateRepository.findByCategoryAndIsActiveTrueOrderByNameAsc(category);
    }

    public IndicatorTemplate getById(Long id) {
        return templateRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy template với id: " + id));
    }

    @Transactional
    public IndicatorTemplate create(IndicatorTemplateRequest request) {
        IndicatorTemplate template = new IndicatorTemplate();
        template.setCode(normalizeCode(request.getCode()));
        template.setName(normalizeText(request.getName(), "Tên chỉ số không hợp lệ"));
        template.setUnit(normalizeOptional(request.getUnit()));
        template.setNormalMin(request.getNormalMin());
        template.setNormalMax(request.getNormalMax());
        template.setCriticalMin(request.getCriticalMin());
        template.setCriticalMax(request.getCriticalMax());
        template.setReferenceNote(normalizeOptional(request.getReferenceNote()));
        template.setCategory(normalizeOptional(request.getCategory()));
        template.setIsActive(request.getIsActive() != null ? request.getIsActive() : true);

        ensureCodeUnique(template.getCode(), null);
        validateBean(template);
        return templateRepository.save(template);
    }

    @Transactional
    public IndicatorTemplate update(Long id, IndicatorTemplateRequest request) {
        IndicatorTemplate template = getById(id);

        if (request.getCode() != null) {
            String normalized = normalizeCode(request.getCode());
            if (!normalized.equalsIgnoreCase(template.getCode())) {
                ensureCodeUnique(normalized, template.getId());
            }
            template.setCode(normalized);
        }

        if (request.getName() != null) {
            template.setName(normalizeText(request.getName(), "Tên chỉ số không hợp lệ"));
        }

        if (request.getUnit() != null) {
            template.setUnit(normalizeOptional(request.getUnit()));
        }

        if (request.getNormalMin() != null || request.getNormalMax() != null) {
            template.setNormalMin(request.getNormalMin());
            template.setNormalMax(request.getNormalMax());
        }

        if (request.getCriticalMin() != null || request.getCriticalMax() != null) {
            template.setCriticalMin(request.getCriticalMin());
            template.setCriticalMax(request.getCriticalMax());
        }

        if (request.getReferenceNote() != null) {
            template.setReferenceNote(normalizeOptional(request.getReferenceNote()));
        }

        if (request.getCategory() != null) {
            template.setCategory(normalizeOptional(request.getCategory()));
        }

        if (request.getIsActive() != null) {
            template.setIsActive(request.getIsActive());
        }

        validateBean(template);
        return templateRepository.save(template);
    }

    @Transactional
    public void delete(Long id) {
        if (!templateRepository.existsById(id)) {
            throw new EntityNotFoundException("Không tìm thấy template với id: " + id);
        }
        templateRepository.deleteById(id);
    }

    private void ensureCodeUnique(String code, Long currentTemplateId) {
        templateRepository.findByCode(code).ifPresent(existing -> {
            boolean same = currentTemplateId != null && existing.getId().equals(currentTemplateId);
            if (!same) {
                throw new EntityExistsException("Đã tồn tại template với mã: " + code);
            }
        });
    }

    private void validateBean(IndicatorTemplate template) {
        var violations = validator.validate(template);
        if (!violations.isEmpty()) {
            throw new ConstraintViolationException(violations);
        }
    }

    private String normalizeCode(String code) {
        if (!StringUtils.hasText(code)) {
            throw new IllegalArgumentException("Mã chỉ số không được để trống");
        }
        return code.trim().toUpperCase();
    }

    private String normalizeText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private String normalizeOptional(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
