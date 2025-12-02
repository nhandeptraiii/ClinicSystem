<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue';
import { useRouter } from 'vue-router';
import AdminHeader from '@/components/AdminHeader.vue';
import { useAuthStore } from '@/stores/authStore';
import { useToast, type ToastType } from '@/composables/useToast';
import {
  createClinicRoom,
  deleteClinicRoom,
  fetchClinicRoomPage,
  type ClinicRoom,
  type ClinicRoomPayload,
  updateClinicRoom,
} from '@/services/clinicRoom.service';

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

const rooms = ref<ClinicRoom[]>([]);
const loading = ref(false);
const error = ref<string | null>(null);

const PAGE_SIZE = 10;
const FLOOR_PRESETS = ['Tầng Trệt', 'Tầng 1', 'Tầng 2', 'Tầng 3', 'Tầng 4'];
const currentPage = ref(1);
const totalPages = ref(1);
const totalElements = ref(0);
const hasNext = ref(false);
const hasPrevious = ref(false);

const searchTerm = ref('');
const floorFilter = ref('');

let filterTimer: ReturnType<typeof setTimeout> | null = null;

const formModalOpen = ref(false);
const modalSubmitting = ref(false);
const modalMode = ref<'create' | 'edit'>('create');
const formError = ref<string | null>(null);
const formState = reactive<ClinicRoomPayload>({
  code: '',
  name: '',
  floor: '',
  note: '',
  capacity: 1,
});
const selectedRoom = ref<ClinicRoom | null>(null);

const detailModalOpen = ref(false);
const detailRoom = ref<ClinicRoom | null>(null);

const deleteTarget = ref<ClinicRoom | null>(null);
const deleting = ref(false);

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

const floorOptions = computed(() => {
  const unique = new Set<string>();
  rooms.value.forEach((room) => {
    if (room.floor) {
      unique.add(room.floor);
    }
  });
  return Array.from(unique).sort((a, b) => a.localeCompare(b, 'vi'));
});

const loadRooms = async () => {
  loading.value = true;
  error.value = null;
  const keyword = searchTerm.value.trim();
  const floor = floorFilter.value.trim();
  const pageIndex = Math.max(currentPage.value - 1, 0);
  try {
    const response = await fetchClinicRoomPage({
      page: pageIndex,
      size: PAGE_SIZE,
      keyword: keyword ? keyword : undefined,
      floor: floor ? floor : undefined,
    });
    rooms.value = response.items ?? [];
    totalElements.value = response.totalElements ?? rooms.value.length;
    const derivedTotalPages = response.totalPages && response.totalPages > 0 ? response.totalPages : 1;
    totalPages.value = derivedTotalPages;
    currentPage.value = response.totalPages && response.totalPages > 0 ? response.page + 1 : 1;
    hasNext.value = response.hasNext ?? false;
    hasPrevious.value = response.hasPrevious ?? false;
  } catch (err) {
    error.value = extractErrorMessage(err);
    rooms.value = [];
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
  loadRooms();
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

const openCreateModal = () => {
  modalMode.value = 'create';
  selectedRoom.value = null;
  formError.value = null;
  formState.code = '';
  formState.name = '';
  formState.floor = '';
  formState.note = '';
  formState.capacity = 1;
  formModalOpen.value = true;
};

const openEditModal = (room: ClinicRoom) => {
  modalMode.value = 'edit';
  selectedRoom.value = room;
  formError.value = null;
  formState.code = room.code ?? '';
  formState.name = room.name ?? '';
  formState.floor = room.floor ?? '';
  formState.note = room.note ?? '';
  formState.capacity = room.capacity ?? 1;
  formModalOpen.value = true;
};

const closeFormModal = () => {
  formModalOpen.value = false;
  formError.value = null;
};

const submitForm = async () => {
  if (modalSubmitting.value) return;

  const payload: ClinicRoomPayload = {
    code: formState.code?.trim() ?? '',
    name: formState.name?.trim() ?? '',
    floor: formState.floor?.trim() ? formState.floor.trim() : null,
    note: formState.note?.trim() ? formState.note.trim() : null,
    capacity: formState.capacity ?? 1,
  };

  if (!payload.code || !payload.name) {
    formError.value = 'Vui lòng nhập đầy đủ mã và tên phòng.';
    return;
  }
  if (!payload.capacity || payload.capacity < 1) {
    payload.capacity = 1;
  }

  if (!payload.code || !payload.name) {
    formError.value = 'Vui lòng nhập đầy đủ mã phòng và tên phòng.';
    return;
  }

  modalSubmitting.value = true;
  try {
    if (modalMode.value === 'create') {
      await createClinicRoom(payload);
      showToast('success', 'Đã thêm phòng khám mới.');
    } else if (selectedRoom.value) {
      await updateClinicRoom(selectedRoom.value.id, payload);
      showToast('success', 'Đã cập nhật phòng khám.');
    }
    closeFormModal();
    await loadRooms();
  } catch (error) {
    formError.value = extractErrorMessage(error);
    showToast('error', formError.value ?? 'Không thể lưu thông tin phòng khám.');
  } finally {
    modalSubmitting.value = false;
  }
};

const openDetailModal = (room: ClinicRoom) => {
  detailRoom.value = room;
  detailModalOpen.value = true;
};

const closeDetailModal = () => {
  detailModalOpen.value = false;
};

const confirmDelete = (room: ClinicRoom) => {
  deleteTarget.value = room;
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
    await deleteClinicRoom(deleteTarget.value.id);
    showToast('success', 'Đã xóa phòng khám.');
    const shouldGoPrev = rooms.value.length <= 1 && currentPage.value > 1;
    deleteTarget.value = null;
    deleting.value = false;
    if (shouldGoPrev) {
      currentPage.value = currentPage.value - 1;
    }
    await loadRooms();
  } catch (error) {
    deleting.value = false;
    showToast('error', extractErrorMessage(error));
  }
};

watch(
  [searchTerm, floorFilter],
  () => {
    if (filterTimer) {
      clearTimeout(filterTimer);
    }
    filterTimer = setTimeout(() => {
      currentPage.value = 1;
      loadRooms();
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
  loadRooms();
});
</script>

<template>
  <div class="relative min-h-screen bg-gradient-to-br from-emerald-50 via-white to-white">
    <AdminHeader :user-name="userName" @sign-out="handleSignOut" />

    <main class="mx-auto w-full max-w-6xl px-4 pb-20 pt-10 sm:px-6 lg:px-8">
      <section>
        <div class="flex flex-col gap-4 border-b border-emerald-100 pb-6 md:flex-row md:items-center md:justify-between">
          <div>
            <p class="text-xs font-semibold uppercase tracking-[0.35em] text-emerald-500">Quản lý phòng khám</p>
            <h1 class="mt-2 text-2xl font-semibold text-slate-900">Danh sách phòng khám</h1>
            <p class="mt-1 text-sm text-slate-600">
              Theo dõi, cập nhật thông tin phòng khám và phân bổ dễ dàng trong hệ thống.
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
            Thêm phòng
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
              placeholder="Tìm theo mã hoặc tên phòng..."
            />
          </div>
          <div class="flex flex-wrap items-center gap-2">
            <select
              v-model="floorFilter"
              class="w-full rounded-full border border-emerald-100 bg-white px-4 py-2 text-sm text-slate-700 shadow-sm transition focus:border-emerald-400 focus:outline-none focus:ring-4 focus:ring-emerald-100/80 sm:w-auto"
            >
              <option value="">Tất cả tầng/khu</option>
              <option v-for="floor in floorOptions" :key="floor" :value="floor">
                {{ floor }}
              </option>
            </select>
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
            class="hidden rounded-2xl border border-emerald-100 bg-emerald-50/70 px-4 py-3 text-xs font-semibold uppercase tracking-wide text-emerald-700 md:grid md:grid-cols-[140px_minmax(0,320px)_100px_100px_1fr] md:gap-6"
          >
            <span>Mã phòng</span>
            <span>Tên phòng</span>
            <span>Tầng/Khu</span>
            <span>Sức chứa</span>
            <span class="flex justify-end pr-20">Thao tác</span>
          </div>

          <template v-if="loading">
            <div
              v-for="skeleton in 4"
              :key="`clinic-room-skeleton-${skeleton}`"
              class="mt-3 animate-pulse rounded-2xl border border-slate-100 bg-white px-4 py-4 shadow-sm"
            >
              <div class="grid gap-6 md:grid-cols-[140px_minmax(0,400px)_100px_1fr] md:items-center">
                <div class="h-4 rounded-full bg-slate-200/70"></div>
                <div class="h-4 rounded-full bg-slate-200/60"></div>
                <div class="h-4 rounded-full bg-slate-200/50"></div>
                <div class="ml-auto h-8 w-24 rounded-full bg-slate-200/70"></div>
              </div>
            </div>
          </template>
          <template v-else-if="rooms.length === 0">
            <div class="mt-8 rounded-3xl border border-dashed border-emerald-200 bg-emerald-50/40 p-10 text-center text-emerald-700">
              <svg xmlns="http://www.w3.org/2000/svg" class="mx-auto h-12 w-12 text-emerald-400" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.6">
                <path stroke-linecap="round" stroke-linejoin="round" d="M5 13l4 4L19 7" />
              </svg>
              <h3 class="mt-4 text-lg font-semibold">Chưa có phòng khám nào phù hợp</h3>
              <p class="mt-2 text-sm text-emerald-600/80">Thử thay đổi bộ lọc hoặc tạo mới phòng khám.</p>
            </div>
          </template>
          <template v-else>
            <div
              v-for="room in rooms"
              :key="room.id"
              class="mt-3 rounded-2xl border border-slate-200 bg-white px-4 py-4 shadow-sm transition hover:border-emerald-200 hover:shadow-md"
            >
              <div class="grid gap-6 md:grid-cols-[140px_minmax(0,320px)_100px_100px_1fr] md:items-center">
                <div>
                  <p class="text-xs font-semibold uppercase tracking-wide text-slate-400 md:hidden">Mã phòng</p>
                  <p class="text-sm font-semibold text-slate-900">{{ room.code }}</p>
                </div>
                <div>
                  <p class="text-xs font-semibold uppercase tracking-wide text-slate-400 md:hidden">Tên phòng</p>
                  <p class="text-sm text-slate-700">{{ room.name }}</p>
                </div>
                <div>
                  <p class="text-xs font-semibold uppercase tracking-wide text-slate-400 md:hidden">Tầng/Khu</p>
                  <p class="text-sm text-slate-700">{{ room.floor || '—' }}</p>
                </div>
                <div>
                  <p class="text-xs font-semibold uppercase tracking-wide text-slate-400 md:hidden">Sức chứa</p>
                  <p class="text-sm text-slate-700">{{ room.capacity ?? 1 }}</p>
                </div>
                <div class="flex flex-wrap items-center justify-start gap-1.5 md:justify-end">
                  <button
                    type="button"
                    class="inline-flex items-center gap-1 rounded-full border border-sky-200 bg-white px-3 py-1.5 text-[11px] font-semibold uppercase tracking-wide text-sky-600 shadow-sm transition hover:border-sky-300 hover:bg-sky-50"
                    @click="openDetailModal(room)"
                  >
                    <svg xmlns="http://www.w3.org/2000/svg" class="h-3.5 w-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.8">
                      <path stroke-linecap="round" stroke-linejoin="round" d="M12 5c-7 0-9 7-9 7s2 7 9 7 9-7 9-7-2-7-9-7Zm0 4a3 3 0 1 1 0 6 3 3 0 0 1 0-6Z" />
                    </svg>
                    Chi tiết
                  </button>
                  <button
                    type="button"
                    class="inline-flex items-center gap-1 rounded-full border border-emerald-200 bg-white px-3 py-1.5 text-[11px] font-semibold uppercase tracking-wide text-emerald-600 shadow-sm transition hover:border-emerald-300 hover:bg-emerald-50"
                    @click="openEditModal(room)"
                  >
                    <svg xmlns="http://www.w3.org/2000/svg" class="h-3.5 w-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.8">
                      <path stroke-linecap="round" stroke-linejoin="round" d="m16.862 4.487 1.95 1.95M7.5 20.5l-4-4L16 4l4 4-12.5 12.5Z" />
                    </svg>
                    Sửa
                  </button>
                  <button
                    type="button"
                    class="inline-flex items-center gap-1 rounded-full border border-rose-200 bg-white px-3 py-1.5 text-[11px] font-semibold uppercase tracking-wide text-rose-600 shadow-sm transition hover:border-rose-300 hover:bg-rose-50"
                    @click="confirmDelete(room)"
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
          <span>Đang hiển thị {{ rooms.length }} / {{ totalElements }} phòng khám</span>
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

    <Transition name="fade">
      <div
        v-if="formModalOpen"
        class="fixed inset-0 z-[90] flex items-center justify-center bg-slate-900/50 backdrop-blur-sm px-4"
      >
        <div class="w-full max-w-lg rounded-3xl border border-emerald-100 bg-white p-6 shadow-xl">
          <div class="flex items-start justify-between">
            <div>
              <p class="text-xs font-semibold uppercase tracking-[0.35em] text-emerald-500">
                {{ modalMode === 'create' ? 'Thêm phòng' : 'Chỉnh sửa phòng' }}
              </p>
              <h2 class="mt-1 text-xl font-semibold text-slate-900">
                {{ modalMode === 'create' ? 'Tạo phòng khám mới' : 'Cập nhật thông tin phòng' }}
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

          <form class="mt-6 space-y-4" @submit.prevent="submitForm">
            <div class="grid gap-4 sm:grid-cols-2">
              <div class="space-y-1.5">
                <label class="text-xs font-semibold uppercase tracking-wide text-slate-500" for="clinic-room-code">
                  Mã phòng *
                </label>
                <input
                  id="clinic-room-code"
                  v-model="formState.code"
                  type="text"
                  autocomplete="off"
                  class="w-full rounded-xl border border-slate-200 px-4 py-2 text-sm text-slate-700 shadow-sm transition focus:border-emerald-400 focus:outline-none focus:ring-4 focus:ring-emerald-100/80"
                  placeholder="VD: R-101"
                  maxlength="80"
                />
              </div>
              <div class="space-y-1.5">
                <label class="text-xs font-semibold uppercase tracking-wide text-slate-500" for="clinic-room-name">
                  Tên phòng *
                </label>
                <input
                  id="clinic-room-name"
                  v-model="formState.name"
                  type="text"
                  class="w-full rounded-xl border border-slate-200 px-4 py-2 text-sm text-slate-700 shadow-sm transition focus:border-emerald-400 focus:outline-none focus:ring-4 focus:ring-emerald-100/80"
                  placeholder="Phòng Khám Nội tổng quát"
                  maxlength="120"
                />
              </div>
            </div>
            <div class="space-y-1.5">
              <label class="text-xs font-semibold uppercase tracking-wide text-slate-500" for="clinic-room-floor">
                Tầng / Khu
              </label>
              <select
                id="clinic-room-floor"
                v-model="formState.floor"
                class="w-full rounded-xl border border-slate-200 px-4 py-2 text-sm text-slate-700 shadow-sm transition focus:border-emerald-400 focus:outline-none focus:ring-4 focus:ring-emerald-100/80"
              >
                <option value="">Chọn tầng/khu</option>
                <option v-for="option in FLOOR_PRESETS" :key="option" :value="option">
                  {{ option }}
                </option>
                <option
                  v-if="formState.floor && !FLOOR_PRESETS.includes(formState.floor)"
                  :value="formState.floor"
                >
                  {{ formState.floor }}
                </option>
              </select>
            </div>
            <div class="space-y-1.5">
              <label class="text-xs font-semibold uppercase tracking-wide text-slate-500" for="clinic-room-capacity">
                Sức chứa (số nhân viên tối đa)
              </label>
              <input
                id="clinic-room-capacity"
                v-model.number="formState.capacity"
                type="number"
                min="1"
                class="w-full rounded-xl border border-slate-200 px-4 py-2 text-sm text-slate-700 shadow-sm transition focus:border-emerald-400 focus:outline-none focus:ring-4 focus:ring-emerald-100/80"
                placeholder="VD: 1"
              />
            </div>
            <div class="space-y-1.5">
              <label class="text-xs font-semibold uppercase tracking-wide text-slate-500" for="clinic-room-note">
                Ghi chú
              </label>
              <textarea
                id="clinic-room-note"
                v-model="formState.note"
                rows="3"
                class="w-full rounded-xl border border-slate-200 px-4 py-2 text-sm text-slate-700 shadow-sm transition focus:border-emerald-400 focus:outline-none focus:ring-4 focus:ring-emerald-100/80"
                placeholder="Ghi chú thêm về trang thiết bị, lịch vệ sinh..."
                maxlength="255"
              ></textarea>
            </div>

            <p
              v-if="formError"
              class="rounded-2xl border border-rose-100 bg-rose-50/90 px-4 py-3 text-sm text-rose-600"
            >
              {{ formError }}
            </p>

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
                <span>{{ modalMode === 'create' ? 'Thêm phòng' : 'Lưu thay đổi' }}</span>
              </button>
            </div>
          </form>
        </div>
      </div>
    </Transition>

    <Transition name="fade">
      <div
        v-if="detailModalOpen && detailRoom"
        class="fixed inset-0 z-[90] flex items-center justify-center bg-slate-900/50 backdrop-blur-sm px-4"
      >
        <div class="w-full max-w-lg rounded-3xl border border-emerald-100 bg-white p-6 shadow-xl">
          <div class="flex items-start justify-between">
            <div>
              <p class="text-xs font-semibold uppercase tracking-[0.35em] text-emerald-500">Chi tiết phòng</p>
              <h2 class="mt-2 text-xl font-semibold text-slate-900">{{ detailRoom.name }}</h2>
              <p class="text-sm text-slate-500">Mã phòng: {{ detailRoom.code }}</p>
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

          <div class="mt-6 space-y-4 text-sm text-slate-700">
            <div class="flex items-start gap-3">
              <span class="w-28 shrink-0 text-xs font-semibold uppercase tracking-wide text-slate-500">Mã phòng</span>
              <span class="font-semibold text-slate-900">{{ detailRoom.code }}</span>
            </div>
            <div class="flex items-start gap-3">
              <span class="w-28 shrink-0 text-xs font-semibold uppercase tracking-wide text-slate-500">Tên phòng</span>
              <span>{{ detailRoom.name }}</span>
            </div>
            <div class="flex items-start gap-3">
              <span class="w-28 shrink-0 text-xs font-semibold uppercase tracking-wide text-slate-500">Tầng / Khu</span>
              <span>{{ detailRoom.floor || '—' }}</span>
            </div>
            <div class="flex items-start gap-3">
              <span class="w-28 shrink-0 text-xs font-semibold uppercase tracking-wide text-slate-500">Sức chứa</span>
              <span>{{ detailRoom.capacity ?? 1 }} nhân viên/ca</span>
            </div>
            <div class="flex items-start gap-3">
              <span class="w-28 shrink-0 text-xs font-semibold uppercase tracking-wide text-slate-500">Ghi chú</span>
              <span class="whitespace-pre-line text-slate-600">{{ detailRoom.note || '—' }}</span>
            </div>
            <div class="flex items-start gap-3">
              <span class="w-28 shrink-0 text-xs font-semibold uppercase tracking-wide text-slate-500">Tạo lúc</span>
              <span>{{ formatDateTime(detailRoom.createdAt) }}</span>
            </div>
            <div class="flex items-start gap-3">
              <span class="w-28 shrink-0 text-xs font-semibold uppercase tracking-wide text-slate-500">Cập nhật</span>
              <span>{{ formatDateTime(detailRoom.updatedAt) }}</span>
            </div>
          </div>
        </div>
      </div>
    </Transition>

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
              <h3 class="text-lg font-semibold text-slate-900">Xóa phòng khám?</h3>
              <p class="mt-1 text-sm text-slate-600">
                Bạn sắp xóa phòng <span class="font-semibold text-rose-600">{{ deleteTarget.name }}</span>. Hành động này không thể hoàn tác.
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
              <span>{{ deleting ? 'Đang xóa...' : 'Xóa phòng' }}</span>
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
