<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';
import { approveAppointmentRequest, type AppointmentRequest } from '@/services/appointmentRequest.service';
import { fetchDoctors, type Doctor } from '@/services/doctor.service';
import { createPatient, searchPatients, type Patient } from '@/services/patient.service';
import { useToast, type ToastType } from '@/composables/useToast';

type Step = 1 | 2 | 3;
type PatientMode = 'existing' | 'new' | 'auto';

const props = defineProps<{
  modelValue: boolean;
  request: AppointmentRequest | null;
}>();

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void;
  (e: 'completed'): void;
}>();

const isOpen = computed(() => props.modelValue);

const { toast, show: showToast, hide: hideToast } = useToast();

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

const currentStep = ref<Step>(1);
const patientMode = ref<PatientMode>('existing');

const doctors = ref<Doctor[]>([]);
const doctorLoading = ref(false);
const doctorsLoaded = ref(false);

const selectedPatient = ref<Patient | null>(null);

const patientSearchKeyword = ref('');
const patientSearchResults = ref<Patient[]>([]);
const patientSearchLoading = ref(false);

const newPatientForm = ref({
  code: '',
  fullName: '',
  gender: '',
  dateOfBirth: '',
  phone: '',
  email: '',
  address: '',
  note: '',
});
const newPatientLoading = ref(false);
const newPatientSaved = ref(false);

const scheduleForm = ref({
  doctorId: null as number | null,
  scheduledDate: '',
  scheduledTime: '',
  duration: 30,
  staffNote: '',
});

const submissionLoading = ref(false);

const today = new Date().toISOString().slice(0, 10);

const stepLabels: Record<Step, string> = {
  1: 'Xác nhận yêu cầu',
  2: 'Chọn bệnh nhân',
  3: 'Lên lịch khám',
};

const patientModeOptions: Array<{ key: PatientMode; label: string; description: string }> = [
  { key: 'existing', label: 'Chọn bệnh nhân sẵn có', description: 'Tìm kiếm và gắn với hồ sơ bệnh nhân đã có.' },
  { key: 'new', label: 'Tạo bệnh nhân mới', description: 'Nhập nhanh thông tin và lưu hồ sơ mới.' },
  { key: 'auto', label: 'Tạo tự động', description: 'Hệ thống tạo hồ sơ dựa trên yêu cầu hiện tại.' },
];

const preferredAtDisplay = computed(() => {
  const preferredAt = props.request?.preferredAt;
  if (!preferredAt) return null;
  try {
    const date = new Date(preferredAt);
    if (Number.isNaN(date.getTime())) return preferredAt;
    return new Intl.DateTimeFormat('vi-VN', {
      dateStyle: 'medium',
      timeStyle: 'short',
    }).format(date);
  } catch {
    return preferredAt;
  }
});

const selectedDoctor = computed(() => {
  if (!scheduleForm.value.doctorId) return null;
  return doctors.value.find((doctor) => doctor.id === scheduleForm.value.doctorId) ?? null;
});

const canProceed = computed(() => {
  if (currentStep.value === 1) return true;
  if (currentStep.value === 2) {
    if (patientMode.value === 'existing') {
      return Boolean(selectedPatient.value);
    }
    if (patientMode.value === 'new') {
      return Boolean(selectedPatient.value) && newPatientSaved.value;
    }
    return true;
  }
  if (currentStep.value === 3) {
    return (
      !submissionLoading.value &&
      !!scheduleForm.value.doctorId &&
      !!scheduleForm.value.scheduledDate &&
      !!scheduleForm.value.scheduledTime &&
      scheduleForm.value.duration > 0
    );
  }
  return false;
});

const requestSnapshot = computed(() => props.request);

const deriveSuggestedDate = () => {
  const preferredAt = props.request?.preferredAt;
  if (preferredAt && preferredAt.includes('T')) {
    return preferredAt.split('T')[0];
  }
  return new Date().toISOString().slice(0, 10);
};

const deriveSuggestedTime = () => {
  const preferredAt = props.request?.preferredAt;
  if (preferredAt && preferredAt.includes('T')) {
    const timePart = preferredAt.split('T')[1];
    if (timePart) {
      return timePart.slice(0, 5);
    }
  }
  return '08:00';
};

const resetWizardState = () => {
  currentStep.value = 1;
  patientMode.value = 'existing';
  selectedPatient.value = null;
  patientSearchKeyword.value = '';
  patientSearchResults.value = [];
  newPatientForm.value = {
    code: generatePatientCode(),
    fullName: props.request?.fullName ?? '',
    gender: '',
    dateOfBirth: props.request?.dateOfBirth ?? '',
    phone: props.request?.phone ?? '',
    email: props.request?.email ?? '',
    address: '',
    note: props.request?.symptomDescription ? `Ghi chú yêu cầu: ${props.request.symptomDescription}` : '',
  };
  newPatientLoading.value = false;
  newPatientSaved.value = false;
  scheduleForm.value = {
    doctorId: null,
    scheduledDate: deriveSuggestedDate(),
    scheduledTime: deriveSuggestedTime(),
    duration: 30,
    staffNote: '',
  };
};

const closeWizard = () => {
  emit('update:modelValue', false);
};

const ensureDoctorsLoaded = async () => {
  if (doctorLoading.value || doctorsLoaded.value) return;
  doctorLoading.value = true;
  try {
    const data = await fetchDoctors();
    doctors.value = data;
    doctorsLoaded.value = true;
  } catch (error) {
    showToast('error', extractErrorMessage(error));
  } finally {
    doctorLoading.value = false;
  }
};

const handlePatientSearch = async () => {
  if (!patientSearchKeyword.value.trim()) {
    patientSearchResults.value = [];
    showToast('error', 'Nhập họ tên, số điện thoại hoặc mã bệnh nhân để tìm kiếm.');
    return;
  }
  patientSearchLoading.value = true;
  try {
    const results = await searchPatients({ keyword: patientSearchKeyword.value.trim() });
    patientSearchResults.value = results;
    if (!results.length) {
      showToast('error', 'Không tìm thấy bệnh nhân phù hợp.');
    }
  } catch (error) {
    patientSearchResults.value = [];
    showToast('error', extractErrorMessage(error));
  } finally {
    patientSearchLoading.value = false;
  }
};

const handleSelectPatient = (patient: Patient) => {
  selectedPatient.value = patient;
};

const handleGeneratePatientCode = () => {
  newPatientForm.value.code = generatePatientCode();
};

const handleCreatePatient = async () => {
  if (!newPatientForm.value.code.trim()) {
    showToast('error', 'Vui lòng nhập mã bệnh nhân.');
    return;
  }
  if (!newPatientForm.value.fullName.trim()) {
    showToast('error', 'Vui lòng nhập họ tên bệnh nhân.');
    return;
  }
  newPatientLoading.value = true;
  try {
    const payload = {
      code: newPatientForm.value.code.trim().toUpperCase(),
      fullName: newPatientForm.value.fullName.trim(),
      gender: newPatientForm.value.gender || null,
      dateOfBirth: newPatientForm.value.dateOfBirth || null,
      phone: newPatientForm.value.phone || null,
      email: newPatientForm.value.email || null,
      address: newPatientForm.value.address || null,
      note: newPatientForm.value.note || null,
    };
    const patient = await createPatient(payload);
    selectedPatient.value = patient;
    newPatientSaved.value = true;
  } catch (error) {
    showToast('error', extractErrorMessage(error));
    selectedPatient.value = null;
    newPatientSaved.value = false;
  } finally {
    newPatientLoading.value = false;
  }
};

const goToNextStep = () => {
  if (currentStep.value === 1) {
    currentStep.value = 2;
    return;
  }
  if (currentStep.value === 2) {
    if (patientMode.value === 'existing' && !selectedPatient.value) {
      showToast('error', 'Vui lòng chọn một bệnh nhân trước khi tiếp tục.');
      return;
    }
    if (patientMode.value === 'new') {
      if (!selectedPatient.value || !newPatientSaved.value) {
        showToast('error', 'Vui lòng lưu hồ sơ bệnh nhân mới trước khi tiếp tục.');
        return;
      }
    }
    currentStep.value = 3;
  }
};

const goToPreviousStep = () => {
  if (submissionLoading.value) return;
  if (currentStep.value > 1) {
    currentStep.value = (currentStep.value - 1) as Step;
    return;
  }
  closeWizard();
};

const submitWizard = async () => {
  if (!props.request) {
    showToast('error', 'Không xác định được yêu cầu cần xử lý.');
    return;
  }
  if (!canProceed.value) {
    showToast('error', 'Vui lòng hoàn thiện thông tin trước khi xác nhận.');
    return;
  }
  submissionLoading.value = true;
  try {
    const scheduledAt = `${scheduleForm.value.scheduledDate}T${scheduleForm.value.scheduledTime}:00`;
    await approveAppointmentRequest(props.request.id, {
      patientId: patientMode.value === 'auto' ? undefined : selectedPatient.value?.id,
      doctorId: scheduleForm.value.doctorId!,
      scheduledAt,
      duration: scheduleForm.value.duration,
      staffNote: scheduleForm.value.staffNote || undefined,
    });
    emit('completed');
    closeWizard();
  } catch (error) {
    showToast('error', extractErrorMessage(error));
  } finally {
    submissionLoading.value = false;
  }
};

const handlePrimaryAction = () => {
  if (currentStep.value < 3) {
    goToNextStep();
  } else {
    void submitWizard();
  }
};

const extractErrorMessage = (input: unknown) => {
  const fallback = 'Đã xảy ra lỗi không xác định. Vui lòng thử lại.';
  if (!input || typeof input !== 'object') return fallback;
  const maybeError = input as { message?: string; response?: { data?: { message?: unknown; error?: string } } };
  const responseMessage = maybeError.response?.data?.message;
  if (typeof responseMessage === 'string' && responseMessage.trim()) {
    return responseMessage;
  }
  if (Array.isArray(responseMessage) && responseMessage.length > 0) {
    const first = responseMessage[0];
    if (typeof first === 'string' && first.trim()) {
      return first;
    }
  }
  if (maybeError.response?.data?.error) {
    return maybeError.response.data.error;
  }
  if (maybeError.message) {
    return maybeError.message;
  }
  return fallback;
};

const generatePatientCode = () => {
  const random = Math.random().toString(36).slice(2, 8).toUpperCase();
  return `BN${random}`;
};

watch(
  () => props.modelValue,
  (open) => {
    if (open) {
      resetWizardState();
      void ensureDoctorsLoaded();
      if (props.request?.patient) {
        const basePatient = props.request.patient;
        selectedPatient.value = {
          id: basePatient.id,
          code: basePatient.code ?? `BN${basePatient.id}`,
          fullName: basePatient.fullName ?? props.request?.fullName ?? 'Bệnh nhân',
          phone: basePatient.phone ?? props.request?.phone ?? '',
          gender: null,
          email: props.request?.email ?? null,
          address: null,
          note: null,
          createdAt: undefined,
          updatedAt: undefined,
        };
        patientMode.value = 'existing';
      }
    } else {
      resetWizardState();
    }
  },
  { immediate: false }
);

watch(
  () => patientMode.value,
  (mode) => {
    if (mode === 'existing') {
      newPatientSaved.value = Boolean(selectedPatient.value);
    }
    if (mode === 'auto') {
      selectedPatient.value = null;
      newPatientSaved.value = false;
    }
    if (mode === 'new' && !newPatientSaved.value) {
      selectedPatient.value = null;
    }
  }
);

onMounted(() => {
  if (isOpen.value) {
    resetWizardState();
    void ensureDoctorsLoaded();
  }
});
</script>

<template>
  <Transition name="fade">
    <div
      v-if="isOpen"
      class="fixed inset-0 z-[70] flex items-center justify-center bg-slate-900/35 backdrop-blur-sm"
    >
      <div
        class="relative max-h-[90vh] w-[min(940px,94vw)] overflow-hidden rounded-[32px] border border-emerald-100 bg-white shadow-[0_40px_120px_-60px_rgba(15,118,110,0.85)]"
      >
        <div class="pointer-events-none absolute inset-0">
          <div class="absolute -top-32 -right-32 h-72 w-72 rounded-full bg-emerald-100/40 blur-3xl"></div>
          <div class="absolute -bottom-32 -left-24 h-72 w-72 rounded-full bg-teal-100/35 blur-3xl"></div>
        </div>

        <button
          type="button"
          class="absolute right-6 top-6 z-10 flex h-10 w-10 items-center justify-center rounded-full bg-white/80 text-slate-500 shadow-sm transition hover:bg-white hover:text-slate-700"
          @click="closeWizard"
          aria-label="Đóng hộp thoại"
        >
          <svg xmlns="http://www.w3.org/2000/svg" class="h-4.5 w-4.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.8">
            <path stroke-linecap="round" stroke-linejoin="round" d="m16 8-8 8m0-8 8 8" />
          </svg>
        </button>

        <div class="relative grid max-h-[90vh] grid-rows-[auto_1fr_auto] gap-6 overflow-y-auto px-10 pb-8 pt-12">
          <header class="flex flex-col gap-4 md:flex-row md:items-center md:justify-between">
            <div>
              <p class="text-xs font-semibold uppercase tracking-[0.35em] text-emerald-500">Tạo lịch từ yêu cầu</p>
              <h2 class="mt-2 text-2xl font-semibold text-slate-900">Bệnh nhân: {{ requestSnapshot?.fullName }}</h2>
              <p class="mt-1 text-sm text-slate-600">
                Hoàn thành 3 bước để ghép yêu cầu vào lịch khám chính thức.
              </p>
            </div>
            <div class="flex flex-wrap items-center gap-3">
              <ol class="flex items-center gap-2 text-xs font-semibold uppercase tracking-wide text-emerald-600">
                <li
                  v-for="step in [1, 2, 3]"
                  :key="step"
                  class="flex items-center gap-2 rounded-full border px-3 py-1 transition"
                  :class="currentStep === step ? 'border-emerald-300 bg-emerald-50 text-emerald-700 shadow-sm' : 'border-emerald-100 bg-white text-emerald-500'"
                >
                  <span class="flex h-6 w-6 items-center justify-center rounded-full bg-emerald-100 text-emerald-600">{{ step }}</span>
                  {{ stepLabels[step as Step] }}
                </li>
              </ol>
            </div>
          </header>

          <section v-if="currentStep === 1" class="space-y-6">
            <article class="rounded-[24px] border border-emerald-100 bg-emerald-50/70 p-6 text-sm text-slate-700">
              <h3 class="text-base font-semibold text-slate-900">Thông tin yêu cầu</h3>
              <div class="mt-4 grid gap-4 sm:grid-cols-2">
                <div>
                  <p class="text-xs font-semibold uppercase tracking-wide text-slate-400">Họ tên</p>
                  <p class="mt-1 text-sm font-semibold text-slate-900">{{ requestSnapshot?.fullName }}</p>
                </div>
                <div>
                  <p class="text-xs font-semibold uppercase tracking-wide text-slate-400">Số điện thoại</p>
                  <p class="mt-1 text-sm font-semibold text-slate-900">{{ requestSnapshot?.phone }}</p>
                </div>
                <div>
                  <p class="text-xs font-semibold uppercase tracking-wide text-slate-400">Email</p>
                  <p class="mt-1 text-sm text-slate-700">{{ requestSnapshot?.email || '—' }}</p>
                </div>
                <div>
                  <p class="text-xs font-semibold uppercase tracking-wide text-slate-400">Ngày sinh</p>
                  <p class="mt-1 text-sm text-slate-700">
                    {{ requestSnapshot?.dateOfBirth ? new Intl.DateTimeFormat('vi-VN').format(new Date(requestSnapshot.dateOfBirth)) : '—' }}
                  </p>
                </div>
              </div>
              <div class="mt-4 grid gap-4 sm:grid-cols-2">
                <div>
                  <p class="text-xs font-semibold uppercase tracking-wide text-slate-400">Khung giờ mong muốn</p>
                  <p class="mt-1 text-sm text-slate-700">{{ preferredAtDisplay || 'Chưa chọn' }}</p>
                </div>
                <div>
                  <p class="text-xs font-semibold uppercase tracking-wide text-slate-400">Ngày tạo yêu cầu</p>
                  <p class="mt-1 text-sm text-slate-700">
                    {{
                      requestSnapshot?.createdAt
                        ? new Intl.DateTimeFormat('vi-VN', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(requestSnapshot.createdAt))
                        : '—'
                    }}
                  </p>
                </div>
              </div>
              <div class="mt-4">
                <p class="text-xs font-semibold uppercase tracking-wide text-slate-400">Triệu chứng / ghi chú</p>
                <p class="mt-2 whitespace-pre-line rounded-2xl border border-emerald-100 bg-white/80 p-4 text-sm text-slate-700">
                  {{ requestSnapshot?.symptomDescription || 'Không có ghi chú cụ thể.' }}
                </p>
              </div>
            </article>
            <div class="flex items-center justify-end">
              <p class="text-xs text-emerald-600">
                Bấm &ldquo;Tiếp tục&rdquo; để chọn bệnh nhân tương ứng.
              </p>
            </div>
          </section>

          <section v-else-if="currentStep === 2" class="space-y-6">
            <div class="grid gap-3 sm:grid-cols-3">
              <button
                v-for="option in patientModeOptions"
                :key="option.key"
                type="button"
                class="rounded-2xl border p-4 text-left transition hover:border-emerald-200 hover:bg-emerald-50/70"
                :class="patientMode === option.key ? 'border-emerald-300 bg-emerald-50/80 shadow-sm' : 'border-emerald-100 bg-white'"
                @click="patientMode = option.key"
              >
                <p class="text-sm font-semibold text-slate-900">{{ option.label }}</p>
                <p class="mt-2 text-xs text-slate-500">{{ option.description }}</p>
              </button>
            </div>

            <article v-if="patientMode === 'existing'" class="rounded-[24px] border border-slate-200 bg-white p-6 shadow-sm">
              <h3 class="text-base font-semibold text-slate-900">Tìm kiếm bệnh nhân</h3>
              <p class="mt-1 text-sm text-slate-600">
                Nhập họ tên, số điện thoại hoặc mã bệnh nhân để liên kết yêu cầu.
              </p>
              <div class="mt-4 flex flex-col gap-3 sm:flex-row">
                <div class="relative flex-1">
                  <input
                    v-model="patientSearchKeyword"
                    type="search"
                    class="w-full rounded-full border border-emerald-100 bg-white py-2.5 pl-10 pr-4 text-sm text-slate-700 shadow-sm transition focus:border-emerald-400 focus:outline-none focus:ring-4 focus:ring-emerald-100/80"
                    placeholder="Ví dụ: Nguyễn Văn A, 0987123456, BN0001..."
                  />
                  <svg xmlns="http://www.w3.org/2000/svg" class="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-emerald-400" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6">
                    <path stroke-linecap="round" stroke-linejoin="round" d="m21 21-4.35-4.35m0 0A7.35 7.35 0 1 0 6.3 6.3a7.35 7.35 0 0 0 10.35 10.35Z" />
                  </svg>
                </div>
                <button
                  type="button"
                  class="inline-flex items-center gap-2 rounded-full bg-emerald-600 px-5 py-2 text-sm font-semibold uppercase tracking-wide text-white shadow-sm transition hover:bg-emerald-700 disabled:cursor-not-allowed disabled:opacity-70"
                  :disabled="patientSearchLoading"
                  @click="handlePatientSearch"
                >
                  <svg v-if="patientSearchLoading" class="h-4 w-4 animate-spin" viewBox="0 0 24 24" fill="none">
                    <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
                    <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 0 1 8-8v4l3.5-3.5L12 1v4a7 7 0 0 0-7 7h-1z"></path>
                  </svg>
                  <span>{{ patientSearchLoading ? 'Đang tìm...' : 'Tìm kiếm' }}</span>
                </button>
              </div>

              <div v-if="patientSearchResults.length" class="mt-5 grid gap-3 sm:grid-cols-2">
                <button
                  v-for="patient in patientSearchResults"
                  :key="patient.id"
                  type="button"
                  class="rounded-2xl border p-4 text-left transition"
                  :class="selectedPatient?.id === patient.id ? 'border-emerald-300 bg-emerald-50/80 shadow-sm' : 'border-slate-200 bg-white hover:border-emerald-200 hover:bg-emerald-50/70'"
                  @click="handleSelectPatient(patient)"
                >
                  <div class="flex items-center justify-between">
                    <p class="text-sm font-semibold text-slate-900">{{ patient.fullName }}</p>
                    <span class="rounded-full bg-emerald-100 px-2 py-0.5 text-[11px] font-semibold text-emerald-600">{{ patient.code }}</span>
                  </div>
                  <p class="mt-1 text-xs text-slate-500">SĐT: {{ patient.phone || '—' }} • Email: {{ patient.email || '—' }}</p>
                  <p class="mt-1 text-xs text-slate-400">Ghi chú: {{ patient.note || 'Không có' }}</p>
                </button>
              </div>
            </article>

            <article v-else-if="patientMode === 'new'" class="rounded-[24px] border border-emerald-100 bg-white p-6 shadow-sm">
              <div class="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
                <div>
                  <h3 class="text-base font-semibold text-slate-900">Tạo hồ sơ bệnh nhân mới</h3>
                  <p class="mt-1 text-sm text-slate-600">Thông tin đã nhập sẽ được lưu lại và sử dụng cho các lần khám sau.</p>
                </div>
                <button
                  type="button"
                  class="inline-flex items-center gap-2 rounded-full border border-emerald-200 bg-white px-4 py-2 text-xs font-semibold uppercase tracking-wide text-emerald-600 shadow-sm transition hover:border-emerald-300 hover:bg-emerald-50"
                  @click="handleGeneratePatientCode"
                >
                  Tạo mã ngẫu nhiên
                </button>
              </div>
              <div class="mt-5 grid gap-4 md:grid-cols-2">
                <div>
                  <label class="mb-1 block text-xs font-semibold uppercase tracking-wide text-slate-500" for="patient-code">Mã bệnh nhân *</label>
                  <input
                    id="patient-code"
                    v-model="newPatientForm.code"
                    type="text"
                    class="w-full rounded-xl border border-slate-200 bg-white px-4 py-2.5 text-sm text-slate-700 shadow-sm transition focus:border-emerald-400 focus:outline-none focus:ring-4 focus:ring-emerald-100/80"
                    placeholder="BN0001"
                  />
                </div>
                <div>
                  <label class="mb-1 block text-xs font-semibold uppercase tracking-wide text-slate-500" for="patient-fullname">Họ và tên *</label>
                  <input
                    id="patient-fullname"
                    v-model="newPatientForm.fullName"
                    type="text"
                    class="w-full rounded-xl border border-slate-200 bg-white px-4 py-2.5 text-sm text-slate-700 shadow-sm transition focus:border-emerald-400 focus:outline-none focus:ring-4 focus:ring-emerald-100/80"
                    placeholder="Nhập họ tên đầy đủ"
                  />
                </div>
                <div>
                  <label class="mb-1 block text-xs font-semibold uppercase tracking-wide text-slate-500" for="patient-dob">Ngày sinh</label>
                  <input
                    id="patient-dob"
                    v-model="newPatientForm.dateOfBirth"
                    type="date"
                    :max="today"
                    class="w-full rounded-xl border border-slate-200 bg-white px-4 py-2.5 text-sm text-slate-700 shadow-sm transition focus:border-emerald-400 focus:outline-none focus:ring-4 focus:ring-emerald-100/80"
                  />
                </div>
                <div>
                  <label class="mb-1 block text-xs font-semibold uppercase tracking-wide text-slate-500" for="patient-gender">Giới tính</label>
                  <select
                    id="patient-gender"
                    v-model="newPatientForm.gender"
                    class="w-full rounded-xl border border-slate-200 bg-white px-4 py-2.5 text-sm text-slate-700 shadow-sm transition focus:border-emerald-400 focus:outline-none focus:ring-4 focus:ring-emerald-100/80"
                  >
                    <option value="">Chưa xác định</option>
                    <option value="MALE">Nam</option>
                    <option value="FEMALE">Nữ</option>
                    <option value="OTHER">Khác</option>
                  </select>
                </div>
                <div>
                  <label class="mb-1 block text-xs font-semibold uppercase tracking-wide text-slate-500" for="patient-phone">Số điện thoại</label>
                  <input
                    id="patient-phone"
                    v-model="newPatientForm.phone"
                    type="tel"
                    maxlength="10"
                    class="w-full rounded-xl border border-slate-200 bg-white px-4 py-2.5 text-sm text-slate-700 shadow-sm transition focus:border-emerald-400 focus:outline-none focus:ring-4 focus:ring-emerald-100/80"
                    placeholder="Ví dụ: 0987123456"
                  />
                </div>
                <div>
                  <label class="mb-1 block text-xs font-semibold uppercase tracking-wide text-slate-500" for="patient-email">Email</label>
                  <input
                    id="patient-email"
                    v-model="newPatientForm.email"
                    type="email"
                    class="w-full rounded-xl border border-slate-200 bg-white px-4 py-2.5 text-sm text-slate-700 shadow-sm transition focus:border-emerald-400 focus:outline-none focus:ring-4 focus:ring-emerald-100/80"
                    placeholder="example@email.com"
                  />
                </div>
                <div class="md:col-span-2">
                  <label class="mb-1 block text-xs font-semibold uppercase tracking-wide text-slate-500" for="patient-address">Địa chỉ</label>
                  <input
                    id="patient-address"
                    v-model="newPatientForm.address"
                    type="text"
                    class="w-full rounded-xl border border-slate-200 bg-white px-4 py-2.5 text-sm text-slate-700 shadow-sm transition focus:border-emerald-400 focus:outline-none focus:ring-4 focus:ring-emerald-100/80"
                    placeholder="Số nhà, đường, phường/xã..."
                  />
                </div>
                <div class="md:col-span-2">
                  <label class="mb-1 block text-xs font-semibold uppercase tracking-wide text-slate-500" for="patient-note">Ghi chú</label>
                  <textarea
                    id="patient-note"
                    v-model="newPatientForm.note"
                    rows="3"
                    class="w-full rounded-xl border border-slate-200 bg-white px-4 py-2.5 text-sm text-slate-700 shadow-sm transition focus:border-emerald-400 focus:outline-none focus:ring-4 focus:ring-emerald-100/80"
                    placeholder="Ghi chú nội bộ (nếu có)"
                  ></textarea>
                </div>
              </div>
              <div class="mt-4 flex flex-wrap items-center justify-between gap-3">
                <p v-if="newPatientSaved" class="text-xs text-emerald-600">Đã lưu hồ sơ bệnh nhân • Mã: {{ selectedPatient?.code }}</p>
                <p v-else class="text-xs text-slate-500">Hồ sơ sẽ được lưu và ghép vào yêu cầu sau khi bạn bấm &ldquo;Lưu bệnh nhân mới&rdquo;.</p>
                <button
                  type="button"
                  class="inline-flex items-center gap-2 rounded-full bg-emerald-600 px-5 py-2 text-sm font-semibold uppercase tracking-wide text-white shadow-sm transition hover:bg-emerald-700 disabled:cursor-not-allowed disabled:opacity-70"
                  :disabled="newPatientLoading"
                  @click="handleCreatePatient"
                >
                  <svg v-if="newPatientLoading" class="h-4 w-4 animate-spin" viewBox="0 0 24 24" fill="none">
                    <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
                    <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 0 1 8-8v4l3.5-3.5L12 1v4a7 7 0 0 0-7 7h-1z"></path>
                  </svg>
                  <span>{{ newPatientLoading ? 'Đang lưu...' : 'Lưu bệnh nhân mới' }}</span>
                </button>
              </div>
            </article>

            <article v-else class="rounded-[24px] border border-dashed border-emerald-200 bg-emerald-50/50 p-6 text-sm text-emerald-700">
              <h3 class="text-base font-semibold text-emerald-700">Tạo hồ sơ tự động</h3>
              <p class="mt-2 text-sm text-emerald-700/80">
                Hệ thống sẽ tự tạo hồ sơ bệnh nhân mới dựa trên thông tin trong yêu cầu hiện tại. Bạn có thể bổ sung chi tiết sau khi lịch hẹn được xác nhận.
              </p>
            </article>
          </section>

          <section v-else class="space-y-6">
            <article class="rounded-[24px] border border-emerald-100 bg-white/95 p-6 shadow-sm">
              <div class="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
                <div>
                  <h3 class="text-base font-semibold text-slate-900">Lên lịch khám</h3>
                  <p class="mt-1 text-sm text-slate-600">Chọn bác sĩ, thời gian phù hợp và ghi chú xử lý yêu cầu.</p>
                </div>
                <span v-if="selectedDoctor" class="inline-flex items-center gap-2 rounded-full bg-emerald-50 px-4 py-1 text-xs font-semibold uppercase tracking-wide text-emerald-700">
                  Bác sĩ: {{ selectedDoctor.account?.fullName || ('#' + selectedDoctor.id) }}
                </span>
              </div>
              <div class="mt-5 grid gap-4 md:grid-cols-2">
                <div>
                  <label class="mb-1 block text-xs font-semibold uppercase tracking-wide text-slate-500" for="schedule-doctor">Bác sĩ phụ trách *</label>
                  <select
                    id="schedule-doctor"
                    v-model.number="scheduleForm.doctorId"
                    class="w-full rounded-xl border border-slate-200 bg-white px-4 py-2.5 text-sm text-slate-700 shadow-sm transition focus:border-emerald-400 focus:outline-none focus:ring-4 focus:ring-emerald-100/80"
                  >
                    <option :value="null">Chọn bác sĩ</option>
                    <option
                      v-for="doctor in doctors"
                      :key="doctor.id"
                      :value="doctor.id"
                    >
                      {{ doctor.account?.fullName || ('Bác sĩ #' + doctor.id) }} • {{ doctor.specialty || 'Chuyên khoa' }}
                    </option>
                  </select>
                </div>
                <div>
                  <label class="mb-1 block text-xs font-semibold uppercase tracking-wide text-slate-500" for="schedule-date">Ngày khám *</label>
                  <input
                    id="schedule-date"
                    v-model="scheduleForm.scheduledDate"
                    type="date"
                    :min="today"
                    class="w-full rounded-xl border border-slate-200 bg-white px-4 py-2.5 text-sm text-slate-700 shadow-sm transition focus:border-emerald-400 focus:outline-none focus:ring-4 focus:ring-emerald-100/80"
                  />
                </div>
                <div>
                  <label class="mb-1 block text-xs font-semibold uppercase tracking-wide text-slate-500" for="schedule-time">Giờ khám *</label>
                  <input
                    id="schedule-time"
                    v-model="scheduleForm.scheduledTime"
                    type="time"
                    class="w-full rounded-xl border border-slate-200 bg-white px-4 py-2.5 text-sm text-slate-700 shadow-sm transition focus:border-emerald-400 focus:outline-none focus:ring-4 focus:ring-emerald-100/80"
                  />
                </div>
                <div>
                  <label class="mb-1 block text-xs font-semibold uppercase tracking-wide text-slate-500" for="schedule-duration">Thời lượng (phút)</label>
                  <input
                    id="schedule-duration"
                    v-model.number="scheduleForm.duration"
                    type="number"
                    min="15"
                    step="5"
                    class="w-full rounded-xl border border-slate-200 bg-white px-4 py-2.5 text-sm text-slate-700 shadow-sm transition focus:border-emerald-400 focus:outline-none focus:ring-4 focus:ring-emerald-100/80"
                  />
                </div>
                <div class="md:col-span-2">
                  <label class="mb-1 block text-xs font-semibold uppercase tracking-wide text-slate-500" for="schedule-note">Ghi chú nội bộ</label>
                  <textarea
                    id="schedule-note"
                    v-model="scheduleForm.staffNote"
                    rows="3"
                    class="w-full rounded-xl border border-slate-200 bg-white px-4 py-2.5 text-sm text-slate-700 shadow-sm transition focus:border-emerald-400 focus:outline-none focus:ring-4 focus:ring-emerald-100/80"
                    placeholder="Ví dụ: Liên hệ trước khi khám, ưu tiên sắp phòng siêu âm..."
                  ></textarea>
                </div>
              </div>
              <div class="mt-4 rounded-2xl border border-emerald-100 bg-emerald-50/70 p-4 text-xs text-emerald-700">
                <p v-if="preferredAtDisplay">
                  Khung giờ bệnh nhân mong muốn: <span class="font-semibold text-emerald-800">{{ preferredAtDisplay }}</span>
                </p>
                <p v-else>
                  Bệnh nhân chưa chọn khung giờ cụ thể. Vui lòng thông báo cho họ sau khi đặt lịch.
                </p>
              </div>
            </article>
          </section>

          <footer class="flex flex-col gap-3 border-t border-emerald-100 pt-5 sm:flex-row sm:items-center sm:justify-between">
            <div class="text-xs text-slate-500">
              Bước {{ currentStep }} / 3 • {{ stepLabels[currentStep] }}
            </div>
            <div class="flex flex-wrap items-center gap-3">
              <button
                type="button"
                class="inline-flex items-center gap-2 rounded-full border border-slate-200 bg-white px-5 py-2 text-xs font-semibold uppercase tracking-wide text-slate-600 shadow-sm transition hover:border-slate-300 hover:text-slate-800"
                @click="goToPreviousStep"
              >
                <svg xmlns="http://www.w3.org/2000/svg" class="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.6">
                  <path stroke-linecap="round" stroke-linejoin="round" d="m15 19-7-7 7-7" />
                </svg>
                Quay lại
              </button>
              <button
                type="button"
                class="inline-flex items-center gap-2 rounded-full bg-emerald-600 px-6 py-2.5 text-xs font-semibold uppercase tracking-wide text-white shadow-sm transition hover:bg-emerald-700 disabled:cursor-not-allowed disabled:opacity-70"
                :disabled="!canProceed || submissionLoading"
                @click="handlePrimaryAction"
              >
                <svg v-if="submissionLoading" class="h-4 w-4 animate-spin" viewBox="0 0 24 24" fill="none">
                  <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
                  <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 0 1 8-8v4l3.5-3.5L12 1v4a7 7 0 0 0-7 7h-1z"></path>
                </svg>
                <span>
                  {{ currentStep < 3 ? 'Tiếp tục' : 'Hoàn tất & tạo lịch' }}
                </span>
              </button>
            </div>
          </footer>
        </div>
      </div>
    </div>
  </Transition>

  <!-- Toast -->
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
</template>

<style scoped>
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.18s ease;
}
.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}
</style>
