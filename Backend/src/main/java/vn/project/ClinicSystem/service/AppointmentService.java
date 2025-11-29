package vn.project.ClinicSystem.service;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Objects;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import vn.project.ClinicSystem.model.Appointment;
import vn.project.ClinicSystem.model.AppointmentRequest;
import vn.project.ClinicSystem.model.ClinicRoom;
import vn.project.ClinicSystem.model.Doctor;
import vn.project.ClinicSystem.model.Patient;
import vn.project.ClinicSystem.model.User;
import vn.project.ClinicSystem.model.UserWorkSchedule;
import vn.project.ClinicSystem.model.dto.AppointmentCreateRequest;
import vn.project.ClinicSystem.model.dto.AppointmentPageResponse;
import vn.project.ClinicSystem.model.dto.AppointmentStatusUpdateRequest;
import vn.project.ClinicSystem.model.dto.AppointmentUpdateRequest;
import vn.project.ClinicSystem.model.enums.AppointmentLifecycleStatus;
import vn.project.ClinicSystem.repository.AppointmentRepository;
import vn.project.ClinicSystem.repository.AppointmentRequestRepository;
import vn.project.ClinicSystem.repository.ClinicRoomRepository;
import vn.project.ClinicSystem.repository.DoctorRepository;
import vn.project.ClinicSystem.repository.PatientRepository;
import vn.project.ClinicSystem.repository.UserRepository;
import vn.project.ClinicSystem.repository.UserWorkScheduleRepository;

@Service
@Transactional(readOnly = true)
public class AppointmentService {

    private static final LocalTime MORNING_SHIFT_START = LocalTime.of(8, 0);
    private static final LocalTime MORNING_SHIFT_END = LocalTime.of(12, 0);
    private static final LocalTime AFTERNOON_SHIFT_START = LocalTime.of(13, 0);
    private static final LocalTime AFTERNOON_SHIFT_END = LocalTime.of(17, 0);

    private final AppointmentRepository appointmentRepository;
    private final PatientRepository patientRepository;
    private final DoctorRepository doctorRepository;
    private final ClinicRoomRepository clinicRoomRepository;
    private final UserRepository userRepository;
    private final UserWorkScheduleRepository userWorkScheduleRepository;
    private final AppointmentRequestRepository appointmentRequestRepository;
    private final Validator validator;

    public AppointmentService(AppointmentRepository appointmentRepository,
            PatientRepository patientRepository,
            DoctorRepository doctorRepository,
            ClinicRoomRepository clinicRoomRepository,
            UserRepository userRepository,
            UserWorkScheduleRepository userWorkScheduleRepository,
            AppointmentRequestRepository appointmentRequestRepository,
            Validator validator) {
        this.appointmentRepository = appointmentRepository;
        this.patientRepository = patientRepository;
        this.doctorRepository = doctorRepository;
        this.clinicRoomRepository = clinicRoomRepository;
        this.userRepository = userRepository;
        this.userWorkScheduleRepository = userWorkScheduleRepository;
        this.appointmentRequestRepository = appointmentRequestRepository;
        this.validator = validator;
    }

    public Appointment getById(Long id) {
        return appointmentRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy lịch khám với id: " + id));
    }

    public List<Appointment> findAll() {
        return appointmentRepository.findAll();
    }

    public List<Appointment> findByDoctor(Long doctorId) {
        return appointmentRepository.findByDoctorIdOrderByScheduledAtAsc(doctorId);
    }

    public List<Appointment> findByPatient(Long patientId) {
        return appointmentRepository.findByPatientIdOrderByScheduledAtDesc(patientId);
    }

    public List<Appointment> findByStatus(AppointmentLifecycleStatus status) {
        return appointmentRepository.findByStatus(status);
    }

    public AppointmentPageResponse getPaged(String keyword, AppointmentLifecycleStatus status, Pageable pageable) {
        Page<Appointment> page = appointmentRepository.search(
                normalizeKeyword(keyword),
                status,
                pageable);
        return AppointmentPageResponse.from(page);
    }

    private String normalizeKeyword(String keyword) {
        if (keyword == null) {
            return null;
        }
        String trimmed = keyword.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    @Transactional
    public Appointment createAppointment(AppointmentCreateRequest request, String createdByUsername) {
        Appointment appointment = new Appointment();
        Patient patient = loadPatient(request.getPatientId());
        Doctor doctor = loadDoctor(request.getDoctorId());
        ClinicRoom clinicRoom = loadClinicRoom(request.getClinicRoomId());

        appointment.setPatient(patient);
        appointment.setDoctor(doctor);
        appointment.setClinicRoom(clinicRoom);
        appointment.setScheduledAt(request.getScheduledAt());
        appointment.setDuration(resolveDuration(request.getDuration()));
        appointment.setReason(request.getReason());
        appointment.setNotes(request.getNotes());
        appointment.setStatus(AppointmentLifecycleStatus.CONFIRMED);

        if (createdByUsername != null) {
            appointment.setCreatedBy(loadUserByUsername(createdByUsername));
        }

        validateBean(appointment);
        ensureAvailability(appointment, null);
        return appointmentRepository.save(appointment);
    }

    @Transactional
    public Appointment createFromRequest(AppointmentRequest requestEntity,
            Long patientId,
            Long doctorId,
            LocalDateTime scheduledAt,
            User staffUser,
            Integer duration) {

        Patient patient = loadPatient(patientId);
        Doctor doctor = loadDoctor(doctorId);
        int resolvedDuration = resolveDuration(duration);
        LocalDateTime appointmentEnd = scheduledAt.plusMinutes(resolvedDuration);

        UserWorkSchedule schedule = validateDoctorWorkingHours(doctor, scheduledAt, appointmentEnd);
        ClinicRoom clinicRoom = requireScheduleClinicRoom(schedule);

        Appointment appointment = new Appointment();
        appointment.setPatient(patient);
        appointment.setDoctor(doctor);
        appointment.setClinicRoom(clinicRoom);
        appointment.setScheduledAt(scheduledAt);
        appointment.setDuration(resolvedDuration);
        appointment.setReason(requestEntity.getSymptomDescription());
        appointment.setStatus(AppointmentLifecycleStatus.CONFIRMED);
        appointment.setRequest(requestEntity);
        appointment.setCreatedBy(staffUser);

        validateBean(appointment);
        ensureAvailability(appointment, null);
        return appointmentRepository.save(appointment);
    }

    @Transactional
    public Appointment updateAppointment(Long id, AppointmentUpdateRequest request) {
        Appointment appointment = getById(id);

        if (request.getPatientId() != null && !request.getPatientId().equals(appointment.getPatient().getId())) {
            appointment.setPatient(loadPatient(request.getPatientId()));
        }

        if (request.getDoctorId() != null && !request.getDoctorId().equals(appointment.getDoctor().getId())) {
            appointment.setDoctor(loadDoctor(request.getDoctorId()));
        }

        if (request.getClinicRoomId() != null) {
            if (appointment.getClinicRoom() == null
                    || !request.getClinicRoomId().equals(appointment.getClinicRoom().getId())) {
                appointment.setClinicRoom(loadClinicRoom(request.getClinicRoomId()));
            }
        }

        if (request.getScheduledAt() != null) {
            appointment.setScheduledAt(request.getScheduledAt());
        }

        if (request.getDuration() != null) {
            appointment.setDuration(resolveDuration(request.getDuration()));
        }

        if (request.getReason() != null) {
            appointment.setReason(request.getReason());
        }

        if (request.getNotes() != null) {
            appointment.setNotes(request.getNotes());
        }

        validateBean(appointment);
        ensureAvailability(appointment, appointment.getId());
        return appointmentRepository.save(appointment);
    }

    @Transactional
    public Appointment updateStatus(Long id, AppointmentStatusUpdateRequest request) {
        Appointment appointment = getById(id);
        appointment.setStatus(request.getStatus());
        if (request.getNote() != null) {
            appointment.setNotes(request.getNote());
        }
        return appointmentRepository.save(appointment);
    }

    @Transactional
    public void delete(Long id) {
        Appointment appt = appointmentRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy lịch khám với id: " + id));

        // Chỉ cho phép xóa cứng khi đã hủy
        if (appt.getStatus() != AppointmentLifecycleStatus.CANCELLED) {
            throw new IllegalStateException("Chỉ xóa lịch hẹn đã hủy.");
        }

        // Ngắt liên kết với request nếu có
        AppointmentRequest req = appt.getRequest();
        if (req != null) {
            req.setAppointment(null);
            appt.setRequest(null);
            appointmentRequestRepository.save(req);
        }

        appointmentRepository.delete(appt);
    }

    private void ensureAvailability(Appointment appointment, Long ignoreId) {
        LocalDateTime start = appointment.getScheduledAt();
        int duration = resolveDuration(appointment.getDuration());
        LocalDateTime end = start.plusMinutes(duration);

        // BƯỚC 1: KIỂM TRA LỊCH LÀM VIỆC VÀ PHÒNG
        UserWorkSchedule schedule = validateDoctorWorkingHours(appointment.getDoctor(), start, end);
        ensureClinicRoomConsistency(appointment.getClinicRoom(), schedule);

        // BƯỚC 2: KIỂM TRA XUNG ĐỘT LỊCH HẸN
        boolean doctorConflict = appointmentRepository.existsDoctorOverlap(
                appointment.getDoctor().getId(), start, end, ignoreId) > 0;
        if (doctorConflict) {
            throw new IllegalStateException("Bác sĩ đã có lịch khám khác trong khoảng thời gian này.");
        }

        boolean roomConflict = appointmentRepository.existsClinicRoomOverlap(
                appointment.getClinicRoom().getId(), start, end, ignoreId) > 0;
        if (roomConflict) {
            throw new IllegalStateException("Phòng khám đã có lịch khám khác trong khoảng thời gian này.");
        }

    }

    private UserWorkSchedule validateDoctorWorkingHours(Doctor doctor, LocalDateTime appointmentStart,
            LocalDateTime appointmentEnd) {
        if (doctor == null) {
            throw new IllegalStateException("Thông tin bác sĩ không hợp lệ.");
        }
        User account = resolveDoctorAccount(doctor);
        DayOfWeek day = appointmentStart.getDayOfWeek();

        UserWorkSchedule schedule = userWorkScheduleRepository
                .findByUserIdAndDayOfWeek(account.getId(), day)
                .orElseThrow(() -> new IllegalStateException(
                        "Bác sĩ chưa thiết lập lịch làm việc cho " + formatDayOfWeek(day) + "."));

        if (!schedule.isMorning() && !schedule.isAfternoon()) {
            throw new IllegalStateException("Bác sĩ nghỉ làm " + formatDayOfWeek(day) + ".");
        }

        LocalTime startTime = appointmentStart.toLocalTime();
        LocalTime endTime = appointmentEnd.toLocalTime();

        boolean withinMorning = schedule.isMorning()
                && !startTime.isBefore(MORNING_SHIFT_START)
                && !endTime.isAfter(MORNING_SHIFT_END);

        boolean withinAfternoon = schedule.isAfternoon()
                && !startTime.isBefore(AFTERNOON_SHIFT_START)
                && !endTime.isAfter(AFTERNOON_SHIFT_END);

        if (!(withinMorning || withinAfternoon)) {
            throw new IllegalStateException(
                    "Thời gian đã chọn không nằm trong ca làm việc của bác sĩ vào " + formatDayOfWeek(day) + ".");
        }

        return schedule;
    }

    private void ensureClinicRoomConsistency(ClinicRoom appointmentRoom, UserWorkSchedule schedule) {
        ClinicRoom scheduledRoom = schedule.getClinicRoom();
        if (scheduledRoom == null) {
            throw new IllegalStateException("Bác sĩ chưa được gán phòng khám cho lịch làm việc.");
        }
        if (appointmentRoom == null) {
            throw new IllegalStateException("Vui lòng chọn phòng khám cho lịch hẹn.");
        }
        if (!Objects.equals(scheduledRoom.getId(), appointmentRoom.getId())) {
            throw new IllegalStateException(
                    "Phòng khám của lịch hẹn không khớp với phòng đã đăng ký trong lịch làm việc của bác sĩ.");
        }
    }

    private ClinicRoom requireScheduleClinicRoom(UserWorkSchedule schedule) {
        ClinicRoom clinicRoom = schedule.getClinicRoom();
        if (clinicRoom == null) {
            throw new IllegalStateException("Bác sĩ chưa được gán phòng khám cho lịch làm việc.");
        }
        return clinicRoom;
    }

    private User resolveDoctorAccount(Doctor doctor) {
        if (doctor == null) {
            throw new IllegalStateException("Thông tin bác sĩ không hợp lệ.");
        }
        User account = doctor.getAccount();
        if (account == null) {
            throw new IllegalStateException("Bác sĩ chưa được liên kết với tài khoản người dùng.");
        }
        return account;
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

    private void validateBean(Appointment appointment) {
        var violations = validator.validate(appointment);
        if (!violations.isEmpty()) {
            throw new ConstraintViolationException(violations);
        }
    }

    private int resolveDuration(Integer duration) {
        if (duration == null || duration <= 0) {
            return 30;
        }
        return duration;
    }

    private ClinicRoom loadClinicRoom(Long clinicRoomId) {
        return clinicRoomRepository.findById(clinicRoomId)
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy phòng với id: " + clinicRoomId));
    }

    private Patient loadPatient(Long patientId) {
        return patientRepository.findById(patientId)
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy bệnh nhân với id: " + patientId));
    }

    private Doctor loadDoctor(Long doctorId) {
        return doctorRepository.findById(doctorId)
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy bác sĩ với id: " + doctorId));
    }

    private User loadUserByUsername(String username) {
        return userRepository.findByEmail(username)
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy người dùng với email: " + username));
    }
}
