package vn.project.ClinicSystem.service;

import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityExistsException;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import vn.project.ClinicSystem.model.Doctor;
import vn.project.ClinicSystem.model.Role;
import vn.project.ClinicSystem.model.User;
import vn.project.ClinicSystem.repository.DoctorRepository;
import vn.project.ClinicSystem.repository.RoleRepository;
import vn.project.ClinicSystem.repository.UserRepository;

@Service
@Transactional(readOnly = true)
public class DoctorService {
    private static final String DOCTOR_ROLE_NAME = "DOCTOR";

    private final DoctorRepository doctorRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final Validator validator;

    public DoctorService(DoctorRepository doctorRepository,
            UserRepository userRepository,
            RoleRepository roleRepository,
            Validator validator) {
        this.doctorRepository = doctorRepository;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.validator = validator;
    }

    public List<Doctor> findAll() {
        return doctorRepository.findAll();
    }

    public List<Doctor> searchBySpecialty(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return findAll();
        }
        return doctorRepository.findBySpecialtyContainingIgnoreCase(keyword.trim());
    }

    public Doctor getById(Long id) {
        return doctorRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy bác sĩ với id: " + id));
    }

    public Doctor getByAccountId(Long accountId) {
        return doctorRepository.findByAccountId(accountId)
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy bác sĩ với account id: " + accountId));
    }

    @Transactional
    public Doctor create(Doctor doctor) {
        if (doctor == null) {
            throw new IllegalArgumentException("Thông tin bác sĩ không được null");
        }
        doctor.setLicenseNumber(normalizeLicense(doctor.getLicenseNumber()));
        doctor.setSpecialty(normalizeText(doctor.getSpecialty()));

        validateBean(doctor);
        ensureLicenseUnique(doctor.getLicenseNumber(), null);

        if (doctor.getAccount() != null && doctor.getAccount().getId() != null) {
            Long accountId = doctor.getAccount().getId();
            ensureAccountAvailable(accountId, null);
            User account = loadUser(accountId);
            doctor.setAccount(account);
            attachDoctorRole(account);
        } else {
            doctor.setAccount(null);
        }

        return doctorRepository.save(doctor);
    }

    @Transactional
    public Doctor update(Long id, Doctor changes) {
        if (changes == null) {
            throw new IllegalArgumentException("Thông tin bác sĩ không được null");
        }
        Doctor existing = getById(id);

        if (changes.getSpecialty() != null) {
            existing.setSpecialty(normalizeText(changes.getSpecialty()));
        }
        if (changes.getLicenseNumber() != null) {
            String normalized = normalizeLicense(changes.getLicenseNumber());
            if (!normalized.equalsIgnoreCase(existing.getLicenseNumber())) {
                ensureLicenseUnique(normalized, existing.getId());
                existing.setLicenseNumber(normalized);
            }
        }
        if (changes.getExaminationRoom() != null) {
            existing.setExaminationRoom(changes.getExaminationRoom());
        }
        if (changes.getBiography() != null) {
            existing.setBiography(changes.getBiography());
        }
        if (changes.getAccount() != null) {
            Long newAccountId = changes.getAccount().getId();
            if (newAccountId != null) {
                ensureAccountAvailable(newAccountId, existing.getId());
                User account = loadUser(newAccountId);
                existing.setAccount(account);
                attachDoctorRole(account);
            } else {
                existing.setAccount(null);
            }
        }

        validateBean(existing);
        return doctorRepository.save(existing);
    }

    @Transactional
    public Doctor assignAccount(Long doctorId, Long userId) {
        Doctor doctor = getById(doctorId);
        ensureAccountAvailable(userId, doctorId);
        User account = loadUser(userId);
        doctor.setAccount(account);
        attachDoctorRole(account);
        return doctorRepository.save(doctor);
    }

    @Transactional
    public Doctor detachAccount(Long doctorId) {
        Doctor doctor = getById(doctorId);
        doctor.setAccount(null);
        return doctorRepository.save(doctor);
    }

    @Transactional
    public void delete(Long id) {
        if (!doctorRepository.existsById(id)) {
            throw new EntityNotFoundException("Không tìm thấy bác sĩ với id: " + id);
        }
        doctorRepository.deleteById(id);
    }

    private void validateBean(Doctor doctor) {
        var violations = validator.validate(doctor);
        if (!violations.isEmpty()) {
            throw new ConstraintViolationException(violations);
        }
    }

    private void ensureLicenseUnique(String licenseNumber, Long currentDoctorId) {
        if (licenseNumber == null || licenseNumber.isBlank()) {
            throw new IllegalArgumentException("Số giấy phép không được để trống");
        }
        doctorRepository.findByLicenseNumberIgnoreCase(licenseNumber)
                .ifPresent(existing -> {
                    if (currentDoctorId == null || !existing.getId().equals(currentDoctorId)) {
                        throw new EntityExistsException("Đã tồn tại bác sĩ với số giấy phép: " + licenseNumber);
                    }
                });
    }

    private void ensureAccountAvailable(Long accountId, Long currentDoctorId) {
        doctorRepository.findByAccountId(accountId).ifPresent(existing -> {
            if (currentDoctorId == null || !existing.getId().equals(currentDoctorId)) {
                throw new IllegalStateException("Tài khoản đã được gán cho bác sĩ khác");
            }
        });
    }

    private User loadUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy người dùng với id: " + userId));
    }

    private void attachDoctorRole(User user) {
        roleRepository.findByName(DOCTOR_ROLE_NAME).ifPresent(role -> {
            Set<Role> roles = user.getRoles();
            if (roles.add(role)) {
                userRepository.save(user);
            }
        });
    }

    private String normalizeLicense(String licenseNumber) {
        if (licenseNumber == null) {
            return null;
        }
        return licenseNumber.trim().toUpperCase();
    }

    private String normalizeText(String value) {
        if (value == null) {
            return null;
        }
        return value.trim();
    }
}