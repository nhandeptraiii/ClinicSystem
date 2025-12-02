<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';
import { useRouter } from 'vue-router';
import AdminHeader from '@/components/AdminHeader.vue';
import { useAuthStore } from '@/stores/authStore';
import { useToast, type ToastType } from '@/composables/useToast';
import {
  fetchVisits,
  createVisit,
  type PatientVisit,
  type PatientVisitCreatePayload,
} from '@/services/visit.service';
import { fetchAppointments, type AppointmentDetail } from '@/services/appointment.service';

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

const visits = ref<PatientVisit[]>([]);
const loading = ref(false);

// Pagination (client-side since backend doesn't support it)
const PAGE_SIZE = 20;
const currentPage = ref(1);
const totalPages = computed(() => Math.ceil(filteredVisits.value.length / PAGE_SIZE));
const paginatedVisits = computed(() => {
  const start = (currentPage.value - 1) * PAGE_SIZE;
  const end = start + PAGE_SIZE;
  return filteredVisits.value.slice(start, end);
});

// Filters
const searchTerm = ref('');
const statusFilter = ref<string>('');
const dateFilter = ref<string>('');
const quickFilter = ref<string>('');

const toLocalDateString = (value?: string | Date | null) => {
  if (!value) return null;
  const date = value instanceof Date ? value : new Date(value);
  if (Number.isNaN(date.getTime())) return null;
  return date.toLocaleDateString('sv-SE');
};

const getVisitLocalDate = (visit: PatientVisit) =>
  toLocalDateString(visit.primaryAppointment?.scheduledAt ?? visit.createdAt);

const filteredVisits = computed(() => {
  let result = [...visits.value];
  const today = toLocalDateString(new Date());
  const currentDoctorIdRaw = authStore.user?.doctorId ?? authStore.user?.id ?? authStore.user?.accountId;
  const currentDoctorId = currentDoctorIdRaw != null ? String(currentDoctorIdRaw) : null;
  const currentDoctorUsername = authStore.user?.username
    ? authStore.user.username.toLowerCase()
    : null;
  const currentDoctorEmail = authStore.user?.email ? authStore.user.email.toLowerCase() : null;

  // Search by patient name
  if (searchTerm.value.trim()) {
    const term = searchTerm.value.toLowerCase().trim();
    result = result.filter(
      (visit) =>
        visit.patient?.fullName?.toLowerCase().includes(term) ||
        visit.patient?.code?.toLowerCase().includes(term),
    );
  }

  // Filter by status
  if (statusFilter.value) {
    result = result.filter((visit) => visit.status === statusFilter.value);
  }

  // Quick filters
  if (quickFilter.value === 'TODAY' && today) {
    result = result.filter((visit) => getVisitLocalDate(visit) === today);
  }

  if (quickFilter.value === 'MY_VISITS') {
    result = result.filter((visit) => {
      const doctorIds = [
        visit.primaryAppointment?.doctor?.account?.id,
        visit.primaryAppointment?.doctor?.id,
      ];
      const doctorUsername = visit.primaryAppointment?.doctor?.account?.username;
      const doctorEmail = visit.primaryAppointment?.doctor?.account?.email;

      // Ưu tiên so khớp theo id nếu có, fallback theo username/email
      if (
        currentDoctorId &&
        doctorIds.some((id) => id != null && String(id) === currentDoctorId)
      ) {
        return true;
      }

      if (
        currentDoctorUsername &&
        typeof doctorUsername === 'string' &&
        doctorUsername.toLowerCase() === currentDoctorUsername
      ) {
        return true;
      }

      if (
        currentDoctorEmail &&
        typeof doctorEmail === 'string' &&
        doctorEmail.toLowerCase() === currentDoctorEmail
      ) {
        return true;
      }
      return false;
    });
  }

  // Filter by date
  if (dateFilter.value) {
    const filterDate = dateFilter.value;
    result = result.filter((visit) => getVisitLocalDate(visit) === filterDate);
  }

  return result;
});

const visitStatusOptions = [
  { value: '', label: 'Tất cả trạng thái' },
  { value: 'OPEN', label: 'Đang khám' },
  { value: 'COMPLETED', label: 'Hoàn thành' },
  { value: 'CANCELLED', label: 'Đã hủy' },
];

const getStatusLabel = (status?: string) => {
  const option = visitStatusOptions.find((opt) => opt.value === status);
  return option?.label ?? status ?? 'N/A';
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

// Create Visit Modal
const createModalOpen = ref(false);
const createModalSubmitting = ref(false);
const createFormState = ref<PatientVisitCreatePayload>({
  primaryAppointmentId: 0,
  provisionalDiagnosis: null,
});

const availableAppointments = ref<AppointmentDetail[]>([]);
const loadingAppointments = ref(false);
const appointmentSearchTerm = ref('');

const filteredAppointments = computed(() => {
  if (!appointmentSearchTerm.value.trim()) {
    return availableAppointments.value.filter((apt) => apt.status === 'CONFIRMED');
  }
  const term = appointmentSearchTerm.value.toLowerCase();
  return availableAppointments.value.filter(
    (apt) =>
      apt.status === 'CONFIRMED' &&
      (apt.patient?.fullName?.toLowerCase().includes(term) ||
        apt.patient?.code?.toLowerCase().includes(term) ||
        apt.doctor?.account?.fullName?.toLowerCase().includes(term)),
  );
});

const loadAppointments = async () => {
  loadingAppointments.value = true;
  try {
    const data = await fetchAppointments({ status: 'CONFIRMED' });
    availableAppointments.value = data;
  } catch (err) {
    console.error('Failed to load appointments:', err);
    showToast('error', 'Không thể tải danh sách lịch hẹn.');
  } finally {
    loadingAppointments.value = false;
  }
};

const loadVisits = async () => {
  loading.value = true;
  try {
    visits.value = await fetchVisits();
  } catch (err: any) {
    const errorMessage = err?.message ?? 'Không thể tải danh sách lượt khám.';
    showToast('error', errorMessage);
  } finally {
    loading.value = false;
  }
};

const openCreateModal = async () => {
  createFormState.value = {
    primaryAppointmentId: 0,
    provisionalDiagnosis: null,
  };
  appointmentSearchTerm.value = '';
  await loadAppointments();
  createModalOpen.value = true;
};

const closeCreateModal = () => {
  createModalOpen.value = false;
};

const submitCreateVisit = async () => {
  if (createModalSubmitting.value) return;
  if (!createFormState.value.primaryAppointmentId || createFormState.value.primaryAppointmentId <= 0) {
    showToast('error', 'Vui lòng chọn lịch hẹn.');
    return;
  }

  createModalSubmitting.value = true;
  try {
    await createVisit(createFormState.value);
    showToast('success', 'Đã tạo lượt khám mới.');
    closeCreateModal();
    await loadVisits();
  } catch (err: any) {
    const errorMessage =
      err?.response?.data?.message ?? err?.message ?? 'Không thể tạo lượt khám.';
    showToast('error', errorMessage);
  } finally {
    createModalSubmitting.value = false;
  }
};

const prevPage = () => {
  if (currentPage.value > 1) {
    currentPage.value--;
  }
};

const nextPage = () => {
  if (currentPage.value < totalPages.value) {
    currentPage.value++;
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

const viewVisitDetail = (visitId: number) => {
  router.push(`/dashboard/visits/${visitId}`);
};

// Watch filters to reset page
watch([searchTerm, statusFilter, dateFilter, quickFilter], () => {
  currentPage.value = 1;
});

onMounted(() => {
  void loadVisits();
});
</script>

<template>
  <div class="relative min-h-screen bg-gradient-to-br from-emerald-50 via-white to-white">
    <AdminHeader :user-name="userName" @sign-out="handleSignOut" />

    <main class="mx-auto w-full max-w-6xl px-4 pb-20 pt-10 sm:px-6 lg:px-8">
      <section>
        <div class="flex flex-col gap-4 border-b border-emerald-100 pb-6 md:flex-row md:items-center md:justify-between">
          <div>
            <p class="text-xs font-semibold uppercase tracking-[0.35em] text-emerald-500">Quản lý lượt khám</p>
            <h1 class="mt-2 text-2xl font-semibold text-slate-900">Danh sách lượt khám</h1>
            <p class="mt-1 text-sm text-slate-600">
              Quản lý toàn bộ quy trình khám và điều trị của bệnh nhân.
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
            Tạo Lượt khám
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
              placeholder="Tìm theo tên, mã bệnh nhân..."
            />
          </div>
          <div class="flex gap-3">
            <select
              v-model="statusFilter"
              class="rounded-full border border-emerald-100 bg-white px-4 py-2.5 text-sm text-slate-700 shadow-sm transition focus:border-emerald-400 focus:outline-none focus:ring-4 focus:ring-emerald-100/80"
            >
              <option v-for="opt in visitStatusOptions" :key="opt.value" :value="opt.value">
                {{ opt.label }}
              </option>
            </select>
            <select
              v-model="quickFilter"
              class="rounded-full border border-emerald-100 bg-white px-4 py-2.5 text-sm text-slate-700 shadow-sm transition focus:border-emerald-400 focus:outline-none focus:ring-4 focus:ring-emerald-100/80"
            >
              <option value="">Tất cả</option>
              <option value="TODAY">Hôm nay</option>
              <option value="MY_VISITS">Của tôi</option>
            </select>
            <input
              v-model="dateFilter"
              type="date"
              class="rounded-full border border-emerald-100 bg-white px-4 py-2.5 text-sm text-slate-700 shadow-sm transition focus:border-emerald-400 focus:outline-none focus:ring-4 focus:ring-emerald-100/80"
            />
          </div>
        </div>

        <div class="mt-5">
          <div
            class="hidden rounded-2xl border border-emerald-100 bg-emerald-50/70 px-4 py-3 text-xs font-semibold uppercase tracking-wide text-emerald-700 md:grid md:grid-cols-[50px_250px_200px_200px_90px_150px] md:gap-4"
          >
            <span>Mã</span>
            <span>Bệnh nhân</span>
            <span>Bác sĩ</span>
            <span>Thời gian</span>
            <span class="pl-1">Trạng thái</span>
            <span class="pl-20">Thao tác</span>
          </div>

          <template v-if="loading">
            <div
              v-for="skeleton in 4"
              :key="`visit-skeleton-${skeleton}`"
              class="mt-3 animate-pulse rounded-2xl border border-slate-100 bg-white px-4 py-4 shadow-sm"
            >
              <div class="grid gap-4 md:grid-cols-[50px_250px_200px_200px_100px_150px] md:items-center">
                <div class="h-4 rounded-full bg-slate-200/70"></div>
                <div class="h-4 rounded-full bg-slate-200/60"></div>
                <div class="h-4 rounded-full bg-slate-200/50"></div>
                <div class="h-4 rounded-full bg-slate-200/60"></div>
                <div class="h-4 rounded-full bg-slate-200/60"></div>
                <div class="ml-auto h-8 w-24 rounded-full bg-slate-200/70"></div>
              </div>
            </div>
          </template>
          <template v-else-if="paginatedVisits.length === 0">
            <div class="mt-8 rounded-3xl border border-dashed border-emerald-200 bg-emerald-50/40 p-10 text-center text-emerald-700">
              <svg xmlns="http://www.w3.org/2000/svg" class="mx-auto h-12 w-12 text-emerald-400" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.6">
                <path stroke-linecap="round" stroke-linejoin="round" d="M5 13l4 4L19 7" />
              </svg>
              <h3 class="mt-4 text-lg font-semibold">Chưa có lượt khám nào phù hợp</h3>
              <p class="mt-2 text-sm text-emerald-600/80">Thử thay đổi bộ lọc hoặc tạo mới lượt khám.</p>
            </div>
          </template>
          <template v-else>
            <div
              v-for="visit in paginatedVisits"
              :key="visit.id"
              class="mt-3 rounded-2xl border border-slate-200 bg-white px-4 py-4 shadow-sm transition hover:border-emerald-200 hover:shadow-md"
            >
              <div class="grid gap-4 md:grid-cols-[50px_250px_200px_200px_100px_150px] md:items-center">
                <div>
                  <p class="text-xs font-semibold uppercase tracking-wide text-slate-400 md:hidden">Mã lượt khám</p>
                  <p class="text-sm font-semibold text-slate-900">#{{ visit.id }}</p>
                </div>
                <div>
                  <p class="text-xs font-semibold uppercase tracking-wide text-slate-400 md:hidden">Bệnh nhân</p>
                  <div>
                    <p class="text-sm font-semibold text-slate-900">{{ visit.patient?.fullName ?? 'N/A' }}</p>
                    <p class="text-xs text-slate-500">{{ visit.patient?.code ?? '' }}</p>
                  </div>
                </div>
                <div>
                  <p class="text-xs font-semibold uppercase tracking-wide text-slate-400 md:hidden">Bác sĩ</p>
                  <p class="text-sm text-slate-700">{{ visit.primaryAppointment?.doctor?.account?.fullName ?? 'N/A' }}</p>
                </div>
                <div>
                  <p class="text-xs font-semibold uppercase tracking-wide text-slate-400 md:hidden">Thời gian</p>
                  <p class="text-sm text-slate-700">{{ formatDate(visit.primaryAppointment?.scheduledAt) }}</p>
                </div>
                <div>
                  <p class="text-xs font-semibold uppercase tracking-wide text-slate-400 md:hidden">Trạng thái</p>
                  <span
                    :class="getStatusBadgeClass(visit.status)"
                    class="inline-flex items-center rounded-full border px-2.5 py-0.5 text-xs font-semibold"
                  >
                    {{ getStatusLabel(visit.status) }}
                  </span>
                </div>
                <div class="flex flex-nowrap items-center justify-start gap-1.5 md:justify-end ">
                  <button
                    type="button"
                    class="inline-flex items-center gap-1 rounded-full border border-sky-200 bg-white px-3 py-1.5 text-[11px] font-semibold uppercase tracking-wide text-sky-600 shadow-sm transition hover:border-sky-300 hover:bg-sky-50"
                    @click="viewVisitDetail(visit.id)"
                  >
                    <svg xmlns="http://www.w3.org/2000/svg" class="h-3.5 w-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.8">
                      <path stroke-linecap="round" stroke-linejoin="round" d="M12 5c-7 0-9 7-9 7s2 7 9 7 9-7 9-7-2-7-9-7Zm0 4a3 3 0 1 1 0 6 3 3 0 0 1 0-6Z" />
                    </svg>
                    Chi tiết
                  </button>
                </div>
              </div>
            </div>
          </template>
        </div>

        <div class="mt-6 flex flex-col gap-4 border-t border-emerald-100 pt-4 text-sm text-slate-600 md:flex-row md:items-center md:justify-between">
          <span>Đang hiển thị {{ filteredVisits.length }} lượt khám</span>
          <div class="flex items-center gap-2">
            <button
              type="button"
              class="inline-flex items-center gap-2 rounded-full border border-emerald-200 bg-white px-4 py-1.5 text-xs font-semibold uppercase tracking-wide text-emerald-600 shadow-sm transition hover:border-emerald-300 hover:bg-emerald-50 disabled:cursor-not-allowed disabled:opacity-60"
              :disabled="currentPage === 1 || loading"
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
              :disabled="currentPage === totalPages || loading"
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

    <!-- Create Visit Modal -->
    <Transition name="fade">
      <div
        v-if="createModalOpen"
        class="fixed inset-0 z-[90] flex items-center justify-center bg-slate-900/50 backdrop-blur-sm px-4"
        @click.self="closeCreateModal"
      >
        <div class="w-full max-w-2xl max-h-[90vh] overflow-y-auto rounded-3xl border border-emerald-100 bg-white p-6 shadow-xl">
          <div class="flex items-start justify-between">
            <div>
              <p class="text-xs font-semibold uppercase tracking-[0.35em] text-emerald-500">Tạo lượt khám</p>
              <h2 class="mt-1 text-xl font-semibold text-slate-900">Tạo Lượt khám mới</h2>
              <p class="mt-1 text-sm text-slate-600">Chọn lịch hẹn đã xác nhận để tạo lượt khám</p>
            </div>
            <button
              type="button"
              class="flex h-9 w-9 items-center justify-center rounded-full border border-slate-200 text-slate-500 transition hover:border-slate-300 hover:text-slate-700"
              @click="closeCreateModal"
            >
              <svg xmlns="http://www.w3.org/2000/svg" class="h-4.5 w-4.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.8">
                <path stroke-linecap="round" stroke-linejoin="round" d="m16 8-8 8m0-8 8 8" />
              </svg>
            </button>
          </div>

          <div class="mt-6 space-y-4">
            <!-- Appointment Search -->
            <div class="space-y-1.5">
              <label class="text-xs font-semibold uppercase tracking-wide text-slate-500">
                Tìm kiếm lịch hẹn <span class="text-rose-500">*</span>
              </label>
              <input
                v-model="appointmentSearchTerm"
                type="text"
                placeholder="Tìm theo tên bệnh nhân, mã bệnh nhân, bác sĩ..."
                class="w-full rounded-xl border border-slate-200 px-4 py-2 text-sm text-slate-700 shadow-sm transition focus:border-emerald-400 focus:outline-none focus:ring-4 focus:ring-emerald-100/80"
              />
            </div>

            <!-- Appointment List -->
            <div class="space-y-1.5">
              <label class="text-xs font-semibold uppercase tracking-wide text-slate-500">
                Danh sách lịch hẹn
              </label>
              <div class="max-h-64 overflow-y-auto rounded-xl border border-slate-200">
            <div v-if="loadingAppointments" class="p-4 text-center text-sm text-slate-600">
              Đang tải...
            </div>
            <div
              v-else-if="filteredAppointments.length === 0"
              class="p-4 text-center text-sm text-slate-600"
            >
              Không tìm thấy lịch hẹn nào.
            </div>
            <div v-else class="divide-y divide-slate-200">
              <label
                v-for="apt in filteredAppointments"
                :key="apt.id"
                class="flex cursor-pointer items-start gap-3 p-4 hover:bg-slate-50"
              >
                <input
                  v-model="createFormState.primaryAppointmentId"
                  type="radio"
                  :value="apt.id"
                  class="mt-1 h-4 w-4 text-emerald-600 focus:ring-emerald-500"
                />
                <div class="flex-1">
                  <div class="font-semibold text-slate-900">
                    {{ apt.patient?.fullName ?? 'N/A' }} ({{ apt.patient?.code ?? 'N/A' }})
                  </div>
                  <div class="mt-1 text-xs text-slate-600">
                    Bác sĩ: {{ apt.doctor?.account?.fullName ?? 'N/A' }}
                  </div>
                  <div class="mt-1 text-xs text-slate-600">
                    Thời gian: {{ formatDate(apt.scheduledAt) }}
                  </div>
                  <div v-if="apt.clinicRoom" class="mt-1 text-xs text-slate-600">
                    Phòng: {{ apt.clinicRoom.name }}
                  </div>
                </div>
              </label>
            </div>
              </div>
            </div>

            <!-- Optional Fields -->
            <div class="space-y-1.5">
              <label class="text-xs font-semibold uppercase tracking-wide text-slate-500">Chẩn đoán tạm thời</label>
              <textarea
                v-model="createFormState.provisionalDiagnosis"
                rows="2"
                placeholder="Nhập chẩn đoán tạm thời (nếu có)"
                class="w-full rounded-xl border border-slate-200 px-4 py-2 text-sm text-slate-700 shadow-sm transition focus:border-emerald-400 focus:outline-none focus:ring-4 focus:ring-emerald-100/80"
              />
            </div>

          </div>

          <div class="mt-6 flex items-center justify-end gap-3 border-t border-slate-200 pt-4">
            <button
              type="button"
              @click="closeCreateModal"
              class="inline-flex items-center gap-2 rounded-full border border-slate-200 bg-white px-5 py-2 text-xs font-semibold uppercase tracking-wide text-slate-600 shadow-sm transition hover:border-slate-300 hover:bg-slate-50"
            >
              Hủy
            </button>
            <button
              type="button"
              @click="submitCreateVisit"
              :disabled="createModalSubmitting"
              class="inline-flex items-center gap-2 rounded-full border border-emerald-200 bg-emerald-600 px-5 py-2 text-xs font-semibold uppercase tracking-wide text-white shadow-sm transition hover:bg-emerald-700 disabled:opacity-50"
            >
              <span v-if="createModalSubmitting">Đang tạo...</span>
              <span v-else>Tạo lượt khám</span>
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
