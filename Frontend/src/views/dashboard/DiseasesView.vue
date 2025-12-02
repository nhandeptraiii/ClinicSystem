<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { useToast, type ToastType } from '@/composables/useToast';
import {
  createDisease,
  deleteDisease,
  fetchDiseasePage,
  updateDisease,
  type Disease,
} from '@/services/disease.service';

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

const diseases = ref<Disease[]>([]);
const loading = ref(false);
const PAGE_SIZE = 10;
const searchTerm = ref('');
const page = ref(1);
const totalPages = ref(1);
const totalElements = ref(0);

const modalOpen = ref(false);
const submitting = ref(false);
const editingDisease = ref<Disease | null>(null);
const formState = ref({
  code: '',
  name: '',
  description: '',
});

const deleteConfirmOpen = ref(false);
const diseaseToDelete = ref<Disease | null>(null);
const deleting = ref(false);
let searchTimer: ReturnType<typeof setTimeout> | null = null;

const paginationLabel = computed(() => {
  const from = (page.value - 1) * PAGE_SIZE + 1;
  const to = Math.min(page.value * PAGE_SIZE, totalElements.value || diseases.value.length);
  const total = totalElements.value || diseases.value.length;
  return `Đang hiển thị ${total === 0 ? 0 : from}-${to} / ${total}`;
});

const resetForm = () => {
  formState.value = {
    code: '',
    name: '',
    description: '',
  };
  editingDisease.value = null;
};

const loadDiseases = async (targetPage?: number) => {
  loading.value = true;
  const requestedPage = targetPage ?? page.value;
  const pageIndex = Math.max(requestedPage - 1, 0);
  const keyword = searchTerm.value.trim();

  try {
    const response = await fetchDiseasePage({
      keyword: keyword || undefined,
      page: pageIndex,
      size: PAGE_SIZE,
    });
    diseases.value = response.items ?? [];
    totalPages.value = response.totalPages && response.totalPages > 0 ? response.totalPages : 1;
    totalElements.value = response.totalElements ?? diseases.value.length;
    page.value = response.totalPages && response.totalPages > 0 ? response.page + 1 : 1;
  } catch (err: any) {
    diseases.value = [];
    totalPages.value = 1;
    totalElements.value = 0;
    const errorMessage = err?.response?.data?.message ?? err?.message ?? 'Không thể tải danh sách bệnh.';
    showToast('error', errorMessage);
  } finally {
    loading.value = false;
  }
};

const openCreateModal = () => {
  resetForm();
  modalOpen.value = true;
};

const openEditModal = (disease: Disease) => {
  editingDisease.value = disease;
  formState.value = {
    code: disease.code,
    name: disease.name,
    description: disease.description ?? '',
  };
  modalOpen.value = true;
};

const saveDisease = async () => {
  const code = formState.value.code.trim().toUpperCase();
  const name = formState.value.name.trim();
  const description = formState.value.description?.trim() ?? '';

  if (!code || !name) {
    showToast('warning', 'Mã bệnh và tên bệnh không được để trống.');
    return;
  }

  const isEditing = !!editingDisease.value;
  submitting.value = true;
  try {
    if (editingDisease.value) {
      await updateDisease(editingDisease.value.id, {
        code,
        name,
        description: description || null,
      });
      showToast('success', 'Đã cập nhật bệnh.');
    } else {
      await createDisease({
        code,
        name,
        description: description || null,
      });
      showToast('success', 'Đã tạo bệnh mới.');
    }
    modalOpen.value = false;
    resetForm();
    await loadDiseases(isEditing ? page.value : 1);
  } catch (err: any) {
    const errorMessage = err?.response?.data?.message ?? err?.message ?? 'Không thể lưu thông tin bệnh.';
    showToast('error', errorMessage);
  } finally {
    submitting.value = false;
  }
};

const confirmDelete = (disease: Disease) => {
  diseaseToDelete.value = disease;
  deleteConfirmOpen.value = true;
};

const performDelete = async () => {
  if (!diseaseToDelete.value) return;
  deleting.value = true;
  try {
    await deleteDisease(diseaseToDelete.value.id);
    showToast('success', 'Đã xóa bệnh.');
    deleteConfirmOpen.value = false;
    await loadDiseases(page.value);
  } catch (err: any) {
    const errorMessage = err?.response?.data?.message ?? err?.message ?? 'Không thể xóa bệnh.';
    showToast('error', errorMessage);
  } finally {
    deleting.value = false;
  }
};

const goToPage = (target: number) => {
  if (target < 1 || target > totalPages.value) return;
  void loadDiseases(target);
};

watch(
  () => searchTerm.value,
  () => {
    const keyword = searchTerm.value.trim();
    const delay = keyword ? 250 : 0;
    if (searchTimer) {
      clearTimeout(searchTimer);
    }
    searchTimer = setTimeout(() => {
      void loadDiseases(1);
    }, delay);
  },
);

onMounted(() => {
  void loadDiseases();
});

onBeforeUnmount(() => {
  if (searchTimer) {
    clearTimeout(searchTimer);
  }
});
</script>

<template>
  <div class="relative min-h-screen bg-gradient-to-br from-emerald-50 via-white to-white">
    <main class="mx-auto w-full max-w-6xl px-4 pb-20 pt-10 sm:px-6 lg:px-8">
      <section>
        <div class="flex flex-col gap-4 border-b border-emerald-100 pb-6 md:flex-row md:items-center md:justify-between">
          <div>
            <p class="text-xs font-semibold uppercase tracking-[0.35em] text-emerald-500">Danh mục bệnh</p>
            <h1 class="mt-2 text-2xl font-semibold text-slate-900">Quản lý bệnh</h1>
            <p class="mt-1 text-sm text-slate-600">Quản lý mã bệnh nội bộ/ICD phục vụ chẩn đoán và kê đơn.</p>
          </div>
          <button
            type="button"
            class="inline-flex items-center gap-2 self-start rounded-full bg-emerald-600 px-5 py-2 text-xs font-semibold uppercase tracking-wide text-white shadow-sm transition hover:bg-emerald-700 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-emerald-500"
            @click="openCreateModal"
          >
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" class="h-4 w-4" fill="none" stroke="currentColor" stroke-width="1.8">
              <path stroke-linecap="round" stroke-linejoin="round" d="M12 5v14m7-7H5" />
            </svg>
            Thêm bệnh
          </button>
        </div>

        <div class="mt-6">
          <div class="relative w-full sm:max-w-md">
            <svg class="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" fill="none" stroke="currentColor" stroke-width="1.5" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" d="m21 21-4.35-4.35m0 0A7.35 7.35 0 1 0 6.3 6.3a7.35 7.35 0 0 0 10.35 10.35Z" />
            </svg>
            <input
              v-model.trim="searchTerm"
              type="search"
              class="w-full rounded-full border border-emerald-100 bg-white py-2.5 pl-9 pr-4 text-sm text-slate-700 shadow-sm transition focus:border-emerald-400 focus:outline-none focus:ring-4 focus:ring-emerald-100/80"
              placeholder="Tìm theo mã hoặc tên bệnh..."
            />
          </div>
        </div>

        <div class="mt-5">
          <div
            class="hidden rounded-2xl border border-emerald-100 bg-emerald-50/70 pl-4 pr-0 py-3 text-xs font-semibold uppercase tracking-wide text-emerald-700 md:grid md:grid-cols-[120px_320px_1fr_160px] md:gap-3"
          >
            <span>Mã bệnh</span>
            <span>Tên bệnh</span>
            <span>Mô tả</span>
            <span class="text-center">Thao tác</span>
          </div>

          <template v-if="loading">
            <div
              v-for="skeleton in 4"
              :key="`disease-skeleton-${skeleton}`"
              class="mt-3 animate-pulse rounded-2xl border border-slate-100 bg-white pl-4 pr-0 py-4 shadow-sm"
            >
              <div class="grid gap-3 md:grid-cols-[120px_320px_1fr_160px] md:items-center">
                <div class="h-4 rounded-full bg-slate-200/70"></div>
                <div class="h-4 rounded-full bg-slate-200/60"></div>
                <div class="h-4 rounded-full bg-slate-200/50"></div>
                <div class="ml-auto h-8 w-24 rounded-full bg-slate-200/70"></div>
              </div>
            </div>
          </template>
          <template v-else-if="diseases.length === 0">
            <div class="mt-8 rounded-3xl border border-dashed border-emerald-200 bg-emerald-50/40 p-10 text-center text-emerald-700">
              <svg xmlns="http://www.w3.org/2000/svg" class="mx-auto h-12 w-12 text-emerald-400" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.6">
                <path stroke-linecap="round" stroke-linejoin="round" d="M9 12h6m-6 4h6m2 5H7a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5.586a1 1 0 0 1 .707.293l5.414 5.414a1 1 0 0 1 .293.707V19a2 2 0 0 1-2 2Z" />
              </svg>
              <h3 class="mt-4 text-lg font-semibold">Chưa có bệnh nào</h3>
              <p class="mt-2 text-sm text-emerald-600/80">Thử thay đổi bộ lọc hoặc tạo mới bệnh.</p>
            </div>
          </template>
          <template v-else>
            <div
              v-for="disease in diseases"
              :key="disease.id"
              class="mt-3 rounded-2xl border border-slate-200 bg-white pl-4 pr-0 py-4 shadow-sm transition hover:border-emerald-200 hover:shadow-md"
            >
              <div class="grid gap-3 md:grid-cols-[120px_320px_1fr_160px] md:items-center">
                <div>
                  <p class="text-xs font-semibold uppercase tracking-wide text-slate-400 md:hidden">Mã bệnh</p>
                  <p class="text-sm font-semibold text-slate-900">{{ disease.code }}</p>
                </div>
                <div>
                  <p class="text-xs font-semibold uppercase tracking-wide text-slate-400 md:hidden">Tên bệnh</p>
                  <p class="text-sm text-slate-700">{{ disease.name }}</p>
                </div>
                <div>
                  <p class="text-xs font-semibold uppercase tracking-wide text-slate-400 md:hidden">Mô tả</p>
                  <p class="text-sm text-slate-700 line-clamp-2">
                    <span v-if="disease.description">{{ disease.description }}</span>
                    <span v-else class="text-slate-400">Chưa có mô tả</span>
                  </p>
                </div>
                <div class="flex items-center justify-end gap-1.5 pr-4">
                  <button
                    type="button"
                    class="inline-flex items-center gap-1 rounded-full border border-emerald-200 bg-white px-2.5 py-1.5 text-[11px] font-semibold uppercase tracking-wide text-emerald-600 shadow-sm transition hover:border-emerald-300 hover:bg-emerald-50 whitespace-nowrap"
                    @click="openEditModal(disease)"
                  >
                    <svg xmlns="http://www.w3.org/2000/svg" class="h-3.5 w-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.8">
                      <path stroke-linecap="round" stroke-linejoin="round" d="m16.862 4.487 1.95 1.95M7.5 20.5l-4-4L16 4l4 4-12.5 12.5Z" />
                    </svg>
                    Sửa
                  </button>
                  <button
                    type="button"
                    class="inline-flex items-center gap-1 rounded-full border border-rose-200 bg-white px-2.5 py-1.5 text-[11px] font-semibold uppercase tracking-wide text-rose-600 shadow-sm transition hover:border-rose-300 hover:bg-rose-50 whitespace-nowrap"
                    @click="confirmDelete(disease)"
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
          <span>{{ paginationLabel }}</span>
          <div class="flex items-center gap-2">
            <button
              type="button"
              class="inline-flex items-center gap-2 rounded-full border border-emerald-200 bg-white px-4 py-1.5 text-xs font-semibold uppercase tracking-wide text-emerald-600 shadow-sm transition hover:border-emerald-300 hover:bg-emerald-50 disabled:cursor-not-allowed disabled:opacity-60"
              :disabled="page <= 1"
              @click="goToPage(page - 1)"
            >
              Trước
            </button>
            <span class="text-sm font-semibold text-slate-700">{{ page }} / {{ totalPages }}</span>
            <button
              type="button"
              class="inline-flex items-center gap-2 rounded-full border border-emerald-200 bg-white px-4 py-1.5 text-xs font-semibold uppercase tracking-wide text-emerald-600 shadow-sm transition hover:border-emerald-300 hover:bg-emerald-50 disabled:cursor-not-allowed disabled:opacity-60"
              :disabled="page >= totalPages"
              @click="goToPage(page + 1)"
            >
              Sau
            </button>
          </div>
        </div>
      </section>
    </main>

    <!-- Create / Edit Modal -->
    <Transition name="fade">
      <div
        v-if="modalOpen"
        class="fixed inset-0 z-40 flex items-center justify-center bg-slate-900/50 backdrop-blur-sm px-4"
        @click.self="modalOpen = false"
      >
        <div class="w-full max-w-xl rounded-3xl border border-emerald-100 bg-white p-6 shadow-xl">
          <div class="flex items-start justify-between">
            <div>
              <p class="text-xs font-semibold uppercase tracking-[0.35em] text-emerald-500">
                {{ editingDisease ? 'Cập nhật' : 'Thêm mới' }}
              </p>
              <h2 class="mt-1 text-xl font-semibold text-slate-900">
                {{ editingDisease ? 'Chỉnh sửa bệnh' : 'Thêm bệnh mới' }}
              </h2>
            </div>
            <button
              type="button"
              class="flex h-9 w-9 items-center justify-center rounded-full border border-slate-200 text-slate-500 transition hover:border-slate-300 hover:text-slate-700"
              @click="modalOpen = false"
            >
              <svg xmlns="http://www.w3.org/2000/svg" class="h-4.5 w-4.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.8">
                <path stroke-linecap="round" stroke-linejoin="round" d="m16 8-8 8m0-8 8 8" />
              </svg>
            </button>
          </div>

          <div class="mt-6 space-y-4">
            <div class="space-y-1.5">
              <label class="text-xs font-semibold uppercase tracking-wide text-slate-500">Mã bệnh</label>
              <input
                v-model="formState.code"
                type="text"
                placeholder="VD: A15, E11..."
                class="w-full rounded-xl border border-slate-200 px-4 py-2 text-sm uppercase text-slate-700 shadow-sm transition focus:border-emerald-400 focus:outline-none focus:ring-4 focus:ring-emerald-100/80"
              />
            </div>
            <div class="space-y-1.5">
              <label class="text-xs font-semibold uppercase tracking-wide text-slate-500">Tên bệnh</label>
              <input
                v-model="formState.name"
                type="text"
                placeholder="Tên bệnh"
                class="w-full rounded-xl border border-slate-200 px-4 py-2 text-sm text-slate-700 shadow-sm transition focus:border-emerald-400 focus:outline-none focus:ring-4 focus:ring-emerald-100/80"
              />
            </div>
            <div class="space-y-1.5">
              <label class="text-xs font-semibold uppercase tracking-wide text-slate-500">Mô tả</label>
              <textarea
                v-model="formState.description"
                rows="3"
                placeholder="Ghi chú, biểu hiện lâm sàng, ICD..."
                class="w-full rounded-xl border border-slate-200 px-4 py-2 text-sm text-slate-700 shadow-sm transition focus:border-emerald-400 focus:outline-none focus:ring-4 focus:ring-emerald-100/80"
              />
            </div>
          </div>

          <div class="mt-6 flex items-center justify-end gap-3 border-t border-slate-200 pt-4">
            <button
              type="button"
              class="inline-flex items-center gap-2 rounded-full border border-slate-200 bg-white px-5 py-2 text-xs font-semibold uppercase tracking-wide text-slate-600 shadow-sm transition hover:border-slate-300 hover:bg-slate-50"
              @click="modalOpen = false"
            >
              Hủy
            </button>
            <button
              type="button"
              class="inline-flex items-center gap-2 rounded-full bg-emerald-600 px-6 py-2 text-xs font-semibold uppercase tracking-wide text-white shadow-sm transition hover:bg-emerald-700 disabled:cursor-not-allowed disabled:opacity-70"
              :disabled="submitting"
              @click="saveDisease"
            >
              <svg v-if="submitting" class="h-4 w-4 animate-spin" viewBox="0 0 24 24" fill="none">
                <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
                <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 0 1 8-8v4l3.5-3.5L12 1v4a7 7 0 0 0-7 7H4z"></path>
              </svg>
              <span>{{ editingDisease ? 'Lưu thay đổi' : 'Thêm bệnh' }}</span>
            </button>
          </div>
        </div>
      </div>
    </Transition>

    <!-- Delete Confirmation Modal -->
    <Transition name="fade">
      <div
        v-if="deleteConfirmOpen"
        class="fixed inset-0 z-40 flex items-center justify-center bg-slate-900/60 px-4"
      >
        <div class="w-full max-w-md rounded-3xl border border-rose-100 bg-white p-6 text-slate-700 shadow-xl">
          <div class="flex items-start gap-3">
            <div class="flex h-10 w-10 items-center justify-center rounded-full bg-rose-50 text-rose-600">
              <svg xmlns="http://www.w3.org/2000/svg" class="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.8">
                <path stroke-linecap="round" stroke-linejoin="round" d="M12 9v4m0 4h.01M5 12c0 7 7 9 7 9s7-2 7-9-7-9-7-9-7 2-7 9Z" />
              </svg>
            </div>
            <div>
              <h3 class="text-lg font-semibold text-slate-900">Xóa bệnh?</h3>
              <p class="mt-1 text-sm text-slate-600">
                Bạn sắp xóa <span class="font-semibold text-rose-600">{{ diseaseToDelete?.name }}</span>. Hành động này không thể hoàn tác.
              </p>
            </div>
          </div>
          <div class="mt-6 flex items-center justify-end gap-3">
            <button
              type="button"
              class="inline-flex items-center gap-2 rounded-full border border-slate-200 bg-white px-4 py-2 text-xs font-semibold uppercase tracking-wide text-slate-600 shadow-sm transition hover:border-slate-300 hover:bg-slate-50"
              :disabled="deleting"
              @click="deleteConfirmOpen = false"
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
              <span>{{ deleting ? 'Đang xóa...' : 'Xóa bệnh' }}</span>
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
              <p class="mt-1 text-sm leading-relaxed">{{ toast?.message }}</p>
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
