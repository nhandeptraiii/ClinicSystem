<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue';
import { useRouter } from 'vue-router';
import AdminHeader from '@/components/AdminHeader.vue';
import { useAuthStore } from '@/stores/authStore';
import { useToast, type ToastType } from '@/composables/useToast';
import {
  createMedication,
  deleteMedication,
  fetchMedicationPage,
  type Medication,
  type MedicationPayload,
  updateMedication,
  fetchMedicationById,
} from '@/services/medication.service';

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

const medications = ref<Medication[]>([]);
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
const formState = reactive<MedicationPayload>({
  name: '',
  activeIngredient: null,
  strength: null,
  batchNo: '',
  unit: null,
  unitPrice: 0,
  manufacturer: '',
  expiryDate: '',
  stockQuantity: 0,
});
const formErrors = reactive<Record<string, boolean>>({
  name: false,
  strength: false,
  batchNo: false,
  unit: false,
  unitPrice: false,
  manufacturer: false,
  expiryDate: false,
  stockQuantity: false,
});
const selectedMedication = ref<Medication | null>(null);

const detailModalOpen = ref(false);
const detailMedication = ref<Medication | null>(null);
const loadingDetail = ref(false);

const deleteTarget = ref<Medication | null>(null);
const deleting = ref(false);

const formatCurrency = (value: number) => {
  return new Intl.NumberFormat('vi-VN', {
    style: 'currency',
    currency: 'VND',
  }).format(value);
};

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

const getDaysUntilExpiry = (expiryDate?: string | null): number | null => {
  if (!expiryDate) return null;
  try {
    const expiry = new Date(expiryDate);
    const today = new Date();
    today.setHours(0, 0, 0, 0);
    expiry.setHours(0, 0, 0, 0);
    const diffTime = expiry.getTime() - today.getTime();
    const diffDays = Math.ceil(diffTime / (1000 * 60 * 60 * 24));
    return diffDays;
  } catch (_error) {
    return null;
  }
};

const getExpiryStatus = (expiryDate?: string | null): { text: string; className: string } => {
  const days = getDaysUntilExpiry(expiryDate);
  if (days === null) return { text: '', className: '' };
  if (days < 0) {
    return { text: 'Đã hết hạn', className: 'text-rose-600 font-semibold bg-rose-50 px-2 py-0.5 rounded-full' };
  }
  if (days <= 30) {
    return { text: `Còn ${days} ngày`, className: 'text-amber-600 font-semibold bg-amber-50 px-2 py-0.5 rounded-full' };
  }
  return { text: '', className: '' };
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

const loadMedications = async () => {
  loading.value = true;
  error.value = null;
  const keyword = searchTerm.value.trim();
  const pageIndex = Math.max(currentPage.value - 1, 0);
  try {
    const response = await fetchMedicationPage({
      page: pageIndex,
      size: PAGE_SIZE,
      keyword: keyword ? keyword : undefined,
    });
    medications.value = response.items ?? [];
    totalElements.value = response.totalElements ?? medications.value.length;
    const derivedTotalPages = response.totalPages && response.totalPages > 0 ? response.totalPages : 1;
    totalPages.value = derivedTotalPages;
    currentPage.value = response.totalPages && response.totalPages > 0 ? response.page + 1 : 1;
    hasNext.value = response.hasNext ?? false;
    hasPrevious.value = response.hasPrevious ?? false;
  } catch (err) {
    error.value = extractErrorMessage(err);
    medications.value = [];
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
  loadMedications();
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

const clearFormErrors = () => {
  Object.keys(formErrors).forEach((key) => {
    formErrors[key] = false;
  });
};

const openCreateModal = () => {
  modalMode.value = 'create';
  selectedMedication.value = null;
  formState.name = '';
  formState.activeIngredient = null;
  formState.strength = null;
  formState.batchNo = '';
  formState.unit = null;
  formState.unitPrice = 0;
  formState.manufacturer = '';
  formState.expiryDate = '';
  formState.stockQuantity = 0;
  clearFormErrors();
  formModalOpen.value = true;
};

const openEditModal = (medication: Medication) => {
  modalMode.value = 'edit';
  selectedMedication.value = medication;
  formState.name = medication.name ?? '';
  formState.activeIngredient = medication.activeIngredient ?? null;
  formState.strength = medication.strength ?? null;
  formState.batchNo = medication.batchNo || '';
  formState.unit = medication.unit ?? null;
  formState.unitPrice = medication.unitPrice ?? 0;
  formState.manufacturer = medication.manufacturer || '';
  formState.expiryDate = (medication.expiryDate ? medication.expiryDate.split('T')[0] : '') as string;
  formState.stockQuantity = medication.stockQuantity ?? 0;
  clearFormErrors();
  formModalOpen.value = true;
};

const closeFormModal = () => {
  formModalOpen.value = false;
};

const submitForm = async () => {
  if (modalSubmitting.value) return;

  const payload: MedicationPayload = {
    name: formState.name?.trim() ?? '',
    activeIngredient: formState.activeIngredient?.trim() || null,
    strength: formState.strength?.trim() || null,
    batchNo: formState.batchNo?.trim() ?? '',
    unit: formState.unit?.trim() || null,
    unitPrice: formState.unitPrice ?? 0,
    manufacturer: formState.manufacturer?.trim() ?? '',
    expiryDate: formState.expiryDate?.trim() ?? '',
    stockQuantity: formState.stockQuantity ?? 0,
  };

  // Reset errors
  Object.keys(formErrors).forEach((key) => {
    formErrors[key] = false;
  });

  // Validate và thu thập tất cả lỗi
  const errors: string[] = [];

  if (!payload.name) {
    errors.push('Vui lòng nhập tên thuốc.');
    formErrors.name = true;
  }

  if (!payload.strength || !payload.strength.trim()) {
    errors.push('Vui lòng nhập hàm lượng.');
    formErrors.strength = true;
  }

  if (!payload.batchNo) {
    errors.push('Vui lòng nhập mã lô.');
    formErrors.batchNo = true;
  }

  if (!payload.unit || !payload.unit.trim()) {
    errors.push('Vui lòng nhập đơn vị.');
    formErrors.unit = true;
  }

  if (payload.unitPrice < 100) {
    errors.push('Đơn giá phải từ 100 đồng trở lên.');
    formErrors.unitPrice = true;
  }

  if (!payload.manufacturer) {
    errors.push('Vui lòng nhập nhà sản xuất.');
    formErrors.manufacturer = true;
  }

  if (!payload.expiryDate) {
    errors.push('Vui lòng nhập hạn sử dụng.');
    formErrors.expiryDate = true;
  }

  if (payload.stockQuantity < 0) {
    errors.push('Số lượng phải >= 0.');
    formErrors.stockQuantity = true;
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
      await createMedication(payload);
      showToast('success', 'Đã thêm thuốc mới.');
    } else if (selectedMedication.value) {
      await updateMedication(selectedMedication.value.id, payload);
      showToast('success', 'Đã cập nhật thông tin thuốc.');
    }
    closeFormModal();
    await loadMedications();
  } catch (error) {
    const errorMessage = extractErrorMessage(error);
    showToast('error', errorMessage ?? 'Không thể lưu thông tin thuốc.');
  } finally {
    modalSubmitting.value = false;
  }
};

const openDetailModal = async (medication: Medication) => {
  detailMedication.value = medication;
  loadingDetail.value = true;
  detailModalOpen.value = true;
  try {
    const fullMedication = await fetchMedicationById(medication.id);
    detailMedication.value = fullMedication;
  } catch (err) {
    console.error('Không thể tải chi tiết thuốc:', err);
    showToast('error', 'Không thể tải thông tin chi tiết.');
  } finally {
    loadingDetail.value = false;
  }
};

const closeDetailModal = () => {
  detailModalOpen.value = false;
};

const confirmDelete = (medication: Medication) => {
  deleteTarget.value = medication;
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
    await deleteMedication(deleteTarget.value.id);
    showToast('success', 'Đã xóa thuốc.');
    const shouldGoPrev = medications.value.length <= 1 && currentPage.value > 1;
    deleteTarget.value = null;
    deleting.value = false;
    if (shouldGoPrev) {
      currentPage.value = currentPage.value - 1;
    }
    await loadMedications();
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
      loadMedications();
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
  loadMedications();
});
</script>

<template>
  <div class="relative min-h-screen bg-gradient-to-br from-emerald-50 via-white to-white">
    <AdminHeader :user-name="userName" @sign-out="handleSignOut" />

    <main class="mx-auto w-full max-w-7xl px-4 pb-20 pt-10 sm:px-6 lg:px-8">
      <section>
        <div class="flex flex-col gap-4 border-b border-emerald-100 pb-6 md:flex-row md:items-center md:justify-between">
          <div>
            <p class="text-xs font-semibold uppercase tracking-[0.35em] text-emerald-500">Quản lý kho thuốc</p>
            <h1 class="mt-2 text-2xl font-semibold text-slate-900">Danh sách thuốc</h1>
            <p class="mt-1 text-sm text-slate-600">
              Theo dõi, cập nhật thông tin thuốc và tồn kho trong hệ thống.
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
            Thêm thuốc
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
              placeholder="Tìm theo tên thuốc..."
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
            class="hidden rounded-2xl border border-emerald-100 bg-emerald-50/70 px-4 py-3 text-xs font-semibold uppercase tracking-wide text-emerald-700 md:grid md:grid-cols-[250px_120px_120px_120px_120px_120px_240px] md:gap-4"
          >
            <span>Tên thuốc</span>
            <span>Hàm lượng</span>
            <span>Đơn vị</span>
            <span>Đơn giá</span>
            <span>Số lượng</span>
            <span>Hạn sử dụng</span>
            <span class="flex justify-end pr-20">Thao tác</span>
          </div>

          <template v-if="loading">
            <div
              v-for="skeleton in 4"
              :key="`medication-skeleton-${skeleton}`"
              class="mt-3 animate-pulse rounded-2xl border border-slate-100 bg-white px-4 py-4 shadow-sm"
            >
              <div class="grid gap-4 md:grid-cols-[250px_120px_120px_120px_120px_120px_240px] md:items-center">
                <div class="h-4 rounded-full bg-slate-200/70"></div>
                <div class="h-4 rounded-full bg-slate-200/60"></div>
                <div class="h-4 rounded-full bg-slate-200/50"></div>
                <div class="h-4 rounded-full bg-slate-200/60"></div>
                <div class="h-4 rounded-full bg-slate-200/60"></div>
                <div class="h-4 rounded-full bg-slate-200/60"></div>
                <div class="ml-auto h-8 w-24 rounded-full bg-slate-200/70"></div>
              </div>
            </div>
          </template>
          <template v-else-if="medications.length === 0">
            <div class="mt-8 rounded-3xl border border-dashed border-emerald-200 bg-emerald-50/40 p-10 text-center text-emerald-700">
              <svg xmlns="http://www.w3.org/2000/svg" class="mx-auto h-12 w-12 text-emerald-400" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.6">
                <path stroke-linecap="round" stroke-linejoin="round" d="M5 13l4 4L19 7" />
              </svg>
              <h3 class="mt-4 text-lg font-semibold">Chưa có thuốc nào phù hợp</h3>
              <p class="mt-2 text-sm text-emerald-600/80">Thử thay đổi bộ lọc hoặc tạo mới thuốc.</p>
            </div>
          </template>
          <template v-else>
            <div
              v-for="medication in medications"
              :key="medication.id"
              class="mt-3 rounded-2xl border border-slate-200 bg-white px-4 py-4 shadow-sm transition hover:border-emerald-200 hover:shadow-md"
            >
              <div class="grid gap-4 md:grid-cols-[250px_120px_120px_120px_120px_120px_240px] md:items-center">
                <div>
                  <p class="text-xs font-semibold uppercase tracking-wide text-slate-400 md:hidden">Tên thuốc</p>
                  <p class="text-sm font-semibold text-slate-900">{{ medication.name }}</p>
                </div>
                <div>
                  <p class="text-xs font-semibold uppercase tracking-wide text-slate-400 md:hidden">Hàm lượng</p>
                  <p class="text-sm text-slate-700 pl-3">{{ medication.strength || '—' }}</p>
                </div>
                <div>
                  <p class="text-xs font-semibold uppercase tracking-wide text-slate-400 md:hidden">Đơn vị</p>
                  <p class="text-sm text-slate-700 pl-1">{{ medication.unit || '—' }}</p>
                </div>
                <div>
                  <p class="text-xs font-semibold uppercase tracking-wide text-slate-400 md:hidden">Đơn giá</p>
                  <p class="text-sm font-semibold text-emerald-600 pl-1">{{ formatCurrency(medication.unitPrice) }}</p>
                </div>
                <div>
                  <p class="text-xs font-semibold uppercase tracking-wide text-slate-400 md:hidden">Số lượng</p>
                  <p class="text-sm font-semibold text-slate-700 pl-4">{{ medication.stockQuantity ?? 0 }}</p>
                </div>
                <div>
                  <p class="text-xs font-semibold uppercase tracking-wide text-slate-400 md:hidden">Hạn sử dụng</p>
                  <div class="flex flex-col gap-1">
                    <p class="text-sm text-slate-700 pl-1">{{ formatDate(medication.expiryDate) }}</p>
                    <span v-if="getExpiryStatus(medication.expiryDate).text" :class="getExpiryStatus(medication.expiryDate).className" class="text-xs">
                      {{ getExpiryStatus(medication.expiryDate).text }}
                    </span>
                  </div>
                </div>
                <div class="flex flex-nowrap items-center justify-start gap-1.5 md:justify-end">
                  <button
                    type="button"
                    class="inline-flex items-center gap-1 rounded-full border border-sky-200 bg-white px-3 py-1.5 text-[11px] font-semibold uppercase tracking-wide text-sky-600 shadow-sm transition hover:border-sky-300 hover:bg-sky-50"
                    @click="openDetailModal(medication)"
                  >
                    <svg xmlns="http://www.w3.org/2000/svg" class="h-3.5 w-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.8">
                      <path stroke-linecap="round" stroke-linejoin="round" d="M12 5c-7 0-9 7-9 7s2 7 9 7 9-7 9-7-2-7-9-7Zm0 4a3 3 0 1 1 0 6 3 3 0 0 1 0-6Z" />
                    </svg>
                    Chi tiết
                  </button>
                  <button
                    type="button"
                    class="inline-flex items-center gap-1 rounded-full border border-emerald-200 bg-white px-3 py-1.5 text-[11px] font-semibold uppercase tracking-wide text-emerald-600 shadow-sm transition hover:border-emerald-300 hover:bg-emerald-50"
                    @click="openEditModal(medication)"
                  >
                    <svg xmlns="http://www.w3.org/2000/svg" class="h-3.5 w-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.8">
                      <path stroke-linecap="round" stroke-linejoin="round" d="m16.862 4.487 1.95 1.95M7.5 20.5l-4-4L16 4l4 4-12.5 12.5Z" />
                    </svg>
                    Sửa
                  </button>
                  <button
                    type="button"
                    class="inline-flex items-center gap-1 rounded-full border border-rose-200 bg-white px-3 py-1.5 text-[11px] font-semibold uppercase tracking-wide text-rose-600 shadow-sm transition hover:border-rose-300 hover:bg-rose-50"
                    @click="confirmDelete(medication)"
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
          <span>Đang hiển thị {{ medications.length }} / {{ totalElements }} thuốc</span>
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

    <!-- Modal Thêm/Sửa Thuốc -->
    <Transition name="fade">
      <div
        v-if="formModalOpen"
        class="fixed inset-0 z-[90] flex items-center justify-center bg-slate-900/50 backdrop-blur-sm px-4"
      >
        <div class="w-full max-w-2xl max-h-[90vh] overflow-y-auto rounded-3xl border border-emerald-100 bg-white p-6 shadow-xl">
          <div class="flex items-start justify-between">
            <div>
              <p class="text-xs font-semibold uppercase tracking-[0.35em] text-emerald-500">
                {{ modalMode === 'create' ? 'Thêm thuốc' : 'Chỉnh sửa thuốc' }}
              </p>
              <h2 class="mt-1 text-xl font-semibold text-slate-900">
                {{ modalMode === 'create' ? 'Tạo thuốc mới' : 'Cập nhật thông tin thuốc' }}
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
                <label class="text-xs font-semibold uppercase tracking-wide text-slate-500" for="medication-name">
                  Tên thuốc *
                </label>
                <input
                  id="medication-name"
                  v-model="formState.name"
                  type="text"
                  autocomplete="off"
                  :class="[
                    'w-full rounded-xl border px-4 py-2 text-sm text-slate-700 shadow-sm transition focus:outline-none focus:ring-4',
                    formErrors.name
                      ? 'border-rose-400 bg-rose-50 focus:border-rose-500 focus:ring-rose-100/80 animate-shake'
                      : 'border-slate-200 focus:border-emerald-400 focus:ring-emerald-100/80'
                  ]"
                  placeholder="VD: Paracetamol 500mg"
                  maxlength="150"
                  @input="formErrors.name = false"
                />
              </div>
            <div class="space-y-1.5">
              <label class="text-xs font-semibold uppercase tracking-wide text-slate-500" for="medication-strength">
                Hàm lượng *
              </label>
                <input
                  id="medication-strength"
                  v-model="formState.strength"
                  type="text"
                  :class="[
                    'w-full rounded-xl border px-4 py-2 text-sm text-slate-700 shadow-sm transition focus:outline-none focus:ring-4',
                    formErrors.strength
                      ? 'border-rose-400 bg-rose-50 focus:border-rose-500 focus:ring-rose-100/80 animate-shake'
                      : 'border-slate-200 focus:border-emerald-400 focus:ring-emerald-100/80'
                  ]"
                  placeholder="VD: 500mg"
                  maxlength="100"
                  @input="formErrors.strength = false"
                />
              </div>
            </div>
            <div class="grid gap-4 sm:grid-cols-2">
              <div class="space-y-1.5">
                <label class="text-xs font-semibold uppercase tracking-wide text-slate-500" for="medication-unit">
                  Đơn vị *
                </label>
                <input
                  id="medication-unit"
                  v-model="formState.unit"
                  type="text"
                  :class="[
                    'w-full rounded-xl border px-4 py-2 text-sm text-slate-700 shadow-sm transition focus:outline-none focus:ring-4',
                    formErrors.unit
                      ? 'border-rose-400 bg-rose-50 focus:border-rose-500 focus:ring-rose-100/80 animate-shake'
                      : 'border-slate-200 focus:border-emerald-400 focus:ring-emerald-100/80'
                  ]"
                  placeholder="VD: Viên, Chai, Hộp"
                  maxlength="30"
                  @input="formErrors.unit = false"
                />
              </div>
              <div class="space-y-1.5">
                <label class="text-xs font-semibold uppercase tracking-wide text-slate-500" for="medication-unitPrice">
                  Đơn giá *
                </label>
                <input
                  id="medication-unitPrice"
                  v-model.number="formState.unitPrice"
                  type="number"
                  step="1"
                  :class="[
                    'w-full rounded-xl border px-4 py-2 text-sm text-slate-700 shadow-sm transition focus:outline-none focus:ring-4',
                    formErrors.unitPrice
                      ? 'border-rose-400 bg-rose-50 focus:border-rose-500 focus:ring-rose-100/80 animate-shake'
                      : 'border-slate-200 focus:border-emerald-400 focus:ring-emerald-100/80'
                  ]"
                  placeholder="100"
                  @input="formErrors.unitPrice = false"
                />
              </div>
            </div>
            <div class="grid gap-4 sm:grid-cols-2">
              <div class="space-y-1.5">
                <label class="text-xs font-semibold uppercase tracking-wide text-slate-500" for="medication-activeIngredient">
                  Hoạt chất
                </label>
                <input
                  id="medication-activeIngredient"
                  v-model="formState.activeIngredient"
                  type="text"
                  class="w-full rounded-xl border border-slate-200 px-4 py-2 text-sm text-slate-700 shadow-sm transition focus:border-emerald-400 focus:outline-none focus:ring-4 focus:ring-emerald-100/80"
                  placeholder="VD: Paracetamol"
                  maxlength="150"
                />
              </div>
              <div class="space-y-1.5">
                <label class="text-xs font-semibold uppercase tracking-wide text-slate-500" for="medication-batchNo">
                  Mã lô *
                </label>
                <input
                  id="medication-batchNo"
                  v-model="formState.batchNo"
                  type="text"
                  :class="[
                    'w-full rounded-xl border px-4 py-2 text-sm text-slate-700 shadow-sm transition focus:outline-none focus:ring-4',
                    formErrors.batchNo
                      ? 'border-rose-400 bg-rose-50 focus:border-rose-500 focus:ring-rose-100/80 animate-shake'
                      : 'border-slate-200 focus:border-emerald-400 focus:ring-emerald-100/80'
                  ]"
                  placeholder="VD: LOT-2024-001"
                  maxlength="50"
                  @input="formErrors.batchNo = false"
                />
              </div>
            </div>
            <div class="grid gap-4 sm:grid-cols-2">
              <div class="space-y-1.5">
                <label class="text-xs font-semibold uppercase tracking-wide text-slate-500" for="medication-manufacturer">
                  Nhà sản xuất *
                </label>
                <input
                  id="medication-manufacturer"
                  v-model="formState.manufacturer"
                  type="text"
                  :class="[
                    'w-full rounded-xl border px-4 py-2 text-sm text-slate-700 shadow-sm transition focus:outline-none focus:ring-4',
                    formErrors.manufacturer
                      ? 'border-rose-400 bg-rose-50 focus:border-rose-500 focus:ring-rose-100/80 animate-shake'
                      : 'border-slate-200 focus:border-emerald-400 focus:ring-emerald-100/80'
                  ]"
                  placeholder="VD: Công ty ABC"
                  maxlength="150"
                  @input="formErrors.manufacturer = false"
                />
              </div>
              <div class="space-y-1.5">
                <label class="text-xs font-semibold uppercase tracking-wide text-slate-500" for="medication-expiryDate">
                  Hạn sử dụng *
                </label>
                <input
                  id="medication-expiryDate"
                  v-model="formState.expiryDate"
                  type="date"
                  :class="[
                    'w-full rounded-xl border px-4 py-2 text-sm text-slate-700 shadow-sm transition focus:outline-none focus:ring-4',
                    formErrors.expiryDate
                      ? 'border-rose-400 bg-rose-50 focus:border-rose-500 focus:ring-rose-100/80 animate-shake'
                      : 'border-slate-200 focus:border-emerald-400 focus:ring-emerald-100/80'
                  ]"
                  @input="formErrors.expiryDate = false"
                />
              </div>
            </div>
            <div class="space-y-1.5">
              <label class="text-xs font-semibold uppercase tracking-wide text-slate-500" for="medication-stockQuantity">
                Số lượng *
              </label>
              <input
                id="medication-stockQuantity"
                v-model.number="formState.stockQuantity"
                type="number"
                :class="[
                  'w-full rounded-xl border px-4 py-2 text-sm text-slate-700 shadow-sm transition focus:outline-none focus:ring-4',
                  formErrors.stockQuantity
                    ? 'border-rose-400 bg-rose-50 focus:border-rose-500 focus:ring-rose-100/80 animate-shake'
                    : 'border-slate-200 focus:border-emerald-400 focus:ring-emerald-100/80'
                ]"
                placeholder="0"
                @input="formErrors.stockQuantity = false"
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
                <span>{{ modalMode === 'create' ? 'Thêm thuốc' : 'Lưu thay đổi' }}</span>
              </button>
            </div>
          </form>
        </div>
      </div>
    </Transition>

    <!-- Modal Chi Tiết -->
    <Transition name="fade">
      <div
        v-if="detailModalOpen && detailMedication"
        class="fixed inset-0 z-[90] flex items-center justify-center bg-slate-900/50 backdrop-blur-sm px-4"
      >
        <div class="w-full max-w-2xl max-h-[90vh] overflow-y-auto rounded-3xl border border-emerald-100 bg-white p-6 shadow-xl">
          <div class="flex items-start justify-between">
            <div>
              <p class="text-xs font-semibold uppercase tracking-[0.35em] text-emerald-500">Chi tiết thuốc</p>
              <h2 class="mt-2 text-xl font-semibold text-slate-900">{{ detailMedication.name }}</h2>
              <p class="text-sm text-slate-500">ID: {{ detailMedication.id }}</p>
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
              <span class="w-32 shrink-0 text-xs font-semibold uppercase tracking-wide text-slate-500">Tên thuốc</span>
              <span class="font-semibold text-slate-900">{{ detailMedication.name }}</span>
            </div>
            <div class="flex items-start gap-3">
              <span class="w-32 shrink-0 text-xs font-semibold uppercase tracking-wide text-slate-500">Hoạt chất</span>
              <span>{{ detailMedication.activeIngredient || '—' }}</span>
            </div>
            <div class="flex items-start gap-3">
              <span class="w-32 shrink-0 text-xs font-semibold uppercase tracking-wide text-slate-500">Hàm lượng</span>
              <span>{{ detailMedication.strength || '—' }}</span>
            </div>
            <div class="flex items-start gap-3">
              <span class="w-32 shrink-0 text-xs font-semibold uppercase tracking-wide text-slate-500">Mã lô</span>
              <span>{{ detailMedication.batchNo || '—' }}</span>
            </div>
            <div class="flex items-start gap-3">
              <span class="w-32 shrink-0 text-xs font-semibold uppercase tracking-wide text-slate-500">Đơn vị</span>
              <span>{{ detailMedication.unit || '—' }}</span>
            </div>
            <div class="flex items-start gap-3">
              <span class="w-32 shrink-0 text-xs font-semibold uppercase tracking-wide text-slate-500">Đơn giá</span>
              <span class="font-semibold text-emerald-600">{{ formatCurrency(detailMedication.unitPrice) }}</span>
            </div>
            <div class="flex items-start gap-3">
              <span class="w-32 shrink-0 text-xs font-semibold uppercase tracking-wide text-slate-500">Nhà sản xuất</span>
              <span>{{ detailMedication.manufacturer || '—' }}</span>
            </div>
            <div class="flex items-start gap-3">
              <span class="w-32 shrink-0 text-xs font-semibold uppercase tracking-wide text-slate-500">Số lượng</span>
              <span class="font-semibold">{{ detailMedication.stockQuantity ?? 0 }}</span>
            </div>
            <div class="flex items-start gap-3">
              <span class="w-32 shrink-0 text-xs font-semibold uppercase tracking-wide text-slate-500">Hạn sử dụng</span>
              <div class="flex flex-col gap-1">
                <span>{{ formatDate(detailMedication.expiryDate) }}</span>
                <span v-if="getExpiryStatus(detailMedication.expiryDate).text" :class="getExpiryStatus(detailMedication.expiryDate).className" class="text-xs">
                  {{ getExpiryStatus(detailMedication.expiryDate).text }}
                </span>
              </div>
            </div>
            <div class="flex items-start gap-3">
              <span class="w-32 shrink-0 text-xs font-semibold uppercase tracking-wide text-slate-500">Tạo lúc</span>
              <span>{{ formatDateTime(detailMedication.createdAt) }}</span>
            </div>
            <div class="flex items-start gap-3">
              <span class="w-32 shrink-0 text-xs font-semibold uppercase tracking-wide text-slate-500">Cập nhật</span>
              <span>{{ formatDateTime(detailMedication.updatedAt) }}</span>
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
              <h3 class="text-lg font-semibold text-slate-900">Xóa thuốc?</h3>
              <p class="mt-1 text-sm text-slate-600">
                Bạn sắp xóa thuốc <span class="font-semibold text-rose-600">{{ deleteTarget.name }}</span>. Hành động này không thể hoàn tác.
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
              <span>{{ deleting ? 'Đang xóa...' : 'Xóa thuốc' }}</span>
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
