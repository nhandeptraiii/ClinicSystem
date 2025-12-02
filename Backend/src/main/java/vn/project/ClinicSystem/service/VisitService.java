package vn.project.ClinicSystem.service;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import jakarta.persistence.EntityNotFoundException;
import vn.project.ClinicSystem.model.Appointment;
import vn.project.ClinicSystem.model.ClinicRoom;
import vn.project.ClinicSystem.model.Doctor;
import vn.project.ClinicSystem.model.Disease;
import vn.project.ClinicSystem.model.MedicalService;
import vn.project.ClinicSystem.model.PatientVisit;
import vn.project.ClinicSystem.model.ServiceOrder;
import vn.project.ClinicSystem.model.dto.PatientVisitCreateRequest;
import vn.project.ClinicSystem.model.dto.PatientVisitPageResponse;
import vn.project.ClinicSystem.model.dto.PatientVisitStatusUpdateRequest;
import vn.project.ClinicSystem.model.dto.PatientVisitUpdateRequest;
import vn.project.ClinicSystem.model.dto.ServiceOrderCreateRequest;
import vn.project.ClinicSystem.model.dto.ServiceOrderStatusUpdateRequest;
import vn.project.ClinicSystem.model.enums.AppointmentLifecycleStatus;
import vn.project.ClinicSystem.model.enums.ServiceOrderStatus;
import vn.project.ClinicSystem.model.enums.VisitStatus;
import vn.project.ClinicSystem.repository.AppointmentRepository;
import vn.project.ClinicSystem.repository.DoctorRepository;
import vn.project.ClinicSystem.repository.DiseaseRepository;
import vn.project.ClinicSystem.repository.MedicalServiceRepository;
import vn.project.ClinicSystem.repository.PatientVisitRepository;
import vn.project.ClinicSystem.repository.ServiceOrderRepository;

@Service
@Transactional(readOnly = true)
public class VisitService {

    private final PatientVisitRepository patientVisitRepository;
    private final ServiceOrderRepository serviceOrderRepository;
    private final AppointmentService appointmentService;
    private final AppointmentRepository appointmentRepository;
    private final DiseaseRepository diseaseRepository;
    private final MedicalServiceRepository medicalServiceRepository;
    private final DoctorRepository doctorRepository;

    public VisitService(PatientVisitRepository patientVisitRepository,
            ServiceOrderRepository serviceOrderRepository,
            AppointmentService appointmentService,
            AppointmentRepository appointmentRepository,
            DiseaseRepository diseaseRepository,
            MedicalServiceRepository medicalServiceRepository,
            DoctorRepository doctorRepository) {
        this.patientVisitRepository = patientVisitRepository;
        this.serviceOrderRepository = serviceOrderRepository;
        this.appointmentService = appointmentService;
        this.appointmentRepository = appointmentRepository;
        this.diseaseRepository = diseaseRepository;
        this.medicalServiceRepository = medicalServiceRepository;
        this.doctorRepository = doctorRepository;
    }

    public PatientVisit getById(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("Cần cung cấp id hồ sơ khám");
        }
        return patientVisitRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy hồ sơ khám với id: " + id));
    }

    public List<PatientVisit> findByPatient(Long patientId) {
        return patientVisitRepository.findByPatientIdOrderByCreatedAtDesc(patientId);
    }

    public List<PatientVisit> findAll() {
        return patientVisitRepository.findAll();
    }

    public PatientVisitPageResponse getPaged(String keyword, VisitStatus status, Pageable pageable) {
        Page<PatientVisit> page = patientVisitRepository.search(
                normalizeKeyword(keyword),
                status,
                pageable);
        return PatientVisitPageResponse.from(page);
    }

    public PatientVisitPageResponse getCompletedWithoutBilling(String keyword, Pageable pageable) {
        Page<PatientVisit> page = patientVisitRepository.searchCompletedWithoutBilling(
                normalizeKeyword(keyword),
                pageable);
        return PatientVisitPageResponse.from(page);
    }

    private String normalizeKeyword(String keyword) {
        if (keyword == null) {
            return null;
        }
        String trimmed = keyword.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public List<ServiceOrder> findServiceOrders(Long visitId) {
        ensureVisitExists(visitId);
        return serviceOrderRepository.findByVisitId(visitId);
    }

    @Transactional
    public PatientVisit createVisit(PatientVisitCreateRequest request) {
        if (request.getPrimaryAppointmentId() == null) {
            throw new IllegalArgumentException("Cần chọn lịch hẹn chính để tạo hồ sơ khám");
        }

        Appointment primaryAppointment = appointmentService.getById(request.getPrimaryAppointmentId());

        if (patientVisitRepository.existsByPrimaryAppointmentId(primaryAppointment.getId())) {
            throw new IllegalStateException("Lịch khám này đã có hồ sơ khám");
        }
        if (primaryAppointment.getPatient() == null) {
            throw new IllegalStateException("Lịch khám chưa gắn bệnh nhân, không thể tạo hồ sơ khám");
        }
        if (primaryAppointment.getStatus() != AppointmentLifecycleStatus.CONFIRMED) {
            throw new IllegalStateException("Chỉ có thể tạo hồ sơ khám cho lịch hẹn đã xác nhận");
        }

        // ✅ Tự động cập nhật Appointment status thành CHECKED_IN
        primaryAppointment.setStatus(AppointmentLifecycleStatus.CHECKED_IN);
        appointmentRepository.save(primaryAppointment);

        PatientVisit visit = new PatientVisit();
        visit.setPrimaryAppointment(primaryAppointment);
        visit.setPatient(primaryAppointment.getPatient());
        visit.setProvisionalDiagnosis(request.getProvisionalDiagnosis());
        visit.setClinicalNote(request.getClinicalNote());
        visit.setStatus(VisitStatus.OPEN);
        return patientVisitRepository.save(visit);
    }

    @Transactional
    public List<ServiceOrder> createServiceOrders(Long visitId,
            List<ServiceOrderCreateRequest> requests) {

        if (requests == null || requests.isEmpty()) {
            throw new IllegalArgumentException("Danh sách dịch vụ không được để trống");
        }

        PatientVisit visit = getById(visitId);
        if (visit.getStatus() != VisitStatus.OPEN) {
            throw new IllegalStateException("Hồ sơ khám không ở trạng thái mở");
        }

        List<ServiceOrder> createdOrders = new ArrayList<>();
        for (ServiceOrderCreateRequest item : requests) {
            createdOrders.add(createSingleOrder(visit, item));
        }
        return createdOrders;
    }

    @Transactional
    public PatientVisit updateStatus(Long visitId, PatientVisitStatusUpdateRequest request) {
        PatientVisit visit = getById(visitId);

        if (request.getStatus() == VisitStatus.COMPLETED) {
            boolean hasPending = !serviceOrderRepository.findByVisitIdAndStatus(visitId, ServiceOrderStatus.PENDING)
                    .isEmpty();
            boolean hasScheduled = !serviceOrderRepository
                    .findByVisitIdAndStatus(visitId, ServiceOrderStatus.SCHEDULED)
                    .isEmpty();
            boolean hasInProgress = !serviceOrderRepository
                    .findByVisitIdAndStatus(visitId, ServiceOrderStatus.IN_PROGRESS)
                    .isEmpty();
            if (hasPending || hasScheduled || hasInProgress) {
                throw new IllegalStateException("Không thể hoàn tất hồ sơ khi vẫn còn phiếu dịch vụ chưa xử lý.");
            }
        }

        visit.setStatus(request.getStatus());

        // Đồng bộ trạng thái Appointment khi hồ sơ hoàn tất
        if (request.getStatus() == VisitStatus.COMPLETED && visit.getPrimaryAppointment() != null) {
            Appointment primaryAppointment = visit.getPrimaryAppointment();
            primaryAppointment.setStatus(AppointmentLifecycleStatus.COMPLETED);
            appointmentRepository.save(primaryAppointment);
        }

        return patientVisitRepository.save(visit);
    }

    @Transactional
    public PatientVisit updateClinicalInfo(Long visitId, PatientVisitUpdateRequest request) {
        PatientVisit visit = getById(visitId);

        visit.setProvisionalDiagnosis(normalizeNullableText(request.getProvisionalDiagnosis()));
        visit.setClinicalNote(normalizeNullableText(request.getClinicalNote()));
        visit.setDiagnosisNote(normalizeNullableText(request.getDiagnosisNote()));

        // Multi-disease handling
        if (request.getDiseaseIds() != null) {
            if (request.getDiseaseIds().isEmpty()) {
                visit.getDiseases().clear();
            } else {
                List<Disease> diseases = diseaseRepository.findAllById(request.getDiseaseIds());
                if (diseases.size() != request.getDiseaseIds().size()) {
                    throw new EntityNotFoundException("Một hoặc nhiều bệnh không tồn tại.");
                }
                visit.getDiseases().clear();
                visit.getDiseases().addAll(diseases);
            }
        }

        return patientVisitRepository.save(visit);
    }

    @Transactional
    public ServiceOrder updateServiceOrderStatus(Long orderId, ServiceOrderStatusUpdateRequest request) {
        if (orderId == null) {
            throw new IllegalArgumentException("Cần cung cấp id phiếu dịch vụ");
        }
        if (request == null || request.getStatus() == null) {
            throw new IllegalArgumentException("Trạng thái cập nhật phiếu dịch vụ không được để trống");
        }
        ServiceOrder order = serviceOrderRepository.findById(orderId)
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy phiếu dịch vụ với id: " + orderId));

        order.setStatus(request.getStatus());
        order.setResultNote(request.getResultNote());

        return serviceOrderRepository.save(order);
    }

    private String normalizeNullableText(String input) {
        if (!StringUtils.hasText(input)) {
            return null;
        }
        return input.trim();
    }

    private ServiceOrder createSingleOrder(PatientVisit visit,
            ServiceOrderCreateRequest request) {

        if (visit == null) {
            throw new IllegalArgumentException("Hồ sơ khám không được để trống");
        }
        if (request == null) {
            throw new IllegalArgumentException("Thông tin chỉ định dịch vụ không được để trống");
        }
        Long medicalServiceId = request.getMedicalServiceId();
        if (medicalServiceId == null) {
            throw new IllegalArgumentException("Cần chọn dịch vụ chuyên khoa");
        }

        MedicalService medicalService = medicalServiceRepository.findById(medicalServiceId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Không tìm thấy dịch vụ với id: " + medicalServiceId));

        ClinicRoom clinicRoom = medicalService.getClinicRoom();
        if (clinicRoom == null) {
            throw new IllegalStateException("Dịch vụ chưa được gán vào phòng khám, không thể tạo chỉ định");
        }

        // Tự động tìm bác sĩ phụ trách từ ClinicRoom thông qua UserWorkSchedule
        Doctor assignedDoctor = findAssignedDoctorForClinicRoom(clinicRoom.getId());

        ServiceOrder order = new ServiceOrder();
        order.setVisit(visit);
        order.setMedicalService(medicalService);
        order.setAssignedDoctor(assignedDoctor);
        order.setStatus(ServiceOrderStatus.PENDING);
        order.setNote(request.getNote());

        return serviceOrderRepository.save(order);
    }

    /**
     * Tìm bác sĩ phụ trách từ ClinicRoom thông qua UserWorkSchedule.
     * Ưu tiên tìm bác sĩ đang làm việc vào ngày hiện tại, nếu không có thì lấy bất
     * kỳ bác sĩ nào.
     */
    private Doctor findAssignedDoctorForClinicRoom(Long clinicRoomId) {
        DayOfWeek currentDay = LocalDateTime.now().getDayOfWeek();
        boolean isMorning = LocalDateTime.now().getHour() < 12;

        // Thử tìm bác sĩ đang làm việc vào ca hiện tại
        List<Doctor> doctors = doctorRepository.findByClinicRoomAndDayAndShift(
                clinicRoomId, currentDay, isMorning);

        if (!doctors.isEmpty()) {
            return doctors.get(0); // Lấy bác sĩ đầu tiên
        }

        // Nếu không có, thử tìm bác sĩ làm ca còn lại trong ngày
        doctors = doctorRepository.findByClinicRoomAndDayAndShift(
                clinicRoomId, currentDay, !isMorning);

        if (!doctors.isEmpty()) {
            return doctors.get(0);
        }

        // Nếu vẫn không có, tìm bất kỳ bác sĩ nào có lịch làm việc tại phòng này
        // (tìm trong tất cả các ngày)
        List<DayOfWeek> allDays = List.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY);

        for (DayOfWeek day : allDays) {
            doctors = doctorRepository.findByClinicRoomAndDayAndShift(clinicRoomId, day, true);
            if (!doctors.isEmpty()) {
                return doctors.get(0);
            }
            doctors = doctorRepository.findByClinicRoomAndDayAndShift(clinicRoomId, day, false);
            if (!doctors.isEmpty()) {
                return doctors.get(0);
            }
        }

        throw new IllegalStateException(
                "Không tìm thấy bác sĩ phụ trách cho phòng khám của dịch vụ này. Vui lòng kiểm tra lịch làm việc.");
    }

    private void ensureVisitExists(Long visitId) {
        if (visitId == null) {
            throw new IllegalArgumentException("Cần cung cấp id hồ sơ khám");
        }
        if (!patientVisitRepository.existsById(visitId)) {
            throw new EntityNotFoundException("Không tìm thấy hồ sơ khám với id: " + visitId);
        }
    }
}
