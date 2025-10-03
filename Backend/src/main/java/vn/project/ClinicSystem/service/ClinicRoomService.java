package vn.project.ClinicSystem.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityNotFoundException;
import vn.project.ClinicSystem.model.ClinicRoom;
import vn.project.ClinicSystem.repository.ClinicRoomRepository;

@Service
@Transactional(readOnly = true)
public class ClinicRoomService {
    private final ClinicRoomRepository clinicRoomRepository;

    public ClinicRoomService(ClinicRoomRepository clinicRoomRepository) {
        this.clinicRoomRepository = clinicRoomRepository;
    }

    public List<ClinicRoom> findAll() {
        return clinicRoomRepository.findAll();
    }

    public ClinicRoom getById(Long id) {
        return clinicRoomRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Clinic room not found with id: " + id));
    }

    public ClinicRoom getByCode(String code) {
        String normalized = normalizeCode(code);
        return clinicRoomRepository.findByCode(normalized)
                .orElseThrow(() -> new EntityNotFoundException("Clinic room not found with code: " + normalized));
    }

    public List<ClinicRoom> searchByFloor(String floor) {
        if (floor == null || floor.isBlank()) {
            return findAll();
        }
        return clinicRoomRepository.findByFloorIgnoreCase(floor.trim());
    }

    @Transactional
    public ClinicRoom create(ClinicRoom room) {
        if (room == null) {
            throw new IllegalArgumentException("Clinic room payload must not be null");
        }
        validateCode(room.getCode(), null);
        room.setCode(normalizeCode(room.getCode()));
        return clinicRoomRepository.save(room);
    }

    @Transactional
    public ClinicRoom update(Long id, ClinicRoom changes) {
        if (changes == null) {
            throw new IllegalArgumentException("Clinic room payload must not be null");
        }
        ClinicRoom existing = getById(id);
        if (changes.getCode() != null
                && !normalizeCode(changes.getCode()).equalsIgnoreCase(existing.getCode())) {
            validateCode(changes.getCode(), id);
            existing.setCode(normalizeCode(changes.getCode()));
        }
        if (changes.getName() != null) {
            existing.setName(changes.getName());
        }
        if (changes.getFloor() != null) {
            existing.setFloor(changes.getFloor());
        }
        if (changes.getNote() != null) {
            existing.setNote(changes.getNote());
        }
        return clinicRoomRepository.save(existing);
    }

    @Transactional
    public void delete(Long id) {
        if (!clinicRoomRepository.existsById(id)) {
            throw new EntityNotFoundException("Clinic room not found with id: " + id);
        }
        clinicRoomRepository.deleteById(id);
    }

    private void validateCode(String code, Long currentId) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("Clinic room code must not be blank");
        }
        String normalized = normalizeCode(code);
        clinicRoomRepository.findByCode(normalized).ifPresent(existing -> {
            boolean same = currentId != null && existing.getId().equals(currentId);
            if (!same) {
                throw new IllegalStateException("Clinic room code already exists: " + normalized);
            }
        });
    }

    private String normalizeCode(String code) {
        if (code == null) {
            throw new IllegalArgumentException("Clinic room code must not be null");
        }
        return code.trim().toUpperCase();
    }
}
