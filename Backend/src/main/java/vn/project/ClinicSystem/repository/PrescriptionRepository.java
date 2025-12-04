package vn.project.ClinicSystem.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import vn.project.ClinicSystem.model.Prescription;
import vn.project.ClinicSystem.model.enums.PrescriptionStatus;

@Repository
public interface PrescriptionRepository extends JpaRepository<Prescription, Long> {

    List<Prescription> findByVisitIdOrderByIssuedAtDesc(Long visitId);

    List<Prescription> findByStatusOrderByIssuedAtDesc(PrescriptionStatus status);
}
