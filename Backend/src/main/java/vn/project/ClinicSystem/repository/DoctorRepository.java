package vn.project.ClinicSystem.repository;

import java.time.DayOfWeek;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import vn.project.ClinicSystem.model.Doctor;

@Repository
public interface DoctorRepository extends JpaRepository<Doctor, Long> {
    Optional<Doctor> findByAccountId(Long accountId);

    List<Doctor> findBySpecialtyContainingIgnoreCase(String specialty);

    Optional<Doctor> findByLicenseNumberIgnoreCase(String licenseNumber);

    boolean existsByLicenseNumberIgnoreCase(String licenseNumber);

    List<Doctor> findByAccountIdIn(List<Long> accountIds);

    /**
     * Lấy danh sách bác sĩ có lịch làm việc tại phòng khám vào ngày và ca cụ thể
     * Sử dụng JOIN từ UserWorkSchedule -> User -> Doctor
     * 
     * @param clinicRoomId ID phòng khám
     * @param dayOfWeek    Ngày trong tuần
     * @param isMorning    true nếu là ca sáng, false nếu là ca chiều
     */
    @Query("""
            SELECT DISTINCT d FROM Doctor d
            JOIN d.account u
            JOIN UserWorkSchedule uws ON uws.user.id = u.id
            WHERE uws.clinicRoom.id = :clinicRoomId
            AND uws.dayOfWeek = :dayOfWeek
            AND (
                (:isMorning = true AND uws.morning = true)
                OR
                (:isMorning = false AND uws.afternoon = true)
            )
            """)
    List<Doctor> findByClinicRoomAndDayAndShift(
            @Param("clinicRoomId") Long clinicRoomId,
            @Param("dayOfWeek") DayOfWeek dayOfWeek,
            @Param("isMorning") boolean isMorning);
}
