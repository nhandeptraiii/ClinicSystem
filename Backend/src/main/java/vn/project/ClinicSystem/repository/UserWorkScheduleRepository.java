package vn.project.ClinicSystem.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import vn.project.ClinicSystem.model.UserWorkSchedule;

public interface UserWorkScheduleRepository extends JpaRepository<UserWorkSchedule, Long> {
    List<UserWorkSchedule> findByUserIdOrderByDayOfWeekAsc(Long userId);

    void deleteByUserId(Long userId);
}
