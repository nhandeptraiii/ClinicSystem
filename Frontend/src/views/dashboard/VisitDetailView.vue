<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import AdminHeader from '@/components/AdminHeader.vue';
import { useAuthStore } from '@/stores/authStore';
import { useToast, type ToastType } from '@/composables/useToast';
import {
  fetchVisitById,
  updateVisitStatus,
  updateVisitClinicalInfo,
  fetchServiceOrders,
  createServiceOrders,
  type PatientVisit,
  type ServiceOrder,
  type ServiceOrderCreatePayload,
  type PatientVisitStatusUpdatePayload,
  type PatientVisitUpdatePayload,
} from '@/services/visit.service';
import {
  fetchPrescriptions,
  createPrescription,
  updatePrescription,
  type Prescription,
  type PrescriptionCreatePayload,
  type PrescriptionUpdatePayload,
} from '@/services/prescription.service';
import {
  fetchServiceOrderResults,
  recordServiceOrderResults,
  type ServiceOrderResult,
  type ServiceOrderResultPayload,
} from '@/services/visit.service';
import {
  fetchMedicalServicePage,
  fetchServiceIndicatorMappings,
  type MedicalService,
  type ServiceIndicatorMapping,
} from '@/services/medicalService.service';
import { fetchMedications, type Medication } from '@/services/medication.service';
import { fetchDiseases, type Disease } from '@/services/disease.service';

const route = useRoute();
const router = useRouter();
const { toast, show: showToast, hide: hideToast } = useToast();

const userName = computed(() => {
  const authStore = useAuthStore();
  return authStore.user?.username ?? 'Quản trị viên';
});

type ToastVisual = {
  title: string;
  container: string;
  icon: string;
  iconType: 'success' | 'error' | 'warning' | 'info';
};

const toastVisualMap: Record<ToastType, ToastVisual> = {
  success: {
    title: 'Thành công',
    container: 'border-emerald-200 bg-emerald-50/95 text-emerald-800',
    icon: 'bg-emerald-100 text-emerald-600',
    iconType: 'success',
  },
  error: {
    title: 'Có lỗi xảy ra',
    container: 'border-rose-200 bg-rose-50/95 text-rose-700',
    icon: 'bg-rose-100 text-rose-600',
    iconType: 'error',
  },
  info: {
    title: 'Thông báo',
    container: 'border-sky-200 bg-sky-50/95 text-sky-700',
    icon: 'bg-sky-100 text-sky-600',
    iconType: 'info',
  },
  warning: {
    title: 'Cảnh báo',
    container: 'border-amber-200 bg-amber-50/95 text-amber-700',
    icon: 'bg-amber-100 text-amber-600',
    iconType: 'warning',
  },
};

const toastVisuals = computed(() => toastVisualMap[toast.value?.type ?? 'info']);
const dismissToast = () => hideToast();

const visitId = computed(() => Number(route.params.id));
const visit = ref<PatientVisit | null>(null);
const loading = ref(false);

// Service Orders
const serviceOrders = ref<ServiceOrder[]>([]);
const loadingServiceOrders = ref(false);

// Chặn hoàn tất khi còn phiếu dịch vụ chưa xử lý
const hasBlockingOrders = computed(() =>
  serviceOrders.value.some((o) => ['PENDING', 'SCHEDULED', 'IN_PROGRESS'].includes(o.status ?? '')),
);

const clinicalServiceOrders = computed(() =>
  serviceOrders.value.filter((o) => o.medicalService?.requiresIndicator === false),
);

const clsServiceOrders = computed(() =>
  serviceOrders.value.filter((o) => o.medicalService?.requiresIndicator !== false),
);

// Service Order Results (grouped by service order)
const serviceOrderResults = ref<Map<number, ServiceOrderResult[]>>(new Map());
const loadingResults = ref(false);

// Prescription
const prescriptions = ref<Prescription[]>([]);
const loadingPrescriptions = ref(false);
const currentPrescription = computed(() => prescriptions.value[0] ?? null);

// Status update
const statusUpdateModalOpen = ref(false);
const newStatus = ref<string>('');

// Clinical info edit
const editClinicalModalOpen = ref(false);
const clinicalFormState = ref({
  provisionalDiagnosis: '',
});
const clinicalSubmitting = ref(false);
const diseaseOptions = ref<Disease[]>([]);
const selectedDiseases = ref<Disease[]>([]);
const diseaseSearchTerm = ref('');
const loadingDiseases = ref(false);
let diseaseSearchTimer: ReturnType<typeof setTimeout> | null = null;
const availableClinicalServices = ref<MedicalService[]>([]);
const loadingClinicalServices = ref(false);
const selectedClinicalServiceIds = ref<number[]>([]);

// Service Order Modal
const serviceOrderModalOpen = ref(false);
const SERVICE_PAGE_SIZE = 8;
const selectedServices = ref<number[]>([]);
const serviceOrderNote = ref('');
const availableServices = ref<MedicalService[]>([]);
const loadingServices = ref(false);
const serviceSearchTerm = ref('');
const serviceCurrentPage = ref(1);
const serviceTotalPages = ref(1);
const serviceTotalElements = ref(0);
const serviceHasNext = ref(false);
const serviceHasPrevious = ref(false);
let serviceSearchTimer: ReturnType<typeof setTimeout> | null = null;

// Service Order Result Modal
const resultModalOpen = ref(false);
const currentServiceOrderForResult = ref<ServiceOrder | null>(null);
const indicatorMappings = ref<ServiceIndicatorMapping[]>([]);
const loadingIndicators = ref(false);
const resultFormState = ref<ServiceOrderResultPayload>({
  performedById: null,
  performedAt: new Date().toISOString(),
  overallConclusion: null,
  indicators: [],
});
const resultSubmitting = ref(false);

const resultNeedsIndicators = computed(() => {
  const requiresFlag = currentServiceOrderForResult.value?.medicalService?.requiresIndicator;
  const serviceRequires = requiresFlag !== undefined ? requiresFlag : true;
  const hasMappings = indicatorMappings.value.length > 0;
  return serviceRequires && hasMappings;
});

const filteredServices = computed(() => availableServices.value);

const servicePaginationLabel = computed(() => {
  const total = serviceTotalElements.value ?? 0;
  const current = availableServices.value.length;
  if (!total) {
    return `Đang hiển thị ${current} dịch vụ`;
  }
  return `Đang hiển thị ${current} / ${total} dịch vụ`;
});

// Prescription Modal
const prescriptionModalOpen = ref(false);
const prescriptionFormState = ref<PrescriptionCreatePayload>({
  visitId: 0,
  prescribedById: null,
  issuedAt: new Date().toISOString(),
  notes: null,
  items: [],
});
const availableMedications = ref<Medication[]>([]);
const loadingMedications = ref(false);
const medicationSearchTerm = ref('');

const filteredMedications = computed(() => {
  if (!medicationSearchTerm.value.trim()) {
    return availableMedications.value;
  }
  const term = medicationSearchTerm.value.toLowerCase();
  return availableMedications.value.filter(
    (med) =>
      med.name.toLowerCase().includes(term) ||
      med.activeIngredient?.toLowerCase().includes(term) ||
      med.batchNo.toLowerCase().includes(term),
  );
});

const loadVisit = async () => {
  loading.value = true;
  try {
    visit.value = await fetchVisitById(visitId.value);
    await Promise.all([loadServiceOrders(), loadPrescriptions()]);
  } catch (err: any) {
    const errorMessage = err?.message ?? 'Không thể tải thông tin lượt khám.';
    showToast('error', errorMessage);
  } finally {
    loading.value = false;
  }
};

const loadServiceOrders = async () => {
  if (!visitId.value) return;
  loadingServiceOrders.value = true;
  try {
    serviceOrders.value = await fetchServiceOrders(visitId.value);
    // Load results for each service order
    await Promise.all(
      serviceOrders.value.map(async (order) => {
        if (order.id) {
          try {
            const results = await fetchServiceOrderResults(order.id);
            serviceOrderResults.value.set(order.id, results);
          } catch (err) {
            console.error(`Failed to load results for order ${order.id}:`, err);
          }
        }
      }),
    );
  } catch (err: any) {
    showToast('error', 'Không thể tải danh sách chỉ định CLS.');
  } finally {
    loadingServiceOrders.value = false;
  }
};

const loadPrescriptions = async () => {
  if (!visitId.value) return;
  loadingPrescriptions.value = true;
  try {
    prescriptions.value = await fetchPrescriptions(visitId.value);
  } catch (err: any) {
    showToast('error', 'Không thể tải đơn thuốc.');
  } finally {
    loadingPrescriptions.value = false;
  }
};

const loadDiseaseOptions = async (keyword?: string) => {
  loadingDiseases.value = true;
  try {
    const sanitized = keyword?.trim();
    if (!sanitized) {
      diseaseOptions.value = [];
      return;
    }
    diseaseOptions.value = await fetchDiseases(sanitized);
  } catch (err: any) {
    diseaseOptions.value = [];
    showToast('error', err?.response?.data?.message ?? 'Không thể tải danh mục bệnh.');
  } finally {
    loadingDiseases.value = false;
  }
};

const loadClinicalServices = async () => {
  loadingClinicalServices.value = true;
  try {
    const clinicRoomId = visit.value?.primaryAppointment?.clinicRoom?.id;
    const response = await fetchMedicalServicePage({
      page: 0,
      size: 50,
      clinicRoomId: clinicRoomId ?? undefined,
    });
    availableClinicalServices.value = (response.items ?? []).filter(
      (svc) => svc.requiresIndicator === false,
    );
  } catch (err: any) {
    availableClinicalServices.value = [];
    showToast('error', err?.response?.data?.message ?? 'Không thể tải dịch vụ khám lâm sàng.');
  } finally {
    loadingClinicalServices.value = false;
  }
};

const addDisease = (disease: Disease) => {
  const exists = selectedDiseases.value.some((d) => d.id === disease.id);
  if (!exists) {
    selectedDiseases.value = [...selectedDiseases.value, disease];
  }
  diseaseSearchTerm.value = '';
  diseaseOptions.value = [];
};

const removeDisease = (id: number) => {
  selectedDiseases.value = selectedDiseases.value.filter((d) => d.id !== id);
};

const updateStatus = async () => {
  if (!visit.value || !newStatus.value) return;

  if (newStatus.value === 'COMPLETED' && hasBlockingOrders.value) {
    showToast('error', 'Không thể hoàn tất hồ sơ khi vẫn còn phiếu dịch vụ chưa xử lý.');
    return;
  }

  try {
    const payload: PatientVisitStatusUpdatePayload = { status: newStatus.value };
    visit.value = await updateVisitStatus(visit.value.id, payload);
    statusUpdateModalOpen.value = false;
    showToast('success', 'Đã cập nhật trạng thái.');
  } catch (err: any) {
    const errorMessage = err?.response?.data?.message ?? err?.message ?? 'Không thể cập nhật trạng thái.';
    showToast('error', errorMessage);
  }
};

const openEditClinicalModal = () => {
  if (visit.value) {
    clinicalFormState.value = {
      provisionalDiagnosis: visit.value.provisionalDiagnosis ?? '',
    };
    selectedDiseases.value = Array.isArray(visit.value.diseases) ? [...(visit.value.diseases ?? [])] : [];
    diseaseSearchTerm.value = '';
    diseaseOptions.value = [];
    const existingClinicalOrders = serviceOrders.value.filter(
      (o) => o.medicalService?.requiresIndicator === false && o.medicalService?.id,
    );
    selectedClinicalServiceIds.value = existingClinicalOrders
      .map((o) => o.medicalService?.id)
      .filter((id): id is number => typeof id === 'number');
    void loadClinicalServices();
    editClinicalModalOpen.value = true;
  }
};

const saveClinicalInfo = async () => {
  if (!visit.value) return;

  const trimmedDiagnosis = clinicalFormState.value.provisionalDiagnosis?.trim() ?? '';
  const selectedIds = selectedDiseases.value.map((d) => d.id);

  const payload: PatientVisitUpdatePayload = {
    provisionalDiagnosis: trimmedDiagnosis.length > 0 ? trimmedDiagnosis : null,
    diseaseIds: selectedIds,
  };

  try {
    clinicalSubmitting.value = true;
    const updatedVisit = await updateVisitClinicalInfo(visit.value.id, payload);
    const existingVisit = visit.value as PatientVisit;
    visit.value = {
      ...existingVisit,
      provisionalDiagnosis: updatedVisit.provisionalDiagnosis ?? null,
      diseases: updatedVisit.diseases ?? [],
      updatedAt: updatedVisit.updatedAt ?? existingVisit?.updatedAt,
    };
    clinicalFormState.value = {
      provisionalDiagnosis: payload.provisionalDiagnosis ?? '',
    };
    selectedDiseases.value = updatedVisit.diseases ?? [];

    // Thêm dịch vụ khám lâm sàng (requiresIndicator=false) nếu được chọn
    const existingClinicalOrderIds = serviceOrders.value
      .filter((o) => o.medicalService?.requiresIndicator === false && o.medicalService?.id)
      .map((o) => o.medicalService?.id as number);
    const missingClinicalIds = selectedClinicalServiceIds.value.filter(
      (id) => !existingClinicalOrderIds.includes(id),
    );
    if (missingClinicalIds.length > 0 && visit.value?.id) {
      const clinicalNote = payload.provisionalDiagnosis || null;
      const clinicalPayloads: ServiceOrderCreatePayload[] = missingClinicalIds.map((id) => ({
        medicalServiceId: id,
        note: clinicalNote,
      }));
      await createServiceOrders(visit.value.id, clinicalPayloads);
      await loadServiceOrders();
    }

    showToast('success', 'Đã cập nhật thông tin lâm sàng.');
    editClinicalModalOpen.value = false;
  } catch (err: any) {
    const errorMessage =
      err?.response?.data?.message ?? err?.message ?? 'Không thể cập nhật thông tin lâm sàng.';
    showToast('error', errorMessage);
  } finally {
    clinicalSubmitting.value = false;
  }
};

const loadServiceCatalog = async (targetPage?: number) => {
  loadingServices.value = true;
  const keyword = serviceSearchTerm.value.trim();
  const requestedPage = targetPage ?? serviceCurrentPage.value;
  const pageIndex = Math.max(requestedPage - 1, 0);

  try {
    const response = await fetchMedicalServicePage({
      page: pageIndex,
      size: SERVICE_PAGE_SIZE,
      keyword: keyword ? keyword : undefined,
    });

    availableServices.value = response.items ?? [];
    serviceTotalElements.value = response.totalElements ?? availableServices.value.length;
    const derivedTotalPages = response.totalPages && response.totalPages > 0 ? response.totalPages : 1;
    serviceTotalPages.value = derivedTotalPages;
    serviceCurrentPage.value = response.totalPages && response.totalPages > 0 ? response.page + 1 : 1;
    serviceHasNext.value = response.hasNext ?? false;
    serviceHasPrevious.value = response.hasPrevious ?? false;
  } catch (err) {
    availableServices.value = [];
    serviceTotalElements.value = 0;
    serviceTotalPages.value = 1;
    serviceCurrentPage.value = 1;
    serviceHasNext.value = false;
    serviceHasPrevious.value = false;
    showToast('error', 'Không thể tải danh sách dịch vụ.');
  } finally {
    loadingServices.value = false;
  }
};

const openServiceOrderModal = async () => {
  serviceOrderModalOpen.value = true;
  selectedServices.value = [];
  serviceOrderNote.value = '';
  serviceSearchTerm.value = '';
  serviceCurrentPage.value = 1;
  await loadServiceCatalog(1);
};

const toggleServiceSelection = (serviceId: number) => {
  const index = selectedServices.value.indexOf(serviceId);
  if (index > -1) {
    selectedServices.value.splice(index, 1);
  } else {
    selectedServices.value.push(serviceId);
  }
};

const goToServicePage = async (page: number) => {
  const total = serviceTotalPages.value > 0 ? serviceTotalPages.value : 1;
  const target = Math.min(Math.max(page, 1), total);
  await loadServiceCatalog(target);
};

const nextServicePage = () => {
  if (serviceHasNext.value) {
    void goToServicePage(serviceCurrentPage.value + 1);
  }
};

const prevServicePage = () => {
  if (serviceHasPrevious.value) {
    void goToServicePage(serviceCurrentPage.value - 1);
  }
};

watch(serviceSearchTerm, () => {
  if (serviceSearchTimer) {
    clearTimeout(serviceSearchTimer);
  }
  serviceSearchTimer = setTimeout(() => {
    if (!serviceOrderModalOpen.value) {
      return;
    }
    void goToServicePage(1);
  }, 350);
});

watch(diseaseSearchTerm, (value) => {
  if (diseaseSearchTimer) {
    clearTimeout(diseaseSearchTimer);
  }
  diseaseSearchTimer = setTimeout(() => {
    if (!editClinicalModalOpen.value) {
      return;
    }
    const term = value?.trim();
    if (!term || term.length < 2) {
      diseaseOptions.value = [];
      return;
    }
    void loadDiseaseOptions(term);
  }, 320);
});

const submitServiceOrders = async () => {
  if (!visit.value || selectedServices.value.length === 0) {
    showToast('error', 'Vui lòng chọn ít nhất một dịch vụ.');
    return;
  }

  try {
    const payloads: ServiceOrderCreatePayload[] = selectedServices.value.map((serviceId) => ({
      medicalServiceId: serviceId,
      note: serviceOrderNote.value.trim() || null,
    }));

    await createServiceOrders(visit.value.id, payloads);
    showToast('success', 'Đã thêm chỉ định CLS. Bác sĩ phụ trách sẽ được tự động gán theo phòng khám.');
    serviceOrderModalOpen.value = false;
    await loadServiceOrders();
  } catch (err: any) {
    const errorMessage = err?.response?.data?.message ?? err?.message ?? 'Không thể tạo chỉ định CLS.';
    showToast('error', errorMessage);
  }
};

const openResultModal = async (order: ServiceOrder) => {
  if (!order.id || !order.medicalService?.id) return;
  currentServiceOrderForResult.value = order;
  loadingIndicators.value = true;
  resultFormState.value = {
    performedById: null,
    performedAt: new Date().toISOString(),
    overallConclusion: order.resultNote || null,
    indicators: [],
  };

  try {
    // Load indicator mappings for this service
    const mappings = await fetchServiceIndicatorMappings(order.medicalService.id);
    indicatorMappings.value = mappings;
    const requiresIndicators = order.medicalService?.requiresIndicator !== false;
    const hasMappings = mappings.length > 0;

    if (requiresIndicators && hasMappings && order.indicatorResults && order.indicatorResults.length > 0) {
      // Prefill from existing results
      resultFormState.value.indicators = order.indicatorResults.map((result) => ({
        indicatorId: result.indicatorTemplate?.id ?? 0,
        value: result.measuredValue ?? 0,
        note: result.note ?? null,
      }));
    } else if (requiresIndicators && hasMappings) {
      // Initialize with all required indicators
      resultFormState.value.indicators = mappings
        .filter((m) => m.required)
        .map((m) => ({
          indicatorId: m.indicatorTemplate.id,
          value: 0,
          note: null,
        }));
    } else {
      // Dịch vụ lâm sàng không yêu cầu chỉ số: không cần prefill indicators
      resultFormState.value.indicators = [];
    }
    resultModalOpen.value = true;
  } catch (err) {
    showToast('error', 'Không thể tải danh sách chỉ số xét nghiệm.');
  } finally {
    loadingIndicators.value = false;
  }
};

const addIndicatorToResult = (mapping: ServiceIndicatorMapping) => {
  const exists = resultFormState.value.indicators.some(
    (item) => item.indicatorId === mapping.indicatorTemplate.id,
  );
  if (!exists) {
    resultFormState.value.indicators.push({
      indicatorId: mapping.indicatorTemplate.id,
      value: 0,
      note: null,
    });
  }
};

const removeIndicatorFromResult = (indicatorId: number) => {
  const index = resultFormState.value.indicators.findIndex((item) => item.indicatorId === indicatorId);
  if (index > -1) {
    resultFormState.value.indicators.splice(index, 1);
  }
};

const submitResults = async () => {
  if (!currentServiceOrderForResult.value?.id) return;
  const needsIndicators = resultNeedsIndicators.value;
  const indicators = needsIndicators ? resultFormState.value.indicators : [];

  if (needsIndicators && indicators.length === 0) {
    showToast('error', 'Vui lòng nhập ít nhất một chỉ số kết quả.');
    return;
  }

  if (needsIndicators) {
    // Validate required indicators
    const requiredIndicatorIds = new Set(
      indicatorMappings.value.filter((m) => m.required).map((m) => m.indicatorTemplate.id),
    );
    const providedIndicatorIds = new Set(indicators.map((i) => i.indicatorId));
    const missingRequired = Array.from(requiredIndicatorIds).filter((id) => !providedIndicatorIds.has(id));
    if (missingRequired.length > 0) {
      const missingNames = missingRequired
        .map((id) => indicatorMappings.value.find((m) => m.indicatorTemplate.id === id)?.indicatorTemplate.name)
        .filter(Boolean)
        .join(', ');
      showToast('error', `Thiếu kết quả cho các chỉ số bắt buộc: ${missingNames}`);
      return;
    }

    // Validate values
    for (const item of indicators) {
      if (!item.value || item.value <= 0) {
        showToast('error', 'Vui lòng nhập giá trị hợp lệ cho tất cả các chỉ số.');
        return;
      }
    }
  }

  resultSubmitting.value = true;
  try {
    const payload: ServiceOrderResultPayload = {
      ...resultFormState.value,
      indicators,
    };
    await recordServiceOrderResults(currentServiceOrderForResult.value.id, payload);
    showToast('success', 'Đã lưu kết quả xét nghiệm.');
    resultModalOpen.value = false;
    await loadServiceOrders();
  } catch (err: any) {
    const errorMessage = err?.response?.data?.message ?? err?.message ?? 'Không thể lưu kết quả xét nghiệm.';
    showToast('error', errorMessage);
  } finally {
    resultSubmitting.value = false;
  }
};

const getEvaluationBadgeClass = (evaluation?: string) => {
  switch (evaluation) {
    case 'NORMAL':
      return 'bg-emerald-100 text-emerald-800 border-emerald-200';
    case 'LOW':
      return 'bg-amber-100 text-amber-800 border-amber-200';
    case 'HIGH':
      return 'bg-rose-100 text-rose-800 border-rose-200';
    default:
      return 'bg-slate-100 text-slate-800 border-slate-200';
  }
};

const getEvaluationLabel = (evaluation?: string) => {
  switch (evaluation) {
    case 'NORMAL':
      return 'Bình thường';
    case 'LOW':
      return 'Thấp';
    case 'HIGH':
      return 'Cao';
    case 'UNKNOWN':
      return 'Không xác định';
    default:
      return evaluation ?? 'N/A';
  }
};

const openPrescriptionModal = async () => {
  prescriptionModalOpen.value = true;
  prescriptionFormState.value = {
    visitId: visitId.value,
    prescribedById: null,
    issuedAt: new Date().toISOString(),
    notes: null,
    items: [],
  };
  medicationSearchTerm.value = '';
  loadingMedications.value = true;
  try {
    availableMedications.value = await fetchMedications();
  } catch (err) {
    showToast('error', 'Không thể tải danh sách thuốc.');
  } finally {
    loadingMedications.value = false;
  }
};

const openEditPrescriptionModal = async () => {
  if (!currentPrescription.value) return;
  prescriptionModalOpen.value = true;
  prescriptionFormState.value = {
    visitId: visitId.value,
    prescribedById: currentPrescription.value.prescribedBy?.id ?? null,
    issuedAt: currentPrescription.value.issuedAt ?? new Date().toISOString(),
    notes: currentPrescription.value.notes ?? null,
    items:
      currentPrescription.value.items?.map((item) => ({
        medicationId: item.medication?.id ?? null,
        medicationName: item.medicationName ?? null,
        quantity: item.quantity ?? 1,
        dosage: item.dosage ?? '',
        frequency: item.frequency ?? '',
        duration: item.duration ?? null,
        instruction: item.instruction ?? null,
      })) ?? [],
  };
  medicationSearchTerm.value = '';
  loadingMedications.value = true;
  try {
    availableMedications.value = await fetchMedications();
  } catch (err) {
    showToast('error', 'Không thể tải danh sách thuốc.');
  } finally {
    loadingMedications.value = false;
  }
};

const addPrescriptionItem = () => {
  prescriptionFormState.value.items.push({
    medicationId: null,
    medicationName: null,
    quantity: 1,
    dosage: '',
    frequency: '',
    duration: null,
    instruction: null,
  });
};

const removePrescriptionItem = (index: number) => {
  prescriptionFormState.value.items.splice(index, 1);
};

const submitPrescription = async () => {
  if (prescriptionFormState.value.items.length === 0) {
    showToast('error', 'Vui lòng thêm ít nhất một thuốc vào đơn.');
    return;
  }

  // Validate items
  for (const item of prescriptionFormState.value.items) {
    if (!item.medicationId && !item.medicationName) {
      showToast('error', 'Vui lòng chọn thuốc hoặc nhập tên thuốc cho tất cả các mục.');
      return;
    }
    if (!item.dosage || !item.frequency) {
      showToast('error', 'Vui lòng nhập đầy đủ liều dùng và tần suất cho tất cả các mục.');
      return;
    }
  }

  try {
    if (currentPrescription.value) {
      // Update existing prescription
      const updatePayload: PrescriptionUpdatePayload = {
        prescribedById: prescriptionFormState.value.prescribedById,
        issuedAt: prescriptionFormState.value.issuedAt,
        notes: prescriptionFormState.value.notes,
        items: prescriptionFormState.value.items,
      };
      await updatePrescription(currentPrescription.value.id, updatePayload);
      showToast('success', 'Đã cập nhật đơn thuốc.');
    } else {
      // Create new prescription
      await createPrescription(prescriptionFormState.value);
      showToast('success', 'Đã tạo đơn thuốc.');
    }
    prescriptionModalOpen.value = false;
    await loadPrescriptions();
  } catch (err: any) {
    const errorMessage = err?.response?.data?.message ?? err?.message ?? 'Không thể lưu đơn thuốc.';
    showToast('error', errorMessage);
  }
};

const getStatusLabel = (status?: string) => {
  switch (status) {
    case 'OPEN':
      return 'Đang khám';
    case 'COMPLETED':
      return 'Hoàn thành';
    case 'CANCELLED':
      return 'Đã hủy';
    default:
      return status ?? 'N/A';
  }
};

const getStatusBadgeClass = (status?: string) => {
  switch (status) {
    case 'OPEN':
      return 'bg-blue-100 text-blue-800 border-blue-200';
    case 'COMPLETED':
      return 'bg-emerald-100 text-emerald-800 border-emerald-200';
    case 'CANCELLED':
      return 'bg-rose-100 text-rose-800 border-rose-200';
    default:
      return 'bg-slate-100 text-slate-800 border-slate-200';
  }
};

const getServiceOrderStatusLabel = (status?: string) => {
  switch (status) {
    case 'PENDING':
      return 'Chờ thực hiện';
    case 'SCHEDULED':
      return 'Đã lên lịch';
    case 'IN_PROGRESS':
      return 'Đang thực hiện';
    case 'COMPLETED_WITH_RESULT':
      return 'Hoàn thành có kết quả';
    case 'COMPLETED':
      return 'Hoàn thành';
    case 'CANCELLED':
      return 'Đã hủy';
    default:
      return status ?? 'N/A';
  }
};

const formatDate = (dateString?: string | null) => {
  if (!dateString) return 'N/A';
  try {
    const date = new Date(dateString);
    return date.toLocaleString('vi-VN', {
      day: '2-digit',
      month: '2-digit',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
    });
  } catch {
    return dateString;
  }
};

const calculateAge = (dateOfBirth?: string | null) => {
  if (!dateOfBirth) return null;
  try {
    const birth = new Date(dateOfBirth);
    const today = new Date();
    let age = today.getFullYear() - birth.getFullYear();
    const monthDiff = today.getMonth() - birth.getMonth();
    if (monthDiff < 0 || (monthDiff === 0 && today.getDate() < birth.getDate())) {
      age--;
    }
    return age;
  } catch {
    return null;
  }
};

watch(
  () => route.params.id,
  (newId) => {
    if (newId) {
      void loadVisit();
    }
  },
  { immediate: true },
);

onBeforeUnmount(() => {
  if (serviceSearchTimer) {
    clearTimeout(serviceSearchTimer);
  }
  if (diseaseSearchTimer) {
    clearTimeout(diseaseSearchTimer);
  }
});

onMounted(() => {
  if (visitId.value) {
    void loadVisit();
  }
});
</script>

<template>
  <div class="relative min-h-screen bg-gradient-to-br from-emerald-50 via-white to-white">
    <AdminHeader :user-name="userName" />

    <main class="mx-auto w-full max-w-7xl px-4 pb-20 pt-10 sm:px-6 lg:px-8">
      <section>
        <div class="flex flex-col gap-4 border-b border-emerald-100 pb-6 md:flex-row md:items-center md:justify-between">
          <div class="flex items-center gap-4">
            <button
              type="button"
              @click="router.push('/dashboard/visits')"
              class="inline-flex items-center gap-2 rounded-full border border-emerald-200 bg-white px-4 py-2 text-xs font-semibold uppercase tracking-wide text-emerald-600 shadow-sm transition hover:border-emerald-300 hover:bg-emerald-50"
            >
              <svg xmlns="http://www.w3.org/2000/svg" class="h-3.5 w-3.5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8">
                <path stroke-linecap="round" stroke-linejoin="round" d="m15 18-6-6 6-6" />
              </svg>
              Quay lại
            </button>
            <div>
              <p class="text-xs font-semibold uppercase tracking-[0.35em] text-emerald-500">Chi tiết lượt khám</p>
              <h1 class="mt-2 text-2xl font-semibold text-slate-900">Lượt khám #{{ visitId }}</h1>
              <p class="mt-1 text-sm text-slate-600">Quản lý toàn bộ quy trình khám và điều trị</p>
            </div>
          </div>
        </div>

        <!-- Loading State -->
        <div v-if="loading" class="mt-6 flex items-center justify-center p-12">
          <div class="text-center">
            <div class="mx-auto h-8 w-8 animate-spin rounded-full border-4 border-emerald-500 border-t-transparent"></div>
            <p class="mt-4 text-sm text-slate-600">Đang tải...</p>
          </div>
        </div>

        <!-- Content -->
        <div v-else-if="visit" class="mt-6 space-y-6">
          <!-- Khu vực 1: Thông tin Hành chính -->
          <div class="rounded-2xl border border-emerald-100 bg-white/90 p-6 shadow-sm">
            <div class="mb-4 flex items-center justify-between">
              <h2 class="text-lg font-semibold text-slate-900">Thông tin Hành chính</h2>
              <button
                type="button"
                @click="
                  newStatus = visit.status ?? '';
                  statusUpdateModalOpen = true;
                "
                class="inline-flex items-center gap-2 rounded-full border border-blue-200 bg-blue-600 px-4 py-2 text-xs font-semibold uppercase tracking-wide text-white shadow-sm transition hover:bg-blue-700"
              >
                Cập nhật trạng thái
              </button>
            </div>

            
          <div class="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
            <div>
              <p class="text-xs font-semibold uppercase tracking-wide text-slate-400">Bệnh nhân</p>
              <p class="mt-1 text-sm font-semibold text-slate-900">
                {{ visit.patient?.fullName ?? 'N/A' }}
              </p>
              <p class="text-xs text-slate-600">{{ visit.patient?.code ?? '' }}</p>
            </div>

            <div>
              <p class="text-xs font-semibold uppercase tracking-wide text-slate-400">Tuổi / Giới tính</p>
              <p class="mt-1 text-sm text-slate-900">
                <span v-if="calculateAge(visit.patient?.dateOfBirth)">{{ calculateAge(visit.patient?.dateOfBirth) }} tuổi</span>
                <span v-else>N/A</span>
                <span v-if="visit.patient?.gender"> / {{ visit.patient.gender }}</span>
              </p>
            </div>

            <div>
              <p class="text-xs font-semibold uppercase tracking-wide text-slate-400">Bác sĩ phụ trách</p>
              <p class="mt-1 text-sm text-slate-900">
                {{ visit.primaryAppointment?.doctor?.account?.fullName ?? 'N/A' }}
              </p>
            </div>

            <div>
              <p class="text-xs font-semibold uppercase tracking-wide text-slate-400">Phòng khám</p>
              <p class="mt-1 text-sm text-slate-900">
                {{ visit.primaryAppointment?.clinicRoom?.name ?? 'N/A' }}
              </p>
            </div>

            <div>
              <p class="text-xs font-semibold uppercase tracking-wide text-slate-400">Thời gian khám</p>
              <p class="mt-1 text-sm text-slate-900">
                {{ formatDate(visit.primaryAppointment?.scheduledAt) }}
              </p>
            </div>

            <div>
              <p class="text-xs font-semibold uppercase tracking-wide text-slate-400">Trạng thái</p>
              <p class="mt-1">
                <span
                  :class="getStatusBadgeClass(visit.status)"
                  class="inline-flex items-center rounded-full border px-2.5 py-0.5 text-xs font-semibold"
                >
                  {{ getStatusLabel(visit.status) }}
                </span>
              </p>
            </div>
          </div>
        </div>

          <!-- Khu vực 2: Khám lâm sàng & Chẩn đoán -->
          <div class="rounded-2xl border border-emerald-100 bg-white/90 p-6 shadow-sm">
            <div class="mb-4 flex items-center justify-between">
              <h2 class="text-lg font-semibold text-slate-900">Khám lâm sàng & Chẩn đoán</h2>
              <button
                type="button"
                @click="openEditClinicalModal"
                class="inline-flex items-center gap-2 rounded-full border border-emerald-200 bg-emerald-600 px-4 py-2 text-xs font-semibold uppercase tracking-wide text-white shadow-sm transition hover:bg-emerald-700"
              >
                Chỉnh sửa
              </button>
            </div>

          <div class="space-y-4">
            <div>
              <p class="text-xs font-semibold uppercase tracking-wide text-slate-400">Lý do khám</p>
              <p class="mt-1 text-sm text-slate-900">
                {{ visit.primaryAppointment?.reason ?? 'N/A' }}
              </p>
            </div>

            <div>
              <p class="text-xs font-semibold uppercase tracking-wide text-slate-400">Chẩn đoán tạm thời</p>
              <p class="mt-1 text-sm text-slate-900 whitespace-pre-wrap">
                {{ visit.provisionalDiagnosis || 'Chưa có' }}
              </p>
            </div>

            <div>
              <p class="text-xs font-semibold uppercase tracking-wide text-slate-400">Chẩn đoán chính</p>
              <div class="mt-1 flex flex-wrap gap-2">
                <template v-if="visit.diseases && visit.diseases.length">
                  <span
                    v-for="d in visit.diseases"
                    :key="d.id"
                    class="inline-flex items-center gap-1 rounded-full bg-emerald-50 px-3 py-1 text-xs font-semibold text-emerald-700 border border-emerald-100"
                  >
                    <span class="font-bold">{{ d.code }}</span>
                    <span class="text-slate-500">-</span>
                    <span>{{ d.name }}</span>
                  </span>
                </template>
                <span v-else class="text-sm text-slate-700">Chưa xác định</span>
              </div>
            </div>

            <div>
              <div class="mb-2 flex items-center justify-between">
                <p class="text-xs font-semibold uppercase tracking-wide text-slate-400">Dịch vụ khám lâm sàng</p>
                <button
                  type="button"
                  @click="openEditClinicalModal"
                  class="inline-flex items-center gap-1 rounded-full border border-emerald-200 bg-white px-3 py-1 text-[11px] font-semibold uppercase tracking-wide text-emerald-600 shadow-sm transition hover:bg-emerald-50"
                >
                  Sửa dịch vụ
                </button>
              </div>
              <div v-if="clinicalServiceOrders.length === 0" class="text-sm text-slate-700">
                Chưa có dịch vụ khám lâm sàng.
              </div>
              <div v-else class="space-y-3">
                <div
                  v-for="order in clinicalServiceOrders"
                  :key="order.id"
                  class="rounded-xl border border-emerald-100 bg-emerald-50/50 p-3"
                >
                  <div class="flex items-start justify-between gap-3">
                    <div>
                      <div class="text-sm font-semibold text-slate-900">
                        {{ order.medicalService?.name ?? 'N/A' }}
                      </div>
                      <div class="text-xs text-slate-600">
                        Mã: {{ order.medicalService?.code ?? 'N/A' }}
                      </div>
                      <div class="text-xs text-slate-600 mt-1">
                        Bác sĩ phụ trách: {{ order.assignedDoctor?.account?.fullName ?? 'N/A' }}
                      </div>
                      <div v-if="order.note" class="mt-1 text-sm text-slate-700">
                        Ghi chú: {{ order.note }}
                      </div>
                    </div>
                    <span
                      class="inline-flex items-center rounded-full border px-2.5 py-0.5 text-xs font-semibold"
                      :class="
                        order.status === 'COMPLETED_WITH_RESULT' || order.status === 'COMPLETED'
                          ? 'bg-emerald-100 text-emerald-800 border-emerald-200'
                          : 'bg-amber-100 text-amber-800 border-amber-200'
                      "
                    >
                      {{ getServiceOrderStatusLabel(order.status) }}
                    </span>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>

          <!-- Khu vực 3: Chỉ định Dịch vụ Cận lâm sàng (CLS) -->
          <div class="rounded-2xl border border-emerald-100 bg-white/90 p-6 shadow-sm">
            <div class="mb-4 flex items-center justify-between">
              <h2 class="text-lg font-semibold text-slate-900">Chỉ định Dịch vụ Cận lâm sàng</h2>
              <button
                type="button"
                @click="openServiceOrderModal"
                class="inline-flex items-center gap-2 rounded-full border border-emerald-200 bg-emerald-600 px-4 py-2 text-xs font-semibold uppercase tracking-wide text-white shadow-sm transition hover:bg-emerald-700"
              >
                <svg xmlns="http://www.w3.org/2000/svg" class="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.8">
                  <path stroke-linecap="round" stroke-linejoin="round" d="M12 4.5v15m7.5-7.5h-15" />
                </svg>
                Thêm chỉ định CLS
              </button>
            </div>

          <div v-if="loadingServiceOrders" class="p-4 text-center text-sm text-slate-600">
            Đang tải...
          </div>

          <div v-else-if="serviceOrders.length === 0" class="p-4 text-center text-sm text-slate-600">
            Chưa có chỉ định CLS nào.
          </div>

            <div v-else class="space-y-4">
              <div
                v-for="order in clsServiceOrders"
                :key="order.id"
                class="rounded-xl border border-emerald-100 bg-emerald-50/40 p-4"
              >
              <div class="flex items-start justify-between">
                <div class="flex-1">
                  <div class="font-semibold text-slate-900">{{ order.medicalService?.name ?? 'N/A' }}</div>
                  <div class="mt-1 text-xs text-slate-600">
                    Mã: {{ order.medicalService?.code ?? 'N/A' }}
                  </div>
                  <div class="mt-1 text-xs text-slate-600">
                    Bác sĩ phụ trách: {{ order.assignedDoctor?.account?.fullName ?? 'N/A' }}
                  </div>
                  <div class="mt-1">
                    <span
                      class="inline-flex items-center rounded-full border px-2 py-0.5 text-xs font-semibold"
                      :class="
                        order.status === 'COMPLETED_WITH_RESULT'
                          ? 'bg-emerald-100 text-emerald-800 border-emerald-200'
                          : order.status === 'COMPLETED'
                            ? 'bg-blue-100 text-blue-800 border-blue-200'
                            : 'bg-amber-100 text-amber-800 border-amber-200'
                      "
                    >
                      {{ getServiceOrderStatusLabel(order.status) }}
                    </span>
                  </div>
                  <div v-if="order.note" class="mt-2 text-sm text-slate-600">
                    Ghi chú: {{ order.note }}
                  </div>
                  <div v-if="order.performedBy || order.performedAt" class="mt-1 text-xs text-slate-600">
                    <span v-if="order.performedBy">Thực hiện: {{ order.performedBy.account?.fullName ?? 'N/A' }}</span>
                    <span v-if="order.performedAt">
                      <span v-if="order.performedBy"> • </span>{{ formatDate(order.performedAt) }}
                    </span>
                  </div>
                  <div v-if="order.resultNote" class="mt-2 text-sm text-slate-700">
                    Kết luận: {{ order.resultNote }}
                  </div>
                </div>
                <button
                  v-if="order.status !== 'CANCELLED'"
                  type="button"
                  @click="openResultModal(order)"
                  class="ml-4 inline-flex items-center gap-2 rounded-full border border-emerald-200 bg-emerald-600 px-4 py-2 text-xs font-semibold uppercase tracking-wide text-white shadow-sm transition hover:bg-emerald-700"
                >
                  <svg xmlns="http://www.w3.org/2000/svg" class="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.8">
                    <path stroke-linecap="round" stroke-linejoin="round" d="M9 12h3.75M9 15h3.75M9 18h3.75m3 .75H18a2.25 2.25 0 0 0 2.25-2.25V6.108c0-1.135-.845-2.098-1.976-2.192a48.424 48.424 0 0 0-1.123-.08m-5.801 0c-.065.21-.1.433-.1.664 0 .414.336.75.75.75h4.5a.75.75 0 0 0 .75-.75 2.25 2.25 0 0 0-.1-.664m-5.8 0A2.251 2.251 0 0 1 13.5 2.25H15c1.012 0 1.867.668 2.15 1.586m-5.8 0c-.376.023-.75.05-1.124.08C9.095 4.01 8.25 4.973 8.25 6.108V8.25m0 0H4.875c-.621 0-1.125.504-1.125 1.125v11.25c0 .621.504 1.125 1.125 1.125h9.75c.621 0 1.125-.504 1.125-1.125V9.375c0-.621-.504-1.125-1.125-1.125H8.25ZM6.75 12h.008v.008H6.75V12Zm0 3h.008v.008H6.75V15Zm0 3h.008v.008H6.75V18Z" />
                  </svg>
                  {{ order.status === 'COMPLETED_WITH_RESULT' ? 'Sửa kết quả' : 'Nhập kết quả' }}
                </button>
              </div>
            </div>
          </div>
        </div>

          <!-- Khu vực 4: Kết quả Cận lâm sàng (CLS) -->
          <div class="rounded-2xl border border-emerald-100 bg-white/90 p-6 shadow-sm">
            <h2 class="mb-4 text-lg font-semibold text-slate-900">Kết quả Cận lâm sàng</h2>

          <div v-if="loadingResults" class="p-4 text-center text-sm text-slate-600">
            Đang tải...
          </div>

          <div v-else-if="serviceOrderResults.size === 0" class="p-4 text-center text-sm text-slate-600">
            Chưa có kết quả CLS nào.
          </div>

          <div v-else class="space-y-6">
            <div
              v-for="order in clsServiceOrders.filter((o) => serviceOrderResults.has(o.id ?? 0))"
              :key="order.id"
            >
              <h3 class="mb-2 text-sm font-semibold text-slate-900">
                {{ order.medicalService?.name ?? 'N/A' }}
              </h3>
              <div class="overflow-x-auto">
                <table class="w-full text-sm">
                  <thead class="bg-slate-50">
                    <tr>
                      <th class="px-4 py-2 text-left text-xs font-semibold uppercase text-slate-600">
                        Chỉ số
                      </th>
                      <th class="px-4 py-2 text-left text-xs font-semibold uppercase text-slate-600">
                        Kết quả
                      </th>
                      <th class="px-4 py-2 text-left text-xs font-semibold uppercase text-slate-600">
                        Đơn vị
                      </th>
                      <th class="px-4 py-2 text-left text-xs font-semibold uppercase text-slate-600">
                        Ngưỡng tham chiếu
                      </th>
                    </tr>
                  </thead>
                  <tbody class="divide-y divide-slate-200">
                    <tr
                      v-for="result in serviceOrderResults.get(order.id ?? 0) ?? []"
                      :key="result.id"
                    >
                      <td class="px-4 py-2 font-medium text-slate-900">
                        {{ result.indicatorNameSnapshot ?? result.indicatorTemplate?.name ?? 'N/A' }}
                      </td>
                      <td class="px-4 py-2">
                        <div class="flex items-center gap-2">
                          <span class="text-slate-900">{{ result.measuredValue ?? 'N/A' }}</span>
                          <span
                            v-if="result.evaluation"
                            :class="getEvaluationBadgeClass(result.evaluation)"
                            class="inline-flex items-center rounded-full border px-2 py-0.5 text-xs font-semibold"
                          >
                            {{ getEvaluationLabel(result.evaluation) }}
                          </span>
                        </div>
                      </td>
                      <td class="px-4 py-2 text-slate-600">
                        {{ result.unitSnapshot ?? result.indicatorTemplate?.unit ?? 'N/A' }}
                      </td>
                      <td class="px-4 py-2 text-slate-600">
                        <span
                          v-if="
                            result.indicatorTemplate &&
                            result.indicatorTemplate.normalMin !== null &&
                            result.indicatorTemplate.normalMax !== null
                          "
                        >
                          {{ result.indicatorTemplate.normalMin }} - {{ result.indicatorTemplate.normalMax }}
                        </span>
                        <span v-else>N/A</span>
                      </td>
                    </tr>
                  </tbody>
                </table>
              </div>
            </div>
          </div>
        </div>

          <!-- Khu vực 5: Đơn thuốc -->
          <div class="rounded-2xl border border-emerald-100 bg-white/90 p-6 shadow-sm">
            <div class="mb-4 flex items-center justify-between">
              <h2 class="text-lg font-semibold text-slate-900">Đơn thuốc</h2>
              <button
                v-if="currentPrescription"
                type="button"
                @click="openEditPrescriptionModal"
                class="inline-flex items-center gap-2 rounded-full border border-emerald-200 bg-emerald-600 px-4 py-2 text-xs font-semibold uppercase tracking-wide text-white shadow-sm transition hover:bg-emerald-700"
              >
                Sửa Đơn thuốc
              </button>
              <button
                v-else
                type="button"
                @click="openPrescriptionModal"
                class="inline-flex items-center gap-2 rounded-full border border-emerald-200 bg-emerald-600 px-4 py-2 text-xs font-semibold uppercase tracking-wide text-white shadow-sm transition hover:bg-emerald-700"
              >
                Tạo Đơn thuốc
              </button>
            </div>

          <div v-if="loadingPrescriptions" class="p-4 text-center text-sm text-slate-600">
            Đang tải...
          </div>

          <div v-else-if="!currentPrescription" class="p-4 text-center text-sm text-slate-600">
            Chưa có đơn thuốc.
          </div>

          <div v-else class="space-y-4">
            <div>
              <p class="text-xs font-semibold uppercase tracking-wide text-slate-400">Ngày kê đơn</p>
              <p class="mt-1 text-sm text-slate-900">
                {{ formatDate(currentPrescription.issuedAt) }}
              </p>
            </div>

            <div v-if="currentPrescription.prescribedBy">
              <p class="text-xs font-semibold uppercase tracking-wide text-slate-400">Bác sĩ kê đơn</p>
              <p class="mt-1 text-sm text-slate-900">
                {{ currentPrescription.prescribedBy.account?.fullName ?? 'N/A' }}
              </p>
            </div>

            <div v-if="currentPrescription.notes">
              <p class="text-xs font-semibold uppercase tracking-wide text-slate-400">Ghi chú</p>
              <p class="mt-1 text-sm text-slate-900 whitespace-pre-wrap">
                {{ currentPrescription.notes }}
              </p>
            </div>

            <div>
              <p class="mb-2 text-xs font-semibold uppercase tracking-wide text-slate-400">Danh sách thuốc</p>
              <div class="overflow-x-auto">
                <table class="w-full text-sm">
                  <thead class="bg-slate-50">
                    <tr>
                      <th class="px-4 py-2 text-left text-xs font-semibold uppercase text-slate-600">
                        Thuốc
                      </th>
                      <th class="px-4 py-2 text-left text-xs font-semibold uppercase text-slate-600">
                        Số lượng
                      </th>
                      <th class="px-4 py-2 text-left text-xs font-semibold uppercase text-slate-600">
                        Liều dùng
                      </th>
                      <th class="px-4 py-2 text-left text-xs font-semibold uppercase text-slate-600">
                        Tần suất
                      </th>
                      <th class="px-4 py-2 text-left text-xs font-semibold uppercase text-slate-600">
                        Hướng dẫn
                      </th>
                    </tr>
                  </thead>
                  <tbody class="divide-y divide-slate-200">
                    <tr
                      v-for="item in currentPrescription.items ?? []"
                      :key="item.id"
                    >
                      <td class="px-4 py-2 font-medium text-slate-900">
                        {{ item.medicationName ?? item.medication?.name ?? 'N/A' }}
                      </td>
                      <td class="px-4 py-2 text-slate-600">{{ item.quantity ?? 'N/A' }}</td>
                      <td class="px-4 py-2 text-slate-600">{{ item.dosage ?? 'N/A' }}</td>
                      <td class="px-4 py-2 text-slate-600">{{ item.frequency ?? 'N/A' }}</td>
                      <td class="px-4 py-2 text-slate-600">{{ item.instruction ?? 'N/A' }}</td>
                    </tr>
                  </tbody>
                </table>
              </div>
            </div>
            </div>
          </div>
        </div>
      </section>
    </main>

    <!-- Status Update Modal -->
    <Transition name="fade">
      <div
        v-if="statusUpdateModalOpen"
        class="fixed inset-0 z-[90] flex items-center justify-center bg-slate-900/50 backdrop-blur-sm px-4"
        @click.self="statusUpdateModalOpen = false"
      >
        <div class="w-full max-w-md rounded-3xl border border-emerald-100 bg-white p-6 shadow-xl">
          <div class="flex items-start justify-between">
            <div>
              <p class="text-xs font-semibold uppercase tracking-[0.35em] text-emerald-500">Cập nhật trạng thái</p>
              <h2 class="mt-1 text-xl font-semibold text-slate-900">Cập nhật Trạng thái</h2>
            </div>
            <button
              type="button"
              class="flex h-9 w-9 items-center justify-center rounded-full border border-slate-200 text-slate-500 transition hover:border-slate-300 hover:text-slate-700"
              @click="statusUpdateModalOpen = false"
            >
              <svg xmlns="http://www.w3.org/2000/svg" class="h-4.5 w-4.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.8">
                <path stroke-linecap="round" stroke-linejoin="round" d="m16 8-8 8m0-8 8 8" />
              </svg>
            </button>
          </div>
          <div class="mt-6 space-y-4">
            <div class="space-y-1.5">
              <label class="text-xs font-semibold uppercase tracking-wide text-slate-500">Trạng thái mới</label>
              <select
                v-model="newStatus"
                class="w-full rounded-xl border border-slate-200 px-4 py-2 text-sm text-slate-700 shadow-sm transition focus:border-emerald-400 focus:outline-none focus:ring-4 focus:ring-emerald-100/80"
              >
                <option value="OPEN">Đang khám</option>
                <option value="COMPLETED">Hoàn thành</option>
                <option value="CANCELLED">Đã hủy</option>
              </select>
            </div>
          </div>
          <div class="mt-6 flex items-center justify-end gap-3 border-t border-slate-200 pt-4">
            <button
              type="button"
              @click="statusUpdateModalOpen = false"
              class="inline-flex items-center gap-2 rounded-full border border-slate-200 bg-white px-5 py-2 text-xs font-semibold uppercase tracking-wide text-slate-600 shadow-sm transition hover:border-slate-300 hover:bg-slate-50"
            >
              Hủy
            </button>
            <button
              type="button"
              @click="updateStatus"
              class="inline-flex items-center gap-2 rounded-full border border-emerald-200 bg-emerald-600 px-5 py-2 text-xs font-semibold uppercase tracking-wide text-white shadow-sm transition hover:bg-emerald-700"
            >
              Cập nhật
            </button>
          </div>
        </div>
      </div>
    </Transition>

    <!-- Clinical Info Edit Modal -->
    <Transition name="fade">
      <div
        v-if="editClinicalModalOpen"
        class="fixed inset-0 z-[90] flex items-center justify-center bg-slate-900/50 backdrop-blur-sm px-4"
        @click.self="editClinicalModalOpen = false"
      >
        <div class="w-full max-w-xl max-h-[85vh] overflow-y-auto rounded-3xl border border-emerald-100 bg-white p-6 shadow-xl">
          <div class="flex items-start justify-between">
            <div>
              <p class="text-xs font-semibold uppercase tracking-[0.35em] text-emerald-500">Chỉnh sửa thông tin</p>
              <h2 class="mt-1 text-xl font-semibold text-slate-900">Chỉnh sửa Thông tin Lâm sàng</h2>
            </div>
            <button
              type="button"
              class="flex h-9 w-9 items-center justify-center rounded-full border border-slate-200 text-slate-500 transition hover:border-slate-300 hover:text-slate-700"
              @click="editClinicalModalOpen = false"
            >
              <svg xmlns="http://www.w3.org/2000/svg" class="h-4.5 w-4.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.8">
                <path stroke-linecap="round" stroke-linejoin="round" d="m16 8-8 8m0-8 8 8" />
              </svg>
            </button>
          </div>
          <div class="mt-4 space-y-3">
            <div class="space-y-1.5">
              <label class="text-xs font-semibold uppercase tracking-wide text-slate-500">Chẩn đoán tạm thời</label>
              <textarea
                v-model="clinicalFormState.provisionalDiagnosis"
                rows="3"
                class="w-full rounded-xl border border-slate-200 px-4 py-2 text-sm text-slate-700 shadow-sm transition focus:border-emerald-400 focus:outline-none focus:ring-4 focus:ring-emerald-100/80"
              />
            </div>
            <div class="space-y-2">
              <div class="flex items-center justify-between">
                <label class="text-xs font-semibold uppercase tracking-wide text-slate-500">Dịch vụ khám lâm sàng</label>
                <span v-if="loadingClinicalServices" class="text-[11px] text-slate-500">Đang tải...</span>
              </div>
              <div class="space-y-2 rounded-xl border border-slate-200 p-3">
                <div v-if="!loadingClinicalServices && availableClinicalServices.length === 0" class="text-sm text-slate-600">
                  Chưa có dịch vụ khám lâm sàng. Vui lòng tạo dịch vụ với "requiresIndicator = 0".
                </div>
                <div v-else class="space-y-2 max-h-52 overflow-y-auto">
                  <label
                    v-for="svc in availableClinicalServices"
                    :key="svc.id"
                    class="flex items-start gap-3 rounded-lg px-2 py-1.5 hover:bg-slate-50"
                  >
                    <input
                      v-model="selectedClinicalServiceIds"
                      :value="svc.id"
                      type="checkbox"
                      class="mt-1 h-4 w-4 text-emerald-600 focus:ring-emerald-500"
                    />
                    <div>
                      <div class="text-sm font-semibold text-slate-900">
                        {{ svc.name }}
                      </div>
                      <div class="text-xs text-slate-600">
                        Mã: {{ svc.code }}
                      </div>
                    </div>
                  </label>
                </div>
              </div>
              <p class="text-xs text-slate-500">
                Chọn dịch vụ khám lâm sàng, ghi chú sẽ dùng chẩn đoán tạm thời/ghi chú lâm sàng làm note phiếu.
              </p>
            </div>
            <div class="space-y-1.5">
              <div class="flex items-center justify-between">
                <label class="text-xs font-semibold uppercase tracking-wide text-slate-500">Chẩn đoán chính</label>
                <span v-if="loadingDiseases" class="text-[11px] text-slate-500">Đang tải danh mục...</span>
              </div>
              <div class="space-y-2">
                <div class="relative">
                  <input
                    v-model="diseaseSearchTerm"
                    type="text"
                    placeholder="Nhập từ khóa (tối thiểu 2 ký tự) để tìm bệnh..."
                    class="w-full rounded-xl border border-slate-200 px-4 py-2 text-sm text-slate-700 shadow-sm transition focus:border-emerald-400 focus:outline-none focus:ring-4 focus:ring-emerald-100/80"
                  />
                  <div
                    v-if="diseaseOptions.length > 0"
                    class="absolute z-10 mt-2 max-h-52 w-full overflow-y-auto rounded-xl border border-slate-200 bg-white shadow-lg"
                  >
                    <button
                      v-for="option in diseaseOptions"
                      :key="option.id"
                      type="button"
                      class="flex w-full items-start gap-2 px-4 py-2 text-left text-sm text-slate-700 hover:bg-emerald-50"
                      @click="addDisease(option)"
                    >
                      <span class="font-semibold text-emerald-700">{{ option.code }}</span>
                      <span class="text-slate-500">-</span>
                      <span>{{ option.name }}</span>
                    </button>
                  </div>
                </div>
                <div class="flex flex-wrap gap-2">
                  <span
                    v-for="d in selectedDiseases"
                    :key="d.id"
                    class="inline-flex items-center gap-2 rounded-full bg-emerald-50 px-3 py-1 text-xs font-semibold text-emerald-700 border border-emerald-100"
                  >
                    <span class="font-bold">{{ d.code }}</span>
                    <span class="text-slate-500">-</span>
                    <span>{{ d.name }}</span>
                    <button
                      type="button"
                      class="ml-1 text-slate-500 hover:text-rose-600"
                      @click="removeDisease(d.id)"
                    >
                      ✕
                    </button>
                  </span>
                  <span v-if="selectedDiseases.length === 0" class="text-xs text-slate-500">Chưa chọn bệnh nào.</span>
                </div>
                <p class="text-xs text-slate-500">Nhập từ khóa để tìm và chọn nhiều bệnh.</p>
              </div>
            </div>
          </div>
          <div class="mt-6 flex items-center justify-end gap-3 border-t border-slate-200 pt-4">
            <button
              type="button"
              @click="editClinicalModalOpen = false"
              class="inline-flex items-center gap-2 rounded-full border border-slate-200 bg-white px-5 py-2 text-xs font-semibold uppercase tracking-wide text-slate-600 shadow-sm transition hover:border-slate-300 hover:bg-slate-50"
            >
              Hủy
            </button>
            <button
              type="button"
              @click="saveClinicalInfo"
              :disabled="clinicalSubmitting"
              class="inline-flex items-center gap-2 rounded-full border border-emerald-200 bg-emerald-600 px-5 py-2 text-xs font-semibold uppercase tracking-wide text-white shadow-sm transition hover:bg-emerald-700 disabled:cursor-not-allowed disabled:opacity-60"
            >
              <span v-if="clinicalSubmitting">Đang lưu...</span>
              <span v-else>Lưu</span>
            </button>
          </div>
        </div>
      </div>
    </Transition>

    <!-- Service Order Modal -->
    <Transition name="fade">
      <div
        v-if="serviceOrderModalOpen"
        class="fixed inset-0 z-[90] flex items-center justify-center bg-slate-900/50 backdrop-blur-sm px-4"
        @click.self="serviceOrderModalOpen = false"
      >
        <div class="w-full max-w-4xl max-h-[90vh] overflow-y-auto rounded-3xl border border-emerald-100 bg-white p-6 shadow-xl">
          <div class="flex items-start justify-between">
            <div>
              <p class="text-xs font-semibold uppercase tracking-[0.35em] text-emerald-500">Thêm chỉ định</p>
              <h2 class="mt-1 text-xl font-semibold text-slate-900">Thêm Chỉ định CLS</h2>
            </div>
            <button
              type="button"
              class="flex h-9 w-9 items-center justify-center rounded-full border border-slate-200 text-slate-500 transition hover:border-slate-300 hover:text-slate-700"
              @click="serviceOrderModalOpen = false"
            >
              <svg xmlns="http://www.w3.org/2000/svg" class="h-4.5 w-4.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.8">
                <path stroke-linecap="round" stroke-linejoin="round" d="m16 8-8 8m0-8 8 8" />
              </svg>
            </button>
          </div>
          <div class="mt-6 space-y-4">
            <!-- Service Search -->
            <div class="space-y-1.5">
              <label class="text-xs font-semibold uppercase tracking-wide text-slate-500">Tìm kiếm dịch vụ</label>
              <input
                v-model="serviceSearchTerm"
                type="text"
                placeholder="Tìm theo tên, mã, danh mục..."
                class="w-full rounded-xl border border-slate-200 px-4 py-2 text-sm text-slate-700 shadow-sm transition focus:border-emerald-400 focus:outline-none focus:ring-4 focus:ring-emerald-100/80"
              />
            </div>

            <!-- Service List -->
            <div class="space-y-3">
              <label class="text-xs font-semibold uppercase tracking-wide text-slate-500">Danh sách dịch vụ</label>
              <div class="max-h-64 overflow-y-auto rounded-xl border border-slate-200">
                <div v-if="loadingServices" class="p-4 text-center text-sm text-slate-600">
                  Đang tải...
                </div>
                <div v-else-if="filteredServices.length === 0" class="p-4 text-center text-sm text-slate-600">
                  Không tìm thấy dịch vụ nào.
                </div>
                <div v-else class="divide-y divide-slate-200">
                  <label
                    v-for="service in filteredServices"
                    :key="service.id"
                    class="flex cursor-pointer items-start gap-3 p-4 hover:bg-slate-50"
                  >
                    <input
                      type="checkbox"
                      :checked="selectedServices.includes(service.id)"
                      @change="toggleServiceSelection(service.id)"
                      class="mt-1 h-4 w-4 rounded text-emerald-600 focus:ring-emerald-500"
                    />
                    <div class="flex-1">
                      <div class="font-semibold text-slate-900">{{ service.name }}</div>
                      <div class="mt-1 text-xs text-slate-600">Mã: {{ service.code }}</div>
                      <div v-if="service.category" class="mt-1 text-xs text-slate-600">
                        Danh mục: {{ service.category }}
                      </div>
                    </div>
                  </label>
                </div>
              </div>
              <div class="flex flex-col gap-2 text-xs text-slate-600 sm:flex-row sm:items-center sm:justify-between">
                <span>{{ servicePaginationLabel }}</span>
                <div class="flex items-center gap-2">
                  <button
                    type="button"
                    class="inline-flex items-center gap-2 rounded-full border border-emerald-200 bg-white px-3.5 py-1.5 text-[11px] font-semibold uppercase tracking-wide text-emerald-600 shadow-sm transition hover:border-emerald-300 hover:bg-emerald-50 disabled:cursor-not-allowed disabled:opacity-60"
                    :disabled="!serviceHasPrevious || loadingServices"
                    @click="prevServicePage"
                  >
                    <svg xmlns="http://www.w3.org/2000/svg" class="h-3 w-3" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6">
                      <path stroke-linecap="round" stroke-linejoin="round" d="m15 18-6-6 6-6" />
                    </svg>
                    Trước
                  </button>
                  <span class="text-sm font-semibold text-slate-700">
                    {{ serviceCurrentPage }} / {{ serviceTotalPages }}
                  </span>
                  <button
                    type="button"
                    class="inline-flex items-center gap-2 rounded-full border border-emerald-200 bg-white px-3.5 py-1.5 text-[11px] font-semibold uppercase tracking-wide text-emerald-600 shadow-sm transition hover:border-emerald-300 hover:bg-emerald-50 disabled:cursor-not-allowed disabled:opacity-60"
                    :disabled="!serviceHasNext || loadingServices"
                    @click="nextServicePage"
                  >
                    Sau
                    <svg xmlns="http://www.w3.org/2000/svg" class="h-3 w-3" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6">
                      <path stroke-linecap="round" stroke-linejoin="round" d="m9 6 6 6-6 6" />
                    </svg>
                  </button>
                </div>
              </div>
            </div>

            <!-- Note -->
            <div class="space-y-1.5">
              <label class="text-xs font-semibold uppercase tracking-wide text-slate-500">Ghi chú</label>
              <textarea
                v-model="serviceOrderNote"
                rows="2"
                placeholder="Nhập ghi chú (nếu có)"
                class="w-full rounded-xl border border-slate-200 px-4 py-2 text-sm text-slate-700 shadow-sm transition focus:border-emerald-400 focus:outline-none focus:ring-4 focus:ring-emerald-100/80"
              />
            </div>
          </div>
          <div class="mt-6 flex items-center justify-end gap-3 border-t border-slate-200 pt-4">
            <button
              type="button"
              @click="serviceOrderModalOpen = false"
              class="inline-flex items-center gap-2 rounded-full border border-slate-200 bg-white px-5 py-2 text-xs font-semibold uppercase tracking-wide text-slate-600 shadow-sm transition hover:border-slate-300 hover:bg-slate-50"
            >
              Hủy
            </button>
            <button
              type="button"
              @click="submitServiceOrders"
              class="inline-flex items-center gap-2 rounded-full border border-emerald-200 bg-emerald-600 px-5 py-2 text-xs font-semibold uppercase tracking-wide text-white shadow-sm transition hover:bg-emerald-700"
            >
              Thêm chỉ định
            </button>
          </div>
        </div>
      </div>
    </Transition>

    <!-- Service Order Result Modal -->
    <Transition name="fade">
      <div
        v-if="resultModalOpen"
        class="fixed inset-0 z-[90] flex items-center justify-center bg-slate-900/50 backdrop-blur-sm px-4"
        @click.self="resultModalOpen = false"
      >
        <div class="w-full max-w-4xl max-h-[90vh] overflow-y-auto rounded-3xl border border-emerald-100 bg-white p-6 shadow-xl">
          <div class="flex items-start justify-between">
            <div>
              <p class="text-xs font-semibold uppercase tracking-[0.35em] text-emerald-500">Nhập kết quả</p>
              <h2 class="mt-1 text-xl font-semibold text-slate-900">
                Nhập Kết quả Xét nghiệm
              </h2>
              <p class="mt-1 text-sm text-slate-600">
                {{ currentServiceOrderForResult?.medicalService?.name ?? 'N/A' }}
              </p>
            </div>
            <button
              type="button"
              class="flex h-9 w-9 items-center justify-center rounded-full border border-slate-200 text-slate-500 transition hover:border-slate-300 hover:text-slate-700"
              @click="resultModalOpen = false"
            >
              <svg xmlns="http://www.w3.org/2000/svg" class="h-4.5 w-4.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.8">
                <path stroke-linecap="round" stroke-linejoin="round" d="m16 8-8 8m0-8 8 8" />
              </svg>
            </button>
          </div>

          <div class="mt-6 space-y-4">
            <div v-if="loadingIndicators" class="p-4 text-center text-sm text-slate-600">
              Đang tải danh sách chỉ số...
            </div>

            <div v-else>
              <template v-if="resultNeedsIndicators">
                <!-- Available Indicators -->
                <div class="mb-4 space-y-2">
                  <h3 class="text-sm font-semibold text-slate-900">Chỉ số có sẵn</h3>
                  <div class="flex flex-wrap gap-2">
                    <button
                      v-for="mapping in indicatorMappings"
                      :key="mapping.id"
                      type="button"
                      @click="addIndicatorToResult(mapping)"
                      :disabled="resultFormState.indicators.some((i) => i.indicatorId === mapping.indicatorTemplate.id)"
                      class="inline-flex items-center gap-1 rounded-full border border-emerald-200 bg-white px-3 py-1.5 text-xs font-semibold text-emerald-600 shadow-sm transition hover:bg-emerald-50 disabled:opacity-50 disabled:cursor-not-allowed"
                    >
                      <svg xmlns="http://www.w3.org/2000/svg" class="h-3.5 w-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.8">
                        <path stroke-linecap="round" stroke-linejoin="round" d="M12 4.5v15m7.5-7.5h-15" />
                      </svg>
                      {{ mapping.indicatorTemplate.name }}
                      <span v-if="mapping.required" class="text-rose-500">*</span>
                    </button>
                  </div>
                </div>

                <!-- Result Form -->
                <div v-if="resultFormState.indicators.length === 0" class="rounded-2xl border border-dashed border-emerald-200 bg-emerald-50/40 p-6 text-center text-sm text-emerald-700">
                  Chưa có chỉ số nào. Nhấn vào các nút ở trên để thêm chỉ số.
                </div>

                <div v-else class="space-y-4">
                  <div
                    v-for="item in resultFormState.indicators"
                    :key="item.indicatorId"
                    class="rounded-xl border border-emerald-100 bg-emerald-50/40 p-4"
                  >
                    <div class="mb-3 flex items-center justify-between">
                      <div>
                        <span class="text-sm font-semibold text-slate-900">
                          {{
                            indicatorMappings.find((m) => m.indicatorTemplate.id === item.indicatorId)
                              ?.indicatorTemplate.name ?? 'N/A'
                          }}
                        </span>
                        <span
                          v-if="
                            indicatorMappings.find((m) => m.indicatorTemplate.id === item.indicatorId)?.required
                          "
                          class="ml-1 text-rose-500"
                        >
                          *
                        </span>
                        <span
                          v-if="
                            indicatorMappings.find((m) => m.indicatorTemplate.id === item.indicatorId)
                              ?.indicatorTemplate.unit
                          "
                          class="ml-2 text-xs text-slate-600"
                        >
                          ({{ indicatorMappings.find((m) => m.indicatorTemplate.id === item.indicatorId)?.indicatorTemplate.unit }})
                        </span>
                      </div>
                      <button
                        type="button"
                        @click="removeIndicatorFromResult(item.indicatorId)"
                        class="flex h-6 w-6 items-center justify-center rounded-full border border-rose-200 text-rose-600 transition hover:border-rose-300 hover:bg-rose-50"
                      >
                        <svg xmlns="http://www.w3.org/2000/svg" class="h-3.5 w-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.8">
                          <path stroke-linecap="round" stroke-linejoin="round" d="m15 9-6 6m0-6 6 6M3 6h18" />
                        </svg>
                      </button>
                    </div>

                    <div class="grid gap-3 sm:grid-cols-2">
                      <div class="space-y-1.5">
                        <label class="text-xs font-semibold uppercase tracking-wide text-slate-500">
                          Giá trị đo <span class="text-rose-500">*</span>
                        </label>
                        <input
                          v-model.number="item.value"
                          type="number"
                          step="0.0001"
                          min="0"
                          placeholder="0"
                          class="w-full rounded-xl border border-slate-200 px-4 py-2 text-sm text-slate-700 shadow-sm transition focus:border-emerald-400 focus:outline-none focus:ring-4 focus:ring-emerald-100/80"
                        />
                        <div
                          v-if="
                            indicatorMappings.find((m) => m.indicatorTemplate.id === item.indicatorId)
                              ?.indicatorTemplate.normalMin !== null &&
                            indicatorMappings.find((m) => m.indicatorTemplate.id === item.indicatorId)
                              ?.indicatorTemplate.normalMax !== null
                          "
                          class="text-xs text-slate-500"
                        >
                          Ngưỡng tham chiếu:
                          {{
                            indicatorMappings.find((m) => m.indicatorTemplate.id === item.indicatorId)
                              ?.indicatorTemplate.normalMin
                          }}
                          -
                          {{
                            indicatorMappings.find((m) => m.indicatorTemplate.id === item.indicatorId)
                              ?.indicatorTemplate.normalMax
                          }}
                        </div>
                      </div>

                      <div class="space-y-1.5">
                        <label class="text-xs font-semibold uppercase tracking-wide text-slate-500">Ghi chú</label>
                        <input
                          v-model="item.note"
                          type="text"
                          placeholder="Ghi chú (nếu có)"
                          class="w-full rounded-xl border border-slate-200 px-4 py-2 text-sm text-slate-700 shadow-sm transition focus:border-emerald-400 focus:outline-none focus:ring-4 focus:ring-emerald-100/80"
                        />
                      </div>
                    </div>
                  </div>
                </div>
              </template>
              <template v-else>
                <div class="rounded-2xl border border-dashed border-emerald-200 bg-emerald-50/40 p-6 text-sm text-emerald-700">
                  Dịch vụ khám lâm sàng không yêu cầu nhập chỉ số. Vui lòng nhập kết luận/kết quả bên dưới.
                </div>
              </template>

              <!-- Overall Conclusion -->
              <div class="mt-4 space-y-1.5">
                <label class="text-xs font-semibold uppercase tracking-wide text-slate-500">Kết luận tổng thể</label>
                <textarea
                  v-model="resultFormState.overallConclusion"
                  rows="3"
                  placeholder="Nhập kết luận tổng thể (nếu có)"
                  class="w-full rounded-xl border border-slate-200 px-4 py-2 text-sm text-slate-700 shadow-sm transition focus:border-emerald-400 focus:outline-none focus:ring-4 focus:ring-emerald-100/80"
                />
              </div>
            </div>
          </div>

          <div class="mt-6 flex items-center justify-end gap-3 border-t border-slate-200 pt-4">
            <button
              type="button"
              @click="resultModalOpen = false"
              class="inline-flex items-center gap-2 rounded-full border border-slate-200 bg-white px-5 py-2 text-xs font-semibold uppercase tracking-wide text-slate-600 shadow-sm transition hover:border-slate-300 hover:bg-slate-50"
            >
              Hủy
            </button>
            <button
              type="button"
              @click="submitResults"
              :disabled="resultSubmitting"
              class="inline-flex items-center gap-2 rounded-full border border-emerald-200 bg-emerald-600 px-5 py-2 text-xs font-semibold uppercase tracking-wide text-white shadow-sm transition hover:bg-emerald-700 disabled:opacity-50"
            >
              <span v-if="resultSubmitting">Đang lưu...</span>
              <span v-else>Lưu kết quả</span>
            </button>
          </div>
        </div>
      </div>
    </Transition>

    <!-- Prescription Modal -->
    <Transition name="fade">
      <div
        v-if="prescriptionModalOpen"
        class="fixed inset-0 z-[90] flex items-center justify-center bg-slate-900/50 backdrop-blur-sm px-4"
        @click.self="prescriptionModalOpen = false"
      >
        <div class="w-full max-w-4xl max-h-[90vh] overflow-y-auto rounded-3xl border border-emerald-100 bg-white p-6 shadow-xl">
          <div class="flex items-start justify-between">
            <div>
              <p class="text-xs font-semibold uppercase tracking-[0.35em] text-emerald-500">
                {{ currentPrescription ? 'Sửa đơn thuốc' : 'Tạo đơn thuốc' }}
              </p>
              <h2 class="mt-1 text-xl font-semibold text-slate-900">
                {{ currentPrescription ? 'Sửa Đơn thuốc' : 'Tạo Đơn thuốc' }}
              </h2>
            </div>
            <button
              type="button"
              class="flex h-9 w-9 items-center justify-center rounded-full border border-slate-200 text-slate-500 transition hover:border-slate-300 hover:text-slate-700"
              @click="prescriptionModalOpen = false"
            >
              <svg xmlns="http://www.w3.org/2000/svg" class="h-4.5 w-4.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.8">
                <path stroke-linecap="round" stroke-linejoin="round" d="m16 8-8 8m0-8 8 8" />
              </svg>
            </button>
          </div>
          <div class="mt-6 space-y-4">
            <!-- Prescription Items -->
            <div class="space-y-4">
              <div class="flex items-center justify-between">
                <h3 class="text-sm font-semibold text-slate-900">Danh sách thuốc</h3>
                <button
                  type="button"
                  @click="addPrescriptionItem"
                  class="inline-flex items-center gap-2 rounded-full border border-emerald-200 bg-emerald-600 px-3 py-1.5 text-xs font-semibold uppercase tracking-wide text-white shadow-sm transition hover:bg-emerald-700"
                >
                  <svg xmlns="http://www.w3.org/2000/svg" class="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.8">
                    <path stroke-linecap="round" stroke-linejoin="round" d="M12 4.5v15m7.5-7.5h-15" />
                  </svg>
                  Thêm thuốc
                </button>
              </div>

              <div
                v-for="(item, index) in prescriptionFormState.items"
                :key="index"
                class="rounded-xl border border-emerald-100 bg-emerald-50/40 p-4"
              >
              <div class="mb-3 flex items-center justify-between">
                <span class="text-sm font-semibold text-slate-900">Thuốc {{ index + 1 }}</span>
                <button
                  type="button"
                  @click="removePrescriptionItem(index)"
                  class="flex h-6 w-6 items-center justify-center rounded-full border border-rose-200 text-rose-600 transition hover:border-rose-300 hover:bg-rose-50"
                >
                  <svg xmlns="http://www.w3.org/2000/svg" class="h-3.5 w-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.8">
                    <path stroke-linecap="round" stroke-linejoin="round" d="m15 9-6 6m0-6 6 6M3 6h18" />
                  </svg>
                </button>
              </div>

              <div class="space-y-3">
                <!-- Medication Search -->
                <div class="space-y-1.5">
                  <label class="text-xs font-semibold uppercase tracking-wide text-slate-500">Thuốc</label>
                  <input
                    v-model="medicationSearchTerm"
                    type="text"
                    placeholder="Tìm kiếm thuốc..."
                    class="w-full rounded-xl border border-slate-200 px-4 py-2 text-sm text-slate-700 shadow-sm transition focus:border-emerald-400 focus:outline-none focus:ring-4 focus:ring-emerald-100/80"
                  />
                  <div
                    v-if="medicationSearchTerm && filteredMedications.length > 0"
                    class="mt-1 max-h-32 overflow-y-auto rounded-xl border border-slate-200 bg-white"
                  >
                    <button
                      v-for="med in filteredMedications.slice(0, 5)"
                      :key="med.id"
                      type="button"
                      @click="
                        item.medicationId = med.id;
                        item.medicationName = med.name;
                        medicationSearchTerm = '';
                      "
                      class="w-full px-3 py-2 text-left text-sm transition hover:bg-emerald-50"
                    >
                      {{ med.name }} ({{ med.batchNo }})
                    </button>
                  </div>
                </div>

                <div class="grid gap-3 sm:grid-cols-2">
                  <div class="space-y-1.5">
                    <label class="text-xs font-semibold uppercase tracking-wide text-slate-500">
                      Tên thuốc (nếu không có trong danh sách)
                    </label>
                    <input
                      v-model="item.medicationName"
                      type="text"
                      placeholder="Nhập tên thuốc"
                      class="w-full rounded-xl border border-slate-200 px-4 py-2 text-sm text-slate-700 shadow-sm transition focus:border-emerald-400 focus:outline-none focus:ring-4 focus:ring-emerald-100/80"
                    />
                  </div>

                  <div class="space-y-1.5">
                    <label class="text-xs font-semibold uppercase tracking-wide text-slate-500">
                      Số lượng <span class="text-rose-500">*</span>
                    </label>
                    <input
                      v-model.number="item.quantity"
                      type="number"
                      min="1"
                      placeholder="1"
                      class="w-full rounded-xl border border-slate-200 px-4 py-2 text-sm text-slate-700 shadow-sm transition focus:border-emerald-400 focus:outline-none focus:ring-4 focus:ring-emerald-100/80"
                    />
                  </div>

                  <div class="space-y-1.5">
                    <label class="text-xs font-semibold uppercase tracking-wide text-slate-500">
                      Liều dùng <span class="text-rose-500">*</span>
                    </label>
                    <input
                      v-model="item.dosage"
                      type="text"
                      placeholder="VD: 1 viên"
                      class="w-full rounded-xl border border-slate-200 px-4 py-2 text-sm text-slate-700 shadow-sm transition focus:border-emerald-400 focus:outline-none focus:ring-4 focus:ring-emerald-100/80"
                    />
                  </div>

                  <div class="space-y-1.5">
                    <label class="text-xs font-semibold uppercase tracking-wide text-slate-500">
                      Tần suất <span class="text-rose-500">*</span>
                    </label>
                    <input
                      v-model="item.frequency"
                      type="text"
                      placeholder="VD: 2 lần/ngày"
                      class="w-full rounded-xl border border-slate-200 px-4 py-2 text-sm text-slate-700 shadow-sm transition focus:border-emerald-400 focus:outline-none focus:ring-4 focus:ring-emerald-100/80"
                    />
                  </div>

                  <div class="space-y-1.5">
                    <label class="text-xs font-semibold uppercase tracking-wide text-slate-500">Thời gian sử dụng</label>
                    <input
                      v-model="item.duration"
                      type="text"
                      placeholder="VD: 7 ngày"
                      class="w-full rounded-xl border border-slate-200 px-4 py-2 text-sm text-slate-700 shadow-sm transition focus:border-emerald-400 focus:outline-none focus:ring-4 focus:ring-emerald-100/80"
                    />
                  </div>

                  <div class="space-y-1.5">
                    <label class="text-xs font-semibold uppercase tracking-wide text-slate-500">Hướng dẫn</label>
                    <input
                      v-model="item.instruction"
                      type="text"
                      placeholder="Hướng dẫn sử dụng"
                      class="w-full rounded-xl border border-slate-200 px-4 py-2 text-sm text-slate-700 shadow-sm transition focus:border-emerald-400 focus:outline-none focus:ring-4 focus:ring-emerald-100/80"
                    />
                  </div>
                </div>
              </div>
            </div>

              <div v-if="prescriptionFormState.items.length === 0" class="rounded-2xl border border-dashed border-emerald-200 bg-emerald-50/40 p-6 text-center text-sm text-emerald-700">
                Chưa có thuốc nào. Nhấn "Thêm thuốc" để bắt đầu.
              </div>
            </div>

            <!-- Notes -->
            <div class="space-y-1.5">
              <label class="text-xs font-semibold uppercase tracking-wide text-slate-500">Ghi chú</label>
              <textarea
                v-model="prescriptionFormState.notes"
                rows="3"
                placeholder="Nhập ghi chú (nếu có)"
                class="w-full rounded-xl border border-slate-200 px-4 py-2 text-sm text-slate-700 shadow-sm transition focus:border-emerald-400 focus:outline-none focus:ring-4 focus:ring-emerald-100/80"
              />
            </div>
          </div>
          <div class="mt-6 flex items-center justify-end gap-3 border-t border-slate-200 pt-4">
            <button
              type="button"
              @click="prescriptionModalOpen = false"
              class="inline-flex items-center gap-2 rounded-full border border-slate-200 bg-white px-5 py-2 text-xs font-semibold uppercase tracking-wide text-slate-600 shadow-sm transition hover:border-slate-300 hover:bg-slate-50"
            >
              Hủy
            </button>
            <button
              type="button"
              @click="submitPrescription"
              class="inline-flex items-center gap-2 rounded-full border border-emerald-200 bg-emerald-600 px-5 py-2 text-xs font-semibold uppercase tracking-wide text-white shadow-sm transition hover:bg-emerald-700"
            >
              {{ currentPrescription ? 'Cập nhật' : 'Tạo đơn thuốc' }}
            </button>
          </div>
        </div>
      </div>
    </Transition>

    <!-- Toast Notification -->
    <Teleport to="body">
      <Transition
        enter-active-class="transition duration-200"
        enter-from-class="translate-y-2 opacity-0"
        enter-to-class="translate-y-0 opacity-100"
        leave-active-class="transition duration-200"
        leave-from-class="translate-y-0 opacity-100"
        leave-to-class="translate-y-2 opacity-0"
      >
        <div
          v-if="toast"
          class="fixed top-6 right-6 z-[100] w-[min(320px,90vw)] rounded-2xl border px-5 py-4 shadow-xl backdrop-blur"
          :class="toastVisuals.container"
        >
          <div class="flex items-start gap-3">
            <span
              class="mt-0.5 flex h-9 w-9 flex-shrink-0 items-center justify-center rounded-full"
              :class="toastVisuals.icon"
            >
              <svg
                v-if="toastVisuals.iconType === 'success'"
                xmlns="http://www.w3.org/2000/svg"
                viewBox="0 0 24 24"
                class="h-5 w-5"
                fill="none"
                stroke="currentColor"
                stroke-width="2"
              >
                <path stroke-linecap="round" stroke-linejoin="round" d="m5 13 4 4L19 7" />
              </svg>
              <svg
                v-else-if="toastVisuals.iconType === 'error'"
                xmlns="http://www.w3.org/2000/svg"
                viewBox="0 0 24 24"
                class="h-5 w-5"
                fill="none"
                stroke="currentColor"
                stroke-width="2"
              >
                <path stroke-linecap="round" stroke-linejoin="round" d="m15 9-6 6m0-6 6 6" />
              </svg>
              <svg
                v-else-if="toastVisuals.iconType === 'warning'"
                xmlns="http://www.w3.org/2000/svg"
                viewBox="0 0 24 24"
                class="h-5 w-5"
                fill="none"
                stroke="currentColor"
                stroke-width="2"
              >
                <path stroke-linecap="round" stroke-linejoin="round" d="M12 9v4m0 4h.01m-6.938 2h13.856a1 1 0 0 0 .894-1.447L12.894 4.553a1 1 0 0 0-1.788 0l-6.918 12.004A1 1 0 0 0 5.062 19Z" />
              </svg>
              <svg
                v-else
                xmlns="http://www.w3.org/2000/svg"
                viewBox="0 0 24 24"
                class="h-5 w-5"
                fill="none"
                stroke="currentColor"
                stroke-width="2"
              >
                <path stroke-linecap="round" stroke-linejoin="round" d="M12 8v4m0 4h.01m0-14a10 10 0 1 0 0 20 10 10 0 0 0 0-20Z" />
              </svg>
            </span>
            <div class="flex-1">
              <p class="text-sm font-semibold">{{ toastVisuals.title }}</p>
              <p class="mt-1 text-sm leading-relaxed">{{ toast.message }}</p>
            </div>
            <button
              type="button"
              class="mt-1 flex h-8 w-8 items-center justify-center rounded-full bg-white/70 text-slate-500 transition hover:bg-white hover:text-slate-700"
              @click="dismissToast"
              aria-label="Đóng thông báo"
            >
              <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" class="h-4 w-4" fill="none" stroke="currentColor" stroke-width="2">
                <path stroke-linecap="round" stroke-linejoin="round" d="m16 8-8 8m0-8 8 8" />
              </svg>
            </button>
          </div>
        </div>
      </Transition>
    </Teleport>
  </div>
</template>

