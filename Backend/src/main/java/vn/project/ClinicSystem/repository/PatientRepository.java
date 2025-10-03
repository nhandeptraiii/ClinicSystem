package vn.project.ClinicSystem.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import vn.project.ClinicSystem.model.Patient;

@Repository
public interface PatientRepository extends JpaRepository<Patient, Long> {
    Optional<Patient> findByCode(String code);

    boolean existsByCodeIgnoreCase(String code);

    List<Patient> findByFullNameContainingIgnoreCase(String keyword);
}
