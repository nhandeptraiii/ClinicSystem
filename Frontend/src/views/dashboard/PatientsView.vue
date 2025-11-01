<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue';
import { useRouter } from 'vue-router';
import AdminHeader from '@/components/AdminHeader.vue';
import { useAuthStore } from '@/stores/authStore';
import { useToast, type ToastType } from '@/composables/useToast';
import {
  createPatient,
  deletePatient,
  fetchPatientPage,
  type Patient,
  type PatientCreatePayload,
  updatePatient,
  fetchPatientById,
} from '@/services/patient.service';

const authStore = useAuthStore();
const router = useRouter();

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

const userName = computed(() => authStore.user?.username ?? 'Quản trị viên');

const patients = ref<Patient[]>([]);
const loading = ref(false);
const error = ref<string | null>(null);

const PAGE_SIZE = 10;
const currentPage = ref(1);
const totalPages = ref(1);
const totalElements = ref(0);
const hasNext = ref(false);
const hasPrevious = ref(false);

const searchTerm = ref('');
let filterTimer: ReturnType<typeof setTimeout> | null = null;

const formModalOpen = ref(false);
const modalSubmitting = ref(false);
const modalMode = ref<'create' | 'edit'>('create');
const formState = reactive<PatientCreatePayload>({
  code: '',
  fullName: '',
  gender: null,
  dateOfBirth: null,
  phone: null,
  email: null,
  address: null,
  note: null,
});
const formErrors = reactive<Record<string, boolean>>({
  code: false,
  fullName: false,
  phone: false,
  email: false,
});
const selectedPatient = ref<Patient | null>(null);

const detailModalOpen = ref(false);
const detailPatient = ref<Patient | null>(null);
const loadingDetail = ref(false);

const deleteTarget = ref<Patient | null>(null);
const deleting = ref(false);

const formatDate = (input?: string | null) => {
  if (!input) return '—';
  try {
    const date = new Date(input);
    const day = String(date.getDate()).padStart(2, '0');
    const month = String(date.getMonth() + 1).padStart(2, '0');
    const year = date.getFullYear();
    return `${day}/${month}/${year}`;
  } catch (_error) {
    return input;
  }
};

const formatDateTime = (input?: string | null) => {
  if (!input) return '—';
  try {
    return new Intl.DateTimeFormat('vi-VN', {
      dateStyle: 'short',
      timeStyle: 'short',
      hour12: false,
    }).format(new Date(input));
  } catch (_error) {
    return input;
  }
};

const extractErrorMessage = (input: unknown) => {
  const fallback = 'Đã xảy ra lỗi. Vui lòng thử lại.';
  if (!input || typeof input !== 'object') {
    return fallback;
  }
  const maybeError = input as {
    message?: string | null;
    response?: { data?: { message?: unknown; error?: string | null } };
  };
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
  if (maybeError.response?.data?.error && maybeError.response.data.error.trim()) {
    return maybeError.response.data.error;
  }
  if (maybeError.message && maybeError.message.trim()) {
    return maybeError.message;
  }
  return fallback;
};

const loadPatients = async () => {
  loading.value = true;
  error.value = null;
  const keyword = searchTerm.value.trim();
  const pageIndex = Math.max(currentPage.value - 1, 0);
  try {
    const response = await fetchPatientPage({
      page: pageIndex,
      size: PAGE_SIZE,
      keyword: keyword ? keyword : undefined,
    });
    patients.value = response.items ?? [];
    totalElements.value = response.totalElements ?? patients.value.length;
    const derivedTotalPages = response.totalPages && response.totalPages > 0 ? response.totalPages : 1;
    totalPages.value = derivedTotalPages;
    currentPage.value = response.totalPages && response.totalPages > 0 ? response.page + 1 : 1;
    hasNext.value = response.hasNext ?? false;
    hasPrevious.value = response.hasPrevious ?? false;
  } catch (err) {
    error.value = extractErrorMessage(err);
    patients.value = [];
    totalElements.value = 0;
    totalPages.value = 1;
    currentPage.value = 1;
    hasNext.value = false;
    hasPrevious.value = false;
  } finally {
    loading.value = false;
  }
};

const goToPage = (page: number) => {
  const target = Math.min(Math.max(page, 1), totalPages.value || 1);
  if (target === currentPage.value) {
    return;
  }
  currentPage.value = target;
  loadPatients();
};

const nextPage = () => {
  if (hasNext.value) {
    goToPage(currentPage.value + 1);
  }
};

const prevPage = () => {
  if (hasPrevious.value) {
    goToPage(currentPage.value - 1);
  }
};

const generatePatientCode = (): string => {
  const randomNumbers = Math.floor(100000 + Math.random() * 900000);
  return `BN${randomNumbers}`;
};

const clearFormErrors = () => {
  Object.keys(formErrors).forEach((key) => {
    formErrors[key] = false;
  });
};

const openCreateModal = () => {
  modalMode.value = 'create';
  selectedPatient.value = null;
  formState.code = generatePatientCode();
  formState.fullName = '';
  formState.gender = null;
  formState.dateOfBirth = null;
  formState.phone = null;
  formState.email = null;
  formState.address = null;
  formState.note = null;
  clearFormErrors();
  formModalOpen.value = true;
};

const openEditModal = (patient: Patient) => {
  modalMode.value = 'edit';
  selectedPatient.value = patient;
  formState.code = patient.code ?? '';
  formState.fullName = patient.fullName ?? '';
  formState.gender = patient.gender ?? null;
  formState.dateOfBirth = patient.dateOfBirth ? patient.dateOfBirth.split('T')[0] : null;
  formState.phone = patient.phone ?? null;
  formState.email = patient.email ?? null;
  formState.address = patient.address ?? null;
  formState.note = patient.note ?? null;
  clearFormErrors();
  formModalOpen.value = true;
};

const closeFormModal = () => {
  formModalOpen.value = false;
};

const submitForm = async () => {
  if (modalSubmitting.value) return;

  const payload: PatientCreatePayload = {
    code: formState.code?.trim() ?? '',
    fullName: formState.fullName?.trim() ?? '',
    gender: formState.gender?.trim() || null,
    dateOfBirth: formState.dateOfBirth?.trim() || null,
    phone: formState.phone?.trim() || null,
    email: formState.email?.trim() || null,
    address: formState.address?.trim() || null,
    note: formState.note?.trim() || null,
  };

  // Reset errors
  Object.keys(formErrors).forEach((key) => {
    formErrors[key] = false;
  });

  // Validate và thu thập tất cả lỗi
  const errors: string[] = [];

  if (modalMode.value === 'edit' && !payload.code) {
    errors.push('Vui lòng nhập mã bệnh nhân.');
    formErrors.code = true;
  }

  if (!payload.fullName) {
    errors.push('Vui lòng nhập họ và tên.');
    formErrors.fullName = true;
  }

  if (!payload.phone) {
    errors.push('Vui lòng nhập số điện thoại.');
    formErrors.phone = true;
  } else if (payload.phone.length !== 10) {
    errors.push('Số điện thoại phải gồm đúng 10 chữ số.');
    formErrors.phone = true;
  }

  // Hiển thị tất cả lỗi qua toast
  if (errors.length > 0) {
    const errorMessage = errors.join('\n');
    showToast('error', errorMessage);
    return;
  }

  modalSubmitting.value = true;
  try {
    if (modalMode.value === 'create') {
      await createPatient(payload);
      showToast('success', 'Đã thêm bệnh nhân mới.');
    } else if (selectedPatient.value) {
      await updatePatient(selectedPatient.value.id, payload);
      showToast('success', 'Đã cập nhật thông tin bệnh nhân.');
    }
    closeFormModal();
    await loadPatients();
  } catch (error) {
    const errorMessage = extractErrorMessage(error);
    showToast('error', errorMessage ?? 'Không thể lưu thông tin bệnh nhân.');
  } finally {
    modalSubmitting.value = false;
  }
};

const openDetailModal = async (patient: Patient) => {
  detailPatient.value = patient;
  loadingDetail.value = true;
  detailModalOpen.value = true;
  try {
    const fullPatient = await fetchPatientById(patient.id);
    detailPatient.value = fullPatient;
  } catch (err) {
    console.error('Không thể tải chi tiết bệnh nhân:', err);
    showToast('error', 'Không thể tải thông tin chi tiết.');
  } finally {
    loadingDetail.value = false;
  }
};

const closeDetailModal = () => {
  detailModalOpen.value = false;
};

const confirmDelete = (patient: Patient) => {
  deleteTarget.value = patient;
};

const cancelDelete = () => {
  deleteTarget.value = null;
  deleting.value = false;
};

const performDelete = async () => {
  if (!deleteTarget.value) {
    return;
  }
  deleting.value = true;
  try {
    await deletePatient(deleteTarget.value.id);
    showToast('success', 'Đã xóa bệnh nhân.');
    const shouldGoPrev = patients.value.length <= 1 && currentPage.value > 1;
    deleteTarget.value = null;
    deleting.value = false;
    if (shouldGoPrev) {
      currentPage.value = currentPage.value - 1;
    }
    await loadPatients();
  } catch (error) {
    deleting.value = false;
    showToast('error', extractErrorMessage(error));
  }
};

watch(
  searchTerm,
  () => {
    if (filterTimer) {
      clearTimeout(filterTimer);
    }
    filterTimer = setTimeout(() => {
      currentPage.value = 1;
      loadPatients();
    }, 350);
  },
);

onBeforeUnmount(() => {
  if (filterTimer) {
    clearTimeout(filterTimer);
  }
});

const handleSignOut = async () => {
  await authStore.signOut();
  router.replace({ name: 'home' });
};

onMounted(() => {
  loadPatients();
});
</script>

<template>
  <div class="relative min-h-screen bg-gradient-to-br from-emerald-50 via-white to-white">
    <AdminHeader :user-name="userName" @sign-out="handleSignOut" />

    <main class="mx-auto w-full max-w-7xl px-4 pb-20 pt-10 sm:px-6 lg:px-8">
      <section>
        <div class="flex flex-col gap-4 border-b border-emerald-100 pb-6 md:flex-row md:items-center md:justify-between">
          <div>
            <p class="text-xs font-semibold uppercase tracking-[0.35em] text-emerald-500">Quản lý bệnh nhân</p>
            <h1 class="mt-2 text-2xl font-semibold text-slate-900">Danh sách bệnh nhân</h1>
            <p class="mt-1 text-sm text-slate-600">
              Quản lý hồ sơ, thông tin và lịch sử khám bệnh của bệnh nhân.
            </p>
          </div>
          <button
            type="button"
            class="inline-flex items-center gap-2 self-start rounded-full bg-emerald-600 px-5 py-2 text-xs font-semibold uppercase tracking-wide text-white shadow-sm transition hover:bg-emerald-700 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-emerald-500"
            @click="openCreateModal"
          >
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" class="h-4 w-4" fill="none" stroke="currentColor" stroke-width="1.8">
              <path stroke-linecap="round" stroke-linejoin="round" d="M12 5v14m7-7H5" />
            </svg>
            Thêm bệnh nhân
          </button>
        </div>

        <div class="mt-6 flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
          <div class="relative w-full sm:max-w-xs">
            <svg class="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" fill="none" stroke="currentColor" stroke-width="1.5" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" d="m21 21-4.35-4.35m0 0A7.35 7.35 0 1 0 6.3 6.3a7.35 7.35 0 0 0 10.35 10.35Z" />
            </svg>
            <input
              v-model.trim="searchTerm"
              type="search"
              class="w-full rounded-full border border-emerald-100 bg-white py-2.5 pl-9 pr-4 text-sm text-slate-700 shadow-sm transition focus:border-emerald-400 focus:outline-none focus:ring-4 focus:ring-emerald-100/80"
              placeholder="Tìm theo tên, mã, SĐT..."
            />
          </div>
        </div>

        <p
          v-if="error"
          class="mt-4 rounded-2xl border border-rose-100 bg-rose-50/90 px-4 py-3 text-sm text-rose-600"
        >
          {{ error }}
        </p>

        <div class="mt-5">
          <div
            class="hidden rounded-2xl border border-emerald-100 bg-emerald-50/70 px-4 py-3 text-xs font-semibold uppercase tracking-wide text-emerald-700 md:grid md:grid-cols-[150px_250px_150px_150px_160px_240px] md:gap-4"
          >
            <span>Mã BN</span>
            <span>Họ và tên</span>
            <span>Giới tính</span>
            <span>Ngày sinh</span>
            <span>Điện thoại</span>
            <span class="flex justify-end pr-20">Thao tác</span>
          </div>

          <template v-if="loading">
            <div
              v-for="skeleton in 4"
              :key="`patient-skeleton-${skeleton}`"
              class="mt-3 animate-pulse rounded-2xl border border-slate-100 bg-white px-4 py-4 shadow-sm"
            >
              <div class="grid gap-4 md:grid-cols-[150px_250px_150px_150px_160px_240px] md:items-center">
                <div class="h-4 rounded-full bg-slate-200/70"></div>
                <div class="h-4 rounded-full bg-slate-200/60"></div>
                <div class="h-4 rounded-full bg-slate-200/50"></div>
                <div class="h-4 rounded-full bg-slate-200/60"></div>
                <div class="h-4 rounded-full bg-slate-200/60"></div>
                <div class="ml-auto h-8 w-24 rounded-full bg-slate-200/70"></div>
              </div>
            </div>
          </template>
          <template v-else-if="patients.length === 0">
            <div class="mt-8 rounded-3xl border border-dashed border-emerald-200 bg-emerald-50/40 p-10 text-center text-emerald-700">
              <svg xmlns="http://www.w3.org/2000/svg" class="mx-auto h-12 w-12 text-emerald-400" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.6">
                <path stroke-linecap="round" stroke-linejoin="round" d="M5 13l4 4L19 7" />
              </svg>
              <h3 class="mt-4 text-lg font-semibold">Chưa có bệnh nhân nào phù hợp</h3>
              <p class="mt-2 text-sm text-emerald-600/80">Thử thay đổi bộ lọc hoặc tạo mới bệnh nhân.</p>
            </div>
          </template>
          <template v-else>
            <div
              v-for="patient in patients"
              :key="patient.id"
              class="mt-3 rounded-2xl border border-slate-200 bg-white px-4 py-4 shadow-sm transition hover:border-emerald-200 hover:shadow-md"
            >
              <div class="grid gap-4 md:grid-cols-[150px_250px_150px_150px_160px_240px] md:items-center">
                <div>
                  <p class="text-xs font-semibold uppercase tracking-wide text-slate-400 md:hidden">Mã BN</p>
                  <p class="text-sm font-semibold text-slate-900">{{ patient.code }}</p>
                </div>
                <div>
                  <p class="text-xs font-semibold uppercase tracking-wide text-slate-400 md:hidden">Họ và tên</p>
                  <p class="text-sm font-semibold text-slate-900">{{ patient.fullName }}</p>
                </div>
                <div>
                  <p class="text-xs font-semibold uppercase tracking-wide text-slate-400 md:hidden">Giới tính</p>
                  <p class="text-sm text-slate-700 pl-4">{{ patient.gender || '—' }}</p>
                </div>
                <div>
                  <p class="text-xs font-semibold uppercase tracking-wide text-slate-400 md:hidden">Ngày sinh</p>
                  <p class="text-sm text-slate-700">{{ formatDate(patient.dateOfBirth) }}</p>
                </div>
                <div>
                  <p class="text-xs font-semibold uppercase tracking-wide text-slate-400 md:hidden">Điện thoại</p>
                  <p class="text-sm text-slate-700">{{ patient.phone || '—' }}</p>
                </div>
                <div class="flex flex-nowrap items-center justify-start gap-1.5 md:justify-end">
                  <button
                    type="button"
                    class="inline-flex items-center gap-1 rounded-full border border-sky-200 bg-white px-3 py-1.5 text-[11px] font-semibold uppercase tracking-wide text-sky-600 shadow-sm transition hover:border-sky-300 hover:bg-sky-50"
                    @click="openDetailModal(patient)"
                  >
                    <svg xmlns="http://www.w3.org/2000/svg" class="h-3.5 w-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.8">
                      <path stroke-linecap="round" stroke-linejoin="round" d="M12 5c-7 0-9 7-9 7s2 7 9 7 9-7 9-7-2-7-9-7Zm0 4a3 3 0 1 1 0 6 3 3 0 0 1 0-6Z" />
                    </svg>
                    Chi tiết
                  </button>
                  <button
                    type="button"
                    class="inline-flex items-center gap-1 rounded-full border border-emerald-200 bg-white px-3 py-1.5 text-[11px] font-semibold uppercase tracking-wide text-emerald-600 shadow-sm transition hover:border-emerald-300 hover:bg-emerald-50"
                    @click="openEditModal(patient)"
                  >
                    <svg xmlns="http://www.w3.org/2000/svg" class="h-3.5 w-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.8">
                      <path stroke-linecap="round" stroke-linejoin="round" d="m16.862 4.487 1.95 1.95M7.5 20.5l-4-4L16 4l4 4-12.5 12.5Z" />
                    </svg>
                    Sửa
                  </button>
                  <button
                    type="button"
                    class="inline-flex items-center gap-1 rounded-full border border-rose-200 bg-white px-3 py-1.5 text-[11px] font-semibold uppercase tracking-wide text-rose-600 shadow-sm transition hover:border-rose-300 hover:bg-rose-50"
                    @click="confirmDelete(patient)"
                  >
                    <svg xmlns="http://www.w3.org/2000/svg" class="h-3.5 w-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.8">
                      <path stroke-linecap="round" stroke-linejoin="round" d="m15 9-6 6m0-6 6 6M3 6h18" />
                    </svg>
                    Xóa
                  </button>
                </div>
              </div>
            </div>
          </template>
        </div>

        <div class="mt-6 flex flex-col gap-4 border-t border-emerald-100 pt-4 text-sm text-slate-600 md:flex-row md:items-center md:justify-between">
          <span>Đang hiển thị {{ patients.length }} / {{ totalElements }} bệnh nhân</span>
          <div class="flex items-center gap-2">
            <button
              type="button"
              class="inline-flex items-center gap-2 rounded-full border border-emerald-200 bg-white px-4 py-1.5 text-xs font-semibold uppercase tracking-wide text-emerald-600 shadow-sm transition hover:border-emerald-300 hover:bg-emerald-50 disabled:cursor-not-allowed disabled:opacity-60"
              :disabled="!hasPrevious || loading"
              @click="prevPage"
            >
              <svg xmlns="http://www.w3.org/2000/svg" class="h-3.5 w-3.5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8">
                <path stroke-linecap="round" stroke-linejoin="round" d="m15 18-6-6 6-6" />
              </svg>
              Trước
            </button>
            <span class="text-sm font-semibold text-slate-700">
              {{ currentPage }} / {{ totalPages }}
            </span>
            <button
              type="button"
              class="inline-flex items-center gap-2 rounded-full border border-emerald-200 bg-white px-4 py-1.5 text-xs font-semibold uppercase tracking-wide text-emerald-600 shadow-sm transition hover:border-emerald-300 hover:bg-emerald-50 disabled:cursor-not-allowed disabled:opacity-60"
              :disabled="!hasNext || loading"
              @click="nextPage"
            >
              Sau
              <svg xmlns="http://www.w3.org/2000/svg" class="h-3.5 w-3.5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8">
                <path stroke-linecap="round" stroke-linejoin="round" d="m9 6 6 6-6 6" />
              </svg>
            </button>
          </div>
        </div>
      </section>
    </main>

    <!-- Modal Thêm/Sửa Bệnh nhân -->
    <Transition name="fade">
      <div
        v-if="formModalOpen"
        class="fixed inset-0 z-[90] flex items-center justify-center bg-slate-900/50 backdrop-blur-sm px-4"
      >
        <div class="w-full max-w-2xl max-h-[90vh] overflow-y-auto rounded-3xl border border-emerald-100 bg-white p-6 shadow-xl">
          <div class="flex items-start justify-between">
            <div>
              <p class="text-xs font-semibold uppercase tracking-[0.35em] text-emerald-500">
                {{ modalMode === 'create' ? 'Thêm bệnh nhân' : 'Chỉnh sửa bệnh nhân' }}
              </p>
              <h2 class="mt-1 text-xl font-semibold text-slate-900">
                {{ modalMode === 'create' ? 'Tạo bệnh nhân mới' : 'Cập nhật thông tin bệnh nhân' }}
              </h2>
            </div>
            <button
              type="button"
              class="flex h-9 w-9 items-center justify-center rounded-full border border-slate-200 text-slate-500 transition hover:border-slate-300 hover:text-slate-700"
              @click="closeFormModal"
            >
              <svg xmlns="http://www.w3.org/2000/svg" class="h-4.5 w-4.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.8">
                <path stroke-linecap="round" stroke-linejoin="round" d="m16 8-8 8m0-8 8 8" />
              </svg>
            </button>
          </div>

          <form class="mt-6 space-y-4" @submit.prevent="submitForm" novalidate>
            <div class="grid gap-4 sm:grid-cols-2">
              <div class="space-y-1.5">
                <label class="text-xs font-semibold uppercase tracking-wide text-slate-500" for="patient-code">
                  Mã bệnh nhân {{ modalMode === 'create' ? '(Tự động)' : '*' }}
                </label>
                <input
                  id="patient-code"
                  v-model="formState.code"
                  type="text"
                  autocomplete="off"
                  :readonly="modalMode === 'create'"
                  :class="[
                    'w-full rounded-xl border px-4 py-2 text-sm text-slate-700 shadow-sm transition focus:outline-none focus:ring-4',
                    formErrors.code
                      ? 'border-rose-400 bg-rose-50 focus:border-rose-500 focus:ring-rose-100/80 animate-shake'
                      : modalMode === 'create'
                        ? 'border-slate-200 bg-slate-50 cursor-not-allowed'
                        : 'border-slate-200 focus:border-emerald-400 focus:ring-emerald-100/80'
                  ]"
                  placeholder="VD: BN123456"
                  maxlength="30"
                  @input="formErrors.code = false"
                />
                <p v-if="modalMode === 'create'" class="text-xs text-slate-500">
                  Mã bệnh nhân được tự động tạo
                </p>
              </div>
              <div class="space-y-1.5">
                <label class="text-xs font-semibold uppercase tracking-wide text-slate-500" for="patient-fullName">
                  Họ và tên *
                </label>
                <input
                  id="patient-fullName"
                  v-model="formState.fullName"
                  type="text"
                  autocomplete="off"
                  :class="[
                    'w-full rounded-xl border px-4 py-2 text-sm text-slate-700 shadow-sm transition focus:outline-none focus:ring-4',
                    formErrors.fullName
                      ? 'border-rose-400 bg-rose-50 focus:border-rose-500 focus:ring-rose-100/80 animate-shake'
                      : 'border-slate-200 focus:border-emerald-400 focus:ring-emerald-100/80'
                  ]"
                  placeholder="VD: Nguyễn Văn A"
                  maxlength="150"
                  @input="formErrors.fullName = false"
                />
              </div>
            </div>
            <div class="grid gap-4 sm:grid-cols-2">
              <div class="space-y-1.5">
                <label class="text-xs font-semibold uppercase tracking-wide text-slate-500" for="patient-gender">
                  Giới tính
                </label>
                <select
                  id="patient-gender"
                  v-model="formState.gender"
                  class="w-full rounded-xl border border-slate-200 px-4 py-2 text-sm text-slate-700 shadow-sm transition focus:border-emerald-400 focus:outline-none focus:ring-4 focus:ring-emerald-100/80"
                >
                  <option :value="null">Chọn giới tính</option>
                  <option value="Nam">Nam</option>
                  <option value="Nữ">Nữ</option>
                  <option value="Khác">Khác</option>
                </select>
              </div>
              <div class="space-y-1.5">
                <label class="text-xs font-semibold uppercase tracking-wide text-slate-500" for="patient-dateOfBirth">
                  Ngày sinh
                </label>
                <input
                  id="patient-dateOfBirth"
                  v-model="formState.dateOfBirth"
                  type="date"
                  class="w-full rounded-xl border border-slate-200 px-4 py-2 text-sm text-slate-700 shadow-sm transition focus:border-emerald-400 focus:outline-none focus:ring-4 focus:ring-emerald-100/80"
                />
              </div>
            </div>
            <div class="grid gap-4 sm:grid-cols-2">
              <div class="space-y-1.5">
                <label class="text-xs font-semibold uppercase tracking-wide text-slate-500" for="patient-phone">
                  Số điện thoại *
                </label>
                <input
                  id="patient-phone"
                  v-model="formState.phone"
                  type="tel"
                  :class="[
                    'w-full rounded-xl border px-4 py-2 text-sm text-slate-700 shadow-sm transition focus:outline-none focus:ring-4',
                    formErrors.phone
                      ? 'border-rose-400 bg-rose-50 focus:border-rose-500 focus:ring-rose-100/80 animate-shake'
                      : 'border-slate-200 focus:border-emerald-400 focus:ring-emerald-100/80'
                  ]"
                  placeholder="VD: 0901234567"
                  maxlength="10"
                  @input="formErrors.phone = false"
                />
              </div>
              <div class="space-y-1.5">
                <label class="text-xs font-semibold uppercase tracking-wide text-slate-500" for="patient-email">
                  Email
                </label>
                <input
                  id="patient-email"
                  v-model="formState.email"
                  type="email"
                  :class="[
                    'w-full rounded-xl border px-4 py-2 text-sm text-slate-700 shadow-sm transition focus:outline-none focus:ring-4',
                    formErrors.email
                      ? 'border-rose-400 bg-rose-50 focus:border-rose-500 focus:ring-rose-100/80 animate-shake'
                      : 'border-slate-200 focus:border-emerald-400 focus:ring-emerald-100/80'
                  ]"
                  placeholder="VD: example@email.com"
                  maxlength="80"
                  @input="formErrors.email = false"
                />
              </div>
            </div>
            <div class="space-y-1.5">
              <label class="text-xs font-semibold uppercase tracking-wide text-slate-500" for="patient-address">
                Địa chỉ
              </label>
              <input
                id="patient-address"
                v-model="formState.address"
                type="text"
                class="w-full rounded-xl border border-slate-200 px-4 py-2 text-sm text-slate-700 shadow-sm transition focus:border-emerald-400 focus:outline-none focus:ring-4 focus:ring-emerald-100/80"
                placeholder="VD: 123 Đường ABC, Phường XYZ, Quận DEF"
                maxlength="255"
              />
            </div>
            <div class="space-y-1.5">
              <label class="text-xs font-semibold uppercase tracking-wide text-slate-500" for="patient-note">
                Ghi chú
              </label>
              <textarea
                id="patient-note"
                v-model="formState.note"
                rows="3"
                class="w-full rounded-xl border border-slate-200 px-4 py-2 text-sm text-slate-700 shadow-sm transition focus:border-emerald-400 focus:outline-none focus:ring-4 focus:ring-emerald-100/80"
                placeholder="Ghi chú về bệnh nhân..."
                maxlength="255"
              />
            </div>

            <div class="mt-6 flex items-center justify-end gap-3">
              <button
                type="button"
                class="inline-flex items-center gap-2 rounded-full border border-slate-200 bg-white px-5 py-2 text-xs font-semibold uppercase tracking-wide text-slate-600 shadow-sm transition hover:border-slate-300 hover:bg-slate-50"
                @click="closeFormModal"
              >
                Hủy
              </button>
              <button
                type="submit"
                class="inline-flex items-center gap-2 rounded-full bg-emerald-600 px-6 py-2 text-xs font-semibold uppercase tracking-wide text-white shadow-sm transition hover:bg-emerald-700 disabled:cursor-not-allowed disabled:opacity-70"
                :disabled="modalSubmitting"
              >
                <svg v-if="modalSubmitting" class="h-4 w-4 animate-spin" viewBox="0 0 24 24" fill="none">
                  <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
                  <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 0 1 8-8v4l3.5-3.5L12 1v4a7 7 0 0 0-7 7H4z"></path>
                </svg>
                <span>{{ modalMode === 'create' ? 'Thêm bệnh nhân' : 'Lưu thay đổi' }}</span>
              </button>
            </div>
          </form>
        </div>
      </div>
    </Transition>

    <!-- Modal Chi Tiết -->
    <Transition name="fade">
      <div
        v-if="detailModalOpen && detailPatient"
        class="fixed inset-0 z-[90] flex items-center justify-center bg-slate-900/50 backdrop-blur-sm px-4"
      >
        <div class="w-full max-w-2xl max-h-[90vh] overflow-y-auto rounded-3xl border border-emerald-100 bg-white p-6 shadow-xl">
          <div class="flex items-start justify-between">
            <div>
              <p class="text-xs font-semibold uppercase tracking-[0.35em] text-emerald-500">Chi tiết bệnh nhân</p>
              <h2 class="mt-2 text-xl font-semibold text-slate-900">{{ detailPatient.fullName }}</h2>
              <p class="text-sm text-slate-500">Mã: {{ detailPatient.code }} | ID: {{ detailPatient.id }}</p>
            </div>
            <button
              type="button"
              class="flex h-9 w-9 items-center justify-center rounded-full border border-slate-200 text-slate-500 transition hover:border-slate-300 hover:text-slate-700"
              @click="closeDetailModal"
            >
              <svg xmlns="http://www.w3.org/2000/svg" class="h-4.5 w-4.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.8">
                <path stroke-linecap="round" stroke-linejoin="round" d="m16 8-8 8m0-8 8 8" />
              </svg>
            </button>
          </div>

          <div v-if="loadingDetail" class="mt-6 text-center py-8 text-sm text-slate-500">
            <svg class="mx-auto h-6 w-6 animate-spin text-emerald-500" viewBox="0 0 24 24" fill="none">
              <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
              <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 0 1 8-8v4l3.5-3.5L12 1v4a7 7 0 0 0-7 7H4z"></path>
            </svg>
            <p class="mt-2">Đang tải thông tin...</p>
          </div>

          <div v-else class="mt-6 space-y-4 text-sm text-slate-700">
            <div class="flex items-start gap-3">
              <span class="w-32 shrink-0 text-xs font-semibold uppercase tracking-wide text-slate-500">Mã bệnh nhân</span>
              <span class="font-semibold text-slate-900">{{ detailPatient.code }}</span>
            </div>
            <div class="flex items-start gap-3">
              <span class="w-32 shrink-0 text-xs font-semibold uppercase tracking-wide text-slate-500">Họ và tên</span>
              <span class="font-semibold text-slate-900">{{ detailPatient.fullName }}</span>
            </div>
            <div class="flex items-start gap-3">
              <span class="w-32 shrink-0 text-xs font-semibold uppercase tracking-wide text-slate-500">Giới tính</span>
              <span>{{ detailPatient.gender || '—' }}</span>
            </div>
            <div class="flex items-start gap-3">
              <span class="w-32 shrink-0 text-xs font-semibold uppercase tracking-wide text-slate-500">Ngày sinh</span>
              <span>{{ formatDate(detailPatient.dateOfBirth) }}</span>
            </div>
            <div class="flex items-start gap-3">
              <span class="w-32 shrink-0 text-xs font-semibold uppercase tracking-wide text-slate-500">Số điện thoại</span>
              <span>{{ detailPatient.phone || '—' }}</span>
            </div>
            <div class="flex items-start gap-3">
              <span class="w-32 shrink-0 text-xs font-semibold uppercase tracking-wide text-slate-500">Email</span>
              <span>{{ detailPatient.email || '—' }}</span>
            </div>
            <div class="flex items-start gap-3">
              <span class="w-32 shrink-0 text-xs font-semibold uppercase tracking-wide text-slate-500">Địa chỉ</span>
              <span>{{ detailPatient.address || '—' }}</span>
            </div>
            <div class="flex items-start gap-3">
              <span class="w-32 shrink-0 text-xs font-semibold uppercase tracking-wide text-slate-500">Ghi chú</span>
              <span>{{ detailPatient.note || '—' }}</span>
            </div>
            <div class="flex items-start gap-3">
              <span class="w-32 shrink-0 text-xs font-semibold uppercase tracking-wide text-slate-500">Tạo lúc</span>
              <span>{{ formatDateTime(detailPatient.createdAt) }}</span>
            </div>
            <div class="flex items-start gap-3">
              <span class="w-32 shrink-0 text-xs font-semibold uppercase tracking-wide text-slate-500">Cập nhật</span>
              <span>{{ formatDateTime(detailPatient.updatedAt) }}</span>
            </div>
          </div>
        </div>
      </div>
    </Transition>

    <!-- Modal Xóa -->
    <Transition name="fade">
      <div
        v-if="deleteTarget"
        class="fixed inset-0 z-[95] flex items-center justify-center bg-slate-900/60 px-4"
      >
        <div class="w-full max-w-md rounded-3xl border border-rose-100 bg-white p-6 text-slate-700 shadow-xl">
          <div class="flex items-start gap-3">
            <div class="flex h-10 w-10 items-center justify-center rounded-full bg-rose-50 text-rose-600">
              <svg xmlns="http://www.w3.org/2000/svg" class="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.8">
                <path stroke-linecap="round" stroke-linejoin="round" d="M12 9v4m0 4h.01M5 12c0 7 7 9 7 9s7-2 7-9-7-9-7-9-7 2-7 9Z" />
              </svg>
            </div>
            <div>
              <h3 class="text-lg font-semibold text-slate-900">Xóa bệnh nhân?</h3>
              <p class="mt-1 text-sm text-slate-600">
                Bạn sắp xóa bệnh nhân <span class="font-semibold text-rose-600">{{ deleteTarget.fullName }}</span> ({{ deleteTarget.code }}). Hành động này không thể hoàn tác.
              </p>
            </div>
          </div>
          <div class="mt-6 flex items-center justify-end gap-3">
            <button
              type="button"
              class="inline-flex items-center gap-2 rounded-full border border-slate-200 bg-white px-4 py-2 text-xs font-semibold uppercase tracking-wide text-slate-600 shadow-sm transition hover:border-slate-300 hover:bg-slate-50"
              :disabled="deleting"
              @click="cancelDelete"
            >
              Hủy
            </button>
            <button
              type="button"
              class="inline-flex items-center gap-2 rounded-full bg-rose-600 px-5 py-2 text-xs font-semibold uppercase tracking-wide text-white shadow-sm transition hover:bg-rose-700 disabled:cursor-not-allowed disabled:opacity-70"
              :disabled="deleting"
              @click="performDelete"
            >
              <svg v-if="deleting" class="h-4 w-4 animate-spin" viewBox="0 0 24 24" fill="none">
                <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
                <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 0 1 8-8v4l3.5-3.5L12 1v4a7 7 0 0 0-7 7H4z"></path>
              </svg>
              <span>{{ deleting ? 'Đang xóa...' : 'Xóa bệnh nhân' }}</span>
            </button>
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
  </div>
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

@keyframes shake {
  0%, 100% {
    transform: translateX(0);
  }
  10%, 30%, 50%, 70%, 90% {
    transform: translateX(-4px);
  }
  20%, 40%, 60%, 80% {
    transform: translateX(4px);
  }
}

.animate-shake {
  animation: shake 0.5s ease-in-out;
}
</style>
