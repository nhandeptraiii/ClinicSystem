package vn.project.ClinicSystem.service;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import jakarta.persistence.EntityNotFoundException;
import vn.project.ClinicSystem.model.AppointmentRequest;
import vn.project.ClinicSystem.model.Patient;
import vn.project.ClinicSystem.model.dto.PatientPageResponse;
import vn.project.ClinicSystem.repository.PatientRepository;

@Service
@Transactional(readOnly = true)
public class PatientService {
    private final PatientRepository patientRepository;
    private final Validator validator;

    public PatientService(PatientRepository patientRepository, Validator validator) {
        this.patientRepository = patientRepository;
        this.validator = validator;
    }

    public List<Patient> findAll() {
        return patientRepository.findAll();
    }

    public PatientPageResponse getPaged(String keyword, LocalDate dateOfBirth, Pageable pageable) {
        String normalizedKeyword = normalizeKeyword(keyword);
        LocalDate dob = dateOfBirth != null ? dateOfBirth : parseDateFromKeyword(normalizedKeyword);
        boolean keywordLooksLikeDob = dob != null && isDateString(normalizedKeyword);
        String keywordForSearch = keywordLooksLikeDob ? null : normalizedKeyword;

        Page<Patient> page = patientRepository.searchWithDob(
                keywordForSearch,
                dob,
                pageable);
        return PatientPageResponse.from(page);
    }

    public List<Patient> searchByKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return findAll();
        }
        String normalized = keyword.trim();
        List<Patient> patients = patientRepository.findByFullNameContainingIgnoreCase(normalized);
        if (patients.isEmpty()) {
            throw new EntityNotFoundException("Không tìm thấy bệnh nhân với từ khóa: " + normalized);
        }
        return patients;
    }

    public Patient getById(Long id) {
        return patientRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy bệnh nhân với id: " + id));
    }

    public Patient getByCode(String code) {
        String normalizedCode = normalizeCode(code);
        return patientRepository.findByCode(normalizedCode)
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy bệnh nhân với mã: " + normalizedCode));
    }

    @Transactional
    public Patient create(Patient patient) {
        if (patient == null) {
            throw new IllegalArgumentException("Thông tin bệnh nhân không được để trống");
        }
        patient.setCode(normalizeCode(patient.getCode()));
        String normalizedPhone = normalizePhone(patient.getPhone());
        if (normalizedPhone == null) {
            throw new IllegalArgumentException("Số điện thoại không hợp lệ");
        }
        patient.setPhone(normalizedPhone);

        // Kiểm tra nếu cả tên và số điện thoại đều trùng
        String normalizedFullName = patient.getFullName() != null ? patient.getFullName().trim() : null;
        if (normalizedFullName != null && !normalizedFullName.isEmpty()) {
            patientRepository.findFirstByFullNameIgnoreCaseAndPhone(normalizedFullName, normalizedPhone)
                    .ifPresent(existing -> {
                        throw new IllegalStateException("Bệnh nhân này đã có. Vui lòng chọn bệnh nhân đã có.");
                    });
        }

        validateUniquePhone(patient.getPhone(), null);
        if (patient.getEmail() != null && !patient.getEmail().trim().isEmpty()) {
            String normalizedEmail = normalizeEmail(patient.getEmail());
            patient.setEmail(normalizedEmail);
            validateUniqueEmail(patient.getEmail(), null);
        }
        validateBean(patient);
        validateUniqueCode(patient.getCode(), null);
        return patientRepository.save(patient);
    }

    @Transactional
    public Patient update(Long id, Patient changes) {
        if (changes == null) {
            throw new IllegalArgumentException("Thông tin bệnh nhân không được null");
        }
        Patient existing = getById(id);

        if (changes.getCode() != null && !normalizeCode(changes.getCode()).equalsIgnoreCase(existing.getCode())) {
            String normalized = normalizeCode(changes.getCode());
            validateUniqueCode(normalized, existing.getId());
            existing.setCode(normalized);
        }
        if (changes.getFullName() != null) {
            existing.setFullName(changes.getFullName());
        }
        if (changes.getGender() != null) {
            existing.setGender(changes.getGender());
        }
        if (changes.getDateOfBirth() != null) {
            existing.setDateOfBirth(changes.getDateOfBirth());
        }
        if (changes.getPhone() != null) {
            String normalizedPhone = normalizePhone(changes.getPhone());
            if (normalizedPhone != null && !normalizedPhone.equals(existing.getPhone())) {
                validateUniquePhone(normalizedPhone, existing.getId());
                existing.setPhone(normalizedPhone);
            } else if (normalizedPhone != null) {
                existing.setPhone(normalizedPhone);
            }
        }
        if (changes.getEmail() != null) {
            String normalizedEmail = normalizeEmail(changes.getEmail());
            if (normalizedEmail != null && !normalizedEmail.equalsIgnoreCase(existing.getEmail())) {
                validateUniqueEmail(normalizedEmail, existing.getId());
                existing.setEmail(normalizedEmail);
            } else if (normalizedEmail != null) {
                existing.setEmail(normalizedEmail);
            } else {
                existing.setEmail(null);
            }
        }
        if (changes.getAddress() != null) {
            existing.setAddress(changes.getAddress());
        }
        // if (changes.getInsuranceNumber() != null) {
        // existing.setInsuranceNumber(changes.getInsuranceNumber());
        // }
        if (changes.getNote() != null) {
            existing.setNote(changes.getNote());
        }

        validateBean(existing);
        return patientRepository.save(existing);
    }

    @Transactional
    public void delete(Long id) {
        if (!patientRepository.existsById(id)) {
            throw new EntityNotFoundException("Không tìm thấy bệnh nhân với id: " + id);
        }
        patientRepository.deleteById(id);
    }

    @Transactional
    public Patient createFromAppointmentRequest(AppointmentRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Yêu cầu đặt lịch không được null");
        }
        Patient patient = new Patient();
        patient.setCode(generateUniqueCode());
        patient.setFullName(request.getFullName());
        patient.setDateOfBirth(request.getDateOfBirth());
        String normalizedPhone = normalizePhone(request.getPhone());
        patient.setPhone(normalizedPhone);
        patient.setEmail(request.getEmail());
        patient.setNote(buildAutoNote(request));
        return create(patient);
    }

    private void validateUniqueCode(String code, Long currentId) {
        patientRepository.findByCode(code).ifPresent(existing -> {
            boolean sameRecord = currentId != null && existing.getId().equals(currentId);
            if (!sameRecord) {
                throw new IllegalStateException("Mã bệnh nhân đã tồn tại: " + code);
            }
        });
    }

    private void validateUniquePhone(String phone, Long currentId) {
        patientRepository.findFirstByPhone(phone).ifPresent(existing -> {
            boolean sameRecord = currentId != null && existing.getId().equals(currentId);
            if (!sameRecord) {
                throw new IllegalStateException("Số điện thoại đã tồn tại: " + phone);
            }
        });
    }

    private void validateUniqueEmail(String email, Long currentId) {
        if (email == null || email.trim().isEmpty()) {
            return;
        }
        patientRepository.findFirstByEmail(email).ifPresent(existing -> {
            boolean sameRecord = currentId != null && existing.getId().equals(currentId);
            if (!sameRecord) {
                throw new IllegalStateException("Email đã tồn tại: " + email);
            }
        });
    }

    private void validateBean(Patient patient) {
        var violations = validator.validate(patient);
        if (!violations.isEmpty()) {
            throw new ConstraintViolationException(violations);
        }
    }

    private String normalizeCode(String code) {
        if (code == null) {
            throw new IllegalArgumentException("Mã bệnh nhân không được null");
        }
        return code.trim().toUpperCase();
    }

    private String generateUniqueCode() {
        String code;
        do {
            code = "BN" + UUID.randomUUID().toString().replaceAll("-", "").substring(0, 8).toUpperCase();
        } while (patientRepository.existsByCodeIgnoreCase(code));
        return code;
    }

    private String normalizePhone(String phone) {
        if (phone == null) {
            return null;
        }
        String digits = phone.replaceAll("\\D", "");
        if (digits.length() == 10) {
            return digits;
        }
        return null;
    }

    private String normalizeEmail(String email) {
        if (email == null) {
            return null;
        }
        String trimmed = email.trim();
        return trimmed.isEmpty() ? null : trimmed.toLowerCase();
    }

    private String buildAutoNote(AppointmentRequest request) {
        if (request.getId() != null) {
            return "Tạo từ yêu cầu đặt lịch #" + request.getId();
        }
        return "Tạo từ yêu cầu đặt lịch";
    }

    public List<Patient> searchPatients(String keyword, LocalDate dateOfBirth, String phone) {
        String normalizedKeyword = keyword != null ? keyword.trim() : null;
        String normalizedPhone = phone != null ? phone.trim() : null;

        boolean hasKeyword = normalizedKeyword != null && !normalizedKeyword.isEmpty();
        boolean hasDob = dateOfBirth != null;
        boolean hasPhone = normalizedPhone != null && !normalizedPhone.isEmpty();

        if (!hasKeyword && !hasDob && !hasPhone) {
            throw new IllegalArgumentException("Cần cung cấp ít nhất một tiêu chí tìm kiếm");
        }

        return patientRepository.searchPatients(
                hasKeyword ? normalizedKeyword : null,
                dateOfBirth,
                hasPhone ? normalizedPhone : null,
                Pageable.unpaged())
                .getContent();
    }

    private String normalizeKeyword(String keyword) {
        if (keyword == null) {
            return null;
        }
        String trimmed = keyword.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private LocalDate parseDateFromKeyword(String keyword) {
        if (keyword == null) {
            return null;
        }
        String normalized = keyword.replace('/', '-').trim();
        try {
            // yyyy-MM-dd or yyyy-M-d
            if (normalized.matches("^\\d{4}-\\d{1,2}-\\d{1,2}$")) {
                return LocalDate.parse(padDate(normalized));
            }
            // dd-MM-yyyy or d-M-yyyy
            if (normalized.matches("^\\d{1,2}-\\d{1,2}-\\d{4}$")) {
                String[] parts = normalized.split("-");
                String day = parts[0].length() == 1 ? "0" + parts[0] : parts[0];
                String month = parts[1].length() == 1 ? "0" + parts[1] : parts[1];
                return LocalDate.parse(parts[2] + "-" + month + "-" + day);
            }
        } catch (Exception ignored) {
            // Không parse được -> trả null
        }
        return null;
    }

    private boolean isDateString(String keyword) {
        if (keyword == null) {
            return false;
        }
        String normalized = keyword.replace('/', '-').trim();
        return normalized.matches("^\\d{4}-\\d{1,2}-\\d{1,2}$") || normalized.matches("^\\d{1,2}-\\d{1,2}-\\d{4}$");
    }

    private String padDate(String input) {
        // input dạng yyyy-M-d
        String[] parts = input.split("-");
        if (parts.length != 3) {
            return input;
        }
        String month = parts[1].length() == 1 ? "0" + parts[1] : parts[1];
        String day = parts[2].length() == 1 ? "0" + parts[2] : parts[2];
        return parts[0] + "-" + month + "-" + day;
    }

}
