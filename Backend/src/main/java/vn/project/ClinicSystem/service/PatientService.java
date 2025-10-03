package vn.project.ClinicSystem.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityNotFoundException;
import vn.project.ClinicSystem.model.Patient;
import vn.project.ClinicSystem.repository.PatientRepository;

@Service
@Transactional(readOnly = true)
public class PatientService {
    private final PatientRepository patientRepository;

    public PatientService(PatientRepository patientRepository) {
        this.patientRepository = patientRepository;
    }

    public List<Patient> findAll() {
        return patientRepository.findAll();
    }

    public List<Patient> searchByKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return findAll();
        }
        return patientRepository.findByFullNameContainingIgnoreCase(keyword.trim());
    }

    public Patient getById(Long id) {
        return patientRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Patient not found with id: " + id));
    }

    public Patient getByCode(String code) {
        String normalizedCode = normalizeCode(code);
        return patientRepository.findByCode(normalizedCode)
                .orElseThrow(() -> new EntityNotFoundException("Patient not found with code: " + normalizedCode));
    }

    @Transactional
    public Patient create(Patient patient) {
        if (patient == null) {
            throw new IllegalArgumentException("Patient payload must not be null");
        }
        validatePatientCode(patient.getCode(), null);
        patient.setCode(normalizeCode(patient.getCode()));
        return patientRepository.save(patient);
    }

    @Transactional
    public Patient update(Long id, Patient changes) {
        if (changes == null) {
            throw new IllegalArgumentException("Patient payload must not be null");
        }
        Patient existing = getById(id);

        if (changes.getCode() != null
                && !normalizeCode(changes.getCode()).equalsIgnoreCase(existing.getCode())) {
            validatePatientCode(changes.getCode(), id);
            existing.setCode(normalizeCode(changes.getCode()));
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
            existing.setPhone(changes.getPhone());
        }
        if (changes.getEmail() != null) {
            existing.setEmail(changes.getEmail());
        }
        if (changes.getAddress() != null) {
            existing.setAddress(changes.getAddress());
        }
        if (changes.getInsuranceNumber() != null) {
            existing.setInsuranceNumber(changes.getInsuranceNumber());
        }
        if (changes.getNote() != null) {
            existing.setNote(changes.getNote());
        }

        return patientRepository.save(existing);
    }

    @Transactional
    public void delete(Long id) {
        if (!patientRepository.existsById(id)) {
            throw new EntityNotFoundException("Patient not found with id: " + id);
        }
        patientRepository.deleteById(id);
    }

    private void validatePatientCode(String code, Long currentId) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("Patient code must not be blank");
        }
        String normalized = normalizeCode(code);
        patientRepository.findByCode(normalized).ifPresent(existing -> {
            boolean isSameRecord = currentId != null && existing.getId().equals(currentId);
            if (!isSameRecord) {
                throw new IllegalStateException("Patient code already exists: " + normalized);
            }
        });
    }

    private String normalizeCode(String code) {
        if (code == null) {
            throw new IllegalArgumentException("Patient code must not be null");
        }
        return code.trim().toUpperCase();
    }
}
