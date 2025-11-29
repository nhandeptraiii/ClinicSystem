package vn.project.ClinicSystem.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import vn.project.ClinicSystem.model.Patient;

@Repository
public interface PatientRepository extends JpaRepository<Patient, Long> {
  Optional<Patient> findByCode(String code);

  boolean existsByCodeIgnoreCase(String code);

  Optional<Patient> findFirstByPhone(String phone);

  boolean existsByPhone(String phone);

  Optional<Patient> findFirstByEmail(String email);

  boolean existsByEmail(String email);

  Optional<Patient> findFirstByFullNameIgnoreCaseAndPhone(String fullName, String phone);

  List<Patient> findByFullNameContainingIgnoreCase(String keyword);

  @Query("""
      SELECT p FROM Patient p
      WHERE (:keyword IS NULL OR LOWER(p.fullName) LIKE LOWER(CONCAT('%', :keyword, '%'))
          OR LOWER(p.code) LIKE LOWER(CONCAT('%', :keyword, '%'))
          OR LOWER(p.phone) LIKE LOWER(CONCAT('%', :keyword, '%'))
          OR LOWER(p.email) LIKE LOWER(CONCAT('%', :keyword, '%')))
      """)
  Page<Patient> search(@Param("keyword") String keyword, Pageable pageable);

  @Query("""
      SELECT p FROM Patient p
      WHERE (:keyword IS NULL OR LOWER(p.fullName) LIKE LOWER(CONCAT('%', :keyword, '%'))
          OR LOWER(p.code) LIKE LOWER(CONCAT('%', :keyword, '%'))
          OR LOWER(p.phone) LIKE LOWER(CONCAT('%', :keyword, '%'))
          OR LOWER(p.email) LIKE LOWER(CONCAT('%', :keyword, '%')))
        AND (:dateOfBirth IS NULL OR p.dateOfBirth = :dateOfBirth)
      """)
  Page<Patient> searchWithDob(@Param("keyword") String keyword,
      @Param("dateOfBirth") LocalDate dateOfBirth,
      Pageable pageable);

  @Query("""
      SELECT p FROM Patient p
      WHERE (:keyword IS NULL OR LOWER(p.fullName) LIKE LOWER(CONCAT('%', :keyword, '%')))
        AND (:dateOfBirth IS NULL OR p.dateOfBirth = :dateOfBirth)
        AND (:phone IS NULL OR p.phone = :phone)
      """)
  Page<Patient> searchPatients(@Param("keyword") String keyword,
      @Param("dateOfBirth") LocalDate dateOfBirth,
      @Param("phone") String phone,
      Pageable pageable);
}
