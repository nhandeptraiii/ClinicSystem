<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';
import { useRouter } from 'vue-router';
import AdminHeader from '@/components/AdminHeader.vue';
import AppointmentRequestWizard from '@/components/AppointmentRequestWizard.vue';
import { useAuthStore } from '@/stores/authStore';
import { fetchAppointmentRequests, type AppointmentRequest, type AppointmentRequestStatus } from '@/services/appointmentRequest.service';

type StatusFilter = AppointmentRequestStatus | 'ALL';
type StatusCountMap = Record<StatusFilter, number>;

const authStore = useAuthStore();
const router = useRouter();

const userName = computed(() => authStore.user?.username ?? 'Quản trị viên');

const requests = ref<AppointmentRequest[]>([]);
const loading = ref(false);
const error = ref<string | null>(null);
const searchTerm = ref('');
const selectedStatus = ref<StatusFilter>('PENDING');
const selectedRequestId = ref<number | null>(null);
const lastLoadedAt = ref<string | null>(null);
const wizardOpen = ref(false);

const statusMeta: Record<AppointmentRequestStatus, { label: string; badge: string; dot: string }> = {
  PENDING: { label: 'Chờ duyệt', badge: 'bg-amber-100 text-amber-700', dot: 'bg-amber-500' },
  CONFIRMED: { label: 'Đã xác nhận', badge: 'bg-emerald-100 text-emerald-700', dot: 'bg-emerald-500' },
  REJECTED: { label: 'Đã từ chối', badge: 'bg-rose-100 text-rose-700', dot: 'bg-rose-500' },
};

const statusFilters: Array<{ key: StatusFilter; label: string }> = [
  { key: 'ALL', label: 'Tất cả' },
  { key: 'PENDING', label: 'Chờ duyệt' },
  { key: 'CONFIRMED', label: 'Đã xác nhận' },
  { key: 'REJECTED', label: 'Đã từ chối' },
];

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

const loadRequests = async () => {
  loading.value = true;
  error.value = null;
  try {
    const result = await fetchAppointmentRequests();
    requests.value = Array.isArray(result) ? result : [];
    lastLoadedAt.value = new Date().toISOString();
  } catch (err) {
    console.error('Failed to fetch appointment requests', err);
    error.value = extractErrorMessage(err);
  } finally {
    loading.value = false;
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
  return requests.value.reduce(
    (acc, curr) => {
      acc.ALL += 1;
      acc[curr.status] += 1;
      return acc;
    },
    { ALL: 0, PENDING: 0, CONFIRMED: 0, REJECTED: 0 } as StatusCountMap
  );
});

const sortedRequests = computed(() => {
  return [...requests.value].sort((a, b) => {
    const timeA = getDate(a.createdAt)?.getTime() ?? getDate(a.preferredAt)?.getTime() ?? 0;
    const timeB = getDate(b.createdAt)?.getTime() ?? getDate(b.preferredAt)?.getTime() ?? 0;
    return timeB - timeA;
  });
});

const filteredRequests = computed(() => {
  const keyword = searchTerm.value.trim().toLowerCase();
  return sortedRequests.value.filter((request) => {
    const matchStatus = selectedStatus.value === 'ALL' ? true : request.status === selectedStatus.value;
    if (!matchStatus) return false;
    if (!keyword) return true;
    const haystacks = [
      request.fullName,
      request.phone,
      request.email ?? '',
      request.patient?.code ?? '',
      request.patient?.fullName ?? '',
    ];
    return haystacks.some((value) => value?.toLowerCase().includes(keyword));
  });
});

const selectedRequest = computed(() => filteredRequests.value.find((item) => item.id === selectedRequestId.value) ?? null);

const selectedStatusMeta = computed(() =>
  selectedRequest.value ? statusMeta[selectedRequest.value.status] : undefined
);

const canOpenWizard = computed(() => selectedRequest.value?.status === 'PENDING');
const wizardTooltip = computed(() =>
  canOpenWizard.value ? '' : 'Chỉ có thể tạo lịch với yêu cầu đang ở trạng thái chờ duyệt.'
);

watch(
  filteredRequests,
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

const totalRequests = computed(() => statusCounts.value.ALL);
const pendingShare = computed(() => {
  if (statusCounts.value.ALL === 0) return 0;
  return Math.round((statusCounts.value.PENDING / statusCounts.value.ALL) * 100);
});
const lastLoadedDisplay = computed(() => (lastLoadedAt.value ? formatFromNow(lastLoadedAt.value) : 'Chưa tải'));
const hasActiveFilters = computed(() => selectedStatus.value !== 'ALL' || Boolean(searchTerm.value.trim()));

onMounted(() => {
  loadRequests();
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
  await loadRequests();
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
              @click="loadRequests"
            >
              <svg xmlns="http://www.w3.org/2000/svg" class="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.8">
                <path stroke-linecap="round" stroke-linejoin="round" d="M4 4v5h.582m15.356 2A8.001 8.001 0 0 0 5.582 9H4m0 0a8.003 8.003 0 0 1 14.9-3m1.518 9H20v5h.004m-.586-2A8.001 8.001 0 0 1 5.518 15H4m0 0a8.003 8.003 0 0 0 14.9 3" />
              </svg>
              <span>{{ loading ? 'Đang tải...' : 'Làm mới' }}</span>
            </button>
            <div class="rounded-2xl border border-emerald-100 bg-emerald-50/70 px-4 py-2 text-sm text-emerald-700 shadow-inner">
              <p class="font-semibold">Tổng yêu cầu: {{ totalRequests }}</p>
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
            <p class="text-xs font-semibold uppercase tracking-wide text-rose-600/80">Đã từ chối</p>
            <p class="mt-2 text-3xl font-semibold">{{ statusCounts.REJECTED }}</p>
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

        <p v-if="hasActiveFilters" class="mt-4 text-xs text-emerald-600">Đang hiển thị {{ filteredRequests.length }} yêu cầu phù hợp bộ lọc hiện tại.</p>
      </section>

      <section class="mt-10 grid gap-6 lg:grid-cols-[1.7fr_1fr]">
        <div class="rounded-[28px] border border-emerald-100 bg-white/90 p-6 shadow-[0_20px_55px_-45px_rgba(13,148,136,0.55)]">
          <template v-if="error">
            <div class="rounded-2xl border border-rose-100 bg-rose-50/90 p-6 text-rose-700 shadow-inner">
              <h3 class="text-sm font-semibold uppercase tracking-wide">Không thể tải dữ liệu</h3>
              <p class="mt-2 text-sm">{{ error }}</p>
              <button
                type="button"
                class="mt-4 inline-flex items-center gap-2 rounded-full border border-rose-200 bg-white px-4 py-2 text-xs font-semibold uppercase tracking-wide text-rose-600 transition hover:border-rose-300 hover:bg-rose-50"
                @click="loadRequests"
              >
                Thử lại
              </button>
            </div>
          </template>

          <template v-else>
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

            <div v-else-if="filteredRequests.length === 0" class="rounded-3xl border border-dashed border-emerald-200 bg-emerald-50/30 p-8 text-center text-emerald-600">
              <svg xmlns="http://www.w3.org/2000/svg" class="mx-auto h-12 w-12 text-emerald-400" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.6">
                <path stroke-linecap="round" stroke-linejoin="round" d="M9 13h6m-3-3v6m8 1V7a2 2 0 0 0-2-2h-3.172a2 2 0 0 1-1.414-.586l-.828-.828A2 2 0 0 0 12.172 3H8a2 2 0 0 0-2 2v14m-1-1h14" />
              </svg>
              <h3 class="mt-4 text-base font-semibold">Không có yêu cầu phù hợp</h3>
              <p class="mt-2 text-sm text-emerald-600/80">
                {{ hasActiveFilters ? 'Hãy thử thay đổi bộ lọc hoặc xóa từ khóa tìm kiếm.' : 'Khi có yêu cầu mới, chúng sẽ xuất hiện tại đây.' }}
              </p>
            </div>

            <div v-else class="space-y-3">
              <button
                v-for="request in filteredRequests"
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
                    <span class="mt-1 flex h-2.5 w-2.5 flex-shrink-0 rounded-full" :class="statusMeta[request.status].dot"></span>
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
                    <span class="inline-flex items-center rounded-full px-3 py-1 text-xs font-semibold" :class="statusMeta[request.status].badge">
                      {{ statusMeta[request.status].label }}
                    </span>
                    <p v-if="request.preferredAt">
                      Mong muốn: <span class="font-semibold text-slate-700">{{ formatDateTime(request.preferredAt) }}</span>
                    </p>
                    <p>Nhận {{ formatFromNow(request.createdAt) }}</p>
                  </div>
                </div>
              </button>
            </div>
          </template>
        </div>

        <aside class="rounded-[28px] border border-emerald-100 bg-white/95 p-6 shadow-[0_20px_55px_-45px_rgba(13,148,136,0.55)]">
          <template v-if="selectedRequest">
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
              <RouterLink
                to="/dashboard/appointments"
                class="inline-flex items-center justify-center gap-2 rounded-full border border-emerald-100 bg-emerald-50 px-5 py-2 text-sm font-semibold uppercase tracking-wide text-emerald-600 shadow-sm transition hover:border-emerald-200 hover:bg-emerald-100"
              >
                Xem danh sách lịch hẹn
                <svg xmlns="http://www.w3.org/2000/svg" class="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.8">
                  <path stroke-linecap="round" stroke-linejoin="round" d="M9 5l7 7-7 7" />
                </svg>
              </RouterLink>
              <p v-if="selectedRequest.status !== 'PENDING'" class="text-center text-xs text-slate-400">
                Yêu cầu đã được xử lý. Không thể tạo lịch mới từ yêu cầu này.
              </p>
            </div>
          </template>

          <template v-else>
            <div class="flex h-full flex-col items-center justify-center gap-4 text-center text-slate-500">
              <svg xmlns="http://www.w3.org/2000/svg" class="h-16 w-16 text-emerald-300" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.4">
                <path stroke-linecap="round" stroke-linejoin="round" d="m9 13-3 3 3 3m6-6 3 3-3 3M9 5l3-3 3 3M9 9l3-3 3 3" />
              </svg>
              <h3 class="text-lg font-semibold text-slate-800">Chọn một yêu cầu để xem chi tiết</h3>
              <p class="text-sm text-slate-500">Danh sách bên trái sẽ cập nhật theo bộ lọc và tìm kiếm của bạn.</p>
            </div>
          </template>
        </aside>
      </section>
    </main>
    <AppointmentRequestWizard v-model="wizardOpen" :request="selectedRequest" @completed="handleWizardCompleted" />
  </div>
</template>
