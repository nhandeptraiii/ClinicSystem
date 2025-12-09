package vn.project.ClinicSystem.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import jakarta.persistence.EntityNotFoundException;
import vn.project.ClinicSystem.model.Billing;
import vn.project.ClinicSystem.model.BillingItem;
import vn.project.ClinicSystem.model.Medication;
import vn.project.ClinicSystem.model.PatientVisit;
import vn.project.ClinicSystem.model.Prescription;
import vn.project.ClinicSystem.model.PrescriptionItem;
import vn.project.ClinicSystem.model.ServiceOrder;
import vn.project.ClinicSystem.model.enums.BillingItemType;
import vn.project.ClinicSystem.model.enums.BillingStatus;
import vn.project.ClinicSystem.model.enums.ServiceOrderStatus;
import vn.project.ClinicSystem.model.dto.BillingItemCreateRequest;
import vn.project.ClinicSystem.model.dto.BillingItemUpdateRequest;
import vn.project.ClinicSystem.model.dto.BillingPageResponse;
import vn.project.ClinicSystem.model.dto.BillingStatusUpdateRequest;
import vn.project.ClinicSystem.repository.BillingItemRepository;
import vn.project.ClinicSystem.repository.BillingRepository;
import vn.project.ClinicSystem.repository.PatientVisitRepository;
import vn.project.ClinicSystem.repository.PrescriptionRepository;
import vn.project.ClinicSystem.repository.ServiceOrderRepository;

@Service
@Transactional(readOnly = true)
public class BillingService {

    private final BillingRepository billingRepository;
    private final BillingItemRepository billingItemRepository;
    private final PatientVisitRepository patientVisitRepository;
    private final ServiceOrderRepository serviceOrderRepository;
    private final PrescriptionRepository prescriptionRepository;

    public BillingService(BillingRepository billingRepository,
            BillingItemRepository billingItemRepository,
            PatientVisitRepository patientVisitRepository,
            ServiceOrderRepository serviceOrderRepository,
            PrescriptionRepository prescriptionRepository) {
        this.billingRepository = billingRepository;
        this.billingItemRepository = billingItemRepository;
        this.patientVisitRepository = patientVisitRepository;
        this.serviceOrderRepository = serviceOrderRepository;
        this.prescriptionRepository = prescriptionRepository;
    }

    public Billing getById(Long billingId) {
        return billingRepository.findById(billingId)
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy hóa đơn với id: " + billingId));
    }

    public Billing getByVisit(Long visitId) {
        return billingRepository.findByVisitId(visitId)
                .orElseThrow(() -> new EntityNotFoundException("Chưa tồn tại hóa đơn cho hồ sơ khám id: " + visitId));
    }

    public List<Billing> findByPatient(Long patientId) {
        return billingRepository.findByPatientIdOrderByIssuedAtDesc(patientId);
    }

    public List<Billing> findAll() {
        return billingRepository.findAll(Sort.by(Sort.Direction.DESC, "issuedAt"));
    }

    public BillingPageResponse getPaged(String keyword, BillingStatus status, Long patientId, Pageable pageable) {
        String normalizedKeyword = normalizeKeyword(keyword);
        Page<Billing> page = billingRepository.search(normalizedKeyword, status, patientId, pageable);
        return BillingPageResponse.from(page);
    }

    @Transactional
    public Billing generateForVisit(Long visitId) {
        billingRepository.findByVisitId(visitId).ifPresent(existing -> {
            throw new IllegalStateException("Hồ sơ khám đã có hóa đơn. Vui lòng sử dụng lại hóa đơn hiện có.");
        });

        PatientVisit visit = patientVisitRepository.findById(visitId)
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy hồ sơ khám với id: " + visitId));

        Billing billing = new Billing();
        billing.setVisit(visit);
        // visit.setBilling(billing);
        billing.setPatient(visit.getPatient());
        billing.setIssuedAt(LocalDateTime.now());
        billing.setStatus(BillingStatus.UNPAID);

        populateServiceOrderItems(billing, visitId);
        populateMedicationItems(billing, visitId);

        billing.recalculateTotals();

        return billingRepository.save(billing);
    }

    @Transactional
    public Billing updateStatus(Long billingId, BillingStatusUpdateRequest request) {
        Billing billing = getById(billingId);
        billing.setStatus(request.getStatus());
        if (request.getPaymentMethod() != null) {
            billing.setPaymentMethod(normalizeText(request.getPaymentMethod()));
        }
        if (request.getNotes() != null) {
            billing.setNotes(normalizeText(request.getNotes()));
        }
        return billingRepository.save(billing);
    }

    @Transactional
    public Billing addManualItem(Long billingId, BillingItemCreateRequest request) {
        Billing billing = getById(billingId);

        BillingItem item = new BillingItem();
        item.setItemType(request.getItemType());
        item.setDescription(request.getDescription().trim());
        item.setQuantity(request.getQuantity());
        item.setUnitPrice(request.getUnitPrice());
        item.recalculateAmount();

        billing.addItem(item);
        billing.recalculateTotals();
        return billingRepository.save(billing);
    }

    @Transactional
    public BillingItem updateItem(Long billingId, Long itemId, BillingItemUpdateRequest request) {
        Billing billing = getById(billingId);
        BillingItem item = billingItemRepository.findById(itemId)
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy mục hóa đơn với id: " + itemId));

        if (!item.getBilling().getId().equals(billing.getId())) {
            throw new IllegalArgumentException("Mục hóa đơn không thuộc về hóa đơn đã chọn.");
        }

        if (request.getItemType() != null) {
            item.setItemType(request.getItemType());
        }
        if (request.getDescription() != null) {
            item.setDescription(request.getDescription().trim());
        }
        if (request.getQuantity() != null) {
            item.setQuantity(request.getQuantity());
        }
        if (request.getUnitPrice() != null) {
            item.setUnitPrice(request.getUnitPrice());
        }

        item.recalculateAmount();
        BillingItem savedItem = billingItemRepository.save(item);

        billing.recalculateTotals();
        billingRepository.save(billing);

        return savedItem;
    }

    @Transactional
    public void deleteItem(Long billingId, Long itemId) {
        Billing billing = getById(billingId);
        BillingItem item = billingItemRepository.findById(itemId)
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy mục hóa đơn với id: " + itemId));

        if (!item.getBilling().getId().equals(billing.getId())) {
            throw new IllegalArgumentException("Mục hóa đơn không thuộc về hóa đơn đã chọn.");
        }

        billing.getItems().remove(item);
        billingItemRepository.delete(item);
        billing.recalculateTotals();
        billingRepository.save(billing);
    }

    private void populateServiceOrderItems(Billing billing, Long visitId) {
        List<ServiceOrder> orders = serviceOrderRepository.findByVisitId(visitId);

        for (ServiceOrder order : orders) {
            ServiceOrderStatus status = order.getStatus();
            if (status == ServiceOrderStatus.CANCELLED || status == ServiceOrderStatus.PENDING
                    || status == ServiceOrderStatus.SCHEDULED || status == ServiceOrderStatus.IN_PROGRESS) {
                continue;
            }

            if (order.getMedicalService() == null || order.getMedicalService().getBasePrice() == null) {
                continue;
            }

            BigDecimal price = BigDecimal.valueOf(order.getMedicalService().getBasePrice());

            BillingItem item = new BillingItem();
            item.setItemType(BillingItemType.SERVICE);
            item.setDescription(order.getMedicalService().getName());
            item.setQuantity(1);
            item.setUnitPrice(price);
            item.setServiceOrderId(order.getId());
            item.setMedicalServiceId(order.getMedicalService().getId());
            item.recalculateAmount();

            billing.addItem(item);
        }
    }

    private void populateMedicationItems(Billing billing, Long visitId) {
        List<Prescription> prescriptions = prescriptionRepository.findByVisitIdOrderByIssuedAtDesc(visitId);
        for (Prescription prescription : prescriptions) {
            for (PrescriptionItem item : prescription.getItems()) {
                BillingItem billingItem = new BillingItem();
                billingItem.setItemType(BillingItemType.MEDICATION);

                String name = item.getMedicationName();
                Medication medication = item.getMedication();

                if (!StringUtils.hasText(name) && medication != null) {
                    name = medication.getName();
                }
                if (!StringUtils.hasText(name)) {
                    name = "Thuốc";
                }
                // Keep description as medication name only. Do not append dosage to avoid
                // showing quantity-like suffixes (e.g. "Paracetamol 500mg - 1"). If needed,
                // dosage/frequency can be shown separately in the UI in the future.
                billingItem.setDescription(name);

                // Số lượng là số đơn vị trực tiếp (viên hoặc gói), không còn tính theo hộp
                int quantity = item.getQuantity() != null ? item.getQuantity() : 1;
                billingItem.setQuantity(quantity);

                // Lấy giá từ snapshot (đã lưu khi tạo prescription) hoặc từ Medication hiện tại
                // unitPrice là giá cho 1 đơn vị (1 viên hoặc 1 gói), không còn tính theo hộp
                BigDecimal unitPrice = BigDecimal.ZERO;
                if (item.getUnitPriceSnapshot() != null) {
                    // Ưu tiên dùng giá snapshot (giá tại thời điểm kê đơn)
                    unitPrice = item.getUnitPriceSnapshot();
                } else if (medication != null && medication.getUnitPrice() != null) {
                    // Fallback: lấy giá từ Medication nếu snapshot không có
                    unitPrice = medication.getUnitPrice();
                }

                billingItem.setUnitPrice(unitPrice);
                billingItem.setPrescriptionItemId(item.getId());

                if (medication != null) {
                    billingItem.setMedicationId(medication.getId());
                }

                // Tính amount = quantity * unitPrice (đơn giản, không còn logic hộp)
                billingItem.recalculateAmount();

                billing.addItem(billingItem);
            }
        }
    }

    private String normalizeText(String text) {
        return StringUtils.hasText(text) ? text.trim() : null;
    }

    private String normalizeKeyword(String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return null;
        }
        String trimmed = keyword.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    @Transactional
    public void deleteBilling(Long id) {
        Billing billing = getById(id);
        billingRepository.delete(billing);
    }
}
