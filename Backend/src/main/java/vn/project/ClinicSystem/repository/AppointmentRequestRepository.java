package vn.project.ClinicSystem.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import vn.project.ClinicSystem.model.AppointmentRequest;
import vn.project.ClinicSystem.model.AppointmentRequest.RequestStatus;

@Repository
public interface AppointmentRequestRepository extends JpaRepository<AppointmentRequest, Long> {
    List<AppointmentRequest> findByStatusOrderByCreatedAtAsc(RequestStatus status);

    List<AppointmentRequest> findAllByOrderByCreatedAtDesc();
}
