package vn.project.ClinicSystem.repository;

import java.time.DayOfWeek;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import vn.project.ClinicSystem.model.UserWorkSchedule;

public interface UserWorkScheduleRepository extends JpaRepository<UserWorkSchedule, Long> {
    List<UserWorkSchedule> findByUserIdOrderByDayOfWeekAsc(Long userId);

    Optional<UserWorkSchedule> findByUserIdAndDayOfWeek(Long userId, DayOfWeek dayOfWeek);

    List<UserWorkSchedule> findByClinicRoomIdAndDayOfWeek(Long clinicRoomId, DayOfWeek dayOfWeek);

    void deleteByUserId(Long userId);

    boolean existsByClinicRoomId(Long clinicRoomId);

    @Query("""
            SELECT COUNT(DISTINCT uw.user.id) FROM UserWorkSchedule uw
            JOIN uw.user u
            JOIN u.roles r
            WHERE uw.clinicRoom.id = :clinicRoomId
              AND uw.dayOfWeek = :dayOfWeek
              AND LOWER(r.name) = 'doctor'
              AND ((:isMorning = TRUE AND uw.morning = TRUE) OR (:isMorning = FALSE AND uw.afternoon = TRUE))
            """)
    long countDoctorsInRoom(
            @Param("clinicRoomId") Long clinicRoomId,
            @Param("dayOfWeek") DayOfWeek dayOfWeek,
            @Param("isMorning") boolean isMorning);
}
