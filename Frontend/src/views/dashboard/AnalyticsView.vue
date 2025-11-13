<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import AdminHeader from '@/components/AdminHeader.vue';
import { useAuthStore } from '@/stores/authStore';
import { useToast, type ToastType } from '@/composables/useToast';
import { fetchAppointmentPage, type AppointmentDetail } from '@/services/appointment.service';
import { fetchAppointmentRequests } from '@/services/appointmentRequest.service';
import { fetchBillingPage, type Billing } from '@/services/billing.service';

type ToastVisual = {
  title: string;
  container: string;
  icon: string;
  iconType: 'success' | 'error' | 'warning' | 'info';
};

const authStore = useAuthStore();
const router = useRouter();
const userName = computed(() => authStore.user?.username ?? 'Quản trị viên');

const { toast, show: showToast, hide: hideToast } = useToast();
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

const selectedMonth = ref<string>(new Date().toISOString().slice(0, 7));
const monthRange = computed(() => {
  const [rawYear, rawMonth] = selectedMonth.value.split('-');
  const now = new Date();
  const year = Number(rawYear) || now.getFullYear();
  const monthIndex = (Number(rawMonth) || now.getMonth() + 1) - 1; // 0-based
  const start = new Date(year, monthIndex, 1);
  const end = new Date(year, monthIndex + 1, 0);
  return {
    start,
    end,
    startIso: start.toISOString().slice(0, 10),
    endIso: end.toISOString().slice(0, 10),
  };
});

const loading = ref(false);
const error = ref<string | null>(null);

const appointments = ref<AppointmentDetail[]>([]);
const billings = ref<Billing[]>([]);
const pendingRequests = ref<number>(0);

const inRange = (iso?: string | null) => {
  if (!iso) return false;
  const current = new Date(iso);
  const start = new Date(`${monthRange.value.startIso}T00:00:00`);
  const end = new Date(`${monthRange.value.endIso}T23:59:59`);
  return current >= start && current <= end;
};

const filteredAppointments = computed(() => appointments.value.filter((appt) => inRange(appt.scheduledAt)));

const totalAppointments = computed(() => filteredAppointments.value.length);
const confirmedAppointments = computed(
  () => filteredAppointments.value.filter((appt) => appt.status === 'CONFIRMED').length,
);
const checkedInAppointments = computed(
  () => filteredAppointments.value.filter((appt) => appt.status === 'CHECKED_IN').length,
);
const completedAppointments = computed(
  () => filteredAppointments.value.filter((appt) => appt.status === 'COMPLETED').length,
);
const cancelledAppointments = computed(
  () => filteredAppointments.value.filter((appt) => appt.status === 'CANCELLED').length,
);

const paidBillings = computed(() => billings.value.filter((bill) => bill.status === 'PAID' && inRange(bill.issuedAt)));
const totalRevenue = computed(() =>
  paidBillings.value.reduce((sum, bill) => sum + (bill.totalAmount ?? 0), 0),
);

type SeriesPoint = { label: string; value: number };

const dateBuckets = computed(() => {
  const labels: string[] = [];
  const start = new Date(`${monthRange.value.startIso}T00:00:00`);
  const end = new Date(`${monthRange.value.endIso}T00:00:00`);
  const cursor = new Date(start);
  while (cursor <= end) {
    labels.push(cursor.toISOString().slice(0, 10));
    cursor.setDate(cursor.getDate() + 1);
  }
  return labels;
});

const appointmentSeries = computed<SeriesPoint[]>(() =>
  dateBuckets.value.map((label) => ({
    label,
    value: filteredAppointments.value.filter((appt) => (appt.scheduledAt ?? '').startsWith(label)).length,
  })),
);

const revenueSeries = computed<SeriesPoint[]>(() =>
  dateBuckets.value.map((label) => ({
    label,
    value: paidBillings.value
      .filter((bill) => (bill.issuedAt ?? '').startsWith(label))
      .reduce((sum, bill) => sum + (bill.totalAmount ?? 0), 0),
  })),
);

const maxAppointmentValue = computed(() => Math.max(1, ...appointmentSeries.value.map((point) => point.value)));
const maxRevenueValue = computed(() => Math.max(1, ...revenueSeries.value.map((point) => point.value)));

const buildLinePath = (series: SeriesPoint[], width = 560, height = 160, padding = 12) => {
  if (!series.length) return '';
  const maxValue = Math.max(1, ...series.map((point) => point.value));
  const xStep = (width - padding * 2) / Math.max(1, series.length - 1);
  return series
    .map((point, index) => {
      const x = padding + index * xStep;
      const y = height - padding - (point.value / maxValue) * (height - padding * 2);
      return `${index === 0 ? 'M' : 'L'} ${x} ${y}`;
    })
    .join(' ');
};

const formatCurrency = (value: number) =>
  new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' }).format(value ?? 0);

const loadAnalytics = async () => {
  loading.value = true;
  error.value = null;
  try {
    const [apptPage, pendingList, billingPage] = await Promise.all([
      fetchAppointmentPage({ page: 0, size: 500 }),
      fetchAppointmentRequests({ status: 'PENDING' }),
      fetchBillingPage({ page: 0, size: 500, status: 'PAID' }),
    ]);
    appointments.value = Array.isArray(apptPage.items) ? apptPage.items : [];
    pendingRequests.value = Array.isArray(pendingList) ? pendingList.length : 0;
    billings.value = Array.isArray(billingPage.items) ? billingPage.items : [];
  } catch (err: any) {
    const message = err?.response?.data?.message || err?.message || 'Không thể tải dữ liệu thống kê.';
    error.value = message;
    showToast('error', message);
  } finally {
    loading.value = false;
  }
};

const handleSignOut = async () => {
  await authStore.signOut();
  router.replace({ name: 'home' });
};

onMounted(loadAnalytics);
</script>

<template>
  <div class="relative min-h-screen bg-gradient-to-br from-emerald-50 via-white to-white">
    <div class="pointer-events-none absolute inset-0 overflow-hidden">
      <div class="absolute -top-32 -right-48 h-96 w-96 rounded-full bg-emerald-200/30 blur-3xl"></div>
      <div class="absolute -bottom-28 -left-32 h-96 w-96 rounded-full bg-teal-200/30 blur-3xl"></div>
    </div>

    <AdminHeader :user-name="userName" @sign-out="handleSignOut" />
    <main class="relative mx-auto max-w-6xl px-6 py-12">
      <section
        class="rounded-[32px] border border-emerald-100 bg-white/90 p-7 shadow-[0_24px_60px_-40px_rgba(13,148,136,0.6)]"
      >
        <div class="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
          <div>
            <p class="text-xs font-semibold uppercase tracking-[0.4em] text-emerald-500">Analytics</p>
            <h2 class="mt-1 text-2xl font-semibold text-slate-900">Thống kê hoạt động</h2>
            <p class="text-sm text-slate-600">GIám sát lịch hẹn, doanh thu và nhu cầu của bệnh nhân.</p>
          </div>
          <div class="flex flex-wrap items-center gap-3">
            <label class="text-xs font-semibold uppercase tracking-wide text-slate-500">
              Chọn tháng
              <input
                v-model="selectedMonth"
                type="month"
                class="mt-1 rounded-xl border border-emerald-200 px-4 py-2 text-sm text-slate-700 shadow-sm transition focus:border-emerald-400 focus:outline-none focus:ring-4 focus:ring-emerald-100/80"
              />
            </label>
          </div>
        </div>

        <div class="mt-6 grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
          <div class="rounded-2xl border border-emerald-100 bg-emerald-50/70 p-4 text-emerald-700">
            <p class="text-xs font-semibold uppercase tracking-wide">Tổng lịch hẹn</p>
            <p class="mt-2 text-3xl font-semibold">{{ totalAppointments }}</p>
          </div>
          <div class="rounded-2xl border border-sky-100 bg-sky-50/70 p-4 text-sky-700">
            <p class="text-xs font-semibold uppercase tracking-wide">Đã xác nhận</p>
            <p class="mt-2 text-3xl font-semibold">{{ confirmedAppointments }}</p>
          </div>
          <div class="rounded-2xl border border-amber-100 bg-amber-50/70 p-4 text-amber-700">
            <p class="text-xs font-semibold uppercase tracking-wide">Check-in</p>
            <p class="mt-2 text-3xl font-semibold">{{ checkedInAppointments }}</p>
          </div>
          <div class="rounded-2xl border border-rose-100 bg-rose-50/70 p-4 text-rose-700">
            <p class="text-xs font-semibold uppercase tracking-wide">Đã hủy</p>
            <p class="mt-2 text-3xl font-semibold">{{ cancelledAppointments }}</p>
          </div>
        </div>

        <div class="mt-5 grid gap-5 lg:grid-cols-2">
          <div class="rounded-2xl border border-slate-200 bg-white p-5">
            <div class="flex items-center justify-between">
              <div>
                <p class="text-xs font-semibold uppercase tracking-wide text-slate-500">Xu hướng</p>
                <h3 class="text-base font-semibold text-slate-900">Lịch hẹn mỗi ngày</h3>
              </div>
              <p class="text-xs text-slate-500">{{ dateBuckets.length }} ngày</p>
            </div>
            <svg viewBox="0 0 600 190" class="mt-4 h-44 w-full">
              <rect x="0" y="0" width="600" height="190" fill="transparent" />
              <path :d="buildLinePath(appointmentSeries)" stroke="#059669" stroke-width="3" fill="none" />
            </svg>
            <p class="mt-2 text-xs text-slate-500">
              Đỉnh: <span class="font-semibold text-slate-700">{{ maxAppointmentValue }} lịch/ngày</span>
            </p>
          </div>

          <div class="rounded-2xl border border-slate-200 bg-white p-5">
            <div class="flex items-center justify-between">
              <div>
                <p class="text-xs font-semibold uppercase tracking-wide text-slate-500">Doanh thu</p>
                <h3 class="text-base font-semibold text-slate-900">Đã thanh toán</h3>
              </div>
              <p class="text-xs text-slate-500">{{ dateBuckets.length }} ngày</p>
            </div>
            <svg viewBox="0 0 600 190" class="mt-4 h-44 w-full">
              <rect x="0" y="0" width="600" height="190" fill="transparent" />
              <g>
                <template v-for="(point, index) in revenueSeries" :key="point.label">
                  <rect
                    :x="20 + index * (500 / Math.max(1, revenueSeries.length))"
                    :y="180 - (point.value / maxRevenueValue) * 150"
                    :height="(point.value / maxRevenueValue) * 150"
                    :width="Math.max(4, 500 / Math.max(4, revenueSeries.length) - 6)"
                    fill="#10b981"
                    opacity="0.75"
                  />
                </template>
              </g>
            </svg>
            <div class="mt-2 text-xs text-slate-500">
              Tổng: <span class="font-semibold text-emerald-700">{{ formatCurrency(totalRevenue) }}</span>
            </div>
          </div>
        </div>

        <div class="mt-5 grid gap-5 lg:grid-cols-2">
          <div class="rounded-2xl border border-slate-200 bg-white p-5">
            <h3 class="text-sm font-semibold text-slate-900">Yêu cầu đặt lịch đang chờ</h3>
            <p class="mt-3 text-4xl font-semibold text-slate-800">{{ pendingRequests }}</p>
            <p class="text-xs text-slate-500">Cần duyệt để chuyển thành lịch hẹn.</p>
          </div>
          <div class="rounded-2xl border border-slate-200 bg-white p-5">
            <h3 class="text-sm font-semibold text-slate-900">Lịch hẹn đã hoàn tất</h3>
            <p class="mt-3 text-4xl font-semibold text-slate-800">{{ completedAppointments }}</p>
            <p class="text-xs text-slate-500">Trong khoảng thời gian đã chọn.</p>
          </div>
        </div>

        <div v-if="error" class="mt-5 rounded-xl border border-rose-200 bg-rose-50 p-4 text-sm text-rose-700">
          {{ error }}
        </div>
        <p v-else-if="loading" class="mt-5 text-sm text-slate-500">Đang tải dữ liệu thống kê...</p>
      </section>
    </main>

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
              aria-label="Đóng thông báo"
              @click="dismissToast"
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
