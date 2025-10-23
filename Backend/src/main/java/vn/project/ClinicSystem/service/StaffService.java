package vn.project.ClinicSystem.service;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import vn.project.ClinicSystem.model.Doctor;
import vn.project.ClinicSystem.model.Role;
import vn.project.ClinicSystem.model.User;
import vn.project.ClinicSystem.model.dto.DoctorCreateRequest;
import vn.project.ClinicSystem.model.dto.StaffCreateRequest;
import vn.project.ClinicSystem.model.dto.StaffDoctorInfo;
import vn.project.ClinicSystem.model.dto.StaffDoctorSummary;
import vn.project.ClinicSystem.model.dto.StaffPageResponse;
import vn.project.ClinicSystem.model.dto.StaffResponse;
import vn.project.ClinicSystem.model.dto.StaffUpdateRequest;
import vn.project.ClinicSystem.model.enums.StaffRole;
import vn.project.ClinicSystem.repository.DoctorRepository;
import vn.project.ClinicSystem.repository.UserRepository;

@Service
@Transactional
public class StaffService {

    private final UserRepository userRepository;
    private final DoctorRepository doctorRepository;
    private final RoleService roleService;
    private final DoctorService doctorService;
    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final Validator validator;

    public StaffService(UserRepository userRepository,
            DoctorRepository doctorRepository,
            RoleService roleService,
            DoctorService doctorService,
            UserService userService,
            PasswordEncoder passwordEncoder,
            Validator validator) {
        this.userRepository = userRepository;
        this.doctorRepository = doctorRepository;
        this.roleService = roleService;
        this.doctorService = doctorService;
        this.userService = userService;
        this.passwordEncoder = passwordEncoder;
        this.validator = validator;
    }

    @Transactional(readOnly = true)
    public StaffPageResponse getStaff(String role, String keyword, Pageable pageable) {
        String normalizedRole = null;
        if (role != null && !role.isBlank() && !"ALL".equalsIgnoreCase(role)) {
            normalizedRole = StaffRole.from(role).getName();
        }
        String normalizedKeyword = keyword != null && !keyword.isBlank() ? keyword.trim() : null;

        Page<User> page = userRepository.searchStaff(
                normalizedRole,
                normalizedKeyword,
                pageable);

        List<Long> accountIds = page.getContent().stream()
                .map(User::getId)
                .toList();

        Map<Long, Doctor> doctorsByUser = accountIds.isEmpty()
                ? Collections.emptyMap()
                : doctorRepository.findByAccountIdIn(accountIds).stream()
                        .filter(doc -> doc.getAccount() != null)
                        .collect(Collectors.toMap(doc -> doc.getAccount().getId(), Function.identity()));

        List<StaffResponse> responses = page.getContent().stream()
                .map(user -> mapToResponse(user, doctorsByUser.get(user.getId())))
                .toList();

        Map<String, Long> roleTotals = Arrays.stream(StaffRole.values())
                .collect(Collectors.toMap(
                        StaffRole::getName,
                        roleItem -> userRepository.countDistinctByRoles_NameIgnoreCase(roleItem.getName()),
                        (existing, replacement) -> replacement,
                        LinkedHashMap::new));

        StaffPageResponse response = new StaffPageResponse();
        response.setItems(responses);
        response.setPage(page.getNumber());
        response.setSize(page.getSize());
        response.setTotalElements(page.getTotalElements());
        response.setTotalPages(page.getTotalPages());
        response.setHasNext(page.hasNext());
        response.setHasPrevious(page.hasPrevious());
        response.setTotalStaff(userRepository.count());
        response.setRoleTotals(roleTotals);
        return response;
    }

    @Transactional(readOnly = true)
    public StaffResponse getStaffById(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy nhân viên với id: " + id));
        Doctor doctor = doctorRepository.findByAccountId(id).orElse(null);
        return mapToResponse(user, doctor);
    }

    public StaffResponse createStaff(StaffCreateRequest request) {
        Objects.requireNonNull(request, "Thông tin nhân viên không được để trống");
        Set<Role> roles = resolveRoles(request.getRoles());

        User user = new User();
        user.setFullName(request.getFullName());
        user.setEmail(request.getEmail());
        user.setPhone(request.getPhone());
        user.setGender(trimToNull(request.getGender()));
        user.setDateOfBirth(request.getDateOfBirth());
        user.setStatus("ACTIVE");
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRoles(roles);

        User savedUser = userService.handleCreateUser(user);

        Doctor doctor = handleDoctorSectionOnCreate(savedUser, roles, request.getDoctor());
        return mapToResponse(savedUser, doctor);
    }

    public StaffResponse updateStaff(Long id, StaffUpdateRequest request) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy nhân viên với id: " + id));

        if (request.getFullName() != null) {
            user.setFullName(request.getFullName());
        }
        if (request.getEmail() != null && !request.getEmail().equalsIgnoreCase(user.getEmail())) {
            ensureEmailUnique(request.getEmail(), user.getId());
            user.setEmail(request.getEmail());
        }
        if (request.getPhone() != null) {
            user.setPhone(request.getPhone());
        }
        if (request.getGender() != null) {
            user.setGender(trimToNull(request.getGender()));
        }
        if (request.getDateOfBirth() != null) {
            user.setDateOfBirth(request.getDateOfBirth());
        }
        if (request.getPassword() != null && !request.getPassword().isBlank()) {
            user.setPassword(passwordEncoder.encode(request.getPassword()));
        }
        if (request.getStatus() != null) {
            user.setStatus(trimToNull(request.getStatus()));
        }

        if (request.getRoles() != null) {
            if (request.getRoles().isEmpty()) {
                throw new IllegalArgumentException("Cần chọn ít nhất một vai trò");
            }
            Set<Role> roles = resolveRoles(request.getRoles());
            user.setRoles(roles);
        }

        validateUser(user);
        User savedUser = userRepository.save(user);

        Set<Role> currentRoles = savedUser.getRoles();
        Doctor doctor = handleDoctorSectionOnUpdate(savedUser, currentRoles, request.getDoctor());

        return mapToResponse(savedUser, doctor);
    }

    private Doctor handleDoctorSectionOnCreate(User user, Set<Role> roles, StaffDoctorInfo doctorInfo) {
        boolean hasDoctorRole = hasRole(user, StaffRole.DOCTOR.getName())
                || roles.stream().anyMatch(role -> StaffRole.DOCTOR.getName().equals(role.getName()));
        if (!hasDoctorRole) {
            return null;
        }
        if (doctorInfo == null) {
            throw new IllegalArgumentException("Cần cung cấp thông tin bác sĩ khi gán vai trò bác sĩ");
        }
        DoctorCreateRequest createRequest = buildDoctorCreateRequest(user.getId(), doctorInfo);
        return doctorService.createForUser(createRequest);
    }

    private Doctor handleDoctorSectionOnUpdate(User user, Set<Role> roles, StaffDoctorInfo doctorInfo) {
        boolean hasDoctorRole = roles.stream().anyMatch(role -> StaffRole.DOCTOR.getName().equals(role.getName()));
        Optional<Doctor> existingDoctorOpt = doctorRepository.findByAccountId(user.getId());

        if (!hasDoctorRole) {
            existingDoctorOpt.ifPresent(doctorRepository::delete);
            return null;
        }

        if (existingDoctorOpt.isEmpty()) {
            if (doctorInfo == null) {
                throw new IllegalArgumentException("Cần cung cấp thông tin bác sĩ khi gán vai trò bác sĩ");
            }
            return doctorService.createForUser(buildDoctorCreateRequest(user.getId(), doctorInfo));
        }

        Doctor existingDoctor = existingDoctorOpt.get();
        if (doctorInfo == null) {
            // Không cập nhật thông tin bác sĩ nếu không truyền vào
            return existingDoctor;
        }

        vn.project.ClinicSystem.model.Doctor changes = new vn.project.ClinicSystem.model.Doctor();
        changes.setSpecialty(doctorInfo.getSpecialty());
        changes.setLicenseNumber(doctorInfo.getLicenseNumber());
        changes.setBiography(doctorInfo.getBiography());

        doctorService.update(existingDoctor.getId(), changes);
        return doctorRepository.findById(existingDoctor.getId())
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy hồ sơ bác sĩ sau khi cập nhật"));
    }

    private DoctorCreateRequest buildDoctorCreateRequest(Long userId, StaffDoctorInfo doctorInfo) {
        DoctorCreateRequest request = new DoctorCreateRequest();
        request.setUserId(userId);
        request.setSpecialty(doctorInfo.getSpecialty());
        request.setLicenseNumber(doctorInfo.getLicenseNumber());
        request.setBiography(doctorInfo.getBiography());
        return request;
    }

    private void validateUser(User user) {
        var violations = validator.validate(user);
        if (!violations.isEmpty()) {
            throw new ConstraintViolationException(violations);
        }
    }

    private void ensureEmailUnique(String email, Long currentUserId) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Email không được để trống");
        }
        userRepository.findByEmail(email.trim()).ifPresent(existing -> {
            if (currentUserId == null || !existing.getId().equals(currentUserId)) {
                throw new IllegalArgumentException("Email đã tồn tại: " + email);
            }
        });
    }

    private boolean hasRole(User user, String roleName) {
        if (user.getRoles() == null) {
            return false;
        }
        return user.getRoles().stream().anyMatch(role -> roleName.equalsIgnoreCase(role.getName()));
    }

    private StaffResponse mapToResponse(User user, Doctor doctor) {
        StaffResponse response = new StaffResponse();
        response.setId(user.getId());
        response.setFullName(user.getFullName());
        response.setEmail(user.getEmail());
        response.setPhone(user.getPhone());
        response.setGender(user.getGender());
        response.setDateOfBirth(user.getDateOfBirth());
        response.setStatus(user.getStatus());
        response.setCreatedAt(user.getCreatedAt());
        response.setUpdatedAt(user.getUpdatedAt());

        Set<String> roleNames = user.getRoles() == null
                ? Set.of()
                : user.getRoles().stream()
                        .map(Role::getName)
                        .sorted()
                        .collect(Collectors.toCollection(LinkedHashSet::new));
        response.setRoles(roleNames);

        if (doctor != null) {
            StaffDoctorSummary summary = new StaffDoctorSummary();
            summary.setId(doctor.getId());
            summary.setSpecialty(doctor.getSpecialty());
            summary.setLicenseNumber(doctor.getLicenseNumber());
            summary.setBiography(doctor.getBiography());
            response.setDoctor(summary);
        }

        return response;
    }

    private Set<Role> resolveRoles(Set<String> roleNames) {
        if (roleNames == null || roleNames.isEmpty()) {
            throw new IllegalArgumentException("Cần chọn ít nhất một vai trò");
        }
        Map<String, Role> resolved = new LinkedHashMap<>();
        for (String rawName : roleNames) {
            StaffRole staffRole = StaffRole.from(rawName);
            resolved.computeIfAbsent(staffRole.getName(),
                    key -> roleService.ensureRole(staffRole.getName(), staffRole.getDescription()));
        }
        return new LinkedHashSet<>(resolved.values());
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
