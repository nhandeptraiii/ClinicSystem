<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { useRouter } from 'vue-router';
import AdminHeader from '@/components/AdminHeader.vue';
import AppointmentRequestWizard from '@/components/AppointmentRequestWizard.vue';
import { useAuthStore } from '@/stores/authStore';
import { fetchAppointmentRequestPage, rejectAppointmentRequest, type AppointmentRequest, type AppointmentLifecycleStatus } from '@/services/appointmentRequest.service';
import { fetchAppointmentPage, type AppointmentDetail } from '@/services/appointment.service';
import { createVisit, type PatientVisitCreatePayload } from '@/services/visit.service';
import { useToast, type ToastType } from '@/composables/useToast';

type StatusFilter = AppointmentLifecycleStatus | 'ALL';
type StatusCountMap = Record<StatusFilter, number>;

const authStore = useAuthStore();
const router = useRouter();

const userName = computed(() => authStore.user?.username ?? 'Quản trị viên');

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

const requests = ref<AppointmentRequest[]>([]);
const appointments = ref<AppointmentDetail[]>([]);
const cancelledRequests = ref<AppointmentRequest[]>([]);
const cancelledAppointments = ref<AppointmentDetail[]>([]);
const loading = ref(false);
const searchTerm = ref('');
const selectedStatus = ref<StatusFilter>('PENDING');
const selectedRequestId = ref<number | null>(null);
const selectedAppointmentId = ref<number | null>(null);
const lastLoadedAt = ref<string | null>(null);
const wizardOpen = ref(false);
const rejectModalOpen = ref(false);
const rejectNote = ref('');
const rejecting = ref(false);
const checkingIn = ref(false);
let searchTimer: ReturnType<typeof setTimeout> | null = null;

// Computed để xác định view mode
const viewMode = computed(() => {
  if (selectedStatus.value === 'CONFIRMED' || selectedStatus.value === 'CHECKED_IN' || selectedStatus.value === 'COMPLETED') {
    return 'appointments';
  }
  if (selectedStatus.value === 'CANCELLED') return 'mixed'; // ✅ Hiển thị hỗn hợp
  return 'requests';
});

const displayItems = computed(() => {
  if (viewMode.value === 'appointments') return appointments.value;
  if (viewMode.value === 'mixed') return cancelledItems.value;
  return requests.value;
});

const PAGE_SIZE = 10;
const currentPage = ref(1);
const totalPages = ref(1);
const totalElements = ref(0);
const hasNext = ref(false);
const hasPrevious = ref(false);

// Cache tổng số theo từng status
const statusCountsCache = ref<StatusCountMap>({
  ALL: 0,
  PENDING: 0,
  CONFIRMED: 0,
  CHECKED_IN: 0,
  COMPLETED: 0,
  CANCELLED: 0,
});

const statusMeta: Record<AppointmentLifecycleStatus, { label: string; badge: string; dot: string }> = {
  PENDING: { label: 'Chờ duyệt', badge: 'bg-amber-100 text-amber-700', dot: 'bg-amber-500' },
  CONFIRMED: { label: 'Đã xác nhận', badge: 'bg-emerald-100 text-emerald-700', dot: 'bg-emerald-500' },
  CHECKED_IN: { label: 'Đã đến', badge: 'bg-blue-100 text-blue-700', dot: 'bg-blue-500' },
  COMPLETED: { label: 'Hoàn thành', badge: 'bg-slate-100 text-slate-700', dot: 'bg-slate-500' },
  CANCELLED: { label: 'Đã hủy/Từ chối', badge: 'bg-rose-100 text-rose-700', dot: 'bg-rose-500' },
};

const statusFilters: Array<{ key: StatusFilter; label: string }> = [
  { key: 'ALL', label: 'Tất cả' },
  { key: 'PENDING', label: 'Chờ duyệt' },
  { key: 'CONFIRMED', label: 'Đã xác nhận' },
  { key: 'CHECKED_IN', label: 'Đã đến' },
  { key: 'COMPLETED', label: 'Hoàn thành' },
  { key: 'CANCELLED', label: 'Đã hủy/Từ chối' },
];

// Computed để merge và sort cancelled items
const cancelledItems = computed(() => {
  const items: Array<{
    type: 'request' | 'appointment';
    id: number;
    data: AppointmentRequest | AppointmentDetail;
    displayDate: string;
    displayName: string;
    displayInfo: string;
    displaySubInfo?: string;
  }> = [];
  
  cancelledRequests.value.forEach(req => {
    items.push({
      type: 'request',
      id: req.id,
      data: req,
      displayDate: req.createdAt || '',
      displayName: req.fullName,
      displayInfo: `SĐT: ${req.phone}`,
      displaySubInfo: req.symptomDescription || undefined,
    });
  });
  
  cancelledAppointments.value.forEach(apt => {
    items.push({
      type: 'appointment',
      id: apt.id,
      data: apt,
      displayDate: apt.scheduledAt || '',
      displayName: apt.patient?.fullName || '—',
      displayInfo: `Bác sĩ: ${apt.doctor?.account?.fullName || '—'}`,
      displaySubInfo: `Phòng: ${apt.clinicRoom?.name || '—'}`,
    });
  });
  
  // Sort theo ngày (mới nhất trước)
  return items.sort((a, b) => {
    const dateA = a.displayDate ? new Date(a.displayDate).getTime() : 0;
    const dateB = b.displayDate ? new Date(b.displayDate).getTime() : 0;
    return dateB - dateA;
  });
});

const handleSignOut = async () => {
  await authStore.signOut();
  router.replace({ name: 'home' });
};

const extractErrorMessage = (input: unknown) => {
  const fallback = 'Không thể tải danh sách yêu cầu. Vui lòng thử lại.';
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

const loadStatusCounts = async () => {
  try {
    const [pendingResponse, confirmedResponse, checkedInResponse, completedResponse, cancelledRequestsResponse, cancelledAppointmentsResponse] = await Promise.all([
      fetchAppointmentRequestPage({ status: 'PENDING', page: 0, size: 1 }),
      fetchAppointmentPage({ status: 'CONFIRMED', page: 0, size: 1 }),
      fetchAppointmentPage({ status: 'CHECKED_IN', page: 0, size: 1 }),
      fetchAppointmentPage({ status: 'COMPLETED', page: 0, size: 1 }),
      fetchAppointmentRequestPage({ status: 'CANCELLED', page: 0, size: 1 }),
      fetchAppointmentPage({ status: 'CANCELLED', page: 0, size: 1 }),
    ]);
    
    statusCountsCache.value.PENDING = pendingResponse.totalElements;
    statusCountsCache.value.CONFIRMED = confirmedResponse.totalElements;
    statusCountsCache.value.CHECKED_IN = checkedInResponse.totalElements;
    statusCountsCache.value.COMPLETED = completedResponse.totalElements;
    statusCountsCache.value.CANCELLED = cancelledRequestsResponse.totalElements + cancelledAppointmentsResponse.totalElements;
    statusCountsCache.value.ALL = 
      statusCountsCache.value.PENDING + 
      statusCountsCache.value.CONFIRMED + 
      statusCountsCache.value.CHECKED_IN +
      statusCountsCache.value.COMPLETED +
      statusCountsCache.value.CANCELLED;
  } catch (err) {
    console.error('Failed to load status counts', err);
  }
};

const loadAppointments = async (status?: AppointmentLifecycleStatus) => {
  loading.value = true;
  try {
    const keyword = searchTerm.value.trim();
    const pageIndex = Math.max(currentPage.value - 1, 0);
    const statusParam = status || selectedStatus.value;
    
    // Chỉ load appointments cho các status hợp lệ
    if (statusParam !== 'CONFIRMED' && statusParam !== 'CHECKED_IN' && statusParam !== 'COMPLETED') {
      appointments.value = [];
      totalElements.value = 0;
      totalPages.value = 1;
      currentPage.value = 1;
      hasNext.value = false;
      hasPrevious.value = false;
      return;
    }
    
    const response = await fetchAppointmentPage({
      page: pageIndex,
      size: PAGE_SIZE,
      keyword: keyword ? keyword : undefined,
      status: statusParam as AppointmentLifecycleStatus,
    });
    
    appointments.value = response.items ?? [];
    totalElements.value = response.totalElements ?? appointments.value.length;
    const derivedTotalPages = response.totalPages && response.totalPages > 0 ? response.totalPages : 1;
    totalPages.value = derivedTotalPages;
    currentPage.value = response.totalPages && response.totalPages > 0 ? response.page + 1 : 1;
    hasNext.value = response.hasNext ?? false;
    hasPrevious.value = response.hasPrevious ?? false;
    
    lastLoadedAt.value = new Date().toISOString();
  } catch (err) {
    console.error('Failed to fetch appointments', err);
    showToast('error', extractErrorMessage(err));
    appointments.value = [];
    totalElements.value = 0;
    totalPages.value = 1;
    currentPage.value = 1;
    hasNext.value = false;
    hasPrevious.value = false;
  } finally {
    loading.value = false;
  }
};

const loadCancelledRequests = async () => {
  try {
    const keyword = searchTerm.value.trim();
    const pageIndex = Math.max(currentPage.value - 1, 0);
    
    const response = await fetchAppointmentRequestPage({
      page: pageIndex,
      size: PAGE_SIZE,
      keyword: keyword ? keyword : undefined,
      status: 'CANCELLED',
    });
    
    cancelledRequests.value = response.items ?? [];
  } catch (err) {
    console.error('Failed to fetch cancelled requests', err);
    cancelledRequests.value = [];
  }
};

const loadCancelledAppointments = async () => {
  try {
    const keyword = searchTerm.value.trim();
    const pageIndex = Math.max(currentPage.value - 1, 0);
    
    const response = await fetchAppointmentPage({
      page: pageIndex,
      size: PAGE_SIZE,
      keyword: keyword ? keyword : undefined,
      status: 'CANCELLED',
    });
    
    cancelledAppointments.value = response.items ?? [];
  } catch (err) {
    console.error('Failed to fetch cancelled appointments', err);
    cancelledAppointments.value = [];
  }
};

const loadRequests = async () => {
  loading.value = true;
  try {
    const keyword = searchTerm.value.trim();
    const pageIndex = Math.max(currentPage.value - 1, 0);
    const statusParam = selectedStatus.value === 'ALL' ? undefined : selectedStatus.value;
    
    const response = await fetchAppointmentRequestPage({
      page: pageIndex,
      size: PAGE_SIZE,
      keyword: keyword ? keyword : undefined,
      status: statusParam,
    });
    
    requests.value = response.items ?? [];
    totalElements.value = response.totalElements ?? requests.value.length;
    const derivedTotalPages = response.totalPages && response.totalPages > 0 ? response.totalPages : 1;
    totalPages.value = derivedTotalPages;
    currentPage.value = response.totalPages && response.totalPages > 0 ? response.page + 1 : 1;
    hasNext.value = response.hasNext ?? false;
    hasPrevious.value = response.hasPrevious ?? false;
    
    // Khi không có keyword, cập nhật cache cho status hiện tại
    if (!keyword) {
      if (selectedStatus.value === 'ALL') {
        // Khi xem ALL, load tất cả counts
        await loadStatusCounts();
      } else {
        // Khi filter theo status, chỉ cập nhật status đó
        statusCountsCache.value[selectedStatus.value] = totalElements.value;
        statusCountsCache.value.ALL = 
          statusCountsCache.value.PENDING + 
          statusCountsCache.value.CONFIRMED + 
          statusCountsCache.value.CHECKED_IN +
          statusCountsCache.value.COMPLETED +
          statusCountsCache.value.CANCELLED;
      }
    }
    
    lastLoadedAt.value = new Date().toISOString();
  } catch (err) {
    console.error('Failed to fetch appointment requests', err);
    showToast('error', extractErrorMessage(err));
    requests.value = [];
    totalElements.value = 0;
    totalPages.value = 1;
    currentPage.value = 1;
    hasNext.value = false;
    hasPrevious.value = false;
  } finally {
    loading.value = false;
  }
};

const loadData = async () => {
  if (selectedStatus.value === 'CONFIRMED' || selectedStatus.value === 'CHECKED_IN' || selectedStatus.value === 'COMPLETED') {
    await loadAppointments();
  } else if (selectedStatus.value === 'CANCELLED') {
    // ✅ Load cả Requests và Appointments có status CANCELLED
    loading.value = true;
    try {
      await Promise.all([
        loadCancelledRequests(),
        loadCancelledAppointments()
      ]);
      
      // Tính tổng cho pagination
      const totalCancelled = cancelledRequests.value.length + cancelledAppointments.value.length;
      totalElements.value = totalCancelled;
      totalPages.value = Math.ceil(totalCancelled / PAGE_SIZE) || 1;
      hasNext.value = false; // Simplified for mixed view
      hasPrevious.value = currentPage.value > 1;
      
      lastLoadedAt.value = new Date().toISOString();
    } catch (err) {
      showToast('error', extractErrorMessage(err));
      cancelledRequests.value = [];
      cancelledAppointments.value = [];
      totalElements.value = 0;
      totalPages.value = 1;
      currentPage.value = 1;
      hasNext.value = false;
      hasPrevious.value = false;
    } finally {
      loading.value = false;
    }
  } else {
    await loadRequests();
  }
};

const goToPage = (page: number) => {
  const target = Math.min(Math.max(page, 1), totalPages.value || 1);
  if (target === currentPage.value) {
    return;
  }
  currentPage.value = target;
  loadData();
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

const getDate = (value?: string | null) => {
  if (!value) return null;
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? null : date;
};

const formatDate = (value?: string | null, options: Intl.DateTimeFormatOptions = { dateStyle: 'medium' }) => {
  const date = getDate(value);
  if (!date) return 'Không xác định';
  return new Intl.DateTimeFormat('vi-VN', options).format(date);
};

const formatDateTime = (value?: string | null) => {
  return formatDate(value, { dateStyle: 'medium', timeStyle: 'short' });
};

const formatFromNow = (value?: string | Date | null) => {
  const date = value instanceof Date ? value : getDate(typeof value === 'string' ? value : null);
  if (!date) return 'Không xác định';
  const diffMs = Date.now() - date.getTime();
  if (diffMs < 0) {
    return formatDateTime(date.toISOString());
  }
  const diffMinutes = Math.round(diffMs / 60000);
  if (diffMinutes < 1) return 'Vừa xong';
  if (diffMinutes < 60) return `${diffMinutes} phút trước`;
  const diffHours = Math.round(diffMinutes / 60);
  if (diffHours < 24) return `${diffHours} giờ trước`;
  const diffDays = Math.round(diffHours / 24);
  if (diffDays < 7) return `${diffDays} ngày trước`;
  return formatDate(date.toISOString());
};

const statusCounts = computed<StatusCountMap>(() => {
  return { ...statusCountsCache.value };
});

const selectedRequest = computed(() => requests.value.find((item) => item.id === selectedRequestId.value) ?? null);
const selectedAppointment = computed(() => appointments.value.find((item) => item.id === selectedAppointmentId.value) ?? null);

const selectedStatusMeta = computed(() =>
  selectedRequest.value ? statusMeta[selectedRequest.value.status] : undefined
);

// Xóa hàm này, dùng statusMeta thay thế
const getStatusDisplay = (status: AppointmentLifecycleStatus) => {
  return statusMeta[status] || statusMeta.PENDING;
};

const canOpenWizard = computed(() => selectedRequest.value?.status === 'PENDING');
const wizardTooltip = computed(() =>
  canOpenWizard.value ? '' : 'Chỉ có thể tạo lịch với yêu cầu đang ở trạng thái chờ duyệt.'
);

watch(
  requests,
  (list) => {
    if (list.length === 0) {
      selectedRequestId.value = null;
      return;
    }
    if (!selectedRequestId.value || !list.some((item) => item.id === selectedRequestId.value)) {
      const first = list[0];
      if (first) {
        selectedRequestId.value = first.id;
      }
    }
  },
  { immediate: true }
);

watch(
  appointments,
  (list) => {
    if (list.length === 0) {
      selectedAppointmentId.value = null;
      return;
    }
    if (!selectedAppointmentId.value || !list.some((item) => item.id === selectedAppointmentId.value)) {
      const first = list[0];
      if (first) {
        selectedAppointmentId.value = first.id;
      }
    }
  },
  { immediate: true }
);

watch(
  cancelledItems,
  (items) => {
    if (items.length === 0) {
      selectedRequestId.value = null;
      selectedAppointmentId.value = null;
      return;
    }
    // Auto-select first item if nothing selected
    if (!selectedRequestId.value && !selectedAppointmentId.value) {
      const first = items[0];
      if (first) {
        if (first.type === 'request') {
          selectedRequestId.value = first.id;
        } else {
          selectedAppointmentId.value = first.id;
        }
      }
    }
  },
  { immediate: true }
);

watch(selectedStatus, () => {
  currentPage.value = 1;
  selectedRequestId.value = null;
  selectedAppointmentId.value = null;
  loadData();
});

watch(searchTerm, () => {
  if (searchTimer) {
    clearTimeout(searchTimer);
  }
  searchTimer = setTimeout(() => {
    currentPage.value = 1;
    loadData();
  }, 350);
});

const pendingShare = computed(() => {
  if (statusCounts.value.ALL === 0) return 0;
  return Math.round((statusCounts.value.PENDING / statusCounts.value.ALL) * 100);
});
const lastLoadedDisplay = computed(() => (lastLoadedAt.value ? formatFromNow(lastLoadedAt.value) : 'Chưa tải'));

onMounted(() => {
  loadStatusCounts();
  loadData();
});

onBeforeUnmount(() => {
  if (searchTimer) {
    clearTimeout(searchTimer);
  }
});

watch(
  selectedRequest,
  (value) => {
    if (value?.status !== 'PENDING') {
      wizardOpen.value = false;
    }
  }
);

const handleWizardCompleted = async () => {
  await loadStatusCounts();
  await loadData();
};

const openRejectModal = () => {
  rejectNote.value = '';
  rejectModalOpen.value = true;
};

const closeRejectModal = () => {
  rejectModalOpen.value = false;
  rejectNote.value = '';
};

const handleReject = async () => {
  if (!selectedRequest.value || rejecting.value) return;
  
  if (!rejectNote.value.trim()) {
    showToast('error', 'Vui lòng nhập lý do từ chối.');
    return;
  }
  
  rejecting.value = true;
  try {
    await rejectAppointmentRequest(selectedRequest.value.id, {
      staffNote: rejectNote.value.trim(),
    });
    showToast('success', 'Đã từ chối yêu cầu đặt lịch.');
    closeRejectModal();
    await loadStatusCounts();
    await loadData();
  } catch (err) {
    showToast('error', extractErrorMessage(err));
  } finally {
    rejecting.value = false;
  }
};

const handleCheckIn = async () => {
  if (!selectedAppointment.value || checkingIn.value) return;
  
  checkingIn.value = true;
  try {
    const payload: PatientVisitCreatePayload = {
      primaryAppointmentId: selectedAppointment.value.id,
      provisionalDiagnosis: null,
      clinicalNote: null,
    };
    
    await createVisit(payload);
    showToast('success', 'Đã tạo hồ sơ khám thành công. Bệnh nhân có thể bắt đầu khám.');
    
    // Reload appointments để cập nhật trạng thái
    await loadData();
    
    // Option: redirect to visits page
    // router.push({ name: 'visits' });
  } catch (err) {
    const errorMsg = extractErrorMessage(err);
    showToast('error', errorMsg || 'Không thể tạo hồ sơ khám. Vui lòng thử lại.');
  } finally {
    checkingIn.value = false;
  }
};
</script>

<template>
  <div class="relative min-h-screen bg-gradient-to-br from-emerald-50 via-white to-white">
    <div class="pointer-events-none absolute inset-0 overflow-hidden">
      <div class="absolute -top-32 -right-48 h-96 w-96 rounded-full bg-emerald-200/30 blur-3xl"></div>
      <div class="absolute -bottom-28 -left-32 h-96 w-96 rounded-full bg-teal-200/30 blur-3xl"></div>
    </div>

    <AdminHeader :user-name="userName" @sign-out="handleSignOut" />

    <main class="relative mx-auto max-w-6xl px-6 py-12">
      <section class="rounded-[32px] border border-emerald-100 bg-white/90 p-8 shadow-[0_24px_60px_-40px_rgba(13,148,136,0.6)]">
        <div class="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
          <div class="max-w-2xl">
            <span class="inline-flex items-center gap-2 rounded-full bg-emerald-50 px-4 py-1.5 text-xs font-semibold uppercase tracking-[0.3em] text-emerald-600">
              Trung tâm điều phối
            </span>
            <h1 class="mt-4 text-2xl font-semibold text-slate-900 md:text-3xl">Quản lý yêu cầu đặt lịch</h1>
            <p class="mt-3 text-sm leading-relaxed text-slate-600">
              Theo dõi và phản hồi các yêu cầu đặt lịch từ trang công khai. Sử dụng bộ lọc trạng thái, tìm kiếm nhanh và bảng chi tiết bên phải để nắm bắt thông tin trước khi liên hệ bệnh nhân.
            </p>
          </div>
          <div class="flex flex-col items-start gap-3 sm:flex-row sm:items-center lg:flex-col lg:items-end">
            <button
              type="button"
              class="inline-flex items-center gap-2 rounded-full border border-emerald-200 bg-white px-5 py-2 text-xs font-semibold uppercase tracking-wide text-emerald-600 shadow-sm transition hover:border-emerald-300 hover:bg-emerald-50 disabled:opacity-50"
              :disabled="loading"
              @click="loadData"
            >
              <svg xmlns="http://www.w3.org/2000/svg" class="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.8">
                <path stroke-linecap="round" stroke-linejoin="round" d="M4 4v5h.582m15.356 2A8.001 8.001 0 0 0 5.582 9H4m0 0a8.003 8.003 0 0 1 14.9-3m1.518 9H20v5h.004m-.586-2A8.001 8.001 0 0 1 5.518 15H4m0 0a8.003 8.003 0 0 0 14.9 3" />
              </svg>
              <span>{{ loading ? 'Đang tải...' : 'Làm mới' }}</span>
            </button>
            <div class="rounded-2xl border border-emerald-100 bg-emerald-50/70 px-4 py-2 text-sm text-emerald-700 shadow-inner">
              <p class="font-semibold">{{ viewMode === 'appointments' ? 'Tổng lịch hẹn' : 'Tổng yêu cầu' }}: {{ totalElements }}</p>
              <p class="text-xs text-emerald-600/80">Cập nhật {{ lastLoadedDisplay }}</p>
            </div>
          </div>
        </div>

        <div class="mt-8 grid gap-4 sm:grid-cols-3">
          <div class="rounded-2xl border border-amber-100 bg-amber-50/70 p-5 text-amber-700 shadow-sm">
            <p class="text-xs font-semibold uppercase tracking-wide text-amber-600/80">Chờ xử lý</p>
            <p class="mt-2 text-3xl font-semibold">{{ statusCounts.PENDING }}</p>
            <p class="mt-2 text-xs text-amber-600/80">{{ pendingShare }}% tổng số yêu cầu đang chờ phản hồi</p>
          </div>
          <div class="rounded-2xl border border-emerald-100 bg-emerald-50/70 p-5 text-emerald-700 shadow-sm">
            <p class="text-xs font-semibold uppercase tracking-wide text-emerald-600/80">Đã xác nhận</p>
            <p class="mt-2 text-3xl font-semibold">{{ statusCounts.CONFIRMED }}</p>
            <p class="mt-2 text-xs text-emerald-600/80">Đã tạo lịch hẹn hoặc liên hệ bệnh nhân</p>
          </div>
          <div class="rounded-2xl border border-rose-100 bg-rose-50/70 p-5 text-rose-700 shadow-sm">
            <p class="text-xs font-semibold uppercase tracking-wide text-rose-600/80">Đã hủy/Từ chối</p>
            <p class="mt-2 text-3xl font-semibold">{{ statusCounts.CANCELLED }}</p>
            <p class="mt-2 text-xs text-rose-600/80">Ghi chú rõ lý do để tiện tra cứu</p>
          </div>
        </div>

        <div class="mt-8 flex flex-col gap-4 lg:flex-row lg:items-center lg:justify-between">
          <div class="flex flex-wrap items-center gap-2">
            <button
              v-for="filter in statusFilters"
              :key="filter.key"
              type="button"
              class="inline-flex items-center gap-2 rounded-full border px-4 py-2 text-xs font-semibold uppercase tracking-wide shadow-sm transition"
              :class="selectedStatus === filter.key ? 'border-emerald-300 bg-emerald-50 text-emerald-700 shadow-md' : 'border-emerald-100 bg-white text-emerald-600 hover:border-emerald-200 hover:bg-emerald-50/70'"
              @click="selectedStatus = filter.key"
            >
              <span>{{ filter.label }}</span>
              <span class="rounded-full bg-emerald-100 px-2 py-0.5 text-[11px] font-semibold text-emerald-600">{{ statusCounts[filter.key] }}</span>
            </button>
          </div>
          <div class="relative w-full max-w-sm">
            <svg class="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" fill="none" stroke="currentColor" stroke-width="1.5" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" d="m21 21-4.35-4.35m0 0A7.35 7.35 0 1 0 6.3 6.3a7.35 7.35 0 0 0 10.35 10.35Z" />
            </svg>
            <input
              v-model.trim="searchTerm"
              type="search"
              placeholder="Tìm bệnh nhân, số điện thoại, mã hồ sơ..."
              class="w-full rounded-full border border-emerald-100 bg-white py-2.5 pl-10 pr-4 text-sm text-slate-700 shadow-sm transition focus:border-emerald-400 focus:outline-none focus:ring-4 focus:ring-emerald-100/80"
            />
          </div>
        </div>

      </section>

      <section class="mt-10 grid gap-6 lg:grid-cols-[1.7fr_1fr]">
        <div class="rounded-[28px] border border-emerald-100 bg-white/90 p-6 shadow-[0_20px_55px_-45px_rgba(13,148,136,0.55)]">
          <div v-if="loading && !requests.length" class="space-y-4">
            <div v-for="skeleton in 4" :key="skeleton" class="animate-pulse rounded-2xl border border-emerald-50 bg-emerald-50/60 p-5">
              <div class="flex items-center justify-between">
                <div class="h-4 w-32 rounded-full bg-emerald-100/80"></div>
                <div class="h-4 w-20 rounded-full bg-emerald-100/80"></div>
              </div>
              <div class="mt-4 h-3 w-full rounded-full bg-emerald-100/60"></div>
              <div class="mt-2 h-3 w-2/3 rounded-full bg-emerald-100/50"></div>
            </div>
          </div>

          <div v-else-if="displayItems.length === 0" class="rounded-3xl border border-dashed border-emerald-200 bg-emerald-50/30 p-8 text-center text-emerald-600">
            <svg xmlns="http://www.w3.org/2000/svg" class="mx-auto h-12 w-12 text-emerald-400" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.6">
              <path stroke-linecap="round" stroke-linejoin="round" d="M9 13h6m-3-3v6m8 1V7a2 2 0 0 0-2-2h-3.172a2 2 0 0 1-1.414-.586l-.828-.828A2 2 0 0 0 12.172 3H8a2 2 0 0 0-2 2v14m-1-1h14" />
            </svg>
            <h3 class="mt-4 text-base font-semibold">{{ viewMode === 'appointments' ? 'Không có lịch hẹn phù hợp' : viewMode === 'mixed' ? 'Không có mục hủy/từ chối' : 'Không có yêu cầu phù hợp' }}</h3>
            <p class="mt-2 text-sm text-emerald-600/80">
              Hãy thử thay đổi bộ lọc hoặc xóa từ khóa tìm kiếm.
            </p>
          </div>

          <!-- Hiển thị Appointments khi status = CONFIRMED -->
          <div v-else-if="viewMode === 'appointments'" class="space-y-3">
            <button
              v-for="appointment in appointments"
              :key="appointment.id"
              type="button"
              class="w-full rounded-2xl border p-5 text-left transition focus:outline-none"
              :class="[
                selectedAppointmentId === appointment.id
                  ? 'border-emerald-300 bg-emerald-50/70 shadow-md ring-2 ring-emerald-200/70'
                  : 'border-emerald-100 bg-white hover:border-emerald-200 hover:bg-emerald-50/40'
              ]"
              @click="selectedAppointmentId = appointment.id"
            >
              <div class="flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between">
                <div class="flex items-start gap-3">
                  <span 
                    class="mt-1 flex h-2.5 w-2.5 flex-shrink-0 rounded-full" 
                    :class="getStatusDisplay(appointment.status as AppointmentLifecycleStatus).dot"
                  ></span>
                  <div>
                    <p class="text-sm font-semibold uppercase tracking-wider text-slate-400">{{ formatDateTime(appointment.scheduledAt) }}</p>
                    <h3 class="mt-1 text-lg font-semibold text-slate-900">{{ appointment.patient?.fullName || '—' }}</h3>
                    <p class="mt-1 text-sm text-slate-600">
                      Mã BN: {{ appointment.patient?.code || '—' }} <span v-if="appointment.patient?.phone" class="text-slate-400">• SĐT: {{ appointment.patient.phone }}</span>
                    </p>
                    <p class="mt-2 text-sm text-slate-600">
                      <span class="font-semibold">Bác sĩ:</span> {{ appointment.doctor?.account?.fullName || '—' }}
                      <span class="text-slate-400">•</span>
                      <span class="font-semibold">Phòng:</span> {{ appointment.clinicRoom?.name || '—' }}
                    </p>
                  </div>
                </div>
                <div class="flex flex-col items-start gap-2 text-sm text-slate-500 sm:items-end">
                  <span 
                    class="inline-flex items-center rounded-full px-3 py-1 text-xs font-semibold" 
                    :class="getStatusDisplay(appointment.status as AppointmentLifecycleStatus).badge"
                  >
                    {{ getStatusDisplay(appointment.status as AppointmentLifecycleStatus).label }}
                  </span>
                  <p v-if="appointment.reason" class="text-xs">
                    Lý do: <span class="font-semibold text-slate-700">{{ appointment.reason }}</span>
                  </p>
                  <p v-if="appointment.duration">Thời lượng: {{ appointment.duration }} phút</p>
                </div>
              </div>
            </button>
          </div>

          <!-- Hiển thị danh sách CANCELLED hỗn hợp -->
          <div v-else-if="viewMode === 'mixed'" class="space-y-3">
            <div
              v-for="item in cancelledItems"
              :key="`${item.type}-${item.id}`"
              class="w-full rounded-2xl border p-5 text-left transition"
              :class="[
                (item.type === 'request' && selectedRequestId === item.id) ||
                (item.type === 'appointment' && selectedAppointmentId === item.id)
                  ? 'border-rose-300 bg-rose-50/70 shadow-md ring-2 ring-rose-200/70'
                  : 'border-rose-100 bg-white hover:border-rose-200 hover:bg-rose-50/40'
              ]"
              @click="item.type === 'request' ? selectedRequestId = item.id : selectedAppointmentId = item.id"
            >
              <div class="flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between">
                <div class="flex items-start gap-3">
                  <!-- Badge phân biệt loại -->
                  <span 
                    class="mt-1 flex h-2.5 w-2.5 flex-shrink-0 rounded-full bg-rose-500"
                  ></span>
                  <div>
                    <!-- Badge loại -->
                    <span 
                      class="inline-flex items-center rounded-full px-2 py-0.5 text-[10px] font-semibold uppercase mb-2"
                      :class="item.type === 'request' ? 'bg-amber-100 text-amber-700' : 'bg-blue-100 text-blue-700'"
                    >
                      {{ item.type === 'request' ? 'Yêu cầu' : 'Lịch hẹn' }}
                    </span>
                    
                    <p class="mt-1 text-sm font-semibold uppercase tracking-wider text-slate-400">
                      {{ item.type === 'request' ? formatDate(item.displayDate) : formatDateTime(item.displayDate) }}
                    </p>
                    <h3 class="mt-1 text-lg font-semibold text-slate-900">{{ item.displayName }}</h3>
                    <p class="mt-1 text-sm text-slate-600">{{ item.displayInfo }}</p>
                    
                    <!-- Thông tin đặc thù -->
                    <template v-if="item.type === 'request'">
                      <p v-if="item.displaySubInfo" class="mt-2 text-sm text-slate-600 line-clamp-2">
                        {{ item.displaySubInfo }}
                      </p>
                    </template>
                    <template v-else>
                      <p v-if="item.displaySubInfo" class="mt-2 text-sm text-slate-600">
                        {{ item.displaySubInfo }}
                      </p>
                    </template>
                  </div>
                </div>
                
                <div class="flex flex-col items-start gap-2 text-sm text-slate-500 sm:items-end">
                  <span class="inline-flex items-center rounded-full px-3 py-1 text-xs font-semibold bg-rose-100 text-rose-700">
                    {{ item.type === 'request' ? 'Đã từ chối' : 'Đã hủy' }}
                  </span>
                  <p class="text-xs text-slate-400">
                    {{ item.type === 'request' ? 'Yêu cầu bị từ chối' : 'Lịch hẹn bị hủy' }}
                  </p>
                </div>
              </div>
            </div>
          </div>

          <!-- Hiển thị Requests khi status khác CONFIRMED -->
          <div v-else class="space-y-3">
            <button
              v-for="request in requests"
              :key="request.id"
              type="button"
              class="w-full rounded-2xl border p-5 text-left transition focus:outline-none"
              :class="[
                selectedRequestId === request.id
                  ? 'border-emerald-300 bg-emerald-50/70 shadow-md ring-2 ring-emerald-200/70'
                  : 'border-emerald-100 bg-white hover:border-emerald-200 hover:bg-emerald-50/40'
              ]"
              @click="selectedRequestId = request.id"
            >
              <div class="flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between">
                <div class="flex items-start gap-3">
                  <span 
                    class="mt-1 flex h-2.5 w-2.5 flex-shrink-0 rounded-full" 
                    :class="statusMeta[request.status || 'PENDING']?.dot || statusMeta.PENDING.dot"
                  ></span>
                  <div>
                    <p class="text-sm font-semibold uppercase tracking-wider text-slate-400">{{ formatDate(request.createdAt) }}</p>
                    <h3 class="mt-1 text-lg font-semibold text-slate-900">{{ request.fullName }}</h3>
                    <p class="mt-1 text-sm text-slate-600">
                      SĐT: {{ request.phone }} <span v-if="request.email" class="text-slate-400">• {{ request.email }}</span>
                    </p>
                    <p class="mt-2 text-sm text-slate-600 line-clamp-2">{{ request.symptomDescription || 'Bệnh nhân chưa để lại ghi chú cụ thể.' }}</p>
                  </div>
                </div>
                <div class="flex flex-col items-start gap-2 text-sm text-slate-500 sm:items-end">
                  <span 
                    class="inline-flex items-center rounded-full px-3 py-1 text-xs font-semibold" 
                    :class="statusMeta[request.status || 'PENDING']?.badge || statusMeta.PENDING.badge"
                  >
                    {{ statusMeta[request.status || 'PENDING']?.label || statusMeta.PENDING.label }}
                  </span>
                  <p v-if="request.preferredAt">
                    Mong muốn: <span class="font-semibold text-slate-700">{{ formatDateTime(request.preferredAt) }}</span>
                  </p>
                  <p>Nhận {{ formatFromNow(request.createdAt) }}</p>
                </div>
              </div>
            </button>
          </div>
        </div>

        <aside class="rounded-[28px] border border-emerald-100 bg-white/95 p-6 shadow-[0_20px_55px_-45px_rgba(13,148,136,0.55)]">
          <!-- Hiển thị Cancelled item details khi viewMode = mixed -->
          <template v-if="viewMode === 'mixed'">
            <template v-if="selectedRequestId && cancelledItems.find(item => item.type === 'request' && item.id === selectedRequestId)">
              <div class="flex items-start justify-between gap-4">
                <div>
                  <p class="text-xs font-semibold uppercase tracking-[0.35em] text-rose-500">Chi tiết yêu cầu đã từ chối</p>
                  <h2 class="mt-2 text-xl font-semibold text-slate-900">{{ (cancelledItems.find(item => item.type === 'request' && item.id === selectedRequestId)?.data as AppointmentRequest)?.fullName || '—' }}</h2>
                  <p class="mt-1 text-sm text-slate-500">Tạo {{ formatFromNow((cancelledItems.find(item => item.type === 'request' && item.id === selectedRequestId)?.data as AppointmentRequest)?.createdAt) }}</p>
                </div>
                <span class="inline-flex items-center rounded-full px-3 py-1 text-xs font-semibold bg-rose-100 text-rose-700">
                  Đã từ chối
                </span>
              </div>
              <div class="mt-6 space-y-6">
                <section>
                  <h3 class="text-sm font-semibold text-slate-900">Thông tin liên hệ</h3>
                  <ul class="mt-3 space-y-2 text-sm text-slate-600">
                    <li class="flex items-center gap-3">
                      <span class="flex h-8 w-8 items-center justify-center rounded-full bg-rose-50 text-rose-600">
                        <svg xmlns="http://www.w3.org/2000/svg" class="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.8">
                          <path stroke-linecap="round" stroke-linejoin="round" d="M2.75 5.75c0-1.243 1.007-2.25 2.25-2.25h2.086c.466 0 .907.217 1.188.586l1.44 1.92c.424.565.372 1.347-.12 1.86L8.5 8.75a11.042 11.042 0 0 0 6.75 6.75l.884-.934c.512-.493 1.294-.544 1.86-.12l1.92 1.44c.369.281.586.722.586 1.188V19a2.25 2.25 0 0 1-2.25 2.25h-.5C9.096 21.25 2.75 14.904 2.75 7.25v-.5Z" />
                        </svg>
                      </span>
                      <div>
                        <p class="text-xs font-semibold uppercase tracking-wide text-slate-400">Số điện thoại</p>
                        <a :href="`tel:${(cancelledItems.find(item => item.type === 'request' && item.id === selectedRequestId)?.data as AppointmentRequest)?.phone}`" class="text-sm font-semibold text-slate-800 hover:text-rose-600">{{ (cancelledItems.find(item => item.type === 'request' && item.id === selectedRequestId)?.data as AppointmentRequest)?.phone }}</a>
                      </div>
                    </li>
                  </ul>
                </section>
                <section class="rounded-2xl border border-rose-100 bg-rose-50/50 p-4 text-sm text-slate-700">
                  <p class="font-semibold text-slate-900">Lý do từ chối:</p>
                  <p class="mt-2">{{ (cancelledItems.find(item => item.type === 'request' && item.id === selectedRequestId)?.data as AppointmentRequest)?.staffNote || 'Không có ghi chú' }}</p>
                </section>
              </div>
            </template>
            <template v-else-if="selectedAppointmentId && cancelledItems.find(item => item.type === 'appointment' && item.id === selectedAppointmentId)">
              <div class="flex items-start justify-between gap-4">
                <div>
                  <p class="text-xs font-semibold uppercase tracking-[0.35em] text-rose-500">Chi tiết lịch hẹn đã hủy</p>
                  <h2 class="mt-2 text-xl font-semibold text-slate-900">{{ (cancelledItems.find(item => item.type === 'appointment' && item.id === selectedAppointmentId)?.data as AppointmentDetail)?.patient?.fullName || '—' }}</h2>
                  <p class="mt-1 text-sm text-slate-500">{{ formatDateTime((cancelledItems.find(item => item.type === 'appointment' && item.id === selectedAppointmentId)?.data as AppointmentDetail)?.scheduledAt) }}</p>
                </div>
                <span class="inline-flex items-center rounded-full px-3 py-1 text-xs font-semibold bg-rose-100 text-rose-700">
                  Đã hủy
                </span>
              </div>
              <div class="mt-6 space-y-6">
                <section>
                  <h3 class="text-sm font-semibold text-slate-900">Thông tin bệnh nhân</h3>
                  <ul class="mt-3 space-y-2 text-sm text-slate-600">
                    <li class="flex items-center gap-3">
                      <span class="flex h-8 w-8 items-center justify-center rounded-full bg-rose-50 text-rose-600">
                        <svg xmlns="http://www.w3.org/2000/svg" class="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.8">
                          <path stroke-linecap="round" stroke-linejoin="round" d="M15.75 6a3.75 3.75 0 1 1-7.5 0 3.75 3.75 0 0 1 7.5 0ZM4.501 20.118a7.5 7.5 0 0 1 14.998 0A17.933 17.933 0 0 1 12 21.75c-2.676 0-5.216-.584-7.499-1.632Z" />
                        </svg>
                      </span>
                      <div>
                        <p class="text-xs font-semibold uppercase tracking-wide text-slate-400">Họ tên</p>
                        <p class="text-sm font-semibold text-slate-800">{{ (cancelledItems.find(item => item.type === 'appointment' && item.id === selectedAppointmentId)?.data as AppointmentDetail)?.patient?.fullName || '—' }}</p>
                      </div>
                    </li>
                  </ul>
                </section>
                <section>
                  <h3 class="text-sm font-semibold text-slate-900">Thông tin lịch hẹn</h3>
                  <div class="mt-3 rounded-2xl border border-rose-100 bg-rose-50/50 p-4 text-sm text-slate-700">
                    <p><span class="font-semibold text-slate-900">Bác sĩ:</span> {{ (cancelledItems.find(item => item.type === 'appointment' && item.id === selectedAppointmentId)?.data as AppointmentDetail)?.doctor?.account?.fullName || '—' }}</p>
                    <p class="mt-2"><span class="font-semibold text-slate-900">Phòng khám:</span> {{ (cancelledItems.find(item => item.type === 'appointment' && item.id === selectedAppointmentId)?.data as AppointmentDetail)?.clinicRoom?.name || '—' }}</p>
                  </div>
                </section>
              </div>
            </template>
            <template v-else>
              <div class="flex h-full flex-col items-center justify-center gap-4 text-center text-slate-500">
                <svg xmlns="http://www.w3.org/2000/svg" class="h-16 w-16 text-rose-300" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.4">
                  <path stroke-linecap="round" stroke-linejoin="round" d="m9 13-3 3 3 3m6-6 3 3-3 3M9 5l3-3 3 3M9 9l3-3 3 3" />
                </svg>
                <h3 class="text-lg font-semibold text-slate-800">Chọn một mục để xem chi tiết</h3>
                <p class="text-sm text-slate-500">Danh sách bên trái bao gồm cả yêu cầu từ chối và lịch hẹn hủy.</p>
              </div>
            </template>
          </template>

          <!-- Hiển thị Appointment details khi viewMode = appointments -->
          <template v-else-if="viewMode === 'appointments' && selectedAppointment">
            <div class="flex items-start justify-between gap-4">
              <div>
                <p class="text-xs font-semibold uppercase tracking-[0.35em] text-emerald-500">Chi tiết lịch hẹn</p>
                <h2 class="mt-2 text-xl font-semibold text-slate-900">{{ selectedAppointment.patient?.fullName || '—' }}</h2>
                <p class="mt-1 text-sm text-slate-500">{{ formatDateTime(selectedAppointment.scheduledAt) }}</p>
              </div>
              <span 
                class="inline-flex items-center rounded-full px-3 py-1 text-xs font-semibold" 
                :class="getStatusDisplay(selectedAppointment.status as AppointmentLifecycleStatus).badge"
              >
                {{ getStatusDisplay(selectedAppointment.status as AppointmentLifecycleStatus).label }}
              </span>
            </div>

            <div class="mt-6 space-y-6">
              <section>
                <h3 class="text-sm font-semibold text-slate-900">Thông tin bệnh nhân</h3>
                <ul class="mt-3 space-y-2 text-sm text-slate-600">
                  <li class="flex items-center gap-3">
                    <span class="flex h-8 w-8 items-center justify-center rounded-full bg-emerald-50 text-emerald-600">
                      <svg xmlns="http://www.w3.org/2000/svg" class="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.8">
                        <path stroke-linecap="round" stroke-linejoin="round" d="M15.75 6a3.75 3.75 0 1 1-7.5 0 3.75 3.75 0 0 1 7.5 0ZM4.501 20.118a7.5 7.5 0 0 1 14.998 0A17.933 17.933 0 0 1 12 21.75c-2.676 0-5.216-.584-7.499-1.632Z" />
                      </svg>
                    </span>
                    <div>
                      <p class="text-xs font-semibold uppercase tracking-wide text-slate-400">Họ tên</p>
                      <p class="text-sm font-semibold text-slate-800">{{ selectedAppointment.patient?.fullName || '—' }}</p>
                      <p v-if="selectedAppointment.patient?.code" class="text-xs text-slate-500">Mã: {{ selectedAppointment.patient.code }}</p>
                    </div>
                  </li>
                  <li v-if="selectedAppointment.patient?.phone" class="flex items-center gap-3">
                    <span class="flex h-8 w-8 items-center justify-center rounded-full bg-emerald-50 text-emerald-600">
                      <svg xmlns="http://www.w3.org/2000/svg" class="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.8">
                        <path stroke-linecap="round" stroke-linejoin="round" d="M2.75 5.75c0-1.243 1.007-2.25 2.25-2.25h2.086c.466 0 .907.217 1.188.586l1.44 1.92c.424.565.372 1.347-.12 1.86L8.5 8.75a11.042 11.042 0 0 0 6.75 6.75l.884-.934c.512-.493 1.294-.544 1.86-.12l1.92 1.44c.369.281.586.722.586 1.188V19a2.25 2.25 0 0 1-2.25 2.25h-.5C9.096 21.25 2.75 14.904 2.75 7.25v-.5Z" />
                      </svg>
                    </span>
                    <div>
                      <p class="text-xs font-semibold uppercase tracking-wide text-slate-400">Số điện thoại</p>
                      <a :href="`tel:${selectedAppointment.patient.phone}`" class="text-sm font-semibold text-slate-800 hover:text-emerald-600">{{ selectedAppointment.patient.phone }}</a>
                    </div>
                  </li>
                </ul>
              </section>

              <section>
                <h3 class="text-sm font-semibold text-slate-900">Thông tin lịch hẹn</h3>
                <div class="mt-3 rounded-2xl border border-emerald-100 bg-emerald-50/50 p-4 text-sm text-slate-700">
                  <p><span class="font-semibold text-slate-900">Thời gian:</span> {{ formatDateTime(selectedAppointment.scheduledAt) }}</p>
                  <p class="mt-2"><span class="font-semibold text-slate-900">Bác sĩ:</span> {{ selectedAppointment.doctor?.account?.fullName || '—' }}</p>
                  <p class="mt-2"><span class="font-semibold text-slate-900">Phòng khám:</span> {{ selectedAppointment.clinicRoom?.name || '—' }} <span v-if="selectedAppointment.clinicRoom?.code" class="text-slate-500">({{ selectedAppointment.clinicRoom.code }})</span></p>
                  <p class="mt-2"><span class="font-semibold text-slate-900">Thời lượng:</span> {{ selectedAppointment.duration || 30 }} phút</p>
                  <p v-if="selectedAppointment.reason" class="mt-3">
                    <span class="font-semibold text-slate-900">Lý do:</span> {{ selectedAppointment.reason }}
                  </p>
                  <p v-if="selectedAppointment.notes" class="mt-3 whitespace-pre-line">
                    <span class="font-semibold text-slate-900">Ghi chú:</span>
                    <br />
                    {{ selectedAppointment.notes }}
                  </p>
                </div>
              </section>
            </div>

            <div class="mt-8 flex flex-col gap-3">
              <button
                type="button"
                class="inline-flex items-center justify-center gap-2 rounded-full border border-blue-200 bg-blue-500 px-5 py-2 text-sm font-semibold uppercase tracking-wide text-white shadow-sm transition hover:bg-blue-600 disabled:cursor-not-allowed disabled:opacity-60"
                :disabled="checkingIn || (selectedAppointment.status as AppointmentLifecycleStatus) !== 'CONFIRMED'"
                @click="handleCheckIn"
              >
                <svg v-if="checkingIn" class="h-4 w-4 animate-spin" viewBox="0 0 24 24" fill="none">
                  <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
                  <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 0 1 8-8v4l3.5-3.5L12 1v4a7 7 0 0 0-7 7H4z"></path>
                </svg>
                <svg v-else xmlns="http://www.w3.org/2000/svg" class="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.8">
                  <path stroke-linecap="round" stroke-linejoin="round" d="M9 12.75 11.25 15 15 9.75M21 12a9 9 0 1 1-18 0 9 9 0 0 1 18 0Z" />
                </svg>
                {{ checkingIn ? 'Đang tạo hồ sơ...' : 'Check-in - Tạo hồ sơ khám' }}
              </button>
              <a
                v-if="selectedAppointment.patient?.phone"
                :href="`tel:${selectedAppointment.patient.phone}`"
                class="inline-flex items-center justify-center gap-2 rounded-full border border-emerald-200 bg-emerald-500 px-5 py-2 text-sm font-semibold uppercase tracking-wide text-white shadow-sm transition hover:bg-emerald-600"
              >
                <svg xmlns="http://www.w3.org/2000/svg" class="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.8">
                  <path stroke-linecap="round" stroke-linejoin="round" d="M2.75 5.75c0-1.243 1.007-2.25 2.25-2.25h2.086c.466 0 .907.217 1.188.586l1.44 1.92c.424.565.372 1.347-.12 1.86L8.5 8.75a11.042 11.042 0 0 0 6.75 6.75l.884-.934c.512-.493 1.294-.544 1.86-.12l1.92 1.44c.369.281.586.722.586 1.188V19a2.25 2.25 0 0 1-2.25 2.25h-.5C9.096 21.25 2.75 14.904 2.75 7.25v-.5Z" />
                </svg>
                Gọi điện cho bệnh nhân
              </a>
              <p v-if="(selectedAppointment.status as AppointmentLifecycleStatus) !== 'CONFIRMED'" class="text-center text-xs text-slate-400">
                Chỉ có thể tạo hồ sơ khám cho lịch hẹn đã xác nhận.
              </p>
            </div>
          </template>

          <!-- Hiển thị Request details khi viewMode = requests -->
          <template v-else-if="viewMode === 'requests' && selectedRequest">
            <div class="flex items-start justify-between gap-4">
              <div>
                <p class="text-xs font-semibold uppercase tracking-[0.35em] text-emerald-500">Chi tiết yêu cầu</p>
                <h2 class="mt-2 text-xl font-semibold text-slate-900">{{ selectedRequest.fullName }}</h2>
                <p class="mt-1 text-sm text-slate-500">Tạo {{ formatFromNow(selectedRequest.createdAt) }}</p>
              </div>
              <span class="inline-flex items-center rounded-full px-3 py-1 text-xs font-semibold" :class="selectedStatusMeta?.badge ?? ''">
                {{ selectedStatusMeta?.label ?? 'Không xác định' }}
              </span>
            </div>

            <div class="mt-6 space-y-6">
              <section>
                <h3 class="text-sm font-semibold text-slate-900">Thông tin liên hệ</h3>
                <ul class="mt-3 space-y-2 text-sm text-slate-600">
                  <li class="flex items-center gap-3">
                    <span class="flex h-8 w-8 items-center justify-center rounded-full bg-emerald-50 text-emerald-600">
                      <svg xmlns="http://www.w3.org/2000/svg" class="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.8">
                        <path
                          stroke-linecap="round"
                          stroke-linejoin="round"
                          d="M2.75 5.75c0-1.243 1.007-2.25 2.25-2.25h2.086c.466 0 .907.217 1.188.586l1.44 1.92c.424.565.372 1.347-.12 1.86L8.5 8.75a11.042 11.042 0 0 0 6.75 6.75l.884-.934c.512-.493 1.294-.544 1.86-.12l1.92 1.44c.369.281.586.722.586 1.188V19a2.25 2.25 0 0 1-2.25 2.25h-.5C9.096 21.25 2.75 14.904 2.75 7.25v-.5Z"
                        />
                      </svg>
                    </span>
                    <div>
                      <p class="text-xs font-semibold uppercase tracking-wide text-slate-400">Số điện thoại</p>
                      <a :href="`tel:${selectedRequest.phone}`" class="text-sm font-semibold text-slate-800 hover:text-emerald-600">{{ selectedRequest.phone }}</a>
                    </div>
                  </li>
                  <li v-if="selectedRequest.email" class="flex items-center gap-3">
                    <span class="flex h-8 w-8 items-center justify-center rounded-full bg-emerald-50 text-emerald-600">
                      <svg xmlns="http://www.w3.org/2000/svg" class="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.8">
                        <path stroke-linecap="round" stroke-linejoin="round" d="M3 8.385 10.97 13.1a2 2 0 0 0 2.06 0L21 8.385M5 19h14a2 2 0 0 0 2-2V7a2 2 0 0 0-2-2H5a2 2 0 0 0-2 2v10a2 2 0 0 0 2 2Z" />
                      </svg>
                    </span>
                    <div>
                      <p class="text-xs font-semibold uppercase tracking-wide text-slate-400">Email</p>
                      <a :href="`mailto:${selectedRequest.email}`" class="text-sm font-semibold text-slate-800 hover:text-emerald-600">{{ selectedRequest.email }}</a>
                    </div>
                  </li>
                  <li v-if="selectedRequest.dateOfBirth" class="flex items-center gap-3">
                    <span class="flex h-8 w-8 items-center justify-center rounded-full bg-emerald-50 text-emerald-600">
                      <svg xmlns="http://www.w3.org/2000/svg" class="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.8">
                        <path stroke-linecap="round" stroke-linejoin="round" d="M8 7V3m8 4V3m-9 8h10M5 21h14a2 2 0 0 0 2-2V7a2 2 0 0 0-2-2H5a2 2 0 0 0-2 2v12a2 2 0 0 0 2 2Z" />
                      </svg>
                    </span>
                    <div>
                      <p class="text-xs font-semibold uppercase tracking-wide text-slate-400">Ngày sinh</p>
                      <p class="text-sm font-semibold text-slate-800">{{ formatDate(selectedRequest.dateOfBirth) }}</p>
                    </div>
                  </li>
                </ul>
              </section>

              <section>
                <h3 class="text-sm font-semibold text-slate-900">Thông tin khám</h3>
                <div class="mt-3 rounded-2xl border border-emerald-100 bg-emerald-50/50 p-4 text-sm text-slate-700">
                  <p v-if="selectedRequest.preferredAt"><span class="font-semibold text-slate-900">Khung giờ mong muốn:</span> {{ formatDateTime(selectedRequest.preferredAt) }}</p>
                  <p v-else><span class="font-semibold text-slate-900">Khung giờ mong muốn:</span> Bệnh nhân chưa chọn thời gian cụ thể.</p>
                  <p class="mt-3 whitespace-pre-line">
                    <span class="font-semibold text-slate-900">Ghi chú triệu chứng:</span>
                    <br />
                    {{ selectedRequest.symptomDescription || 'Không có ghi chú bổ sung.' }}
                  </p>
                </div>
              </section>

              <section class="rounded-2xl border border-slate-100 bg-white px-4 py-3 text-sm text-slate-600 shadow-sm">
                <p class="text-xs font-semibold uppercase tracking-wide text-slate-400">Tình trạng xử lý</p>
                <p class="mt-2">
                  <span class="font-semibold text-slate-900">Xử lý lần cuối:</span>
                  {{ selectedRequest.processedAt ? formatDateTime(selectedRequest.processedAt) : 'Chưa cập nhật' }}
                </p>
                <p v-if="selectedRequest.processedBy" class="mt-1">
                  <span class="font-semibold text-slate-900">Nhân sự:</span>
                  {{ selectedRequest.processedBy?.fullName || selectedRequest.processedBy?.email }}
                </p>
                <p v-if="selectedRequest.staffNote" class="mt-2 text-slate-600">
                  <span class="font-semibold text-slate-900">Ghi chú nội bộ:</span> {{ selectedRequest.staffNote }}
                </p>
                <p v-else class="mt-2 italic text-slate-400">Chưa có ghi chú nội bộ.</p>
              </section>
            </div>

            <div class="mt-8 flex flex-col gap-3">
              <a
                :href="`tel:${selectedRequest.phone}`"
                class="inline-flex items-center justify-center gap-2 rounded-full border border-emerald-200 bg-emerald-500 px-5 py-2 text-sm font-semibold uppercase tracking-wide text-white shadow-sm transition hover:bg-emerald-600"
              >
                Gọi điện cho bệnh nhân
              </a>
              <button
                v-if="selectedRequest.status === 'PENDING'"
                type="button"
                class="inline-flex items-center justify-center gap-2 rounded-full border border-emerald-200 bg-white px-5 py-2 text-sm font-semibold uppercase tracking-wide text-emerald-600 shadow-sm transition hover:border-emerald-300 hover:bg-emerald-50 disabled:cursor-not-allowed disabled:opacity-60 disabled:hover:border-emerald-200 disabled:hover:bg-white"
                :disabled="!canOpenWizard"
                :title="wizardTooltip"
                @click="wizardOpen = true"
              >
                Tạo lịch hẹn từ yêu cầu này
                <svg xmlns="http://www.w3.org/2000/svg" class="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.8">
                  <path stroke-linecap="round" stroke-linejoin="round" d="M9 5l7 7-7 7" />
                </svg>
              </button>
              <button
                v-if="selectedRequest.status === 'PENDING'"
                type="button"
                class="inline-flex items-center justify-center gap-2 rounded-full border border-rose-200 bg-white px-5 py-2 text-sm font-semibold uppercase tracking-wide text-rose-600 shadow-sm transition hover:border-rose-300 hover:bg-rose-50 disabled:cursor-not-allowed disabled:opacity-60"
                :disabled="rejecting"
                @click="openRejectModal"
              >
                <svg xmlns="http://www.w3.org/2000/svg" class="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.8">
                  <path stroke-linecap="round" stroke-linejoin="round" d="M6 18L18 6M6 6l12 12" />
                </svg>
                Từ chối yêu cầu
              </button>
              <p v-if="selectedRequest.status !== 'PENDING'" class="text-center text-xs text-slate-400">
                Yêu cầu đã được xử lý. Không thể tạo lịch mới hoặc từ chối yêu cầu này.
              </p>
            </div>
          </template>

          <template v-else>
            <div class="flex h-full flex-col items-center justify-center gap-4 text-center text-slate-500">
              <svg xmlns="http://www.w3.org/2000/svg" class="h-16 w-16 text-emerald-300" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.4">
                <path stroke-linecap="round" stroke-linejoin="round" d="m9 13-3 3 3 3m6-6 3 3-3 3M9 5l3-3 3 3M9 9l3-3 3 3" />
              </svg>
              <h3 class="text-lg font-semibold text-slate-800">{{ viewMode === 'appointments' ? 'Chọn một lịch hẹn để xem chi tiết' : 'Chọn một yêu cầu để xem chi tiết' }}</h3>
              <p class="text-sm text-slate-500">Danh sách bên trái sẽ cập nhật theo bộ lọc và tìm kiếm của bạn.</p>
            </div>
          </template>
        </aside>
      </section>

      <div class="mt-6 flex flex-col gap-4 border-t border-emerald-100 pt-4 text-sm text-slate-600 md:flex-row md:items-center md:justify-between">
        <span>Đang hiển thị {{ displayItems.length }} / {{ totalElements }} {{ viewMode === 'appointments' ? 'lịch hẹn' : 'yêu cầu' }}</span>
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
    </main>
    <AppointmentRequestWizard v-model="wizardOpen" :request="selectedRequest" @completed="handleWizardCompleted" />

    <!-- Modal Từ chối -->
    <Transition name="fade">
      <div
        v-if="rejectModalOpen"
        class="fixed inset-0 z-[90] flex items-center justify-center bg-slate-900/50 backdrop-blur-sm px-4"
      >
        <div class="w-full max-w-md rounded-3xl border border-rose-100 bg-white p-6 shadow-xl">
          <div class="flex items-start gap-3">
            <div class="flex h-10 w-10 items-center justify-center rounded-full bg-rose-50 text-rose-600">
              <svg xmlns="http://www.w3.org/2000/svg" class="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.8">
                <path stroke-linecap="round" stroke-linejoin="round" d="M6 18L18 6M6 6l12 12" />
              </svg>
            </div>
            <div class="flex-1">
              <h3 class="text-lg font-semibold text-slate-900">Từ chối yêu cầu đặt lịch</h3>
              <p class="mt-1 text-sm text-slate-600">
                Bạn sắp từ chối yêu cầu từ <span class="font-semibold">{{ selectedRequest?.fullName }}</span>. Vui lòng nhập lý do từ chối.
              </p>
            </div>
            <button
              type="button"
              class="flex h-9 w-9 items-center justify-center rounded-full border border-slate-200 text-slate-500 transition hover:border-slate-300 hover:text-slate-700"
              @click="closeRejectModal"
            >
              <svg xmlns="http://www.w3.org/2000/svg" class="h-4.5 w-4.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.8">
                <path stroke-linecap="round" stroke-linejoin="round" d="m16 8-8 8m0-8 8 8" />
              </svg>
            </button>
          </div>

          <div class="mt-6">
            <label class="text-xs font-semibold uppercase tracking-wide text-slate-500" for="reject-note">
              Lý do từ chối *
            </label>
            <textarea
              id="reject-note"
              v-model="rejectNote"
              rows="4"
              class="mt-2 w-full rounded-xl border border-slate-200 px-4 py-2 text-sm text-slate-700 shadow-sm transition focus:border-rose-400 focus:outline-none focus:ring-4 focus:ring-rose-100/80"
              placeholder="Nhập lý do từ chối yêu cầu đặt lịch..."
              maxlength="255"
            ></textarea>
            <p class="mt-1 text-xs text-slate-400">{{ rejectNote.length }}/255 ký tự</p>
          </div>

          <div class="mt-6 flex items-center justify-end gap-3">
            <button
              type="button"
              class="inline-flex items-center gap-2 rounded-full border border-slate-200 bg-white px-4 py-2 text-xs font-semibold uppercase tracking-wide text-slate-600 shadow-sm transition hover:border-slate-300 hover:bg-slate-50"
              :disabled="rejecting"
              @click="closeRejectModal"
            >
              Hủy
            </button>
            <button
              type="button"
              class="inline-flex items-center gap-2 rounded-full bg-rose-600 px-5 py-2 text-xs font-semibold uppercase tracking-wide text-white shadow-sm transition hover:bg-rose-700 disabled:cursor-not-allowed disabled:opacity-70"
              :disabled="rejecting || !rejectNote.trim()"
              @click="handleReject"
            >
              <svg v-if="rejecting" class="h-4 w-4 animate-spin" viewBox="0 0 24 24" fill="none">
                <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
                <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 0 1 8-8v4l3.5-3.5L12 1v4a7 7 0 0 0-7 7H4z"></path>
              </svg>
              <span>{{ rejecting ? 'Đang xử lý...' : 'Từ chối yêu cầu' }}</span>
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
</style>
