package vn.project.ClinicSystem.service;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import jakarta.persistence.EntityExistsException;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import vn.project.ClinicSystem.model.Disease;
import vn.project.ClinicSystem.model.dto.DiseasePageResponse;
import vn.project.ClinicSystem.model.dto.DiseaseRequest;
import vn.project.ClinicSystem.model.dto.DiseaseUpdateRequest;
import vn.project.ClinicSystem.repository.DiseaseRepository;
import vn.project.ClinicSystem.repository.PatientVisitRepository;

@Service
@Transactional(readOnly = true)
public class DiseaseService {

    private final DiseaseRepository diseaseRepository;
    private final PatientVisitRepository patientVisitRepository;
    private final Validator validator;

    public DiseaseService(DiseaseRepository diseaseRepository,
            PatientVisitRepository patientVisitRepository,
            Validator validator) {
        this.diseaseRepository = diseaseRepository;
        this.patientVisitRepository = patientVisitRepository;
        this.validator = validator;
    }

    public List<Disease> findAll() {
        return diseaseRepository.findAll();
    }

    public List<Disease> search(String keyword) {
        return diseaseRepository.search(normalizeKeyword(keyword), Pageable.unpaged()).getContent();
    }

    public DiseasePageResponse getPaged(String keyword, Pageable pageable) {
        Page<Disease> page = diseaseRepository.search(
                normalizeKeyword(keyword),
                pageable);
        return DiseasePageResponse.from(page);
    }

    public Disease getById(Long id) {
        return diseaseRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy bệnh với id: " + id));
    }

    @Transactional
    public Disease create(DiseaseRequest request) {
        Disease disease = Disease.builder()
                .code(normalizeCode(request.getCode()))
                .name(normalizeName(request.getName()))
                .description(normalizeNullableText(request.getDescription()))
                .build();

        ensureCodeUnique(disease.getCode(), null);
        validateBean(disease);
        return diseaseRepository.save(disease);
    }

    @Transactional
    public Disease update(Long id, DiseaseUpdateRequest request) {
        Disease disease = getById(id);

        if (request.getCode() != null) {
            String normalizedCode = normalizeCode(request.getCode());
            if (!normalizedCode.equalsIgnoreCase(disease.getCode())) {
                ensureCodeUnique(normalizedCode, disease.getId());
            }
            disease.setCode(normalizedCode);
        }
        if (request.getName() != null) {
            disease.setName(normalizeName(request.getName()));
        }
        if (request.getDescription() != null) {
            disease.setDescription(normalizeNullableText(request.getDescription()));
        }

        validateBean(disease);
        return diseaseRepository.save(disease);
    }

    @Transactional
    public void delete(Long id) {
        Disease disease = getById(id);
        if (patientVisitRepository.existsByDiseaseId(id)) {
            throw new IllegalStateException("Không thể xóa bệnh đang được sử dụng trong hồ sơ khám.");
        }
        diseaseRepository.delete(disease);
    }

    private void ensureCodeUnique(String code, Long currentId) {
        diseaseRepository.findByCodeIgnoreCase(code).ifPresent(existing -> {
            boolean same = currentId != null && existing.getId().equals(currentId);
            if (!same) {
                throw new EntityExistsException("Đã tồn tại bệnh với mã: " + code);
            }
        });
    }

    private void validateBean(Disease disease) {
        var violations = validator.validate(disease);
        if (!violations.isEmpty()) {
            throw new ConstraintViolationException(violations);
        }
    }

    private String normalizeCode(String code) {
        if (!StringUtils.hasText(code)) {
            throw new IllegalArgumentException("Mã bệnh không được để trống");
        }
        return code.trim().toUpperCase();
    }

    private String normalizeName(String name) {
        if (!StringUtils.hasText(name)) {
            throw new IllegalArgumentException("Tên bệnh không được để trống");
        }
        return name.trim();
    }

    private String normalizeNullableText(String input) {
        if (!StringUtils.hasText(input)) {
            return null;
        }
        return input.trim();
    }

    private String normalizeKeyword(String keyword) {
        if (keyword == null) {
            return null;
        }
        String trimmed = keyword.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
