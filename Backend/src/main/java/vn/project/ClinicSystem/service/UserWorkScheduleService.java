package vn.project.ClinicSystem.service;

import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityNotFoundException;
import vn.project.ClinicSystem.model.ClinicRoom;
import vn.project.ClinicSystem.model.Role;
import vn.project.ClinicSystem.model.User;
import vn.project.ClinicSystem.model.UserWorkSchedule;
import vn.project.ClinicSystem.model.dto.WorkScheduleDayDto;
import vn.project.ClinicSystem.repository.ClinicRoomRepository;
import vn.project.ClinicSystem.repository.UserRepository;
import vn.project.ClinicSystem.repository.UserWorkScheduleRepository;

@Service
@Transactional
public class UserWorkScheduleService {
    private static final EnumSet<DayOfWeek> SUPPORTED_WORK_DAYS = EnumSet.range(DayOfWeek.MONDAY, DayOfWeek.SATURDAY);
    private static final String DOCTOR_ROLE_NAME = "DOCTOR";
    private static final long UNKNOWN_USER_KEY = -1L;

    private final UserWorkScheduleRepository scheduleRepository;
    private final UserRepository userRepository;
    private final ClinicRoomRepository clinicRoomRepository;

    public UserWorkScheduleService(UserWorkScheduleRepository scheduleRepository,
            UserRepository userRepository,
            ClinicRoomRepository clinicRoomRepository) {
        this.scheduleRepository = scheduleRepository;
        this.userRepository = userRepository;
        this.clinicRoomRepository = clinicRoomRepository;
    }

    public List<WorkScheduleDayDto> getScheduleForUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy người dùng với id: " + userId));
        List<UserWorkSchedule> entries = scheduleRepository.findByUserIdOrderByDayOfWeekAsc(userId);
        return buildScheduleResponse(user, entries);
    }

    public List<WorkScheduleDayDto> getScheduleForUser(User user) {
        List<UserWorkSchedule> entries = scheduleRepository.findByUserIdOrderByDayOfWeekAsc(user.getId());
        return buildScheduleResponse(user, entries);
    }

    public List<WorkScheduleDayDto> updateScheduleForUser(Long userId, List<WorkScheduleDayDto> requestDays,
            Long clinicRoomId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy người dùng với id: " + userId));
        Map<DayOfWeek, WorkScheduleDayDto> normalized = normalizeRequestDays(requestDays);
        ClinicRoom clinicRoom = resolveClinicRoomForSchedule(clinicRoomId, user, normalized);
        validateClinicRoomAvailability(user, normalized, clinicRoom);
        return persistSchedule(user, normalized, clinicRoom);
    }

    public List<WorkScheduleDayDto> updateScheduleForUser(User user, List<WorkScheduleDayDto> requestDays,
            Long clinicRoomId) {
        Map<DayOfWeek, WorkScheduleDayDto> normalized = normalizeRequestDays(requestDays);
        ClinicRoom clinicRoom = resolveClinicRoomForSchedule(clinicRoomId, user, normalized);
        validateClinicRoomAvailability(user, normalized, clinicRoom);
        return persistSchedule(user, normalized, clinicRoom);
    }

    private List<WorkScheduleDayDto> persistSchedule(User user, Map<DayOfWeek, WorkScheduleDayDto> normalized,
            ClinicRoom clinicRoom) {
        scheduleRepository.deleteByUserId(user.getId());
        scheduleRepository.flush();

        boolean isDoctor = hasDoctorRole(user);
        List<UserWorkSchedule> entities = new ArrayList<>();
        for (DayOfWeek day : SUPPORTED_WORK_DAYS) {
            WorkScheduleDayDto dto = normalized.get(day);
            UserWorkSchedule schedule = new UserWorkSchedule();
            schedule.setUser(user);
            schedule.setDayOfWeek(day);

            boolean morningEnabled;
            boolean afternoonEnabled;
            if (isDoctor) {
                morningEnabled = dto != null && dto.isMorning();
                afternoonEnabled = dto != null && dto.isAfternoon();
            } else {
                morningEnabled = true;
                afternoonEnabled = true;
            }
            schedule.setMorning(morningEnabled);
            schedule.setAfternoon(afternoonEnabled);
            schedule.setClinicRoom(clinicRoom);

            entities.add(schedule);
        }

        scheduleRepository.saveAll(entities);
        return buildScheduleResponse(user, entities);
    }

    private void validateClinicRoomAvailability(User user, Map<DayOfWeek, WorkScheduleDayDto> normalized,
            ClinicRoom clinicRoom) {
        if (clinicRoom == null || clinicRoom.getId() == null) {
            return;
        }

        Long clinicRoomId = clinicRoom.getId();
        Map<Long, UserRoomConflict> userConflicts = new LinkedHashMap<>();

        for (DayOfWeek day : SUPPORTED_WORK_DAYS) {
            WorkScheduleDayDto dto = normalized.get(day);
            if (dto == null || (!dto.isMorning() && !dto.isAfternoon())) {
                continue;
            }

            List<UserWorkSchedule> existingSchedules = scheduleRepository
                    .findByClinicRoomIdAndDayOfWeek(clinicRoomId, day);

            for (UserWorkSchedule existing : existingSchedules) {
                User assignedUser = existing.getUser();
                if (assignedUser != null && Objects.equals(assignedUser.getId(), user.getId())) {
                    continue;
                }

                boolean morningConflict = dto.isMorning() && existing.isMorning();
                boolean afternoonConflict = dto.isAfternoon() && existing.isAfternoon();
                if (!morningConflict && !afternoonConflict) {
                    continue;
                }

                long userKey = assignedUser != null && assignedUser.getId() != null ? assignedUser.getId()
                        : UNKNOWN_USER_KEY;
                UserRoomConflict userConflict = userConflicts
                        .computeIfAbsent(userKey, key -> new UserRoomConflict(assignedUser));
                ShiftConflict shift = userConflict.dayConflicts
                        .computeIfAbsent(day, key -> new ShiftConflict());
                shift.register(morningConflict, afternoonConflict);
            }
        }

        if (!userConflicts.isEmpty()) {
            String message = buildConflictMessage(clinicRoom, userConflicts);
            throw new IllegalStateException(message);
        }
    }

    private String buildConflictMessage(ClinicRoom room, Map<Long, UserRoomConflict> userConflicts) {
        List<String> userMessages = new ArrayList<>(userConflicts.size());
        for (UserRoomConflict userConflict : userConflicts.values()) {
            Map<String, List<DayOfWeek>> groupedByShift = new LinkedHashMap<>();
            for (Map.Entry<DayOfWeek, ShiftConflict> entry : userConflict.dayConflicts.entrySet()) {
                ShiftConflict shiftConflict = entry.getValue();
                String shiftLabel = buildConflictShiftLabel(shiftConflict.morning, shiftConflict.afternoon);
                groupedByShift.computeIfAbsent(shiftLabel, key -> new ArrayList<>()).add(entry.getKey());
            }

            List<String> segments = new ArrayList<>(groupedByShift.size());
            for (Map.Entry<String, List<DayOfWeek>> entry : groupedByShift.entrySet()) {
                String shiftLabel = entry.getKey();
                String formattedDays = formatDayList(entry.getValue());
                segments.add(shiftLabel + " " + formattedDays);
            }

            String holderName = resolveUserDisplayName(userConflict.user);
            userMessages.add("đã được " + holderName + " đăng ký " + String.join("; ", segments));
        }

        return "Phòng " + room.getName() + " (" + room.getCode() + ") " + String.join("; ", userMessages);
    }

    private String formatDayList(List<DayOfWeek> days) {
        List<String> labels = new ArrayList<>(days.size());
        for (DayOfWeek supportedDay : SUPPORTED_WORK_DAYS) {
            if (days.contains(supportedDay)) {
                labels.add(formatDayOfWeek(supportedDay));
            }
        }
        return String.join(", ", labels);
    }

    private String resolveUserDisplayName(User user) {
        if (user == null) {
            return "nhân sự khác";
        }
        String fullName = user.getFullName();
        if (fullName != null && !fullName.isBlank()) {
            return fullName;
        }
        String email = user.getEmail();
        if (email != null && !email.isBlank()) {
            return email;
        }
        return "nhân sự khác";
    }

    private static class UserRoomConflict {
        private final User user;
        private final Map<DayOfWeek, ShiftConflict> dayConflicts = new EnumMap<>(DayOfWeek.class);

        private UserRoomConflict(User user) {
            this.user = user;
        }
    }

    private static class ShiftConflict {
        private boolean morning;
        private boolean afternoon;

        private void register(boolean morningConflict, boolean afternoonConflict) {
            this.morning = this.morning || morningConflict;
            this.afternoon = this.afternoon || afternoonConflict;
        }
    }

    private String buildConflictShiftLabel(boolean morningConflict, boolean afternoonConflict) {
        if (morningConflict && afternoonConflict) {
            return "cả ngày";
        }
        if (morningConflict) {
            return "ca sáng";
        }
        return "ca chiều";
    }

    private String formatDayOfWeek(DayOfWeek day) {
        return switch (day) {
            case MONDAY -> "Thứ 2";
            case TUESDAY -> "Thứ 3";
            case WEDNESDAY -> "Thứ 4";
            case THURSDAY -> "Thứ 5";
            case FRIDAY -> "Thứ 6";
            case SATURDAY -> "Thứ 7";
            case SUNDAY -> "Chủ nhật";
        };
    }

    private Map<DayOfWeek, WorkScheduleDayDto> normalizeRequestDays(List<WorkScheduleDayDto> requestDays) {
        Map<DayOfWeek, WorkScheduleDayDto> normalized = new EnumMap<>(DayOfWeek.class);
        if (requestDays == null) {
            return normalized;
        }

        for (WorkScheduleDayDto item : requestDays) {
            if (item == null || item.getDayOfWeek() == null) {
                throw new IllegalArgumentException("Ngày làm việc không được để trống.");
            }
            DayOfWeek day = item.getDayOfWeek();
            if (!SUPPORTED_WORK_DAYS.contains(day)) {
                throw new IllegalArgumentException("Chỉ hỗ trợ thiết lập lịch từ Thứ 2 đến Thứ 7.");
            }
            if (normalized.containsKey(day)) {
                throw new IllegalArgumentException("Ngày \"" + day + "\" đã được khai báo nhiều lần.");
            }

            WorkScheduleDayDto copy = new WorkScheduleDayDto();
            copy.setDayOfWeek(day);
            copy.setMorning(item.isMorning());
            copy.setAfternoon(item.isAfternoon());

            normalized.put(day, copy);
        }
        return normalized;
    }

    private List<WorkScheduleDayDto> buildScheduleResponse(User user, List<UserWorkSchedule> entries) {
        Map<DayOfWeek, UserWorkSchedule> mapped = entries == null
                ? Map.of()
                : entries.stream().filter(Objects::nonNull)
                        .collect(Collectors.toMap(UserWorkSchedule::getDayOfWeek, schedule -> schedule,
                                (left, right) -> right, () -> new EnumMap<>(DayOfWeek.class)));

        boolean isDoctor = hasDoctorRole(user);

        List<WorkScheduleDayDto> result = new ArrayList<>(SUPPORTED_WORK_DAYS.size());
        for (DayOfWeek day : SUPPORTED_WORK_DAYS) {
            UserWorkSchedule stored = mapped.get(day);
            WorkScheduleDayDto dto = new WorkScheduleDayDto();
            dto.setDayOfWeek(day);

            if (stored != null) {
                dto.setMorning(stored.isMorning());
                dto.setAfternoon(stored.isAfternoon());
                ClinicRoom clinicRoom = stored.getClinicRoom();
                if (clinicRoom != null) {
                    dto.setClinicRoomId(clinicRoom.getId());
                    dto.setClinicRoomName(clinicRoom.getName());
                    dto.setClinicRoomCode(clinicRoom.getCode());
                }
            } else {
                boolean defaultShift = !isDoctor;
                dto.setMorning(defaultShift);
                dto.setAfternoon(defaultShift);
                ClinicRoom clinicRoom = entries != null && !entries.isEmpty() ? entries.get(0).getClinicRoom() : null;
                if (clinicRoom != null) {
                    dto.setClinicRoomId(clinicRoom.getId());
                    dto.setClinicRoomName(clinicRoom.getName());
                    dto.setClinicRoomCode(clinicRoom.getCode());
                }
            }
            result.add(dto);
        }

        return result;
    }

    private ClinicRoom resolveClinicRoomForSchedule(Long roomId, User user,
            Map<DayOfWeek, WorkScheduleDayDto> normalized) {
        boolean isDoctor = hasDoctorRole(user);
        if (!isDoctor) {
            return null;
        }
        if (roomId == null) {
            throw new IllegalArgumentException("Vui lòng chọn phòng khám cho lịch làm việc của bác sĩ.");
        }
        ClinicRoom clinicRoom = clinicRoomRepository.findById(roomId)
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy phòng khám với id: " + roomId));
        normalized.values().forEach(dto -> {
            dto.setClinicRoomId(clinicRoom.getId());
            dto.setClinicRoomName(clinicRoom.getName());
            dto.setClinicRoomCode(clinicRoom.getCode());
        });
        return clinicRoom;
    }

    private boolean hasDoctorRole(User user) {
        Set<Role> roles = user.getRoles();
        if (roles == null || roles.isEmpty()) {
            return false;
        }
        return roles.stream()
                .map(Role::getName)
                .filter(Objects::nonNull)
                .anyMatch(name -> DOCTOR_ROLE_NAME.equalsIgnoreCase(name.trim()));
    }
}
