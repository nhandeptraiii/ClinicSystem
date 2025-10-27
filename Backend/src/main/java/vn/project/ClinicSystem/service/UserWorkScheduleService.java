package vn.project.ClinicSystem.service;

import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
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
        return persistSchedule(user, normalized, clinicRoom);
    }

    public List<WorkScheduleDayDto> updateScheduleForUser(User user, List<WorkScheduleDayDto> requestDays,
            Long clinicRoomId) {
        Map<DayOfWeek, WorkScheduleDayDto> normalized = normalizeRequestDays(requestDays);
        ClinicRoom clinicRoom = resolveClinicRoomForSchedule(clinicRoomId, user, normalized);
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
