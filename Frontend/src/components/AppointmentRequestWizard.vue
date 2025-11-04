<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';
import { approveAppointmentRequest, type AppointmentRequest } from '@/services/appointmentRequest.service';
import { fetchDoctors, type Doctor } from '@/services/doctor.service';
import { createPatient, fetchPatientPage, type Patient, type PatientPage } from '@/services/patient.service';
import { createAppointment } from '@/services/appointment.service';
import { fetchClinicRooms, fetchAvailableGeneralRooms, type ClinicRoom, type ClinicRoomAvailability } from '@/services/clinicRoom.service';
import { useToast, type ToastType } from '@/composables/useToast';

type Step = 1 | 2 | 3;
type PatientMode = 'existing' | 'new';

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

// Định nghĩa hàm trước khi sử dụng
const generatePatientCode = () => {
  const random = Math.floor(100000 + Math.random() * 900000).toString();
  return `BN${random}`;
};

const currentStep = ref<Step>(1);
const patientMode = ref<PatientMode>('existing');

const doctors = ref<Doctor[]>([]);
const doctorsLoaded = ref(false);

const clinicRooms = ref<ClinicRoom[]>([]);
const clinicRoomLoading = ref(false);
const clinicRoomsLoaded = ref(false);

const availableRooms = ref<ClinicRoomAvailability[]>([]);
const availableRoomsLoading = ref(false);

const selectedPatient = ref<Patient | null>(null);

const patientSearchKeyword = ref('');
const patientSearchResults = ref<Patient[]>([]);
const patientSearchLoading = ref(false);
const patientPage = ref<PatientPage | null>(null);
const patientCurrentPage = ref(0);
const patientPageSize = ref(10);

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
  clinicRoomId: null as number | null, // Chọn phòng khám trước
  doctorId: null as number | null, // Tự động lấy từ phòng khám
  scheduledDate: '',
  scheduledTime: '',
  reason: '',
  staffNote: '',
});

// Thời lượng luôn luôn là 30 phút
const APPOINTMENT_DURATION = 30;

const doctorLoading = ref(false);

const submissionLoading = ref(false);

const today = new Date().toISOString().slice(0, 10);

// Tạo các khung giờ từ 8h đến 17h (giống AppointmentRequestForm.vue)
const toMinutes = (h: number, m: number) => h * 60 + m;
const toHHMM = (mins: number) => {
  const h = Math.floor(mins / 60).toString().padStart(2, '0');
  const m = (mins % 60).toString().padStart(2, '0');
  return `${h}:${m}`;
};
const makeSlots = (start: number, end: number, step = 30) => {
  const out: string[] = [];
  for (let t = start; t <= end; t += step) out.push(toHHMM(t));
  return out;
};

const MORNING_SLOTS = makeSlots(toMinutes(8, 0), toMinutes(12, 0));
const AFTERNOON_SLOTS = makeSlots(toMinutes(13, 0), toMinutes(17, 0));
const ALL_TIME_SLOTS = [...MORNING_SLOTS, ...AFTERNOON_SLOTS];

const isDirectAppointmentMode = computed(() => !props.request);

const stepLabels = computed<Record<Step, string>>(() => ({
  1: isDirectAppointmentMode.value ? 'Thông tin bệnh nhân' : 'Xác nhận yêu cầu',
  2: 'Chọn bệnh nhân',
  3: 'Lên lịch khám',
}));

const patientModeOptions: Array<{ key: PatientMode; label: string; description: string }> = [
  { key: 'existing', label: 'Chọn bệnh nhân sẵn có', description: 'Tìm kiếm và gắn với hồ sơ bệnh nhân đã có.' },
  { key: 'new', label: 'Tạo bệnh nhân mới', description: 'Nhập nhanh thông tin và lưu hồ sơ mới.' },
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
  // Nếu là direct appointment mode, bỏ qua step 1
  if (isDirectAppointmentMode.value && currentStep.value === 1) {
    return true; // Luôn có thể tiếp tục từ step 1 (sẽ tự động chuyển sang step 2)
  }
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
      !!scheduleForm.value.clinicRoomId && // Phòng khám bắt buộc
      !!scheduleForm.value.doctorId && // Bác sĩ tự động (phải có)
      !!scheduleForm.value.scheduledDate &&
      !!scheduleForm.value.scheduledTime
    );
  }
  return false;
});

const requestSnapshot = computed(() => props.request);

const deriveSuggestedDate = (): string => {
  const preferredAt = props.request?.preferredAt;
  if (preferredAt && preferredAt.includes('T')) {
    const datePart = preferredAt.split('T')[0];
    if (datePart) {
      // Kiểm tra xem ngày có hợp lệ không (phải >= hôm nay)
      const date = new Date(datePart);
      const today = new Date();
      today.setHours(0, 0, 0, 0);
      if (date >= today) {
        return datePart;
      }
    }
  }
  // Mặc định là ngày mai
  const tomorrow = new Date();
  tomorrow.setDate(tomorrow.getDate() + 1);
  return tomorrow.toISOString().slice(0, 10);
};

const deriveSuggestedTime = (): string => {
  const preferredAt = props.request?.preferredAt;
  if (preferredAt && preferredAt.includes('T')) {
    const timePart = preferredAt.split('T')[1];
    if (timePart) {
      const timeStr = timePart.slice(0, 5);
      // Nếu thời gian có trong danh sách khung giờ, trả về nó
      if (ALL_TIME_SLOTS.includes(timeStr)) {
        return timeStr;
      }
      // Nếu không, tìm khung giờ gần nhất
      const timeMinutes = timeStr.split(':').map(Number).reduce((h, m) => h * 60 + m, 0);
      let closestTime = MORNING_SLOTS[0] || '08:00';
      let minDiff = Math.abs(toMinutes(8, 0) - timeMinutes);
      
      for (const slot of ALL_TIME_SLOTS) {
        const slotMinutes = slot.split(':').map(Number).reduce((h, m) => h * 60 + m, 0);
        const diff = Math.abs(slotMinutes - timeMinutes);
        if (diff < minDiff) {
          minDiff = diff;
          closestTime = slot;
        }
      }
      return closestTime;
    }
  }
  return MORNING_SLOTS[0] || '08:00'; // Mặc định là 08:00
};

const resetWizardState = () => {
  // Nếu là direct appointment mode, bắt đầu từ step 2 (chọn bệnh nhân)
  currentStep.value = isDirectAppointmentMode.value ? 2 : 1;
  patientMode.value = 'existing';
  selectedPatient.value = null;
  patientSearchKeyword.value = '';
  patientSearchResults.value = [];
  patientPage.value = null;
  patientCurrentPage.value = 0;
  // Reset với mã mới mỗi lần mở wizard
  const newCode = generatePatientCode();
  newPatientForm.value = {
    code: newCode,
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
    clinicRoomId: null,
    scheduledDate: deriveSuggestedDate(),
    scheduledTime: deriveSuggestedTime(),
    reason: '',
    staffNote: '',
  };
};

const closeWizard = () => {
  emit('update:modelValue', false);
};

const ensureDoctorsLoaded = async () => {
  if (doctorsLoaded.value) return;
  try {
    const data = await fetchDoctors();
    doctors.value = data;
    doctorsLoaded.value = true;
  } catch (error) {
    showToast('error', extractErrorMessage(error));
  }
};

// Tự động lấy bác sĩ theo phòng khám, ngày và giờ
const fetchDoctorByClinicRoom = async () => {
  if (!scheduleForm.value.clinicRoomId || !scheduleForm.value.scheduledDate || !scheduleForm.value.scheduledTime) {
    scheduleForm.value.doctorId = null;
    return;
  }

  try {
    const date = new Date(scheduleForm.value.scheduledDate);
    const dayOfWeek = ['SUNDAY', 'MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY'][date.getDay()] as 'MONDAY' | 'TUESDAY' | 'WEDNESDAY' | 'THURSDAY' | 'FRIDAY' | 'SATURDAY' | 'SUNDAY';
    
    doctorLoading.value = true;
    const availableDoctors = await fetchDoctors({
      clinicRoomId: scheduleForm.value.clinicRoomId,
      dayOfWeek,
      time: scheduleForm.value.scheduledTime,
    });

    // Cập nhật danh sách doctors để có thể hiển thị thông tin bác sĩ
    if (availableDoctors.length > 0) {
      availableDoctors.forEach(doc => {
        if (!doctors.value.find(d => d.id === doc.id)) {
          doctors.value.push(doc);
        }
      });
    }

    if (availableDoctors.length > 0 && availableDoctors[0]) {
      // Tự động chọn bác sĩ đầu tiên
      scheduleForm.value.doctorId = availableDoctors[0].id;
    } else {
      scheduleForm.value.doctorId = null;
      showToast('warning', 'Không có bác sĩ nào làm việc tại phòng khám này vào thời gian đã chọn.');
    }
  } catch (error) {
    scheduleForm.value.doctorId = null;
    showToast('error', extractErrorMessage(error));
  } finally {
    doctorLoading.value = false;
  }
};

const ensureClinicRoomsLoaded = async () => {
  if (clinicRoomLoading.value || clinicRoomsLoaded.value) return;
  clinicRoomLoading.value = true;
  try {
    const data = await fetchClinicRooms();
    clinicRooms.value = Array.isArray(data) ? data : [];
    clinicRoomsLoaded.value = true;
  } catch (error) {
    showToast('error', extractErrorMessage(error));
  } finally {
    clinicRoomLoading.value = false;
  }
};

const handlePatientSearch = async (page = 0) => {
  if (!patientSearchKeyword.value.trim()) {
    patientSearchResults.value = [];
    patientPage.value = null;
    showToast('error', 'Nhập họ tên, số điện thoại hoặc mã bệnh nhân để tìm kiếm.');
    return;
  }
  patientSearchLoading.value = true;
  try {
    const pageData = await fetchPatientPage({
      keyword: patientSearchKeyword.value.trim(),
      page,
      size: patientPageSize.value,
    });
    patientPage.value = pageData;
    patientSearchResults.value = pageData.items;
    patientCurrentPage.value = pageData.page;
    if (!pageData.items.length) {
      showToast('error', 'Không tìm thấy bệnh nhân phù hợp.');
    }
  } catch (error) {
    patientSearchResults.value = [];
    patientPage.value = null;
    showToast('error', extractErrorMessage(error));
  } finally {
    patientSearchLoading.value = false;
  }
};

const handlePatientPageChange = (newPage: number) => {
  void handlePatientSearch(newPage);
};

const handleSelectPatient = (patient: Patient) => {
  selectedPatient.value = patient;
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
  // Nếu là direct appointment mode, step 1 sẽ tự động chuyển sang step 2
  if (currentStep.value === 1) {
    if (isDirectAppointmentMode.value) {
      currentStep.value = 2;
    } else {
      currentStep.value = 2;
    }
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
    // Nếu là direct appointment mode và đang ở step 2, về step 1 sẽ đóng wizard
    if (isDirectAppointmentMode.value && currentStep.value === 2) {
      closeWizard();
      return;
    }
    currentStep.value = (currentStep.value - 1) as Step;
    return;
  }
  closeWizard();
};

const submitWizard = async () => {
  if (!canProceed.value) {
    showToast('error', 'Vui lòng hoàn thiện thông tin trước khi xác nhận.');
    return;
  }
  if (!selectedPatient.value) {
    showToast('error', 'Vui lòng chọn bệnh nhân.');
    return;
  }
  submissionLoading.value = true;
  try {
    const scheduledAt = `${scheduleForm.value.scheduledDate}T${scheduleForm.value.scheduledTime}:00`;
    
    if (isDirectAppointmentMode.value) {
      // Tạo appointment trực tiếp (không có request)
      if (!scheduleForm.value.clinicRoomId) {
        showToast('error', 'Vui lòng chọn phòng khám.');
        return;
      }
      await createAppointment({
        patientId: selectedPatient.value.id,
        doctorId: scheduleForm.value.doctorId!,
        clinicRoomId: scheduleForm.value.clinicRoomId,
        scheduledAt,
        duration: APPOINTMENT_DURATION,
        reason: scheduleForm.value.reason || null,
        notes: scheduleForm.value.staffNote || null,
      });
      showToast('success', 'Đã tạo lịch hẹn thành công.');
    } else {
      // Duyệt appointment request
      if (!props.request) {
        showToast('error', 'Không xác định được yêu cầu cần xử lý.');
        return;
      }
      await approveAppointmentRequest(props.request.id, {
        patientId: selectedPatient.value.id,
        doctorId: scheduleForm.value.doctorId!,
        scheduledAt,
        duration: APPOINTMENT_DURATION,
        staffNote: scheduleForm.value.staffNote || undefined,
      });
    }
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

watch(
  () => props.modelValue,
  (open) => {
    if (open) {
      resetWizardState();
      void ensureDoctorsLoaded();
      void ensureClinicRoomsLoaded();
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
    if (mode === 'new' && !newPatientSaved.value) {
      selectedPatient.value = null;
    }
  }
);

// Lấy danh sách phòng khám có sẵn
const fetchAvailableRooms = async () => {
  if (!scheduleForm.value.scheduledDate || !scheduleForm.value.scheduledTime) {
    availableRooms.value = [];
    return;
  }

  try {
    availableRoomsLoading.value = true;
    // Format: yyyy-MM-ddTHH:mm:ss
    const scheduledAt = `${scheduleForm.value.scheduledDate}T${scheduleForm.value.scheduledTime}:00`;
    const rooms = await fetchAvailableGeneralRooms(scheduledAt, APPOINTMENT_DURATION);
    availableRooms.value = Array.isArray(rooms) ? rooms : [];
    console.log('Fetched available rooms:', rooms);
  } catch (error) {
    console.error('Error fetching available rooms:', error);
    availableRooms.value = [];
    // Chỉ hiển thị toast nếu lỗi nghiêm trọng, không hiển thị nếu chỉ là không có dữ liệu
    const errorMsg = extractErrorMessage(error);
    if (errorMsg && !errorMsg.includes('404') && !errorMsg.includes('Not Found')) {
      showToast('error', errorMsg);
    }
  } finally {
    availableRoomsLoading.value = false;
  }
};

// Watch để tự động lấy phòng khám có sẵn khi thay đổi ngày hoặc giờ
watch(
  () => [scheduleForm.value.scheduledDate, scheduleForm.value.scheduledTime],
  ([newDate, newTime]) => {
    if (currentStep.value === 3) {
      // Nếu có cả ngày và giờ, tự động fetch phòng khám
      if (newDate && newTime) {
        void fetchAvailableRooms();
      } else {
        availableRooms.value = [];
      }
      // Nếu đã chọn phòng khám, tự động lấy bác sĩ
      if (scheduleForm.value.clinicRoomId && newDate && newTime) {
        void fetchDoctorByClinicRoom();
      }
    }
  },
  { immediate: false }
);

// Watch để tự động gọi API khi chuyển sang step 3
watch(
  () => currentStep.value,
  (newStep) => {
    if (newStep === 3) {
      // Đảm bảo có giá trị mặc định cho ngày nếu chưa có
      if (!scheduleForm.value.scheduledDate) {
        const tomorrow = new Date();
        tomorrow.setDate(tomorrow.getDate() + 1);
        scheduleForm.value.scheduledDate = tomorrow.toISOString().slice(0, 10);
      }
      // Đảm bảo có giá trị mặc định cho giờ nếu chưa có
      if (!scheduleForm.value.scheduledTime) {
        scheduleForm.value.scheduledTime = MORNING_SLOTS[0] || '08:00';
      }
      // Nếu đã có ngày và giờ, tự động fetch phòng khám
      if (scheduleForm.value.scheduledDate && scheduleForm.value.scheduledTime) {
        void fetchAvailableRooms();
      }
    }
  },
  { immediate: true }
);

// Watch để tự động lấy bác sĩ khi thay đổi phòng khám
watch(
  () => scheduleForm.value.clinicRoomId,
  () => {
    if (currentStep.value === 3 && scheduleForm.value.scheduledDate && scheduleForm.value.scheduledTime) {
      void fetchDoctorByClinicRoom();
    }
  }
);

onMounted(() => {
  if (isOpen.value) {
    resetWizardState();
    void ensureDoctorsLoaded();
    void ensureClinicRoomsLoaded();
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
              <p class="text-xs font-semibold uppercase tracking-[0.35em] text-emerald-500">
                {{ isDirectAppointmentMode ? 'Tạo lịch hẹn trực tiếp' : 'Tạo lịch từ yêu cầu' }}
              </p>
              <h2 class="mt-2 text-2xl font-semibold text-slate-900">
                {{ isDirectAppointmentMode ? 'Tạo lịch hẹn mới' : `Bệnh nhân: ${requestSnapshot?.fullName}` }}
              </h2>
              <p class="mt-1 text-sm text-slate-600">
                {{ isDirectAppointmentMode ? 'Hoàn thành 2 bước để tạo lịch hẹn cho bệnh nhân đến trực tiếp.' : 'Hoàn thành 3 bước để ghép yêu cầu vào lịch khám chính thức.' }}
              </p>
            </div>
            <div class="flex flex-wrap items-center gap-3">
              <ol class="flex items-center gap-2 text-xs font-semibold uppercase tracking-wide text-emerald-600">
                <li
                  v-for="step in (isDirectAppointmentMode ? [2, 3] : [1, 2, 3])"
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

          <section v-if="currentStep === 1 && !isDirectAppointmentMode" class="space-y-6">
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
            <div class="grid gap-3 sm:grid-cols-2">
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
                    @keyup.enter="handlePatientSearch(0)"
                  />
                  <svg xmlns="http://www.w3.org/2000/svg" class="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-emerald-400" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6">
                    <path stroke-linecap="round" stroke-linejoin="round" d="m21 21-4.35-4.35m0 0A7.35 7.35 0 1 0 6.3 6.3a7.35 7.35 0 0 0 10.35 10.35Z" />
                  </svg>
                </div>
                <button
                  type="button"
                  class="inline-flex items-center gap-2 rounded-full bg-emerald-600 px-5 py-2 text-sm font-semibold uppercase tracking-wide text-white shadow-sm transition hover:bg-emerald-700 disabled:cursor-not-allowed disabled:opacity-70"
                  :disabled="patientSearchLoading"
                  @click="handlePatientSearch(0)"
                >
                  <svg v-if="patientSearchLoading" class="h-4 w-4 animate-spin" viewBox="0 0 24 24" fill="none">
                    <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
                    <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 0 1 8-8v4l3.5-3.5L12 1v4a7 7 0 0 0-7 7h-1z"></path>
                  </svg>
                  <span>{{ patientSearchLoading ? 'Đang tìm...' : 'Tìm kiếm' }}</span>
                </button>
              </div>

              <div v-if="patientSearchResults.length" class="mt-5 space-y-4">
                <div class="grid gap-3 sm:grid-cols-2">
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
                <div v-if="patientPage && patientPage.totalPages > 1" class="flex items-center justify-between rounded-2xl border border-emerald-100 bg-emerald-50/50 px-4 py-3">
                  <p class="text-xs text-slate-600">
                    Trang {{ patientPage.page + 1 }} / {{ patientPage.totalPages }} • Tổng {{ patientPage.totalElements }} bệnh nhân
                  </p>
                  <div class="flex items-center gap-2">
                    <button
                      type="button"
                      class="inline-flex items-center gap-1 rounded-full border border-emerald-200 bg-white px-3 py-1.5 text-xs font-semibold text-emerald-600 transition hover:border-emerald-300 hover:bg-emerald-50 disabled:cursor-not-allowed disabled:opacity-50"
                      :disabled="!patientPage.hasPrevious || patientSearchLoading"
                      @click="handlePatientPageChange(patientCurrentPage - 1)"
                    >
                      <svg xmlns="http://www.w3.org/2000/svg" class="h-3.5 w-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">
                        <path stroke-linecap="round" stroke-linejoin="round" d="m15 19-7-7 7-7" />
                      </svg>
                      Trước
                    </button>
                    <button
                      type="button"
                      class="inline-flex items-center gap-1 rounded-full border border-emerald-200 bg-white px-3 py-1.5 text-xs font-semibold text-emerald-600 transition hover:border-emerald-300 hover:bg-emerald-50 disabled:cursor-not-allowed disabled:opacity-50"
                      :disabled="!patientPage.hasNext || patientSearchLoading"
                      @click="handlePatientPageChange(patientCurrentPage + 1)"
                    >
                      Sau
                      <svg xmlns="http://www.w3.org/2000/svg" class="h-3.5 w-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">
                        <path stroke-linecap="round" stroke-linejoin="round" d="m9 5 7 7-7 7" />
                      </svg>
                    </button>
                  </div>
                </div>
              </div>
            </article>

            <article v-else-if="patientMode === 'new'" class="rounded-[24px] border border-emerald-100 bg-white p-6 shadow-sm">
              <div>
                <h3 class="text-base font-semibold text-slate-900">Tạo hồ sơ bệnh nhân mới</h3>
                <p class="mt-1 text-sm text-slate-600">Thông tin đã nhập sẽ được lưu lại và sử dụng cho các lần khám sau.</p>
              </div>
              <div class="mt-5 grid gap-4 md:grid-cols-2">
                <div>
                  <label class="mb-1 block text-xs font-semibold uppercase tracking-wide text-slate-500" for="patient-code">Mã bệnh nhân *</label>
                  <input
                    id="patient-code"
                    v-model="newPatientForm.code"
                    type="text"
                    readonly
                    class="w-full rounded-xl border border-slate-200 bg-slate-50 px-4 py-2.5 text-sm text-slate-700 shadow-sm cursor-not-allowed"
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
                  :disabled="newPatientLoading || newPatientSaved"
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
          </section>

          <section v-else class="space-y-6">
            <article class="rounded-[24px] border border-emerald-100 bg-white/95 p-6 shadow-sm">
              <div class="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
                <div>
                  <h3 class="text-base font-semibold text-slate-900">Lên lịch khám</h3>
                  <p class="mt-1 text-sm text-slate-600">Chọn phòng khám, thời gian phù hợp và ghi chú xử lý yêu cầu. Bác sĩ sẽ được tự động chọn theo lịch làm việc.</p>
                </div>
                <span v-if="selectedDoctor" class="inline-flex items-center gap-2 rounded-full bg-emerald-50 px-4 py-1 text-xs font-semibold uppercase tracking-wide text-emerald-700">
                  <svg v-if="doctorLoading" class="h-3 w-3 animate-spin" viewBox="0 0 24 24" fill="none">
                    <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
                    <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 0 1 8-8v4l3.5-3.5L12 1v4a7 7 0 0 0-7 7h-1z"></path>
                  </svg>
                  <span v-else>Bác sĩ: {{ selectedDoctor.account?.fullName || ('#' + selectedDoctor.id) }}</span>
                </span>
              </div>
              <div class="mt-5 grid gap-4 md:grid-cols-2">
                <div>
                  <label class="mb-1 block text-xs font-semibold uppercase tracking-wide text-slate-500" for="schedule-clinic-room">Phòng khám *</label>
                  <div v-if="availableRoomsLoading" class="w-full rounded-xl border border-slate-200 bg-slate-50 px-4 py-2.5 text-sm text-slate-400">
                    Đang kiểm tra phòng khám...
                  </div>
                  <div v-else-if="availableRooms.length > 0" class="grid grid-cols-1 gap-2">
                    <button
                      v-for="room in availableRooms"
                      :key="room.id"
                      type="button"
                      @click="scheduleForm.clinicRoomId = room.id"
                      class="rounded-xl border px-4 py-2.5 text-left text-sm transition"
                      :class="scheduleForm.clinicRoomId === room.id
                        ? 'border-emerald-400 bg-emerald-50 text-emerald-700 shadow-sm'
                        : room.available
                        ? 'border-emerald-200 bg-white text-slate-700 hover:border-emerald-300 hover:bg-emerald-50/50'
                        : 'border-slate-200 bg-slate-50 text-slate-400 cursor-not-allowed opacity-60'"
                      :disabled="!room.available"
                    >
                      <div class="flex items-center justify-between">
                        <span class="font-semibold">{{ room.code }} - {{ room.name }}</span>
                        <span
                          class="rounded-full px-2 py-0.5 text-xs font-semibold"
                          :class="room.available
                            ? 'bg-emerald-100 text-emerald-700'
                            : 'bg-rose-100 text-rose-700'"
                        >
                          {{ room.available ? 'Trống' : 'Đã đặt' }}
                        </span>
                      </div>
                    </button>
                  </div>
                  <div v-else class="w-full rounded-xl border border-slate-200 bg-slate-50 px-4 py-2.5 text-sm text-slate-400">
                    Vui lòng chọn ngày và giờ khám để xem phòng khám có sẵn
                  </div>
                  <p class="mt-1 text-xs text-slate-400">Bác sĩ sẽ tự động được chọn theo lịch làm việc</p>
                </div>
                <div>
                  <label class="mb-1 block text-xs font-semibold uppercase tracking-wide text-slate-500" for="schedule-doctor">Bác sĩ phụ trách *</label>
                  <input
                    id="schedule-doctor"
                    type="text"
                    :value="selectedDoctor ? `${selectedDoctor.account?.fullName || 'Bác sĩ #' + selectedDoctor.id} • ${selectedDoctor.specialty || 'Chuyên khoa'}` : (doctorLoading ? 'Đang tìm bác sĩ...' : 'Chưa chọn phòng khám')"
                    readonly
                    class="w-full rounded-xl border border-slate-200 bg-slate-50 px-4 py-2.5 text-sm text-slate-700 shadow-sm cursor-not-allowed"
                    :class="!selectedDoctor && !doctorLoading ? 'text-slate-400' : ''"
                  />
                  <p class="mt-1 text-xs text-slate-400">Tự động chọn từ lịch làm việc</p>
                </div>
                <div>
                  <label class="mb-1 block text-xs font-semibold uppercase tracking-wide text-slate-500" for="schedule-date">Ngày khám *</label>
                  <input
                    id="schedule-date"
                    v-model="scheduleForm.scheduledDate"
                    type="date"
                    :min="today"
                    required
                    class="w-full rounded-xl border border-slate-200 bg-white px-4 py-2.5 text-sm text-slate-700 shadow-sm transition focus:border-emerald-400 focus:outline-none focus:ring-4 focus:ring-emerald-100/80"
                  />
                </div>
                <div>
                  <label class="mb-1 block text-xs font-semibold uppercase tracking-wide text-slate-500" for="schedule-time">Giờ khám *</label>
                  <select
                    id="schedule-time"
                    v-model="scheduleForm.scheduledTime"
                    required
                    class="w-full rounded-xl border border-slate-200 bg-white px-4 py-2.5 text-sm text-slate-700 shadow-sm transition focus:border-emerald-400 focus:outline-none focus:ring-4 focus:ring-emerald-100/80"
                  >
                    <option :value="''">Chọn giờ khám</option>
                    <optgroup label="Buổi sáng">
                      <option v-for="time in MORNING_SLOTS" :key="time" :value="time">{{ time }}</option>
                    </optgroup>
                    <optgroup label="Buổi chiều">
                      <option v-for="time in AFTERNOON_SLOTS" :key="time" :value="time">{{ time }}</option>
                    </optgroup>
                  </select>
                </div>
                <div v-if="isDirectAppointmentMode" class="md:col-span-2">
                  <label class="mb-1 block text-xs font-semibold uppercase tracking-wide text-slate-500" for="schedule-reason">Lý do khám</label>
                  <input
                    id="schedule-reason"
                    v-model="scheduleForm.reason"
                    type="text"
                    class="w-full rounded-xl border border-slate-200 bg-white px-4 py-2.5 text-sm text-slate-700 shadow-sm transition focus:border-emerald-400 focus:outline-none focus:ring-4 focus:ring-emerald-100/80"
                    placeholder="Ví dụ: Khám tổng quát, tái khám..."
                  />
                </div>
                <div class="md:col-span-2">
                  <label class="mb-1 block text-xs font-semibold uppercase tracking-wide text-slate-500" for="schedule-note">Ghi chú nội bộ</label>
                  <textarea
                    id="schedule-note"
                    v-model="scheduleForm.staffNote"
                    rows="3"
                    class="w-full rounded-xl border border-slate-200 bg-white px-4 py-2.5 text-sm text-slate-700 shadow-sm transition focus:border-emerald-400 focus:outline-none focus:ring-4 focus:ring-emerald-100/80"
                    :placeholder="isDirectAppointmentMode ? 'Ví dụ: Bệnh nhân đến trực tiếp, cần chuẩn bị...' : 'Ví dụ: Liên hệ trước khi khám, ưu tiên sắp phòng siêu âm...'"
                  ></textarea>
                </div>
              </div>
              <div v-if="!isDirectAppointmentMode" class="mt-4 rounded-2xl border border-emerald-100 bg-emerald-50/70 p-4 text-xs text-emerald-700">
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
              Bước {{ currentStep }} / {{ isDirectAppointmentMode ? 2 : 3 }} • {{ stepLabels[currentStep] }}
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
