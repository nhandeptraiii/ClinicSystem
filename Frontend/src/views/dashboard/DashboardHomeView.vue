<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { fetchDashboardSummary, type DashboardSummary } from '@/services/dashboard.service';
import { useAuthStore } from '@/stores/authStore';

type StatusCount = {
  appointmentsToday: number;
  appointmentsConfirmed: number;
  appointmentsCheckedIn: number;
  pendingRequests: number;
  visitsInProgress: number;
  visitsCompleted: number;
  activeDoctors: number;
};

const loading = ref(false);
const error = ref<string | null>(null);
const status = ref<StatusCount>({
  appointmentsToday: 0,
  appointmentsConfirmed: 0,
  appointmentsCheckedIn: 0,
  pendingRequests: 0,
  visitsInProgress: 0,
  visitsCompleted: 0,
  activeDoctors: 0,
});

const authStore = useAuthStore();
const canViewDashboard = computed(() => authStore.isAuthenticated);

const loadDashboard = async () => {
  loading.value = true;
  error.value = null;
  try {
    if (!canViewDashboard.value) {
      throw new Error('Không đủ quyền xem tổng quan.');
    }
    const summary: DashboardSummary = await fetchDashboardSummary();
    status.value = { ...summary };
  } catch (err: any) {
    console.error('Failed to load dashboard data', err);
    error.value =
      err?.response?.data?.message ??
      err?.message ??
      'Không thể tải dữ liệu tổng quan. Vui lòng thử lại.';
  } finally {
    loading.value = false;
  }
};

const highlightCards = computed(() => [
  {
    title: 'Lịch hẹn hôm nay',
    value: status.value.appointmentsToday,
    detail: `${status.value.appointmentsConfirmed} đã xác nhận · ${status.value.appointmentsCheckedIn} đã check-in`,
  },
  {
    title: 'Bệnh nhân đang khám',
    value: status.value.visitsInProgress,
    detail: `${status.value.visitsCompleted} đã hoàn thành`,
  },
  {
    title: 'Yêu cầu đặt lịch chờ duyệt',
    value: status.value.pendingRequests,
    detail: 'Cần xử lý sớm',
  },
  {
    title: 'Bác sĩ đang làm việc',
    value: status.value.activeDoctors,
    detail: 'Trong ngày hôm nay',
  },
]);

onMounted(() => {
  loadDashboard();
});
</script>

<template>
  <div class="mx-auto flex max-w-6xl flex-col gap-6">
    <header class="flex flex-col gap-2 border-b border-emerald-100 pb-4">
      <p class="text-xs font-semibold uppercase tracking-[0.32em] text-emerald-500">Tổng quan</p>
      <h1 class="text-2xl font-semibold text-slate-900">Bảng điều khiển chung</h1>
      <p class="text-sm text-slate-600">
        Nắm nhanh tình hình lịch hẹn, khám bệnh và các yêu cầu cần xử lý trong ngày. Dành cho mọi vai trò.
      </p>
    </header>

    <section class="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
      <article
        v-for="card in highlightCards"
        :key="card.title"
        class="rounded-2xl border border-emerald-100 bg-white p-5 shadow-sm"
      >
        <p class="text-xs font-semibold uppercase tracking-wide text-emerald-600">{{ card.title }}</p>
        <div class="mt-3 flex items-baseline gap-2">
          <span class="text-3xl font-semibold text-slate-900">
            <span v-if="loading">--</span>
            <span v-else>{{ card.value }}</span>
          </span>
          <span class="text-xs uppercase tracking-wide text-slate-400">hôm nay</span>
        </div>
        <p class="mt-2 text-sm text-slate-600">{{ card.detail }}</p>
      </article>
    </section>

    <section class="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
      <div class="flex flex-col gap-2 md:flex-row md:items-center md:justify-between">
        <div>
          <p class="text-xs font-semibold uppercase tracking-[0.3em] text-emerald-500">Lịch hẹn</p>
          <h2 class="text-lg font-semibold text-slate-900">Tình hình trong ngày</h2>
          <p class="text-sm text-slate-600">
            Số lịch hẹn đã xác nhận, đã check-in và đang chờ xử lý. Kiểm tra danh sách để cập nhật kịp thời.
          </p>
        </div>
      </div>

      <div class="mt-4 grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
        <div class="rounded-xl border border-emerald-100 bg-emerald-50/70 p-4">
          <p class="text-xs font-semibold uppercase tracking-wide text-emerald-600">Đã xác nhận</p>
          <p class="mt-2 text-2xl font-semibold text-emerald-900">
            <span v-if="loading">--</span>
            <span v-else>{{ status.appointmentsConfirmed }}</span>
          </p>
          <p class="mt-1 text-sm text-emerald-700/80">Lịch hẹn đã xác nhận hôm nay</p>
        </div>
        <div class="rounded-xl border border-sky-100 bg-sky-50/70 p-4">
          <p class="text-xs font-semibold uppercase tracking-wide text-sky-600">Đã check-in</p>
          <p class="mt-2 text-2xl font-semibold text-sky-900">
            <span v-if="loading">--</span>
            <span v-else>{{ status.appointmentsCheckedIn }}</span>
          </p>
          <p class="mt-1 text-sm text-sky-700/80">Bệnh nhân đã đến khám</p>
        </div>
        <div class="rounded-xl border border-amber-100 bg-amber-50/70 p-4">
          <p class="text-xs font-semibold uppercase tracking-wide text-amber-600">Chờ duyệt</p>
          <p class="mt-2 text-2xl font-semibold text-amber-900">
            <span v-if="loading">--</span>
            <span v-else>{{ status.pendingRequests }}</span>
          </p>
          <p class="mt-1 text-sm text-amber-700/80">Yêu cầu đặt lịch cần xử lý</p>
        </div>
      </div>
    </section>

    <section class="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
      <div class="flex flex-col gap-2 md:flex-row md:items-center md:justify-between">
        <div>
          <p class="text-xs font-semibold uppercase tracking-[0.3em] text-emerald-500">Khám bệnh</p>
          <h2 class="text-lg font-semibold text-slate-900">Luồng khám hiện tại</h2>
          <p class="text-sm text-slate-600">
            Số bệnh nhân đang khám và đã hoàn thành trong ngày. Hỗ trợ điều phối phòng khám.
          </p>
        </div>
      </div>

      <div class="mt-4 grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
        <div class="rounded-xl border border-indigo-100 bg-indigo-50/70 p-4">
          <p class="text-xs font-semibold uppercase tracking-wide text-indigo-600">Đang khám</p>
          <p class="mt-2 text-2xl font-semibold text-indigo-900">
            <span v-if="loading">--</span>
            <span v-else>{{ status.visitsInProgress }}</span>
          </p>
          <p class="mt-1 text-sm text-indigo-700/80">Bệnh nhân đang ở phòng khám</p>
        </div>
        <div class="rounded-xl border border-teal-100 bg-teal-50/70 p-4">
          <p class="text-xs font-semibold uppercase tracking-wide text-teal-600">Đã hoàn thành</p>
          <p class="mt-2 text-2xl font-semibold text-teal-900">
            <span v-if="loading">--</span>
            <span v-else>{{ status.visitsCompleted }}</span>
          </p>
          <p class="mt-1 text-sm text-teal-700/80">Ca khám đã xong hôm nay</p>
        </div>
        <div class="rounded-xl border border-slate-200 bg-slate-50 p-4">
          <p class="text-xs font-semibold uppercase tracking-wide text-slate-600">Bác sĩ hoạt động</p>
          <p class="mt-2 text-2xl font-semibold text-slate-900">
            <span v-if="loading">--</span>
            <span v-else>{{ status.activeDoctors }}</span>
          </p>
          <p class="mt-1 text-sm text-slate-600">Có ca khám trong ngày</p>
        </div>
      </div>

      <div v-if="error" class="mt-4 rounded-xl border border-rose-200 bg-rose-50/80 px-4 py-3 text-sm text-rose-700">
        {{ error }}
      </div>
    </section>
  </div>
</template>
