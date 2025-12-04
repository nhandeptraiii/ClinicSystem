<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import AdminHeader from '@/components/AdminHeader.vue';
import { useAuthStore } from '@/stores/authStore';
import { useToast, type ToastType } from '@/composables/useToast';
import {
  fetchPrescriptions,
  fetchPrescriptionById,
  updatePrescriptionStatus,
  type Prescription,
  type PrescriptionStatus,
} from '@/services/prescription.service';

const router = useRouter();
const authStore = useAuthStore();
const { toast, show: showToast, hide: hideToast } = useToast({ autoCloseMs: 5000 });

const userName = computed(() => authStore.user?.username ?? 'Qu?n tr? vi?n');

const statusOptions: { value: PrescriptionStatus | 'ALL'; label: string }[] = [
  { value: 'ALL', label: 'Tất cả' },
  { value: 'WAITING', label: 'Chờ phát' },
  { value: 'DISPENSED', label: 'Đã phát' },
  { value: 'ON_HOLD', label: 'Hoãn phát' },
];

const prescriptions = ref<Prescription[]>([]);
const loading = ref(false);
const updating = ref<Record<number, boolean>>({});
const selectedStatus = ref<PrescriptionStatus | 'ALL'>('ALL');
const searchTerm = ref('');
const dateFilter = ref<'ALL' | 'TODAY'>('ALL');
const statusDrafts = ref<Record<number, PrescriptionStatus>>({});
const normalizedStatus = computed<PrescriptionStatus | undefined>(() =>
  selectedStatus.value === 'ALL' ? undefined : selectedStatus.value,
);
const viewModalOpen = ref(false);
const editModalOpen = ref(false);
const modalPrescription = ref<Prescription | null>(null);
const viewLoading = ref(false);

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

const loadPrescriptions = async () => {
  loading.value = true;
  try {
    prescriptions.value = await fetchPrescriptions({ status: normalizedStatus.value });
  } catch (err: any) {
    showToast('error', err?.response?.data?.message ?? 'Không thể tải danh sách đơn thuốc.');
  } finally {
    loading.value = false;
  }
};

const filteredPrescriptions = computed(() => {
  const term = searchTerm.value.trim().toLowerCase();
  const bySearch = prescriptions.value.filter((p) => {
    if (!term) return true;
    const patient = p.visit?.patient?.fullName?.toLowerCase() ?? '';
    const code = p.visit?.patient?.code?.toLowerCase() ?? '';
    return patient.includes(term) || code.includes(term);
  });

  if (dateFilter.value === 'TODAY') {
    const today = new Date();
    const y = today.getFullYear();
    const m = today.getMonth();
    const d = today.getDate();
    return bySearch.filter((p) => {
      if (!p.issuedAt) return false;
      const dt = new Date(p.issuedAt);
      return dt.getFullYear() === y && dt.getMonth() === m && dt.getDate() === d;
    });
  }
  return bySearch;
});

const formatDate = (dateString?: string | null) => {
  if (!dateString) return 'N/A';
  try {
    const hasZone = /[zZ]|[+-]\d{2}:?\d{2}$/.test(dateString);
    const iso = hasZone ? dateString : `${dateString}Z`;
    const date = new Date(iso);
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

const getStatusBadgeClass = (status?: PrescriptionStatus | null) => {
  switch (status) {
    case 'WAITING':
      return 'bg-blue-100 text-blue-800 border-blue-200';
    case 'DISPENSED':
      return 'bg-emerald-100 text-emerald-800 border-emerald-200';
    case 'ON_HOLD':
      return 'bg-orange-100 text-orange-800 border-orange-200';
    default:
      return 'bg-slate-100 text-slate-800 border-slate-200';
  }
};

const getStatusLabel = (status?: PrescriptionStatus | null) => {
  switch (status) {
    case 'WAITING':
      return 'Chờ phát';
    case 'DISPENSED':
      return 'Đã phát';
    case 'ON_HOLD':
      return 'Hoãn phát';
    default:
      return status ?? 'N/A';
  }
};

const updateStatus = async (prescription: Prescription, status: PrescriptionStatus) => {
  if (!prescription.id) return;
  updating.value = { ...updating.value, [prescription.id]: true };
  try {
    await updatePrescriptionStatus(prescription.id, {
      status,
      pharmacistNote: null,
    });
    showToast('success', 'Đã cập nhật trạng thái đơn thuốc.');
    await loadPrescriptions();
  } catch (err: any) {
    const errorMessage =
      err?.response?.data?.message ?? err?.message ?? 'Không thể cập nhật trạng thái đơn thuốc.';
    showToast('error', errorMessage);
  } finally {
    updating.value = { ...updating.value, [prescription.id]: false };
  }
};

const openViewModal = async (prescription: Prescription) => {
  modalPrescription.value = prescription;
  viewModalOpen.value = true;
  viewLoading.value = true;
  try {
    modalPrescription.value = await fetchPrescriptionById(prescription.id);
  } catch (err: any) {
    showToast('error', err?.response?.data?.message ?? 'Không thể tải đơn thuốc.');
  } finally {
    viewLoading.value = false;
  }
};

const openEditModal = (prescription: Prescription) => {
  modalPrescription.value = prescription;
  statusDrafts.value[prescription.id] = prescription.status as PrescriptionStatus;
  editModalOpen.value = true;
};

const closeModals = () => {
  viewModalOpen.value = false;
  editModalOpen.value = false;
};

const submitEdit = async () => {
  if (!modalPrescription.value?.id) return;
  const targetStatus = statusDrafts.value[modalPrescription.value.id] || 'WAITING';
  await updateStatus(modalPrescription.value, targetStatus);
  editModalOpen.value = false;
};

onMounted(() => {
  void loadPrescriptions();
});
</script>

<template>
  <div class="relative min-h-screen bg-gradient-to-br from-emerald-50 via-white to-white">
    <AdminHeader :user-name="userName" @sign-out="router.push('/login')" />

    <main class="mx-auto w-full max-w-6xl px-4 pb-16 pt-10 sm:px-6 lg:px-8">
      <section>
        <div class="flex flex-col gap-4 border-b border-emerald-100 pb-6 md:flex-row md:items-center md:justify-between">
          <div>
            <p class="text-xs font-semibold uppercase tracking-[0.35em] text-emerald-500">Phát thuốc</p>
            <h1 class="mt-2 text-2xl font-semibold text-slate-900">Hàng chờ phát thuốc</h1>
            <p class="mt-1 text-sm text-slate-600">Theo dõi và cập nhật trạng thái đơn thuốc.</p>
          </div>
          <div class="flex flex-col gap-2 sm:flex-row sm:items-center">
            <div class="relative w-full sm:w-48">
              <select
                v-model="selectedStatus"
                @change="loadPrescriptions"
                class="w-full rounded-full border border-emerald-100 bg-white px-4 py-2 text-sm text-slate-700 shadow-sm transition focus:border-emerald-400 focus:outline-none focus:ring-4 focus:ring-emerald-100/80"
              >
                <option v-for="opt in statusOptions" :key="opt.value" :value="opt.value">
                  {{ opt.label }}
                </option>
              </select>
            </div>
            <div class="relative w-full sm:w-40">
              <select
                v-model="dateFilter"
                class="w-full rounded-full border border-emerald-100 bg-white px-4 py-2 text-sm text-slate-700 shadow-sm transition focus:border-emerald-400 focus:outline-none focus:ring-4 focus:ring-emerald-100/80"
              >
                <option value="ALL">Tất cả ngày</option>
                <option value="TODAY">Hôm nay</option>
              </select>
            </div>
            <div class="relative w-full sm:w-64">
              <svg class="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" fill="none" stroke="currentColor" stroke-width="1.5" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" d="m21 21-4.35-4.35m0 0A7.35 7.35 0 1 0 6.3 6.3a7.35 7.35 0 0 0 10.35 10.35Z" />
              </svg>
              <input
                v-model="searchTerm"
                type="search"
                placeholder="Tìm bệnh nhân/mã hồ sơ"
                class="w-full rounded-full border border-emerald-100 bg-white py-2.5 pl-9 pr-4 text-sm text-slate-700 shadow-sm transition focus:border-emerald-400 focus:outline-none focus:ring-4 focus:ring-emerald-100/80"
              />
            </div>
            <button
              type="button"
              @click="loadPrescriptions"
              class="inline-flex items-center gap-2 self-start rounded-full border border-emerald-200 bg-white px-4 py-2 text-xs font-semibold uppercase tracking-wide text-emerald-700 shadow-sm transition hover:bg-emerald-50"
            >
              Làm mới
            </button>
          </div>
        </div>

        <div class="mt-6">
          <div
            class="hidden rounded-2xl border border-emerald-100 bg-emerald-50/70 px-4 py-3 text-xs font-semibold uppercase tracking-wide text-emerald-700 md:grid md:grid-cols-[200px_180px_220px_100px_250px]"
          >
            <span>Bệnh nhân</span>
            <span class="pl-4">Bác sĩ</span>
            <span class="text-center">Ngày kê</span>
            <span class="text-right">Trạng thái</span>
            <span class="flex justify-end pr-6">Thao tác</span>
          </div>

          <template v-if="loading">
            <div
              v-for="s in 3"
              :key="`rx-skeleton-${s}`"
              class="mt-3 animate-pulse rounded-2xl border border-slate-100 bg-white px-4 py-4 shadow-sm"
            >
              <div class="grid gap-3 md:grid-cols-[200px_180px_220px_100px_250px] md:items-center">
                <div class="h-4 rounded-full bg-slate-200/70"></div>
                <div class="h-4 rounded-full bg-slate-200/60"></div>
                <div class="h-4 rounded-full bg-slate-200/60"></div>
                <div class="h-5 rounded-full bg-slate-200/60"></div>
                <div class="ml-auto h-8 w-24 rounded-full bg-slate-200/70"></div>
              </div>
            </div>
          </template>

          <template v-else-if="filteredPrescriptions.length === 0">
            <div class="mt-8 rounded-3xl border border-dashed border-emerald-200 bg-emerald-50/40 p-10 text-center text-emerald-700">
              <svg xmlns="http://www.w3.org/2000/svg" class="mx-auto h-12 w-12 text-emerald-400" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.6">
                <path stroke-linecap="round" stroke-linejoin="round" d="M5 13l4 4L19 7" />
              </svg>
              <h3 class="mt-4 text-lg font-semibold">Không có đơn thuốc phù hợp</h3>
              <p class="mt-2 text-sm text-emerald-600/80">Thử đổi trạng thái hoặc tìm kiếm khác.</p>
            </div>
          </template>

          <template v-else>
            <div
              v-for="p in filteredPrescriptions"
              :key="p.id"
              class="mt-3 rounded-2xl border border-slate-200 bg-white px-4 py-4 shadow-sm transition hover:border-emerald-200 hover:shadow-md"
            >
              <div class="grid gap-3 md:grid-cols-[200px_180px_220px_100px_250px] md:items-center">
                <div>
                  <p class="text-xs font-semibold uppercase tracking-wide text-slate-400 md:hidden">Bệnh nhân</p>
                  <p class="text-sm font-semibold text-slate-900">{{ p.visit?.patient?.fullName ?? 'N/A' }}</p>
                  <p class="text-xs text-slate-600">{{ p.visit?.patient?.code ?? '' }}</p>
                </div>
                <div>
                  <p class="text-xs font-semibold uppercase tracking-wide text-slate-400 md:hidden">Bác sĩ</p>
                  <p class="text-sm text-slate-700">{{ p.prescribedBy?.account?.fullName ?? 'N/A' }}</p>
                </div>
                <div>
                  <p class="text-xs font-semibold uppercase tracking-wide text-slate-400 md:hidden">Ngày kê</p>
                  <p class="text-sm text-slate-700 pl-8">{{ formatDate(p.issuedAt) }}</p>
                </div>
                <div>
                  <p class="text-xs font-semibold uppercase tracking-wide text-slate-400 md:hidden">Trạng thái</p>
                  <span
                    :class="getStatusBadgeClass(p.status as PrescriptionStatus)"
                    class="inline-flex items-center rounded-full border px-2.5 py-0.5 text-xs font-semibold"
                  >
                    {{ getStatusLabel(p.status as PrescriptionStatus) }}
                  </span>
                </div>
                <div class="flex flex-wrap items-center justify-start gap-2 md:justify-end">
                  <button
                    type="button"
                    class="inline-flex items-center gap-2 rounded-full border border-sky-200 bg-white px-3 py-1.5 text-[11px] font-semibold uppercase tracking-wide text-sky-700 shadow-sm transition hover:bg-sky-50"
                    @click="openViewModal(p)"
                  >
                    Xem đơn
                  </button>
                  <button
                    type="button"
                    class="inline-flex items-center gap-2 rounded-full border border-emerald-200 bg-emerald-600 px-3 py-1.5 text-[11px] font-semibold uppercase tracking-wide text-white shadow-sm transition hover:bg-emerald-700"
                    @click="openEditModal(p)"
                  >
                    Chỉnh sửa
                  </button>
                </div>
              </div>

              <div v-if="p.notes" class="mt-3 rounded-xl border border-slate-200 bg-slate-50 px-3 py-2 text-sm text-slate-700">
                <p class="text-xs font-semibold uppercase tracking-wide text-slate-500">Ghi chú bác sĩ</p>
                <p class="mt-1 whitespace-pre-wrap">{{ p.notes }}</p>
              </div>
              <div v-if="p.pharmacistNote" class="mt-2 text-xs text-slate-600">
                <span class="font-semibold">Ghi chú dược sĩ:</span> {{ p.pharmacistNote }}
              </div>
            </div>
          </template>
        </div>
      </section>
    </main>

    <!-- View Prescription Modal -->
    <Transition name="fade">
      <div
        v-if="viewModalOpen && modalPrescription"
        class="fixed inset-0 z-[90] flex items-center justify-center bg-slate-900/50 px-4 backdrop-blur-sm"
        @click.self="closeModals"
      >
        <div class="w-full max-w-4xl max-h-[90vh] overflow-y-auto rounded-3xl border border-emerald-100 bg-white p-6 shadow-xl">
          <div class="flex items-start justify-between">
            <div>
              <p class="text-xs font-semibold uppercase tracking-[0.35em] text-emerald-500">Chi tiết đơn thuốc</p>
              <h2 class="mt-1 text-xl font-semibold text-slate-900">Đơn thuốc</h2>
            </div>
            <button
              type="button"
              class="flex h-9 w-9 items-center justify-center rounded-full border border-slate-200 text-slate-500 transition hover:border-slate-300 hover:text-slate-700"
              @click="closeModals"
            >
              <svg xmlns="http://www.w3.org/2000/svg" class="h-4.5 w-4.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.8">
                <path stroke-linecap="round" stroke-linejoin="round" d="m16 8-8 8m0-8 8 8" />
              </svg>
            </button>
          </div>

          <div v-if="viewLoading" class="mt-6 flex items-center justify-center py-10">
            <div class="h-8 w-8 animate-spin rounded-full border-4 border-emerald-500 border-t-transparent"></div>
          </div>
          <div v-else-if="modalPrescription" class="mt-4 space-y-4 text-sm text-slate-700">
            <div class="grid gap-3 sm:grid-cols-2">
              <div>
                <p class="text-xs font-semibold uppercase tracking-wide text-slate-500">Bệnh nhân</p>
                <p class="mt-1 font-semibold text-slate-900">{{ modalPrescription.visit?.patient?.fullName ?? 'N/A' }}</p>
                <p class="text-xs text-slate-500">{{ modalPrescription.visit?.patient?.code ?? '' }}</p>
              </div>
              <div>
                <p class="text-xs font-semibold uppercase tracking-wide text-slate-500">Bác sĩ</p>
                <p class="mt-1 text-slate-900">{{ modalPrescription.prescribedBy?.account?.fullName ?? 'N/A' }}</p>
              </div>
            </div>

            <div class="grid gap-3 sm:grid-cols-2">
              <div>
                <p class="text-xs font-semibold uppercase tracking-wide text-slate-500">Ngày kê</p>
                <p class="mt-1 text-slate-900">{{ formatDate(modalPrescription.issuedAt) }}</p>
              </div>
              <div>
                <p class="text-xs font-semibold uppercase tracking-wide text-slate-500">Trạng thái</p>
                <span
                  :class="getStatusBadgeClass(modalPrescription.status as PrescriptionStatus)"
                  class="mt-1 inline-flex items-center rounded-full border px-3 py-1 text-xs font-semibold"
                >
                  {{ getStatusLabel(modalPrescription.status as PrescriptionStatus) }}
                </span>
              </div>
            </div>

            <div v-if="modalPrescription.notes" class="rounded-xl border border-slate-200 bg-slate-50 px-3 py-2">
              <p class="text-xs font-semibold uppercase tracking-wide text-slate-500">Ghi chú bác sĩ</p>
              <p class="mt-1 whitespace-pre-wrap">{{ modalPrescription.notes }}</p>
            </div>
            <div v-if="modalPrescription.pharmacistNote" class="text-xs text-slate-600">
              <span class="font-semibold">Ghi chú dược sĩ:</span> {{ modalPrescription.pharmacistNote }}
            </div>

            <div>
              <p class="mb-2 text-xs font-semibold uppercase tracking-wide text-slate-500">Danh sách thuốc</p>
              <div class="overflow-x-auto rounded-xl border border-slate-200">
                <table class="min-w-full text-sm">
                  <thead class="bg-slate-50">
                    <tr>
                      <th class="px-3 py-2 text-left text-xs font-semibold uppercase text-slate-600">Thuốc</th>
                      <th class="px-3 py-2 text-left text-xs font-semibold uppercase text-slate-600">Số lượng</th>
                      <th class="px-3 py-2 text-left text-xs font-semibold uppercase text-slate-600">Liều dùng</th>
                      <th class="px-3 py-2 text-left text-xs font-semibold uppercase text-slate-600">Tần suất</th>
                      <th class="px-3 py-2 text-left text-xs font-semibold uppercase text-slate-600">Hướng dẫn</th>
                    </tr>
                  </thead>
                  <tbody class="divide-y divide-slate-200">
                    <tr v-for="item in modalPrescription.items ?? []" :key="item.id">
                      <td class="px-3 py-2 font-semibold text-slate-900">
                        {{ item.medicationName ?? item.medication?.name ?? 'N/A' }}
                      </td>
                      <td class="px-8 py-2 text-slate-700 ">{{ item.quantity ?? '—' }}</td>
                      <td class="px-10 py-2 text-slate-700 pr-3">{{ item.dosage ?? '—' }}</td>
                      <td class="px-9 py-2 text-slate-700">{{ item.frequency ?? '—' }}</td>
                      <td class="px-8 py-2 text-slate-700">{{ item.instruction ?? '—' }}</td>
                    </tr>
                  </tbody>
                </table>
              </div>
            </div>
          </div>
        </div>
      </div>
    </Transition>

    <!-- Edit Status Modal -->
    <Transition name="fade">
      <div
        v-if="editModalOpen && modalPrescription"
        class="fixed inset-0 z-[90] flex items-center justify-center bg-slate-900/50 px-4 backdrop-blur-sm"
        @click.self="closeModals"
      >
        <div class="w-full max-w-md rounded-3xl border border-emerald-100 bg-white p-6 shadow-xl">
          <div class="flex items-start justify-between">
            <div>
              <p class="text-xs font-semibold uppercase tracking-[0.35em] text-emerald-500">Cập nhật trạng thái</p>
              <h2 class="mt-1 text-xl font-semibold text-slate-900">Chỉnh sửa đơn thuốc</h2>
            </div>
            <button
              type="button"
              class="flex h-9 w-9 items-center justify-center rounded-full border border-slate-200 text-slate-500 transition hover:border-slate-300 hover:text-slate-700"
              @click="closeModals"
            >
              <svg xmlns="http://www.w3.org/2000/svg" class="h-4.5 w-4.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.8">
                <path stroke-linecap="round" stroke-linejoin="round" d="m16 8-8 8m0-8 8 8" />
              </svg>
            </button>
          </div>

          <div class="mt-4 space-y-3">
            <div>
              <p class="text-xs font-semibold uppercase tracking-wide text-slate-500">Bệnh nhân</p>
              <p class="mt-1 text-sm font-semibold text-slate-900">
                {{ modalPrescription.visit?.patient?.fullName ?? 'N/A' }}
              </p>
            </div>
            <div class="space-y-1.5">
              <label class="text-xs font-semibold uppercase tracking-wide text-slate-500">Trạng thái</label>
              <select
                v-model="statusDrafts[modalPrescription.id]"
                class="w-full rounded-full border border-emerald-100 bg-white px-4 py-2 text-sm text-slate-700 shadow-sm transition focus:border-emerald-400 focus:outline-none focus:ring-4 focus:ring-emerald-100/80"
              >
                <option value="WAITING">Chờ phát</option>
                <option value="DISPENSED">Đã phát</option>
                <option value="ON_HOLD">Hoãn</option>
              </select>
            </div>
          </div>

          <div class="mt-6 flex items-center justify-end gap-3">
            <button
              type="button"
              class="inline-flex items-center gap-2 rounded-full border border-slate-200 bg-white px-5 py-2 text-xs font-semibold uppercase tracking-wide text-slate-600 shadow-sm transition hover:border-slate-300 hover:bg-slate-50"
              @click="closeModals"
            >
              Hủy
            </button>
            <button
              type="button"
              :disabled="updating[modalPrescription.id]"
              class="inline-flex items-center gap-2 rounded-full border border-emerald-200 bg-emerald-600 px-5 py-2 text-xs font-semibold uppercase tracking-wide text-white shadow-sm transition hover:bg-emerald-700 disabled:opacity-60"
              @click="submitEdit"
            >
              Lưu
            </button>
          </div>
        </div>
      </div>
    </Transition>

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
              @click="hideToast"
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
