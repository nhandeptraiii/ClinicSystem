<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { useRouter } from 'vue-router';
import AdminHeader from '@/components/AdminHeader.vue';
import { useAuthStore } from '@/stores/authStore';
import { useToast, type ToastType } from '@/composables/useToast';
import {
  fetchBillingPage,
  fetchBillingById,
  generateBillingForVisit,
  updateBillingStatus,
  deleteBillingItem,
  printBilling,
  type Billing,
  type BillingItemType,
  type BillingStatus,
  type BillingStatusUpdatePayload,
} from '@/services/billing.service';
import { fetchCompletedVisitsWithoutBilling, type PatientVisit } from '@/services/visit.service';

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

const handleSignOut = async () => {
  await authStore.signOut();
  router.push({ name: 'login' });
};

const PAGE_SIZE = 10;
const VISIT_PAGE_SIZE = 8;

const billings = ref<Billing[]>([]);
const loading = ref(false);
const listError = ref<string | null>(null);
const searchTerm = ref('');
const statusFilter = ref<string>('');
const currentPage = ref(1);
const totalPages = ref(1);
const totalElements = ref(0);
const hasNext = ref(false);
const hasPrevious = ref(false);

const selectedBillingId = ref<number | null>(null);
const selectedBilling = ref<Billing | null>(null);
const loadingDetail = ref(false);

const visitCandidates = ref<PatientVisit[]>([]);
const visitLoading = ref(false);
const visitSearchTerm = ref('');
const visitCurrentPage = ref(1);
const visitTotalPages = ref(1);
const visitTotalElements = ref(0);
const visitHasNext = ref(false);
const visitHasPrevious = ref(false);
const selectedVisitCandidateId = ref<number | null>(null);

const statusOptions: Array<{ value: string; label: string }> = [
  { value: '', label: 'Tất cả trạng thái' },
  { value: 'UNPAID', label: 'Chưa thanh toán' },
  { value: 'PAID', label: 'Đã thanh toán' },
  { value: 'CANCELLED', label: 'Đã hủy' },
];

const billingStatusLabels: Record<BillingStatus, string> = {
  UNPAID: 'Chưa thanh toán',
  PARTIALLY_PAID: 'Chưa thanh toán',
  PAID: 'Đã thanh toán',
  CANCELLED: 'Đã hủy',
};

const itemTypeOptions: Array<{ value: BillingItemType; label: string }> = [
  { value: 'SERVICE', label: 'Dịch vụ' },
  { value: 'MEDICATION', label: 'Thuốc' },
  { value: 'OTHER', label: 'Khác' },
];

const statusCounts = ref({
  ALL: 0,
  UNPAID: 0,
  PAID: 0,
  CANCELLED: 0,
});

const statusFilterPills = [
  { value: '', label: 'Tất cả', dataKey: 'ALL' },
  { value: 'UNPAID', label: 'Chưa thanh toán', dataKey: 'UNPAID' },
  { value: 'PAID', label: 'Đã thanh toán', dataKey: 'PAID' },
  { value: 'CANCELLED', label: 'Đã hủy', dataKey: 'CANCELLED' },
];

const loadStatusCounts = async () => {
  try {
    const [all, unpaid, paid, cancelled] = await Promise.all([
      fetchBillingPage({ page: 0, size: 1 }),
      fetchBillingPage({ page: 0, size: 1, status: 'UNPAID' }),
      fetchBillingPage({ page: 0, size: 1, status: 'PAID' }),
      fetchBillingPage({ page: 0, size: 1, status: 'CANCELLED' }),
    ]);
    statusCounts.value = {
      ALL: all.totalElements ?? 0,
      UNPAID: unpaid.totalElements ?? 0,
      PAID: paid.totalElements ?? 0,
      CANCELLED: cancelled.totalElements ?? 0,
    };
  } catch (error) {
    console.error('Failed to load status counts', error);
  }
};

const formatCurrency = (value: number | undefined | null) => {
  const numeric = typeof value === 'number' ? value : 0;
  return new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' }).format(numeric);
};

const formatDateTime = (value?: string | null) => {
  if (!value) return 'N/A';
  try {
    return new Intl.DateTimeFormat('vi-VN', {
      dateStyle: 'medium',
      timeStyle: 'short',
    }).format(new Date(value));
  } catch {
    return value;
  }
};

const getStatusBadgeClass = (status?: BillingStatus) => {
  switch (status) {
    case 'UNPAID':
      return 'bg-amber-100 text-amber-800 border-amber-200';
    case 'PARTIALLY_PAID':
      return 'bg-amber-100 text-amber-800 border-amber-200';
    case 'PAID':
      return 'bg-emerald-100 text-emerald-800 border-emerald-200';
    case 'CANCELLED':
      return 'bg-rose-100 text-rose-800 border-rose-200';
    default:
      return 'bg-slate-100 text-slate-800 border-slate-200';
  }
};

const getItemTypeLabel = (type: BillingItemType) => {
  const option = itemTypeOptions.find((opt) => opt.value === type);
  return option?.label ?? type;
};

const serviceItems = computed(() => {
  return selectedBilling.value?.items?.filter((it) => it.itemType === 'SERVICE') ?? [];
});

const medicationItems = computed(() => {
  return selectedBilling.value?.items?.filter((it) => it.itemType === 'MEDICATION') ?? [];
});

const otherItems = computed(() => {
  return selectedBilling.value?.items?.filter((it) => it.itemType !== 'SERVICE' && it.itemType !== 'MEDICATION') ?? [];
});
const visitPaginationLabel = computed(() => {
  if (!visitTotalElements.value) {
    return `Đang hiển thị ${visitCandidates.value.length} hồ sơ khám`;
  }
  return `Đang hiển thị ${visitCandidates.value.length} / ${visitTotalElements.value} hồ sơ khám`;
});

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

const loadBillingDetail = async (billingId: number) => {
  loadingDetail.value = true;
  try {
    selectedBilling.value = await fetchBillingById(billingId);
  } catch (error) {
    const message = extractErrorMessage(error);
    showToast('error', message);
  } finally {
    loadingDetail.value = false;
  }
};

const loadBillings = async (keepSelection = false) => {
  loading.value = true;
  listError.value = null;
  const keyword = searchTerm.value.trim();
  const status =
    statusFilter.value && (['UNPAID', 'PAID', 'CANCELLED'] as string[]).includes(statusFilter.value)
      ? (statusFilter.value as BillingStatus)
      : undefined;
  const pageIndex = Math.max(currentPage.value - 1, 0);
  try {
    const response = await fetchBillingPage({
      page: pageIndex,
      size: PAGE_SIZE,
      keyword: keyword ? keyword : undefined,
      status,
    });
    billings.value = response.items ?? [];
    totalElements.value = response.totalElements ?? 0;
    totalPages.value = response.totalPages && response.totalPages > 0 ? response.totalPages : 1;
    currentPage.value =
      response.totalPages && response.totalPages > 0 ? Math.min(response.page + 1, totalPages.value) : 1;
    hasNext.value = response.hasNext ?? false;
    hasPrevious.value = response.hasPrevious ?? false;

    if (billings.value.length === 0) {
      selectedBillingId.value = null;
      selectedBilling.value = null;
      return;
    }

    if (
      !keepSelection ||
      !selectedBillingId.value ||
      !billings.value.some((item) => item.id === selectedBillingId.value)
    ) {
      const firstBilling = billings.value[0];
      if (firstBilling?.id != null) {
        selectedBillingId.value = firstBilling.id;
        await loadBillingDetail(firstBilling.id);
      }
    }
  } catch (error) {
    const message = extractErrorMessage(error);
    listError.value = message;
    showToast('error', message);
  } finally {
    loading.value = false;
  }
};

const selectBilling = async (billing: Billing) => {
  if (selectedBillingId.value === billing.id) {
    return;
  }
  selectedBillingId.value = billing.id;
  await loadBillingDetail(billing.id);
};

const goToVisitDetail = (visitId?: number) => {
  if (!visitId) return;
  router.push({ name: 'visit-detail', params: { id: visitId } });
};

const refreshBillings = async () => {
  await Promise.all([loadBillings(true), loadStatusCounts()]);
};

const goToPage = async (page: number) => {
  const target = Math.min(Math.max(page, 1), totalPages.value || 1);
  if (target === currentPage.value) {
    return;
  }
  currentPage.value = target;
  await loadBillings();
};

const nextPage = async () => {
  if (hasNext.value) {
    await goToPage(currentPage.value + 1);
  }
};

const prevPage = async () => {
  if (hasPrevious.value) {
    await goToPage(currentPage.value - 1);
  }
};

let filterTimer: ReturnType<typeof setTimeout> | null = null;
let visitSearchTimer: ReturnType<typeof setTimeout> | null = null;

const loadCompletedVisitCandidates = async (targetPage?: number) => {
  visitLoading.value = true;
  const keyword = visitSearchTerm.value.trim();
  const requestedPage = targetPage ?? visitCurrentPage.value;
  const pageIndex = Math.max(requestedPage - 1, 0);
  try {
    const response = await fetchCompletedVisitsWithoutBilling({
      keyword: keyword ? keyword : undefined,
      page: pageIndex,
      size: VISIT_PAGE_SIZE,
    });
    visitCandidates.value = response.items ?? [];
    visitTotalElements.value = response.totalElements ?? visitCandidates.value.length;
    visitTotalPages.value = response.totalPages && response.totalPages > 0 ? response.totalPages : 1;
    visitCurrentPage.value =
      response.totalPages && response.totalPages > 0 ? Math.min(response.page + 1, visitTotalPages.value) : 1;
    visitHasNext.value = response.hasNext ?? false;
    visitHasPrevious.value = response.hasPrevious ?? false;
    if (
      selectedVisitCandidateId.value &&
      !visitCandidates.value.some((visit) => visit.id === selectedVisitCandidateId.value)
    ) {
      selectedVisitCandidateId.value = null;
    }
    if (!selectedVisitCandidateId.value && visitCandidates.value.length > 0) {
      const firstCandidate = visitCandidates.value[0];
      if (firstCandidate?.id != null) {
        selectedVisitCandidateId.value = firstCandidate.id;
      }
    }
  } catch (error) {
    const message = extractErrorMessage(error);
    showToast('error', message);
    visitCandidates.value = [];
    visitTotalElements.value = 0;
    visitTotalPages.value = 1;
    visitCurrentPage.value = 1;
    visitHasNext.value = false;
    visitHasPrevious.value = false;
  } finally {
    visitLoading.value = false;
  }
};

const selectVisitCandidate = (visit: PatientVisit) => {
  selectedVisitCandidateId.value = visit.id;
};

const goToVisitCandidatePage = async (page: number) => {
  const target = Math.min(Math.max(page, 1), visitTotalPages.value || 1);
  if (target === visitCurrentPage.value) {
    return;
  }
  visitCurrentPage.value = target;
  await loadCompletedVisitCandidates(target);
};

const nextVisitCandidatePage = () => {
  if (visitHasNext.value) {
    void goToVisitCandidatePage(visitCurrentPage.value + 1);
  }
};

const prevVisitCandidatePage = () => {
  if (visitHasPrevious.value) {
    void goToVisitCandidatePage(visitCurrentPage.value - 1);
  }
};

const scheduleFilterReload = () => {
  if (filterTimer) {
    clearTimeout(filterTimer);
  }
  filterTimer = setTimeout(() => {
    currentPage.value = 1;
    void loadBillings();
  }, 350);
};

watch(searchTerm, scheduleFilterReload);
watch(statusFilter, scheduleFilterReload);

watch(visitSearchTerm, () => {
  if (!generateModalOpen.value) {
    return;
  }
  if (visitSearchTimer) {
    clearTimeout(visitSearchTimer);
  }
  visitSearchTimer = setTimeout(() => {
    visitCurrentPage.value = 1;
    void loadCompletedVisitCandidates(1);
  }, 350);
});

const statusModalOpen = ref(false);
const statusSubmitting = ref(false);
const statusForm = ref<BillingStatusUpdatePayload>({
  status: 'UNPAID',
  paymentMethod: null,
  notes: null,
});

const openStatusModal = () => {
  if (!selectedBilling.value) return;
  statusForm.value = {
    status: selectedBilling.value.status ?? 'UNPAID',
    paymentMethod: selectedBilling.value.paymentMethod ?? null,
    notes: selectedBilling.value.notes ?? null,
  };
  statusModalOpen.value = true;
};

const submitStatusUpdate = async () => {
  if (!selectedBilling.value) return;
  statusSubmitting.value = true;
  try {
    const payload: BillingStatusUpdatePayload = {
      status: statusForm.value.status,
      paymentMethod: statusForm.value.paymentMethod ?? null,
      notes: statusForm.value.notes?.trim()
        ? statusForm.value.notes.trim()
        : null,
    };
    const updated = await updateBillingStatus(selectedBilling.value.id, payload);
    selectedBilling.value = updated;
    showToast('success', 'Đã cập nhật trạng thái hóa đơn.');
    statusModalOpen.value = false;
    selectedBillingId.value = updated.id;
    await loadBillings(true);
  } catch (error) {
    const message = extractErrorMessage(error);
    showToast('error', message);
  } finally {
    statusSubmitting.value = false;
  }
};

const removeBillingItem = async (itemId: number) => {
  if (!selectedBilling.value) return;
  const confirmDelete = window.confirm('Bạn có chắc chắn muốn xóa mục này khỏi hóa đơn?');
  if (!confirmDelete) return;
  try {
    await deleteBillingItem(selectedBilling.value.id, itemId);
    showToast('success', 'Đã xóa mục hóa đơn.');
    await loadBillingDetail(selectedBilling.value.id);
    await loadBillings(true);
  } catch (error) {
    const message = extractErrorMessage(error);
    showToast('error', message);
  }
};

const printInvoice = async () => {
  if (!selectedBilling.value) return;
  try {
    const blob = await printBilling(selectedBilling.value.id);
    const url = URL.createObjectURL(blob);
    window.open(url, '_blank');
    setTimeout(() => URL.revokeObjectURL(url), 10000);
  } catch (error) {
    const message = extractErrorMessage(error);
    showToast('error', message);
  }
};

const generateModalOpen = ref(false);
const generateSubmitting = ref(false);
const openGenerateModal = () => {
  visitSearchTerm.value = '';
  visitCurrentPage.value = 1;
  visitTotalPages.value = 1;
  visitTotalElements.value = 0;
  visitHasNext.value = false;
  visitHasPrevious.value = false;
  selectedVisitCandidateId.value = null;
  generateModalOpen.value = true;
  void loadCompletedVisitCandidates(1);
};

const submitGenerateBilling = async () => {
  if (!selectedVisitCandidateId.value) {
    showToast('error', 'Vui lòng chọn một hồ sơ khám để tạo hóa đơn.');
    return;
  }
  generateSubmitting.value = true;
  try {
    const billing = await generateBillingForVisit(selectedVisitCandidateId.value);
    showToast('success', 'Đã tạo hóa đơn từ hồ sơ khám.');
    generateModalOpen.value = false;
    selectedBillingId.value = billing.id;
    await loadBillings();
  } catch (error) {
    const message = extractErrorMessage(error);
    showToast('error', message);
  } finally {
    generateSubmitting.value = false;
  }
};

onMounted(() => {
  void loadBillings();
  void loadStatusCounts();
});

onBeforeUnmount(() => {
  if (filterTimer) {
    clearTimeout(filterTimer);
  }
  if (visitSearchTimer) {
    clearTimeout(visitSearchTimer);
  }
});
</script>

<template>
  <div class="relative min-h-screen bg-gradient-to-br from-emerald-50 via-white to-white">
    <AdminHeader :user-name="userName" @sign-out="handleSignOut" />

    <main class="relative mx-auto max-w-7xl px-4 pb-20 pt-10 sm:px-6 lg:px-8">
      <section class="rounded-[32px] border border-emerald-100 bg-white/90 p-8 shadow-[0_24px_60px_-40px_rgba(13,148,136,0.6)]">
        <div class="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
          <div class="max-w-2xl">
            <span class="inline-flex items-center gap-2 rounded-full bg-emerald-50 px-4 py-1.5 text-xs font-semibold uppercase tracking-[0.3em] text-emerald-600">
              Quản lý thanh toán
            </span>
            <h1 class="mt-4 text-2xl font-semibold text-slate-900 md:text-3xl">Danh sách hóa đơn</h1>
            <p class="mt-3 text-sm leading-relaxed text-slate-600">
               Theo dõi tình trạng thanh toán, chỉnh sửa các mục phí và cập nhật trạng thái hóa đơn của bệnh nhân.
            </p>
          </div>
          <div class="flex flex-col items-start gap-3 sm:flex-row sm:items-center lg:flex-col lg:items-end">
            <div class="flex flex-wrap items-center gap-2">
              <button
                type="button"
                class="inline-flex items-center gap-2 whitespace-nowrap rounded-full border border-emerald-200 bg-emerald-600 px-5 py-2 text-xs font-semibold uppercase tracking-wide text-white shadow-sm transition hover:bg-emerald-700"
                @click="openGenerateModal"
              >
                <svg xmlns="http://www.w3.org/2000/svg" class="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.8">
                  <path stroke-linecap="round" stroke-linejoin="round" d="M12 4.5v15m7.5-7.5h-15" />
                </svg>
                <span>Tạo hóa đơn</span>
              </button>
              <button
                type="button"
                class="inline-flex items-center gap-2 rounded-full border border-emerald-200 bg-white px-5 py-2 text-xs font-semibold uppercase tracking-wide text-emerald-600 shadow-sm transition hover:border-emerald-300 hover:bg-emerald-50 disabled:cursor-not-allowed disabled:opacity-60"
                :disabled="loading"
                @click="refreshBillings"
              >
                <svg xmlns="http://www.w3.org/2000/svg" class="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.8">
                  <path stroke-linecap="round" stroke-linejoin="round" d="M4 4v5h.582m15.356 2A8.001 8.001 0 0 0 5.582 9H4m0 0a8.003 8.003 0 0 1 14.9-3m1.518 9H20v5h.004m-.586-2A8.001 8.001 0 0 1 5.518 15H4m0 0a8.003 8.003 0 0 0 14.9 3" />
                </svg>
                <span>{{ loading ? 'Đang tải...' : 'Làm mới' }}</span>
              </button>
            </div>
            <div class="rounded-2xl border border-emerald-100 bg-emerald-50/70 px-4 py-2 text-sm text-emerald-700 shadow-inner">
               <p class="font-semibold">Tổng hóa đơn: {{ statusCounts.ALL }}</p>
            </div>
          </div>
        </div>

        <div class="mt-8 grid gap-4 sm:grid-cols-3">
          <div class="rounded-2xl border border-amber-100 bg-amber-50/70 p-5 text-amber-700 shadow-sm">
            <p class="text-xs font-semibold uppercase tracking-wide text-amber-600/80">Chưa thanh toán</p>
            <p class="mt-2 text-3xl font-semibold">{{ statusCounts.UNPAID }}</p>
            <p class="mt-2 text-xs text-amber-600/80">Cần theo dõi và nhắc nhở</p>
          </div>
          <div class="rounded-2xl border border-emerald-100 bg-emerald-50/70 p-5 text-emerald-700 shadow-sm">
            <p class="text-xs font-semibold uppercase tracking-wide text-emerald-600/80">Đã thanh toán</p>
            <p class="mt-2 text-3xl font-semibold">{{ statusCounts.PAID }}</p>
            <p class="mt-2 text-xs text-emerald-600/80">Doanh thu đã ghi nhận</p>
          </div>
          <div class="rounded-2xl border border-rose-100 bg-rose-50/70 p-5 text-rose-700 shadow-sm">
            <p class="text-xs font-semibold uppercase tracking-wide text-rose-600/80">Đã hủy</p>
            <p class="mt-2 text-3xl font-semibold">{{ statusCounts.CANCELLED }}</p>
            <p class="mt-2 text-xs text-rose-600/80">Hóa đơn không còn hiệu lực</p>
          </div>
        </div>

        <div class="mt-8 flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
          <div class="relative flex-1 max-w-md">
            <svg class="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" fill="none" stroke="currentColor" stroke-width="1.5" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" d="m21 21-4.35-4.35m0 0A7.35 7.35 0 1 0 6.3 6.3a7.35 7.35 0 0 0 10.35 10.35Z" />
            </svg>
            <input
              v-model.trim="searchTerm"
              type="search"
              placeholder="Tìm theo tên hoặc mã bệnh nhân..."
              class="w-full rounded-xl border border-emerald-100 bg-white py-2.5 pl-10 pr-4 text-sm text-slate-700 shadow-sm transition focus:border-emerald-400 focus:outline-none focus:ring-4 focus:ring-emerald-100/80"
            />
          </div>
          <div class="w-full sm:w-56">
             <select
                v-model="statusFilter"
                class="w-full rounded-xl border border-emerald-100 bg-white py-2.5 px-4 text-sm text-slate-700 shadow-sm transition focus:border-emerald-400 focus:outline-none focus:ring-4 focus:ring-emerald-100/80"
              >
                <option v-for="filter in statusFilterPills" :key="filter.value" :value="filter.value">
                   {{ filter.label }} ({{ statusCounts[filter.dataKey] }})
                </option>
              </select>
          </div>
        </div>

      </section>

      <section class="mt-10 mb-6">

        <div class="grid gap-6 xl:grid-cols-[1.5fr_1.2fr]">
          <div class="rounded-3xl border border-emerald-100 bg-white/90 p-6 shadow h-full flex flex-col">
            <div class="flex items-center justify-between border-b border-slate-200 pb-4">
              <div>
                <h2 class="text-lg font-semibold text-slate-900">Danh sách hóa đơn</h2>
                <p class="text-sm text-slate-600">Nhấn vào một dòng để xem chi tiết</p>
              </div>
              <div class="flex items-center gap-2 text-xs text-slate-500">
                <span class="inline-flex h-2.5 w-2.5 rounded-full bg-emerald-500"></span>
                Đang hiển thị {{ billings.length }} / {{ totalElements }}
              </div>
            </div>

            <div class="mt-4 flex-1">
              <div v-if="loading" class="rounded-2xl border border-slate-200 bg-slate-50/70 p-6 text-center text-sm text-slate-600">
                Đang tải dữ liệu hóa đơn...
              </div>
              <div v-else-if="listError" class="rounded-2xl border border-rose-200 bg-rose-50/80 p-6 text-sm text-rose-600">
                {{ listError }}
              </div>
              <div v-else-if="billings.length === 0" class="rounded-2xl border border-slate-200 bg-slate-50/60 p-6 text-center text-sm text-slate-600">
                Chưa có hóa đơn nào phù hợp với bộ lọc hiện tại.
              </div>
              <div v-else class="space-y-3">
                <button
                  v-for="billing in billings"
                  :key="billing.id"
                  type="button"
                  class="w-full rounded-2xl border p-5 text-left transition focus:outline-none"
                  :class="[
                    selectedBillingId === billing.id
                      ? 'border-emerald-300 bg-emerald-50/70 shadow-md ring-2 ring-emerald-200/70'
                      : 'border-emerald-100 bg-white hover:border-emerald-200 hover:bg-emerald-50/40'
                  ]"
                  @click="selectBilling(billing)"
                >
                  <div class="flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between">
                    <div class="flex items-start gap-3">
                         <span
                          class="mt-1 flex h-2.5 w-2.5 flex-shrink-0 rounded-full"
                          :class="{
                            'bg-amber-500': billing.status === 'UNPAID' || billing.status === 'PARTIALLY_PAID',
                            'bg-emerald-500': billing.status === 'PAID',
                            'bg-rose-500': billing.status === 'CANCELLED',
                            'bg-slate-400': !billing.status
                          }"
                        ></span>
                      <div>
                        <p class="text-sm font-semibold uppercase tracking-wider text-slate-400">{{ formatDateTime(billing.issuedAt) }}</p>
                        <h3 class="mt-1 text-lg font-semibold text-slate-900">{{ billing.patient?.fullName ?? 'Không xác định' }}</h3>
                        <p class="mt-1 text-sm text-slate-600">
                           Mã BN: {{ billing.patient?.code ?? '—' }}
                        </p>
                        <div class="mt-2 grid gap-x-6 gap-y-1 text-sm text-slate-600 sm:grid-cols-2">
                             <div>
                                <span class="font-semibold text-slate-500">Tổng cộng:</span>
                                <span class="ml-1 text-slate-900">{{ formatCurrency(billing.totalAmount) }}</span>
                            </div>
                           <div>
                                <span class="font-semibold text-slate-500">Phương thức:</span>
                                <span class="ml-1 text-slate-700">{{ billing.paymentMethod ?? 'Chưa cập nhật' }}</span>
                           </div>
                        </div>
                      </div>
                    </div>
                    <div class="flex flex-col items-start gap-2 text-sm text-slate-500 sm:items-end">
                      <span
                        class="inline-flex items-center rounded-full px-3 py-1 text-xs font-semibold"
                        :class="getStatusBadgeClass(billing.status)"
                      >
                        {{ billingStatusLabels[billing.status ?? 'UNPAID'] ?? billing.status }}
                      </span>
                    </div>
                  </div>
                </button>
              </div>
            </div>

            <div class="mt-auto flex flex-col gap-3 border-t border-slate-200 pt-4 text-sm text-slate-600 md:flex-row md:items-center md:justify-between">
              <span>Trang {{ currentPage }} / {{ totalPages }}</span>
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
          </div>

          <div class="rounded-3xl border border-emerald-100 bg-white/90 p-6 shadow">
            <div v-if="!selectedBillingId" class="flex h-full flex-col items-center justify-center gap-4 text-center text-sm text-slate-600">
              <div class="rounded-full bg-emerald-50 p-3 text-emerald-500">
                <svg xmlns="http://www.w3.org/2000/svg" class="h-6 w-6" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.8">
                  <path stroke-linecap="round" stroke-linejoin="round" d="M8 6h13M11 12h10M5 18h16M3 6h.01M3 12h.01M3 18h.01" />
                </svg>
              </div>
              <p>Hãy chọn một hóa đơn ở bảng bên trái để xem chi tiết.</p>
            </div>
            <div v-else>
              <div class="flex items-start justify-between gap-3 border-b border-slate-200 pb-4">
                <div>
                  <h2 class="text-lg font-semibold text-slate-900">Chi tiết hóa đơn</h2>
                  <p class="text-sm text-slate-600">Mã hóa đơn: #{{ selectedBilling?.id }}</p>
                </div>
                <span
                  class="inline-flex items-center rounded-full border px-2.5 py-0.5 text-xs font-semibold uppercase tracking-wide"
                  :class="getStatusBadgeClass(selectedBilling?.status)"
                >
                  {{ billingStatusLabels[selectedBilling?.status ?? 'UNPAID'] ?? selectedBilling?.status }}
                </span>
              </div>

              <div v-if="loadingDetail" class="mt-6 rounded-2xl border border-slate-200 bg-slate-50/70 p-6 text-center text-sm text-slate-600">
                Đang tải chi tiết hóa đơn...
              </div>
              <div v-else-if="selectedBilling" class="mt-4 space-y-6">
                <div class="space-y-2 rounded-2xl border border-slate-200 bg-white px-4 py-3 text-sm text-slate-600 shadow-sm">
                  <div class="flex flex-wrap items-center gap-3">
                    <div>
                      <span class="text-xs font-semibold uppercase tracking-wide text-slate-500">Bệnh nhân</span>
                      <p class="text-sm font-semibold text-slate-900">
                        {{ selectedBilling.patient?.fullName ?? 'Không xác định' }}
                      </p>
                      <p class="text-xs text-slate-500">
                        SĐT: {{ selectedBilling.patient?.phone ?? '—' }}
                      </p>
                    </div>
                  </div>
                  <div class="grid gap-3 text-xs text-slate-600 md:grid-cols-2">
                    <div>
                      <span class="font-semibold text-slate-500">Ngày lập:</span>
                      <span class="ml-1 text-slate-700">{{ formatDateTime(selectedBilling.issuedAt) }}</span>
                    </div>
                    <div>
                      <span class="font-semibold text-slate-500">Phương thức thanh toán:</span>
                      <span class="ml-1 text-slate-700">{{ selectedBilling.paymentMethod ?? 'Chưa cập nhật' }}</span>
                    </div>
                    <div>
                      <span class="font-semibold text-slate-500">Ghi chú:</span>
                      <span class="ml-1 text-slate-700">{{ selectedBilling.notes ?? 'Không có' }}</span>
                    </div>
                    <div>
                      <span class="font-semibold text-slate-500">Hồ sơ khám:</span>
                      <button
                        v-if="selectedBilling.visit?.id"
                        type="button"
                        class="ml-1 inline-flex items-center gap-1 rounded-full border border-emerald-200 bg-white px-2.5 py-0.5 text-xs font-semibold uppercase tracking-wide text-emerald-600 shadow-sm transition hover:border-emerald-300 hover:bg-emerald-50"
                        @click="goToVisitDetail(selectedBilling.visit.id)"
                      >
                        <svg xmlns="http://www.w3.org/2000/svg" class="h-3.5 w-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.8">
                          <path stroke-linecap="round" stroke-linejoin="round" d="m9 5 7 7-7 7" />
                        </svg>
                        Xem hồ sơ
                      </button>
                      <span v-else class="ml-1 text-slate-700">Không xác định</span>
                    </div>
                  </div>
                </div>

                <div class="rounded-2xl border border-emerald-100 bg-emerald-50/40 p-4 text-sm text-slate-700">
                  <div class="grid gap-3 md:grid-cols-2">
                    <div>
                      <span class="font-semibold text-slate-600">Dịch vụ:</span>
                      <span class="ml-1 text-slate-900">{{ formatCurrency(selectedBilling.serviceTotal) }}</span>
                    </div>
                    <div>
                      <span class="font-semibold text-slate-600">Thuốc:</span>
                      <span class="ml-1 text-slate-900">{{ formatCurrency(selectedBilling.medicationTotal) }}</span>
                    </div>
                    <div>
                      <span class="font-semibold text-slate-600">Tổng cộng:</span>
                      <span class="ml-1 text-lg font-semibold text-emerald-700">
                        {{ formatCurrency(selectedBilling.totalAmount) }}
                      </span>
                    </div>
                  </div>
                </div>

                <div class="flex items-center justify-between border-b border-slate-200 pb-3">
                  <h3 class="text-sm font-semibold text-slate-900">Danh sách mục hóa đơn</h3>
                  <div class="flex items-center gap-2">
                    <button
                      type="button"
                      class="inline-flex items-center gap-2 rounded-full border border-emerald-200 bg-white px-3 py-1.5 text-xs font-semibold uppercase tracking-wide text-emerald-600 shadow-sm transition hover:border-emerald-300 hover:bg-emerald-50"
                      @click="printInvoice"
                      :disabled="!selectedBilling"
                    >
                      <svg xmlns="http://www.w3.org/2000/svg" class="h-3.5 w-3.5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8">
                        <path stroke-linecap="round" stroke-linejoin="round" d="M6 9V2h12v7M6 14h12v8H6z" />
                      </svg>
                      In hóa đơn
                    </button>
                    <button
                      type="button"
                      class="inline-flex items-center gap-2 rounded-full border border-emerald-200 bg-white px-3 py-1.5 text-xs font-semibold uppercase tracking-wide text-emerald-600 shadow-sm transition hover:border-emerald-300 hover:bg-emerald-50"
                      @click="openStatusModal"
                    >
                      Cập nhật trạng thái
                    </button>
                  </div>
                </div>

                <div v-if="!selectedBilling.items || selectedBilling.items.length === 0" class="rounded-2xl border border-slate-200 bg-slate-50/70 p-4 text-center text-sm text-slate-600">
                  Hóa đơn chưa có mục nào. Hãy thêm mới hoặc tạo từ hồ sơ khám.
                </div>
                <div v-else class="space-y-4">
                  <div v-if="serviceItems.length">
                    <h4 class="text-sm font-semibold text-slate-800 mb-1">Dịch vụ</h4>
                    <div class="space-y-3">
                      <div
                        v-for="item in serviceItems"
                        :key="item.id"
                        class="rounded-2xl border border-slate-200 bg-white p-4 shadow-sm"
                      >
                        <div class="flex flex-wrap items-start justify-between gap-3">
                          <div>
                            <p class="text-sm font-semibold text-slate-900">{{ item.description }}</p>
                            <p class="text-xs text-slate-500">Loại: {{ getItemTypeLabel(item.itemType) }}</p>
                          </div>
                          <button
                            type="button"
                            class="inline-flex items-center gap-1 rounded-full border border-rose-200 bg-white px-2.5 py-1 text-[11px] font-semibold uppercase tracking-wide text-rose-600 shadow-sm transition hover:border-rose-300 hover:bg-rose-50"
                            @click="removeBillingItem(item.id)"
                          >
                            <svg xmlns="http://www.w3.org/2000/svg" class="h-3.5 w-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.8">
                              <path stroke-linecap="round" stroke-linejoin="round" d="m15 9-6 6m0-6 6 6M3 6h18" />
                            </svg>
                            Xóa
                          </button>
                        </div>
                        <div class="mt-3 flex flex-col gap-2 text-xs text-slate-600 sm:flex-row sm:items-center sm:justify-between">
                          <div class="flex items-center gap-2">
                             <span class="font-semibold text-slate-500">Thành tiền:</span>
                             <span class="text-sm font-semibold text-emerald-600">{{ formatCurrency(item.amount) }}</span>
                          </div>
                        </div>
                      </div>
                    </div>
                  </div>

                  <div v-if="medicationItems.length">
                    <h4 class="mt-2 text-sm font-semibold text-slate-800 mb-1">Đơn thuốc</h4>
                    <div class="space-y-3">
                      <div
                        v-for="item in medicationItems"
                        :key="item.id"
                        class="rounded-2xl border border-slate-200 bg-white p-4 shadow-sm"
                      >
                        <div class="flex flex-wrap items-start justify-between gap-3">
                          <div>
                            <p class="text-sm font-semibold text-slate-900">{{ item.description }}</p>
                            <p class="text-xs text-slate-500">Loại: {{ getItemTypeLabel(item.itemType) }}</p>
                          </div>
                          <button
                            type="button"
                            class="inline-flex items-center gap-1 rounded-full border border-rose-200 bg-white px-2.5 py-1 text-[11px] font-semibold uppercase tracking-wide text-rose-600 shadow-sm transition hover:border-rose-300 hover:bg-rose-50"
                            @click="removeBillingItem(item.id)"
                          >
                            <svg xmlns="http://www.w3.org/2000/svg" class="h-3.5 w-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.8">
                              <path stroke-linecap="round" stroke-linejoin="round" d="m15 9-6 6m0-6 6 6M3 6h18" />
                            </svg>
                            Xóa
                          </button>
                        </div>
                        <div class="mt-3 grid gap-2 text-xs text-slate-600 md:grid-cols-3">
                          <div>
                            <span class="font-semibold text-slate-500">Số lượng:</span>
                            <span class="ml-1 text-slate-700">{{ item.quantity }}</span>
                          </div>
                          <div>
                            <span class="font-semibold text-slate-500">Đơn giá:</span>
                            <span class="ml-1 text-slate-700">{{ formatCurrency(item.unitPrice) }}</span>
                          </div>
                          <div>
                            <span class="font-semibold text-slate-500">Thành tiền:</span>
                            <span class="ml-1  font-semibold text-emerald-600">{{ formatCurrency(item.amount) }}</span>
                          </div>
                        </div>
                      </div>
                    </div>
                  </div>

                  <div v-if="otherItems.length">
                    <h4 class="mt-2 text-sm font-semibold text-slate-800">Khác</h4>
                    <div class="space-y-3">
                      <div
                        v-for="item in otherItems"
                        :key="item.id"
                        class="rounded-2xl border border-slate-200 bg-white p-4 shadow-sm"
                      >
                        <div class="flex flex-wrap items-start justify-between gap-3">
                          <div>
                            <p class="text-sm font-semibold text-slate-900">{{ item.description }}</p>
                            <p class="text-xs text-slate-500">Loại: {{ getItemTypeLabel(item.itemType) }}</p>
                          </div>
                          <button
                            type="button"
                            class="inline-flex items-center gap-1 rounded-full border border-rose-200 bg-white px-2.5 py-1 text-[11px] font-semibold uppercase tracking-wide text-rose-600 shadow-sm transition hover:border-rose-300 hover:bg-rose-50"
                            @click="removeBillingItem(item.id)"
                          >
                            <svg xmlns="http://www.w3.org/2000/svg" class="h-3.5 w-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.8">
                              <path stroke-linecap="round" stroke-linejoin="round" d="m15 9-6 6m0-6 6 6M3 6h18" />
                            </svg>
                            Xóa
                          </button>
                        </div>
                        <div class="mt-3 grid gap-2 text-xs text-slate-600 md:grid-cols-3">
                          <div>
                            <span class="font-semibold text-slate-500">Số lượng:</span>
                            <span class="ml-1 text-slate-700">{{ item.quantity }}</span>
                          </div>
                          <div>
                            <span class="font-semibold text-slate-500">Đơn giá:</span>
                            <span class="ml-1 text-slate-700">{{ formatCurrency(item.unitPrice) }}</span>
                          </div>
                          <div>
                            <span class="font-semibold text-slate-500">Thành tiền:</span>
                            <span class="ml-1 text-slate-700">{{ formatCurrency(item.amount) }}</span>
                          </div>
                        </div>
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </section>
    </main>

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

    <!-- Update Status Modal -->
    <Transition name="fade">
      <div
        v-if="statusModalOpen"
        class="fixed inset-0 z-[110] flex items-center justify-center bg-slate-900/50 backdrop-blur-sm px-4"
        @click.self="statusModalOpen = false"
      >
        <div class="w-full max-w-lg rounded-3xl border border-emerald-100 bg-white p-6 shadow-xl">
          <div class="flex items-start justify-between">
            <div>
              <p class="text-xs font-semibold uppercase tracking-[0.35em] text-emerald-500">Cập nhật hóa đơn</p>
              <h2 class="mt-1 text-xl font-semibold text-slate-900">Cập nhật trạng thái thanh toán</h2>
            </div>
            <button
              type="button"
              class="flex h-9 w-9 items-center justify-center rounded-full border border-slate-200 text-slate-500 transition hover:border-slate-300 hover:text-slate-700"
              @click="statusModalOpen = false"
            >
              <svg xmlns="http://www.w3.org/2000/svg" class="h-4.5 w-4.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.8">
                <path stroke-linecap="round" stroke-linejoin="round" d="m16 8-8 8m0-8 8 8" />
              </svg>
            </button>
          </div>

          <div class="mt-6 space-y-4">
            <div class="space-y-1.5">
              <label class="text-xs font-semibold uppercase tracking-wide text-slate-500">Trạng thái</label>
              <select
                v-model="statusForm.status"
                class="w-full rounded-xl border border-slate-200 px-4 py-2 text-sm text-slate-700 shadow-sm transition focus:border-emerald-400 focus:outline-none focus:ring-4 focus:ring-emerald-100/80"
              >
                <option v-for="option in statusOptions.filter((opt) => opt.value)" :key="option.value" :value="option.value">
                  {{ option.label }}
                </option>
              </select>
            </div>
            <div class="space-y-1.5">
              <label class="text-xs font-semibold uppercase tracking-wide text-slate-500">Phương thức thanh toán</label>
              <select
                v-model="statusForm.paymentMethod"
                class="w-full rounded-xl border border-slate-200 px-4 py-2 text-sm text-slate-700 shadow-sm transition focus:border-emerald-400 focus:outline-none focus:ring-4 focus:ring-emerald-100/80"
              >
                <option :value="null">Không chọn</option>
                <option value="Tiền mặt">Tiền mặt</option>
                <option value="Chuyển khoản">Chuyển khoản</option>
              </select>
            </div>
            <div class="space-y-1.5">
              <label class="text-xs font-semibold uppercase tracking-wide text-slate-500">Ghi chú</label>
              <textarea
                v-model="statusForm.notes"
                rows="3"
                placeholder="Thêm ghi chú nếu cần"
                class="w-full rounded-xl border border-slate-200 px-4 py-2 text-sm text-slate-700 shadow-sm transition focus:border-emerald-400 focus:outline-none focus:ring-4 focus:ring-emerald-100/80"
              />
            </div>
          </div>

          <div class="mt-6 flex items-center justify-end gap-3 border-t border-slate-200 pt-4">
            <button
              type="button"
              class="inline-flex items-center gap-2 rounded-full border border-slate-200 bg-white px-5 py-2 text-xs font-semibold uppercase tracking-wide text-slate-600 shadow-sm transition hover:border-slate-300 hover:bg-slate-50"
              @click="statusModalOpen = false"
            >
              Hủy
            </button>
            <button
              type="button"
              class="inline-flex items-center gap-2 rounded-full border border-emerald-200 bg-emerald-600 px-5 py-2 text-xs font-semibold uppercase tracking-wide text-white shadow-sm transition hover:bg-emerald-700 disabled:cursor-not-allowed disabled:opacity-60"
              :disabled="statusSubmitting"
              @click="submitStatusUpdate"
            >
              <span v-if="statusSubmitting">Đang lưu...</span>
              <span v-else>Lưu thay đổi</span>
            </button>
          </div>
        </div>
      </div>
    </Transition>

    <!-- Generate Billing Modal -->
    <Transition name="fade">
      <div
        v-if="generateModalOpen"
        class="fixed inset-0 z-[110] flex items-center justify-center bg-slate-900/50 backdrop-blur-sm px-4"
        @click.self="generateModalOpen = false"
      >
        <div class="w-full max-w-4xl rounded-3xl border border-emerald-100 bg-white p-6 shadow-xl">
          <div class="flex items-start justify-between">
            <div>
              <p class="text-xs font-semibold uppercase tracking-[0.35em] text-emerald-500">Tạo hóa đơn</p>
              <h2 class="mt-1 text-xl font-semibold text-slate-900">Tạo hóa đơn từ hồ sơ khám</h2>
            </div>
            <button
              type="button"
              class="flex h-9 w-9 items-center justify-center rounded-full border border-slate-200 text-slate-500 transition hover:border-slate-300 hover:text-slate-700"
              @click="generateModalOpen = false"
            >
              <svg xmlns="http://www.w3.org/2000/svg" class="h-4.5 w-4.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.8">
                <path stroke-linecap="round" stroke-linejoin="round" d="m16 8-8 8m0-8 8 8" />
              </svg>
            </button>
          </div>

          <div class="mt-6 space-y-4">
            <div class="space-y-1.5">
              <label class="text-xs font-semibold uppercase tracking-wide text-slate-500">Tìm kiếm hồ sơ khám đã hoàn thành</label>
              <input
                v-model="visitSearchTerm"
                type="text"
                placeholder="Tìm theo tên bệnh nhân, mã hồ sơ..."
                class="w-full rounded-xl border border-slate-200 px-4 py-1.5 text-sm leading-tight text-slate-700 shadow-sm transition focus:border-emerald-400 focus:outline-none focus:ring-4 focus:ring-emerald-100/80"
              />
            </div>
            <div class="rounded-2xl border border-slate-200 bg-white/70 max-h-80 overflow-y-auto">
              <div v-if="visitLoading" class="p-4 text-center text-sm text-slate-600">
                Đang tải danh sách hồ sơ khám...
              </div>
              <div v-else-if="visitCandidates.length === 0" class="p-4 text-center text-sm text-slate-600">
                Không tìm thấy hồ sơ khám hoàn thành phù hợp.
              </div>
              <div v-else class="divide-y divide-slate-200">
                <label
                  v-for="visit in visitCandidates"
                  :key="visit.id"
                  class="flex cursor-pointer gap-3 p-4 transition hover:bg-emerald-50/60"
                >
                  <input
                    type="radio"
                    name="visit-selection"
                    class="mt-1 h-4 w-4 text-emerald-600 focus:ring-emerald-500"
                    :checked="selectedVisitCandidateId === visit.id"
                    @change="selectVisitCandidate(visit)"
                  />
                  <div class="flex-1">
                    <div class="flex flex-wrap items-center justify-between gap-2">
                      <div>
                        <p class="text-sm font-semibold text-slate-900">
                          {{ visit.patient?.fullName ?? 'Không xác định' }}
                        </p>
                        <p class="text-xs text-slate-500">
                          Mã bệnh nhân: {{ visit.patient?.code ?? '—' }}
                        </p>
                      </div>
                      <span
                        class="inline-flex items-center rounded-full border border-emerald-200 bg-emerald-50 px-2.5 py-0.5 text-xs font-semibold uppercase tracking-wide text-emerald-700"
                      >
                        Hoàn thành
                      </span>
                    </div>
                    <div class="mt-2 grid gap-2 text-xs text-slate-600 md:grid-cols-3">
                      <div>
                        <span class="font-semibold text-slate-500">Ngày khám:</span>
                        <span class="ml-1 text-slate-700">
                          {{ formatDateTime(visit.primaryAppointment?.scheduledAt ?? visit.createdAt) }}
                        </span>
                      </div>
                      <div>
                        <span class="font-semibold text-slate-500">Bác sĩ:</span>
                        <span class="ml-1 text-slate-700">
                          {{
                            visit.primaryAppointment?.doctor?.account?.fullName
                              ?? visit.primaryAppointment?.doctor?.specialty
                              ?? '—'
                          }}
                        </span>
                      </div>
                      <div>
                        <span class="font-semibold text-slate-500">Phòng khám:</span>
                        <span class="ml-1 text-slate-700">
                          {{ visit.primaryAppointment?.clinicRoom?.name ?? '—' }}
                        </span>
                      </div>
                    </div>
                  </div>
                </label>
              </div>
            </div>
            <div class="mt-3 flex flex-col gap-2 text-xs text-slate-600 sm:flex-row sm:items-center sm:justify-between">
              <span>{{ visitPaginationLabel }}</span>
              <div class="flex items-center gap-2">
                <button
                  type="button"
                  class="inline-flex items-center gap-2 rounded-full border border-emerald-200 bg-white px-3.5 py-1.5 text-[11px] font-semibold uppercase tracking-wide text-emerald-600 shadow-sm transition hover:border-emerald-300 hover:bg-emerald-50 disabled:cursor-not-allowed disabled:opacity-60"
                  :disabled="!visitHasPrevious || visitLoading"
                  @click="prevVisitCandidatePage"
                >
                  <svg xmlns="http://www.w3.org/2000/svg" class="h-3 w-3" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6">
                    <path stroke-linecap="round" stroke-linejoin="round" d="m15 18-6-6 6-6" />
                  </svg>
                  Trước
                </button>
                <span class="text-sm font-semibold text-slate-700">
                  {{ visitCurrentPage }} / {{ visitTotalPages }}
                </span>
                <button
                  type="button"
                  class="inline-flex items-center gap-2 rounded-full border border-emerald-200 bg-white px-3.5 py-1.5 text-[11px] font-semibold uppercase tracking-wide text-emerald-600 shadow-sm transition hover:border-emerald-300 hover:bg-emerald-50 disabled:cursor-not-allowed disabled:opacity-60"
                  :disabled="!visitHasNext || visitLoading"
                  @click="nextVisitCandidatePage"
                >
                  Sau
                  <svg xmlns="http://www.w3.org/2000/svg" class="h-3 w-3" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6">
                    <path stroke-linecap="round" stroke-linejoin="round" d="m9 6 6 6-6 6" />
                  </svg>
                </button>
              </div>
            </div>
            <p class="rounded-2xl border border-amber-100 bg-amber-50/70 px-4 py-3 text-sm text-amber-700">
              Hệ thống sẽ tổng hợp dịch vụ và đơn thuốc của hồ sơ khám để tạo hóa đơn. Các mục có thể chỉnh sửa thêm sau khi tạo.
            </p>
          </div>

          <div class="mt-6 flex items-center justify-end gap-3 border-t border-slate-200 pt-4">
            <button
              type="button"
              class="inline-flex items-center gap-2 rounded-full border border-slate-200 bg-white px-5 py-2 text-xs font-semibold uppercase tracking-wide text-slate-600 shadow-sm transition hover:border-slate-300 hover:bg-slate-50"
              @click="generateModalOpen = false"
            >
              Hủy
            </button>
            <button
              type="button"
              class="inline-flex items-center gap-2 rounded-full border border-emerald-200 bg-emerald-600 px-5 py-2 text-xs font-semibold uppercase tracking-wide text-white shadow-sm transition hover:bg-emerald-700 disabled:cursor-not-allowed disabled:opacity-60"
              :disabled="generateSubmitting"
              @click="submitGenerateBilling"
            >
              <span v-if="generateSubmitting">Đang tạo...</span>
              <span v-else>Tạo hóa đơn</span>
            </button>
          </div>
        </div>
      </div>
    </Transition>
  </div>
</template>
