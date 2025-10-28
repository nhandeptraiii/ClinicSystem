package vn.project.ClinicSystem.repository;

import java.time.DayOfWeek;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import vn.project.ClinicSystem.model.UserWorkSchedule;

public interface UserWorkScheduleRepository extends JpaRepository<UserWorkSchedule, Long> {
    List<UserWorkSchedule> findByUserIdOrderByDayOfWeekAsc(Long userId);

    Optional<UserWorkSchedule> findByUserIdAndDayOfWeek(Long userId, DayOfWeek dayOfWeek);

    List<UserWorkSchedule> findByClinicRoomIdAndDayOfWeek(Long clinicRoomId, DayOfWeek dayOfWeek);

    void deleteByUserId(Long userId);

    boolean existsByClinicRoomId(Long clinicRoomId);
}
