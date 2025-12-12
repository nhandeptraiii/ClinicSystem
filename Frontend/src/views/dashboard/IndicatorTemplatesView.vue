<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue';
import { useRouter } from 'vue-router';
import AdminHeader from '@/components/AdminHeader.vue';
import { useAuthStore } from '@/stores/authStore';
import { useToast, type ToastType } from '@/composables/useToast';
import {
  createIndicatorTemplate,
  deleteIndicatorTemplate,
  fetchIndicatorTemplates,
  fetchMappingsByTemplateId,
  updateIndicatorTemplate,
  type IndicatorTemplate,
  type IndicatorTemplatePayload,
  type ServiceIndicatorMapping,
  fetchCategories,
} from '@/services/indicatorTemplate.service';

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

const templates = ref<IndicatorTemplate[]>([]);
const loading = ref(false);
const error = ref<string | null>(null);

const PAGE_SIZE = 10;
const currentPage = ref(1);
const totalPages = ref(1);
const totalElements = ref(0);
const hasNext = ref(false);
const hasPrevious = ref(false);

const searchTerm = ref('');
const categoryFilter = ref('');
const availableCategories = ref<string[]>([]);

let filterTimer: ReturnType<typeof setTimeout> | null = null;

const formModalOpen = ref(false);
const modalSubmitting = ref(false);
const modalMode = ref<'create' | 'edit'>('create');
const formError = ref<string | null>(null);
const formState = reactive<IndicatorTemplatePayload>({
  code: '',
  name: '',
  unit: '',
  normalMin: null,
  normalMax: null,
  criticalMin: null,
  criticalMax: null,
  referenceNote: '',
  category: '',
});
const selectedTemplate = ref<IndicatorTemplate | null>(null);

const detailModalOpen = ref(false);
const detailTemplate = ref<IndicatorTemplate | null>(null);
const detailMappings = ref<ServiceIndicatorMapping[]>([]);
const loadingMappings = ref(false);

const deleteTarget = ref<IndicatorTemplate | null>(null);
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

const formatNumber = (value?: number | null) => {
  if (value === null || value === undefined) return '—';
  return value.toString();
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

const loadTemplates = async () => {
  loading.value = true;
  error.value = null;
  const keyword = searchTerm.value.trim();
  const category = categoryFilter.value.trim();
  const pageIndex = Math.max(currentPage.value - 1, 0);
  try {
    const response = await fetchIndicatorTemplates(pageIndex, PAGE_SIZE, keyword || undefined, category || undefined);
    templates.value = response.items ?? [];
    totalElements.value = response.totalElements ?? templates.value.length;
    const derivedTotalPages = response.totalPages && response.totalPages > 0 ? response.totalPages : 1;
    totalPages.value = derivedTotalPages;
    currentPage.value = response.totalPages && response.totalPages > 0 ? response.page + 1 : 1;
    hasNext.value = response.hasNext ?? false;
    hasPrevious.value = response.hasPrevious ?? false;
  } catch (err) {
    error.value = extractErrorMessage(err);
    templates.value = [];
    totalElements.value = 0;
    totalPages.value = 1;
    currentPage.value = 1;
    hasNext.value = false;
    hasPrevious.value = false;
  } finally {
    loading.value = false;
  }
};

const loadCategories = async () => {
  try {
    availableCategories.value = await fetchCategories();
  } catch (err) {
    console.error('Failed to load categories:', err);
    availableCategories.value = [];
  }
};

const goToPage = (page: number) => {
  const target = Math.min(Math.max(page, 1), totalPages.value || 1);
  if (target === currentPage.value) {
    return;
  }
  currentPage.value = target;
  loadTemplates();
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
  selectedTemplate.value = null;
  formError.value = null;
  formState.code = '';
  formState.name = '';
  formState.unit = '';
  formState.normalMin = null;
  formState.normalMax = null;
  formState.criticalMin = null;
  formState.criticalMax = null;
  formState.referenceNote = '';
  formState.category = '';
  formModalOpen.value = true;
};

const openEditModal = (template: IndicatorTemplate) => {
  modalMode.value = 'edit';
  selectedTemplate.value = template;
  formError.value = null;
  formState.code = template.code ?? '';
  formState.name = template.name ?? '';
  formState.unit = template.unit ?? '';
  formState.normalMin = template.normalMin ?? null;
  formState.normalMax = template.normalMax ?? null;
  formState.criticalMin = template.criticalMin ?? null;
  formState.criticalMax = template.criticalMax ?? null;
  formState.referenceNote = template.referenceNote ?? '';
  formState.category = template.category ?? '';
  formModalOpen.value = true;
};

const closeFormModal = () => {
  formModalOpen.value = false;
  formError.value = null;
};

const parseOptionalNumber = (value: any): number | null => {
  if (value === null || value === undefined || value === '') return null;
  const num = Number(value);
  return isNaN(num) ? null : num;
};

const submitForm = async () => {
  if (modalSubmitting.value) return;

  const payload: IndicatorTemplatePayload = {
    code: formState.code?.trim() ?? '',
    name: formState.name?.trim() ?? '',
    unit: formState.unit?.trim() || null,
    normalMin: parseOptionalNumber(formState.normalMin),
    normalMax: parseOptionalNumber(formState.normalMax),
    criticalMin: parseOptionalNumber(formState.criticalMin),
    criticalMax: parseOptionalNumber(formState.criticalMax),
    referenceNote: formState.referenceNote?.trim() || null,
    category: formState.category?.trim() || null,
  };

  if (!payload.code || !payload.name) {
    formError.value = 'Vui lòng nhập đầy đủ mã và tên chỉ số.';
    return;
  }

  modalSubmitting.value = true;
  try {
    if (modalMode.value === 'create') {
      await createIndicatorTemplate(payload);
      showToast('success', 'Đã thêm mẫu chỉ số mới.');
    } else if (selectedTemplate.value) {
      await updateIndicatorTemplate(selectedTemplate.value.id, payload);
      showToast('success', 'Đã cập nhật mẫu chỉ số.');
    }
    closeFormModal();
    await loadTemplates();
  } catch (error) {
    formError.value = extractErrorMessage(error);
    showToast('error', formError.value ?? 'Không thể lưu thông tin mẫu chỉ số.');
  } finally {
    modalSubmitting.value = false;
  }
};

const openDetailModal = async (template: IndicatorTemplate) => {
  detailTemplate.value = template;
  detailMappings.value = [];
  detailModalOpen.value = true;
  loadingMappings.value = true;
  
  try {
    const mappings = await fetchMappingsByTemplateId(template.id);
    detailMappings.value = mappings || [];
    console.log('Loaded mappings:', mappings);
  } catch (err) {
    console.error('Failed to load mappings:', err);
    detailMappings.value = [];
  } finally {
    loadingMappings.value = false;
    console.log('Loading complete, loadingMappings set to false');
  }
};

const closeDetailModal = () => {
  detailModalOpen.value = false;
  detailMappings.value = [];
};

const confirmDelete = (template: IndicatorTemplate) => {
  deleteTarget.value = template;
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
    await deleteIndicatorTemplate(deleteTarget.value.id);
    showToast('success', 'Đã xóa mẫu chỉ số.');
    const shouldGoPrev = templates.value.length <= 1 && currentPage.value > 1;
    deleteTarget.value = null;
    deleting.value = false;
    if (shouldGoPrev) {
      currentPage.value = currentPage.value - 1;
    }
    await loadTemplates();
  } catch (error) {
    deleting.value = false;
    showToast('error', extractErrorMessage(error));
  }
};

watch(searchTerm, () => {
  if (filterTimer) {
    clearTimeout(filterTimer);
  }
  filterTimer = setTimeout(() => {
    currentPage.value = 1;
    loadTemplates();
  }, 350);
});

watch(categoryFilter, () => {
  if (filterTimer) {
    clearTimeout(filterTimer);
  }
  filterTimer = setTimeout(() => {
    currentPage.value = 1;
    loadTemplates();
  }, 350);
});

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
  loadTemplates();
  loadCategories();
});
</script>

<template>
  <div class="relative min-h-screen bg-gradient-to-br from-emerald-50 via-white to-white">
    <AdminHeader :user-name="userName" @sign-out="handleSignOut" />

    <main class="mx-auto w-full max-w-6xl px-4 pb-20 pt-10 sm:px-6 lg:px-8">
      <section>
        <div class="flex flex-col gap-4 border-b border-emerald-100 pb-6 md:flex-row md:items-center md:justify-between">
          <div>
            <p class="text-xs font-semibold uppercase tracking-[0.35em] text-emerald-500">Quản lý mẫu chỉ số</p>
            <h1 class="mt-2 text-2xl font-semibold text-slate-900">Danh sách mẫu chỉ số</h1>
            <p class="mt-1 text-sm text-slate-600">
              Quản lý các mẫu chỉ số xét nghiệm trong hệ thống.
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
            Thêm mẫu mới
          </button>
        </div>

        <div class="mt-6 flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
          <div class="relative w-full sm:max-w-md">
            <svg class="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" fill="none" stroke="currentColor" stroke-width="1.5" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" d="m21 21-4.35-4.35m0 0A7.35 7.35 0 1 0 6.3 6.3a7.35 7.35 0 0 0 10.35 10.35Z" />
            </svg>
            <input
              v-model.trim="searchTerm"
              type="search"
              class="w-full rounded-full border border-emerald-100 bg-white py-2.5 pl-9 pr-4 text-sm text-slate-700 shadow-sm transition focus:border-emerald-400 focus:outline-none focus:ring-4 focus:ring-emerald-100/80"
              placeholder="Tìm theo mã hoặc tên chỉ số..."
            />
          </div>
          <div class="relative w-full sm:w-64">
            <select
              v-model.trim="categoryFilter"
              class="w-full rounded-full border border-emerald-100 bg-white py-2.5 px-4 text-sm text-slate-700 shadow-sm transition focus:border-emerald-400 focus:outline-none focus:ring-4 focus:ring-emerald-100/80"
            >
              <option value="">Tất cả danh mục</option>
              <option v-for="cat in availableCategories" :key="cat" :value="cat">
                {{ cat }}
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
            class="hidden rounded-2xl border border-emerald-100 bg-emerald-50/70 pl-4 pr-0 py-3 text-xs font-semibold uppercase tracking-wide text-emerald-700 md:grid md:grid-cols-[100px_300px_200px_80px_1fr] md:gap-3"
          >
            <span>Mã</span>
            <span>Tên</span>
            <span>Khoảng chuẩn</span>
            <span class="text-center">Đơn vị</span>
            <span class="text-center pl-20">Thao tác</span>
          </div>

          <template v-if="loading">
            <div
              v-for="skeleton in 4"
              :key="`template-skeleton-${skeleton}`"
              class="mt-3 animate-pulse rounded-2xl border border-slate-100 bg-white pl-4 pr-0 py-4 shadow-sm"
            >
              <div class="grid gap-3 md:grid-cols-[100px_300px_200px_80px_1fr] md:items-center">
                <div class="h-4 rounded-full bg-slate-200/70"></div>
                <div class="h-4 rounded-full bg-slate-200/60"></div>
                <div class="h-4 rounded-full bg-slate-200/50"></div>
                <div class="h-4 rounded-full bg-slate-200/50"></div>
                <div class="ml-auto h-8 w-24 rounded-full bg-slate-200/70"></div>
              </div>
            </div>
          </template>
          <template v-else-if="templates.length === 0">
            <div class="mt-8 rounded-3xl border border-dashed border-emerald-200 bg-emerald-50/40 p-10 text-center text-emerald-700">
              <svg xmlns="http://www.w3.org/2000/svg" class="mx-auto h-12 w-12 text-emerald-400" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.6">
                <path stroke-linecap="round" stroke-linejoin="round" d="M9 12h6m-6 4h6m2 5H7a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5.586a1 1 0 0 1 .707.293l5.414 5.414a1 1 0 0 1 .293.707V19a2 2 0 0 1-2 2Z" />
              </svg>
              <h3 class="mt-4 text-lg font-semibold">Chưa có mẫu chỉ số nào</h3>
              <p class="mt-2 text-sm text-emerald-600/80">Thử thay đổi bộ lọc hoặc tạo mới mẫu chỉ số.</p>
            </div>
          </template>
          <template v-else>
            <div
              v-for="template in templates"
              :key="template.id"
              class="mt-3 rounded-2xl border border-slate-200 bg-white pl-4 pr-0 py-4 shadow-sm transition hover:border-emerald-200 hover:shadow-md"
            >
              <div class="grid gap-3 md:grid-cols-[100px_300px_200px_80px_1fr] md:items-center">
                <div>
                  <p class="text-xs font-semibold uppercase tracking-wide text-slate-400 md:hidden">Mã</p>
                  <p class="text-sm font-semibold text-slate-900">{{ template.code }}</p>
                </div>
                <div>
                  <p class="text-xs font-semibold uppercase tracking-wide text-slate-400 md:hidden">Tên</p>
                  <p class="text-sm text-slate-700">{{ template.name }}</p>
                </div>
                <div>
                  <p class="text-xs font-semibold uppercase tracking-wide text-slate-400 md:hidden">Khoảng chuẩn</p>
                  <p class="text-sm text-slate-700 pl-8">
                    <span v-if="template.normalMin !== null && template.normalMax !== null">
                      {{ formatNumber(template.normalMin) }} - {{ formatNumber(template.normalMax) }}
                    </span>
                    <span v-else>—</span>
                  </p>
                </div>
                <div>
                  <p class="text-xs font-semibold uppercase tracking-wide text-slate-400 md:hidden">Đơn vị</p>
                  <p class="text-sm text-center text-slate-700">{{ template.unit || '—' }}</p>
                </div>
                <div class="flex items-center justify-end gap-1.5 pr-4">
                  <button
                    type="button"
                    class="inline-flex items-center gap-1 rounded-full border border-sky-200 bg-white px-3.5 py-1.5 text-[11px] font-semibold uppercase tracking-wide text-sky-600 shadow-sm transition hover:border-sky-300 hover:bg-sky-50 whitespace-nowrap"
                    @click="openDetailModal(template)"
                  >
                    <svg xmlns="http://www.w3.org/2000/svg" class="h-3.5 w-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.8">
                      <path stroke-linecap="round" stroke-linejoin="round" d="M12 5c-7 0-9 7-9 7s2 7 9 7 9-7 9-7-2-7-9-7Zm0 4a3 3 0 1 1 0 6 3 3 0 0 1 0-6Z" />
                    </svg>
                    Chi tiết
                  </button>
                  <button
                    type="button"
                    class="inline-flex items-center gap-1 rounded-full border border-emerald-200 bg-white px-2.5 py-1.5 text-[11px] font-semibold uppercase tracking-wide text-emerald-600 shadow-sm transition hover:border-emerald-300 hover:bg-emerald-50 whitespace-nowrap"
                    @click="openEditModal(template)"
                  >
                    <svg xmlns="http://www.w3.org/2000/svg" class="h-3.5 w-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.8">
                      <path stroke-linecap="round" stroke-linejoin="round" d="m16.862 4.487 1.95 1.95M7.5 20.5l-4-4L16 4l4 4-12.5 12.5Z" />
                    </svg>
                    Sửa
                  </button>
                  <button
                    type="button"
                    class="inline-flex items-center gap-1 rounded-full border border-rose-200 bg-white px-2.5 py-1.5 text-[11px] font-semibold uppercase tracking-wide text-rose-600 shadow-sm transition hover:border-rose-300 hover:bg-rose-50 whitespace-nowrap"
                    @click="confirmDelete(template)"
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
          <span>Đang hiển thị {{ templates.length }} / {{ totalElements }} mẫu chỉ số</span>
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

    <!-- Form Modal (Thêm/Sửa) -->
    <Transition name="fade">
      <div
        v-if="formModalOpen"
        class="fixed inset-0 z-[90] flex items-center justify-center bg-slate-900/50 backdrop-blur-sm px-4"
      >
        <div class="w-full max-w-2xl rounded-3xl border border-emerald-100 bg-white p-6 shadow-xl max-h-[90vh] overflow-y-auto">
          <div class="flex items-start justify-between">
            <div>
              <p class="text-xs font-semibold uppercase tracking-[0.35em] text-emerald-500">
                {{ modalMode === 'create' ? 'Thêm mẫu' : 'Chỉnh sửa mẫu' }}
              </p>
              <h2 class="mt-1 text-xl font-semibold text-slate-900">
                {{ modalMode === 'create' ? 'Tạo mẫu chỉ số mới' : 'Cập nhật mẫu chỉ số' }}
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
                <label class="text-xs font-semibold uppercase tracking-wide text-slate-500" for="template-code">
                  Mã chỉ số *
                </label>
                <input
                  id="template-code"
                  v-model="formState.code"
                  type="text"
                  autocomplete="off"
                  class="w-full rounded-xl border border-slate-200 px-4 py-2 text-sm text-slate-700 shadow-sm transition focus:border-emerald-400 focus:outline-none focus:ring-4 focus:ring-emerald-100/80"
                  placeholder="VD: WBC"
                  maxlength="60"
                />
              </div>
              <div class="space-y-1.5">
                <label class="text-xs font-semibold uppercase tracking-wide text-slate-500" for="template-name">
                  Tên chỉ số *
                </label>
                <input
                  id="template-name"
                  v-model="formState.name"
                  type="text"
                  class="w-full rounded-xl border border-slate-200 px-4 py-2 text-sm text-slate-700 shadow-sm transition focus:border-emerald-400 focus:outline-none focus:ring-4 focus:ring-emerald-100/80"
                  placeholder="Bạch cầu"
                  maxlength="150"
                />
              </div>
            </div>

            <div class="space-y-1.5">
              <label class="text-xs font-semibold uppercase tracking-wide text-slate-500" for="template-unit">
                Đơn vị
              </label>
              <input
                id="template-unit"
                v-model="formState.unit"
                type="text"
                class="w-full rounded-xl border border-slate-200 px-4 py-2 text-sm text-slate-700 shadow-sm transition focus:border-emerald-400 focus:outline-none focus:ring-4 focus:ring-emerald-100/80"
                placeholder="VD: 10^9/L"
                maxlength="30"
              />
            </div>

            <div class="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
              <div class="space-y-1.5">
                <label class="text-xs font-semibold uppercase tracking-wide text-slate-500" for="template-normal-min">
                  Khoảng chuẩn Min
                </label>
                <input
                  id="template-normal-min"
                  v-model="formState.normalMin"
                  type="number"
                  step="0.0001"
                  class="w-full rounded-xl border border-slate-200 px-4 py-2 text-sm text-slate-700 shadow-sm transition focus:border-emerald-400 focus:outline-none focus:ring-4 focus:ring-emerald-100/80"
                  placeholder="VD: 4.0"
                />
              </div>
              <div class="space-y-1.5">
                <label class="text-xs font-semibold uppercase tracking-wide text-slate-500" for="template-normal-max">
                  Khoảng chuẩn Max
                </label>
                <input
                  id="template-normal-max"
                  v-model="formState.normalMax"
                  type="number"
                  step="0.0001"
                  class="w-full rounded-xl border border-slate-200 px-4 py-2 text-sm text-slate-700 shadow-sm transition focus:border-emerald-400 focus:outline-none focus:ring-4 focus:ring-emerald-100/80"
                  placeholder="VD: 10.0"
                />
              </div>
              <div class="space-y-1.5">
                <label class="text-xs font-semibold uppercase tracking-wide text-slate-500" for="template-critical-min">
                  Cảnh báo Min
                </label>
                <input
                  id="template-critical-min"
                  v-model="formState.criticalMin"
                  type="number"
                  step="0.0001"
                  class="w-full rounded-xl border border-slate-200 px-4 py-2 text-sm text-slate-700 shadow-sm transition focus:border-emerald-400 focus:outline-none focus:ring-4 focus:ring-emerald-100/80"
                  placeholder="VD: 2.0"
                />
              </div>
              <div class="space-y-1.5">
                <label class="text-xs font-semibold uppercase tracking-wide text-slate-500" for="template-critical-max">
                  Cảnh báo Max
                </label>
                <input
                  id="template-critical-max"
                  v-model="formState.criticalMax"
                  type="number"
                  step="0.0001"
                  class="w-full rounded-xl border border-slate-200 px-4 py-2 text-sm text-slate-700 shadow-sm transition focus:border-emerald-400 focus:outline-none focus:ring-4 focus:ring-emerald-100/80"
                  placeholder="VD: 15.0"
                />
              </div>
            </div>

            <div class="space-y-1.5">
              <label class="text-xs font-semibold uppercase tracking-wide text-slate-500" for="template-category">
                Danh mục
              </label>
              <input
                id="template-category"
                v-model="formState.category"
                type="text"
                class="w-full rounded-xl border border-slate-200 px-4 py-2 text-sm text-slate-700 shadow-sm transition focus:border-emerald-400 focus:outline-none focus:ring-4 focus:ring-emerald-100/80"
                placeholder="VD: Huyết học"
                maxlength="50"
              />
            </div>

            <div class="space-y-1.5">
              <label class="text-xs font-semibold uppercase tracking-wide text-slate-500" for="template-note">
                Ghi chú tham chiếu
              </label>
              <textarea
                id="template-note"
                v-model="formState.referenceNote"
                rows="3"
                class="w-full rounded-xl border border-slate-200 px-4 py-2 text-sm text-slate-700 shadow-sm transition focus:border-emerald-400 focus:outline-none focus:ring-4 focus:ring-emerald-100/80"
                placeholder="Ghi chú về giá trị tham chiếu..."
                maxlength="500"
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
                <span>{{ modalMode === 'create' ? 'Thêm mẫu' : 'Lưu thay đổi' }}</span>
              </button>
            </div>
          </form>
        </div>
      </div>
    </Transition>

    <!-- Detail Modal -->
    <Transition name="fade">
      <div
        v-if="detailModalOpen && detailTemplate"
        class="fixed inset-0 z-[90] flex items-center justify-center bg-slate-900/50 backdrop-blur-sm px-4"
      >
        <div class="w-full max-w-2xl rounded-3xl border border-emerald-100 bg-white p-6 shadow-xl max-h-[90vh] overflow-y-auto">
          <div class="flex items-start justify-between">
            <div>
              <p class="text-xs font-semibold uppercase tracking-[0.35em] text-emerald-500">Chi tiết mẫu chỉ số</p>
              <h2 class="mt-2 text-xl font-semibold text-slate-900">{{ detailTemplate.name }}</h2>
              <p class="text-sm text-slate-500">Mã: {{ detailTemplate.code }}</p>
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
              <span class="w-36 shrink-0 text-xs font-semibold uppercase tracking-wide text-slate-500">Mã chỉ số</span>
              <span class="font-semibold text-slate-900">{{ detailTemplate.code }}</span>
            </div>
            <div class="flex items-start gap-3">
              <span class="w-36 shrink-0 text-xs font-semibold uppercase tracking-wide text-slate-500">Tên chỉ số</span>
              <span>{{ detailTemplate.name }}</span>
            </div>
            <div class="flex items-start gap-3">
              <span class="w-36 shrink-0 text-xs font-semibold uppercase tracking-wide text-slate-500">Đơn vị</span>
              <span>{{ detailTemplate.unit || '—' }}</span>
            </div>
            <div class="flex items-start gap-3">
              <span class="w-36 shrink-0 text-xs font-semibold uppercase tracking-wide text-slate-500">Khoảng chuẩn</span>
              <span>{{ formatNumber(detailTemplate.normalMin) }} - {{ formatNumber(detailTemplate.normalMax) }}</span>
            </div>
            <div class="flex items-start gap-3">
              <span class="w-36 shrink-0 text-xs font-semibold uppercase tracking-wide text-slate-500">Cảnh báo thấp</span>
              <span>{{ formatNumber(detailTemplate.criticalMin) }}</span>
            </div>
            <div class="flex items-start gap-3">
              <span class="w-36 shrink-0 text-xs font-semibold uppercase tracking-wide text-slate-500">Cảnh báo cao</span>
              <span>{{ formatNumber(detailTemplate.criticalMax) }}</span>
            </div>
            <div class="flex items-start gap-3">
              <span class="w-36 shrink-0 text-xs font-semibold uppercase tracking-wide text-slate-500">Danh mục</span>
              <span>{{ detailTemplate.category || '—' }}</span>
            </div>
            <div class="flex items-start gap-3">
              <span class="w-36 shrink-0 text-xs font-semibold uppercase tracking-wide text-slate-500">Ghi chú</span>
              <span class="whitespace-pre-line text-slate-600">{{ detailTemplate.referenceNote || '—' }}</span>
            </div>
            <div class="flex items-start gap-3">
              <span class="w-36 shrink-0 text-xs font-semibold uppercase tracking-wide text-slate-500">Tạo lúc</span>
              <span>{{ formatDateTime(detailTemplate.createdAt) }}</span>
            </div>
            <div class="flex items-start gap-3">
              <span class="w-36 shrink-0 text-xs font-semibold uppercase tracking-wide text-slate-500">Cập nhật</span>
              <span>{{ formatDateTime(detailTemplate.updatedAt) }}</span>
            </div>
          </div>

          <div class="mt-6 border-t border-slate-100 pt-6">
            <p class="text-xs font-semibold uppercase tracking-wide text-slate-500">Đang liên kết với các Dịch vụ Y tế:</p>
            <div v-if="loadingMappings" class="mt-4 flex items-center gap-2 text-sm text-slate-600">
              <svg class="h-4 w-4 animate-spin" viewBox="0 0 24 24" fill="none">
                <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
                <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 0 1 8-8v4l3.5-3.5L12 1v4a7 7 0 0 0-7 7H4z"></path>
              </svg>
              Đang tải...
            </div>
            <div v-else-if="detailMappings.length === 0" class="mt-4 rounded-xl border border-dashed border-slate-200 bg-slate-50/50 px-4 py-3 text-sm text-slate-600">
              Chưa liên kết với dịch vụ nào.
            </div>
            <ul v-else class="mt-4 space-y-2">
              <li
                v-for="mapping in detailMappings"
                :key="mapping.id"
                class="rounded-xl border border-slate-200 bg-slate-50/30 px-4 py-2.5 text-sm text-slate-700"
              >
                <span class="font-semibold text-slate-900">{{ mapping.medicalService?.name || 'N/A' }}</span>
                <span class="text-slate-500"> ({{ mapping.medicalService?.code || 'N/A' }})</span>
              </li>
            </ul>
          </div>
        </div>
      </div>
    </Transition>

    <!-- Delete Confirmation Modal -->
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
              <h3 class="text-lg font-semibold text-slate-900">Xóa mẫu chỉ số?</h3>
              <p class="mt-1 text-sm text-slate-600">
                Bạn sắp xóa mẫu <span class="font-semibold text-rose-600">{{ deleteTarget.name }}</span>. Hành động này không thể hoàn tác.
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
              <span>{{ deleting ? 'Đang xóa...' : 'Xóa mẫu' }}</span>
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

