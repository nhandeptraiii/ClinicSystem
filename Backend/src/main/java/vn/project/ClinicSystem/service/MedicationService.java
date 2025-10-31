package vn.project.ClinicSystem.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
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
import vn.project.ClinicSystem.model.Medication;
import vn.project.ClinicSystem.model.dto.MedicationCreateRequest;
import vn.project.ClinicSystem.model.dto.MedicationPageResponse;
import vn.project.ClinicSystem.model.dto.MedicationUpdateRequest;
import vn.project.ClinicSystem.repository.MedicationRepository;

@Service
@Transactional(readOnly = true)
public class MedicationService {

    private final MedicationRepository medicationRepository;
    private final Validator validator;

    public MedicationService(MedicationRepository medicationRepository, Validator validator) {
        this.medicationRepository = medicationRepository;
        this.validator = validator;
    }

    public List<Medication> findAll() {
        return medicationRepository.findAll();
    }

    public MedicationPageResponse getPaged(String keyword, Pageable pageable) {
        Page<Medication> page = medicationRepository.search(
                normalizeKeyword(keyword),
                pageable);
        return MedicationPageResponse.from(page);
    }

    public List<Medication> search(String keyword) {
        return medicationRepository
                .search(normalizeKeyword(keyword), Pageable.unpaged())
                .getContent();
    }

    public Medication getById(Long id) {
        return medicationRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy thuốc với id: " + id));
    }

    @Transactional
    public Medication create(MedicationCreateRequest request) {
        Medication medication = new Medication();
        medication.setName(normalizeName(request.getName()));
        medication.setActiveIngredient(normalizeText(request.getActiveIngredient()));
        medication.setStrength(normalizeText(request.getStrength()));
        medication.setBatchNo(normalizeName(request.getBatchNo()));
        medication.setUnit(normalizeText(request.getUnit()));
        medication.setUnitPrice(normalizePrice(request.getUnitPrice()));
        medication.setManufacturer(normalizeName(request.getManufacturer()));
        medication.setExpiryDate(request.getExpiryDate());
        medication.setStockQuantity(request.getStockQuantity() != null ? request.getStockQuantity() : 0);

        ensureNameUnique(medication.getName(), null);
        validateBean(medication);
        return medicationRepository.save(medication);
    }

    @Transactional
    public Medication update(Long id, MedicationUpdateRequest request) {
        Medication medication = getById(id);

        if (request.getName() != null) {
            String normalized = normalizeName(request.getName());
            if (!normalized.equalsIgnoreCase(medication.getName())) {
                ensureNameUnique(normalized, medication.getId());
            }
            medication.setName(normalized);
        }
        if (request.getActiveIngredient() != null) {
            medication.setActiveIngredient(normalizeText(request.getActiveIngredient()));
        }
        if (request.getStrength() != null) {
            medication.setStrength(normalizeText(request.getStrength()));
        }
        if (request.getBatchNo() != null) {
            medication.setBatchNo(normalizeName(request.getBatchNo()));
        }
        if (request.getUnit() != null) {
            medication.setUnit(normalizeText(request.getUnit()));
        }
        if (request.getUnitPrice() != null) {
            medication.setUnitPrice(normalizePrice(request.getUnitPrice()));
        }
        if (request.getManufacturer() != null) {
            medication.setManufacturer(normalizeName(request.getManufacturer()));
        }
        if (request.getExpiryDate() != null) {
            medication.setExpiryDate(request.getExpiryDate());
        }
        if (request.getStockQuantity() != null) {
            if (request.getStockQuantity() < 0) {
                throw new IllegalArgumentException("Số lượng phải >= 0");
            }
            medication.setStockQuantity(request.getStockQuantity());
        }

        validateBean(medication);
        return medicationRepository.save(medication);
    }

    @Transactional
    public void delete(Long id) {
        Medication medication = getById(id);
        if (medication.getPrescriptionItems() != null && !medication.getPrescriptionItems().isEmpty()) {
            throw new IllegalStateException("Không thể xóa thuốc đã được kê trong đơn thuốc.");
        }
        medicationRepository.delete(medication);
    }

    private void ensureNameUnique(String name, Long currentMedicationId) {
        medicationRepository.findByNameIgnoreCase(name).ifPresent(existing -> {
            boolean same = currentMedicationId != null && existing.getId().equals(currentMedicationId);
            if (!same) {
                throw new EntityExistsException("Đã tồn tại thuốc với tên: " + name);
            }
        });
    }

    private void validateBean(Medication medication) {
        var violations = validator.validate(medication);
        if (!violations.isEmpty()) {
            throw new ConstraintViolationException(violations);
        }
    }

    private String normalizeName(String input) {
        if (!StringUtils.hasText(input)) {
            throw new IllegalArgumentException("Trường này không được để trống");
        }
        return input.trim();
    }

    private String normalizeText(String input) {
        if (!StringUtils.hasText(input)) {
            return null;
        }
        return input.trim();
    }

    private BigDecimal normalizePrice(BigDecimal price) {
        if (price == null) {
            return BigDecimal.ZERO;
        }
        return price.setScale(2, RoundingMode.HALF_UP);
    }

    private String normalizeKeyword(String keyword) {
        if (keyword == null) {
            return null;
        }
        String trimmed = keyword.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
