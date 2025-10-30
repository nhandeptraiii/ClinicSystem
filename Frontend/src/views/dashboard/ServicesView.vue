<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue';
import { useRouter } from 'vue-router';
import AdminHeader from '@/components/AdminHeader.vue';
import { useAuthStore } from '@/stores/authStore';
import { useToast, type ToastType } from '@/composables/useToast';
import {
  createMedicalService,
  createServiceIndicatorMapping,
  deleteMedicalService,
  deleteServiceIndicatorMapping,
  fetchServiceIndicatorMappings,
  fetchMedicalServicePage,
  type MedicalService,
  type ServiceIndicatorMapping,
  type MedicalServicePayload,
  updateMedicalService,
  updateServiceIndicatorMapping,
} from '@/services/medicalService.service';
import { fetchClinicRooms, type ClinicRoom } from '@/services/clinicRoom.service';
import { fetchIndicatorTemplates, type IndicatorTemplate } from '@/services/indicatorTemplate.service';

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

const services = ref<MedicalService[]>([]);
const loading = ref(false);
const error = ref<string | null>(null);

const PAGE_SIZE = 10;
const currentPage = ref(1);
const totalPages = ref(1);
const totalElements = ref(0);
const hasNext = ref(false);
const hasPrevious = ref(false);

const searchTerm = ref('');
let filterTimer: ReturnType<typeof setTimeout> | null = null;

const formModalOpen = ref(false);
const modalSubmitting = ref(false);
const modalMode = ref<'create' | 'edit'>('create');
const formError = ref<string | null>(null);
const formState = reactive<MedicalServicePayload>({
  code: '',
  name: '',
  basePrice: 0,
  clinicRoomId: 0,
  description: null,
  category: null,
  status: null,
  estimatedDuration: null,
});
const selectedService = ref<MedicalService | null>(null);

const clinicRooms = ref<ClinicRoom[]>([]);
const loadingRooms = ref(false);

const detailModalOpen = ref(false);
const detailService = ref<MedicalService | null>(null);
const detailMappings = ref<ServiceIndicatorMapping[]>([]);
const loadingDetailMappings = ref(false);

const deleteTarget = ref<MedicalService | null>(null);
const deleting = ref(false);

// Template management
const availableTemplates = ref<IndicatorTemplate[]>([]);
const loadingTemplates = ref(false);
const templateSelectorOpen = ref(false);
const templateSearchTerm = ref('');
const selectedTemplateIds = ref<Set<number>>(new Set());

// Mappings for current service
type MappingFormState = {
  id?: number;
  templateId: number;
  template: IndicatorTemplate;
  required: boolean;
  isNew?: boolean;
  isDeleted?: boolean;
};

const mappingsList = ref<MappingFormState[]>([]);
const originalMappings = ref<ServiceIndicatorMapping[]>([]);

const formatCurrency = (value: number) => {
  return new Intl.NumberFormat('vi-VN').format(value) + ' đồng';
};

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

const loadServices = async () => {
  loading.value = true;
  error.value = null;
  const keyword = searchTerm.value.trim();
  const pageIndex = Math.max(currentPage.value - 1, 0);
  try {
    const response = await fetchMedicalServicePage({
      page: pageIndex,
      size: PAGE_SIZE,
      keyword: keyword ? keyword : undefined,
    });
    services.value = response.items ?? [];
    totalElements.value = response.totalElements ?? services.value.length;
    const derivedTotalPages = response.totalPages && response.totalPages > 0 ? response.totalPages : 1;
    totalPages.value = derivedTotalPages;
    currentPage.value = response.totalPages && response.totalPages > 0 ? response.page + 1 : 1;
    hasNext.value = response.hasNext ?? false;
    hasPrevious.value = response.hasPrevious ?? false;
  } catch (err) {
    error.value = extractErrorMessage(err);
    services.value = [];
    totalElements.value = 0;
    totalPages.value = 1;
    currentPage.value = 1;
    hasNext.value = false;
    hasPrevious.value = false;
  } finally {
    loading.value = false;
  }
};

const loadClinicRooms = async () => {
  loadingRooms.value = true;
  try {
    const rooms = await fetchClinicRooms();
    clinicRooms.value = rooms ?? [];
  } catch (err) {
    console.error('Không thể tải danh sách phòng khám:', err);
    clinicRooms.value = [];
  } finally {
    loadingRooms.value = false;
  }
};

const loadTemplates = async () => {
  loadingTemplates.value = true;
  try {
    const templates = await fetchIndicatorTemplates({ activeOnly: true });
    availableTemplates.value = templates ?? [];
  } catch (err) {
    console.error('Không thể tải danh sách template:', err);
    availableTemplates.value = [];
  } finally {
    loadingTemplates.value = false;
  }
};

const filteredTemplates = computed(() => {
  const keyword = templateSearchTerm.value.trim().toLowerCase();
  if (!keyword) {
    return availableTemplates.value;
  }
  return availableTemplates.value.filter(
    (template) =>
      template.code?.toLowerCase().includes(keyword) ||
      template.name?.toLowerCase().includes(keyword) ||
      template.category?.toLowerCase().includes(keyword),
  );
});

const resetMappingsList = () => {
  mappingsList.value = [];
  originalMappings.value = [];
  selectedTemplateIds.value.clear();
};

const loadMappingsForEdit = async (serviceId: number) => {
  try {
    const mappings = await fetchServiceIndicatorMappings(serviceId);
    originalMappings.value = mappings;
    mappingsList.value = mappings.map((mapping) => ({
      id: mapping.id,
      templateId: mapping.indicatorTemplate.id,
      template: mapping.indicatorTemplate,
      required: mapping.required ?? true,
      isNew: false,
      isDeleted: false,
    }));
    selectedTemplateIds.value = new Set(mappings.map((m) => m.indicatorTemplate.id));
  } catch (err) {
    console.error('Không thể tải mappings:', err);
    resetMappingsList();
  }
};

const openTemplateSelectorModal = async () => {
  await loadTemplates();
  templateSelectorOpen.value = true;
  templateSearchTerm.value = '';
};

const closeTemplateSelectorModal = () => {
  templateSelectorOpen.value = false;
};

const isTemplateSelected = (templateId: number) => {
  return selectedTemplateIds.value.has(templateId);
};

const toggleTemplate = (template: IndicatorTemplate) => {
  if (isTemplateSelected(template.id)) {
    // Bỏ chọn - đánh dấu deleted hoặc xóa khỏi list
    const existingMapping = mappingsList.value.find((m) => m.templateId === template.id);
    if (existingMapping) {
      if (existingMapping.isNew) {
        // Xóa luôn nếu là mapping mới
        mappingsList.value = mappingsList.value.filter((m) => m.templateId !== template.id);
      } else {
        // Đánh dấu deleted nếu là mapping đã có
        existingMapping.isDeleted = true;
      }
    }
    selectedTemplateIds.value.delete(template.id);
  } else {
    // Chọn - thêm vào list
    const deletedMapping = mappingsList.value.find(
      (m) => m.templateId === template.id && m.isDeleted,
    );
    if (deletedMapping) {
      // Phục hồi mapping đã bị deleted
      deletedMapping.isDeleted = false;
    } else {
      // Thêm mapping mới
      mappingsList.value.push({
        templateId: template.id,
        template: template,
        required: true,
        isNew: true,
        isDeleted: false,
      });
    }
    selectedTemplateIds.value.add(template.id);
  }
};

const confirmTemplateSelection = () => {
  closeTemplateSelectorModal();
};

const removeMapping = (templateId: number) => {
  const mapping = mappingsList.value.find((m) => m.templateId === templateId);
  if (!mapping) return;

  if (mapping.isNew) {
    mappingsList.value = mappingsList.value.filter((m) => m.templateId !== templateId);
  } else {
    mapping.isDeleted = true;
  }
  selectedTemplateIds.value.delete(templateId);
};

const saveMappings = async (serviceId: number) => {
  const activeMappings = mappingsList.value.filter((m) => !m.isDeleted);

  for (const mapping of activeMappings) {
    if (mapping.isNew) {
      await createServiceIndicatorMapping(serviceId, {
        indicatorTemplateId: mapping.templateId,
        required: mapping.required,
        displayOrder: 0,
      });
    } else if (mapping.id) {
      const original = originalMappings.value.find((orig) => orig.id === mapping.id);
      if (original && original.required !== mapping.required) {
        await updateServiceIndicatorMapping(serviceId, mapping.id, {
          required: mapping.required,
        });
      }
    }
  }

  const deletedMappings = mappingsList.value.filter((m) => m.isDeleted && m.id);
  for (const mapping of deletedMappings) {
    if (mapping.id) {
      await deleteServiceIndicatorMapping(serviceId, mapping.id);
    }
  }
};

const openCreateModal = async () => {
  modalMode.value = 'create';
  selectedService.value = null;
  formError.value = null;
  formState.code = '';
  formState.name = '';
  formState.basePrice = 0;
  formState.clinicRoomId = 0;
  formState.description = null;
  formState.category = null;
  formState.status = null;
  formState.estimatedDuration = null;
  resetMappingsList();
  await loadClinicRooms();
  formModalOpen.value = true;
};

const openEditModal = async (service: MedicalService) => {
  modalMode.value = 'edit';
  selectedService.value = service;
  formError.value = null;
  formState.code = service.code ?? '';
  formState.name = service.name ?? '';
  formState.basePrice = service.basePrice ?? 0;
  formState.clinicRoomId = service.clinicRoom?.id ?? 0;
  formState.description = service.description ?? null;
  formState.category = service.category ?? null;
  formState.status = service.status ?? null;
  formState.estimatedDuration = service.estimatedDuration ?? null;
  await loadClinicRooms();
  await loadMappingsForEdit(service.id);
  formModalOpen.value = true;
};

const closeFormModal = () => {
  formModalOpen.value = false;
  formError.value = null;
  resetMappingsList();
};

const submitForm = async () => {
  if (modalSubmitting.value) return;

  const payload: MedicalServicePayload = {
    code: formState.code?.trim() ?? '',
    name: formState.name?.trim() ?? '',
    basePrice: formState.basePrice ?? 0,
    clinicRoomId: formState.clinicRoomId ?? 0,
    description: formState.description?.trim() || null,
    category: formState.category?.trim() || null,
    status: formState.status?.trim() || null,
    estimatedDuration: formState.estimatedDuration ?? null,
  };

  if (!payload.code || !payload.name) {
    formError.value = 'Vui lòng nhập đầy đủ mã dịch vụ và tên dịch vụ.';
    return;
  }

  if (payload.basePrice <= 0) {
    formError.value = 'Đơn giá phải lớn hơn 0.';
    return;
  }

  if (!payload.clinicRoomId || payload.clinicRoomId <= 0) {
    formError.value = 'Vui lòng chọn phòng thực hiện.';
    return;
  }

  modalSubmitting.value = true;
  try {
    let serviceId: number;

    if (modalMode.value === 'create') {
      const created = await createMedicalService(payload);
      serviceId = created.id;
      showToast('success', 'Đã thêm dịch vụ y tế mới.');
    } else if (selectedService.value) {
      await updateMedicalService(selectedService.value.id, payload);
      serviceId = selectedService.value.id;
      showToast('success', 'Đã cập nhật dịch vụ y tế.');
    } else {
      throw new Error('Không tìm thấy dịch vụ để cập nhật.');
    }

    await saveMappings(serviceId);

    closeFormModal();
    await loadServices();
  } catch (error) {
    formError.value = extractErrorMessage(error);
    showToast('error', formError.value ?? 'Không thể lưu thông tin dịch vụ y tế.');
  } finally {
    modalSubmitting.value = false;
  }
};

const openDetailModal = async (service: MedicalService) => {
  detailService.value = service;
  detailMappings.value = [];
  loadingDetailMappings.value = true;
  detailModalOpen.value = true;
  try {
    const mappings = await fetchServiceIndicatorMappings(service.id);
    detailMappings.value = mappings;
  } catch (err) {
    console.error('Không thể tải mappings:', err);
    detailMappings.value = [];
  } finally {
    loadingDetailMappings.value = false;
  }
};

const closeDetailModal = () => {
  detailModalOpen.value = false;
  detailMappings.value = [];
};

const confirmDelete = (service: MedicalService) => {
  deleteTarget.value = service;
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
    await deleteMedicalService(deleteTarget.value.id);
    showToast('success', 'Đã xóa dịch vụ y tế.');
    const shouldGoPrev = services.value.length <= 1 && currentPage.value > 1;
    deleteTarget.value = null;
    deleting.value = false;
    if (shouldGoPrev) {
      currentPage.value = currentPage.value - 1;
    }
    await loadServices();
  } catch (error) {
    deleting.value = false;
    showToast('error', extractErrorMessage(error));
  }
};

const goToPage = (page: number) => {
  const target = Math.min(Math.max(page, 1), totalPages.value || 1);
  if (target === currentPage.value) {
    return;
  }
  currentPage.value = target;
  loadServices();
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

watch(
  searchTerm,
  () => {
    if (filterTimer) {
      clearTimeout(filterTimer);
    }
    filterTimer = setTimeout(() => {
      currentPage.value = 1;
      loadServices();
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
  loadServices();
});
</script>

<template>
  <div class="relative min-h-screen bg-gradient-to-br from-emerald-50 via-white to-white">
    <AdminHeader :user-name="userName" @sign-out="handleSignOut" />

    <main class="mx-auto w-full max-w-6xl px-4 pb-20 pt-10 sm:px-6 lg:px-8">
      <section>
        <div class="flex flex-col gap-4 border-b border-emerald-100 pb-6 md:flex-row md:items-center md:justify-between">
          <div>
            <p class="text-xs font-semibold uppercase tracking-[0.35em] text-emerald-500">Quản lý dịch vụ</p>
            <h1 class="mt-2 text-2xl font-semibold text-slate-900">Danh sách dịch vụ y tế</h1>
            <p class="mt-1 text-sm text-slate-600">
              Theo dõi, cập nhật thông tin dịch vụ và chỉ số liên quan trong hệ thống.
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
            Thêm dịch vụ
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
              placeholder="Tìm theo mã, tên dịch vụ hoặc phòng..."
            />
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
            class="hidden rounded-2xl border border-emerald-100 bg-emerald-50/70 px-4 py-3 text-xs font-semibold uppercase tracking-wide text-emerald-700 md:grid md:grid-cols-[120px_minmax(0,300px)_140px_minmax(0,1fr)_200px] md:gap-6"
          >
            <span>Mã Dịch Vụ</span>
            <span>Tên Dịch Vụ</span>
            <span>Đơn Giá</span>
            <span>Phòng Thực Hiện</span>
            <span class="flex justify-end pr-14">Thao tác</span>
          </div>

          <template v-if="loading">
            <div
              v-for="skeleton in 4"
              :key="`service-skeleton-${skeleton}`"
              class="mt-3 animate-pulse rounded-2xl border border-slate-100 bg-white px-4 py-4 shadow-sm"
            >
              <div class="grid gap-6 md:grid-cols-[120px_minmax(0,300px)_140px_minmax(0,1fr)_200px] md:items-center">
                <div class="h-4 rounded-full bg-slate-200/70"></div>
                <div class="h-4 rounded-full bg-slate-200/60"></div>
                <div class="h-4 rounded-full bg-slate-200/50"></div>
                <div class="h-4 rounded-full bg-slate-200/60"></div>
                <div class="ml-auto h-8 w-24 rounded-full bg-slate-200/70"></div>
              </div>
            </div>
          </template>
          <template v-else-if="services.length === 0">
            <div class="mt-8 rounded-3xl border border-dashed border-emerald-200 bg-emerald-50/40 p-10 text-center text-emerald-700">
              <svg xmlns="http://www.w3.org/2000/svg" class="mx-auto h-12 w-12 text-emerald-400" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.6">
                <path stroke-linecap="round" stroke-linejoin="round" d="M5 13l4 4L19 7" />
              </svg>
              <h3 class="mt-4 text-lg font-semibold">Chưa có dịch vụ y tế nào phù hợp</h3>
              <p class="mt-2 text-sm text-emerald-600/80">Thử thay đổi bộ lọc hoặc tạo mới dịch vụ.</p>
            </div>
          </template>
          <template v-else>
            <div
              v-for="service in services"
              :key="service.id"
              class="mt-3 rounded-2xl border border-slate-200 bg-white px-4 py-4 shadow-sm transition hover:border-emerald-200 hover:shadow-md"
            >
              <div class="grid gap-6 md:grid-cols-[120px_minmax(0,300px)_140px_minmax(0,1fr)_200px] md:items-center">
                <div>
                  <p class="text-xs font-semibold uppercase tracking-wide text-slate-400 md:hidden">Mã Dịch Vụ</p>
                  <p class="text-sm font-semibold text-slate-900">{{ service.code }}</p>
                </div>
                <div>
                  <p class="text-xs font-semibold uppercase tracking-wide text-slate-400 md:hidden">Tên Dịch Vụ</p>
                  <p class="text-sm text-slate-700">{{ service.name }}</p>
                </div>
                <div>
                  <p class="text-xs font-semibold uppercase tracking-wide text-slate-400 md:hidden">Đơn Giá</p>
                  <p class="text-sm font-semibold text-emerald-600">{{ formatCurrency(service.basePrice) }}</p>
                </div>
                <div>
                  <p class="text-xs font-semibold uppercase tracking-wide text-slate-400 md:hidden">Phòng Thực Hiện</p>
                  <p class="text-sm text-slate-700">
                    {{ service.clinicRoom?.name || service.clinicRoom?.code || '—' }}
                  </p>
                </div>
                <div class="flex flex-nowrap items-center justify-start gap-1.5 md:justify-end">
                  <button
                    type="button"
                    class="inline-flex items-center gap-1 rounded-full border border-sky-200 bg-white px-2.5 py-1.5 text-[11px] font-semibold uppercase tracking-wide text-sky-600 shadow-sm transition hover:border-sky-300 hover:bg-sky-50 whitespace-nowrap"
                    @click="openDetailModal(service)"
                  >
                    <svg xmlns="http://www.w3.org/2000/svg" class="h-3.5 w-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.8">
                      <path stroke-linecap="round" stroke-linejoin="round" d="M12 5c-7 0-9 7-9 7s2 7 9 7 9-7 9-7-2-7-9-7Zm0 4a3 3 0 1 1 0 6 3 3 0 0 1 0-6Z" />
                    </svg>
                    Chi tiết
                  </button>
                  <button
                    type="button"
                    class="inline-flex items-center gap-1 rounded-full border border-emerald-200 bg-white px-2.5 py-1.5 text-[11px] font-semibold uppercase tracking-wide text-emerald-600 shadow-sm transition hover:border-emerald-300 hover:bg-emerald-50 whitespace-nowrap"
                    @click="openEditModal(service)"
                  >
                    <svg xmlns="http://www.w3.org/2000/svg" class="h-3.5 w-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.8">
                      <path stroke-linecap="round" stroke-linejoin="round" d="m16.862 4.487 1.95 1.95M7.5 20.5l-4-4L16 4l4 4-12.5 12.5Z" />
                    </svg>
                    Sửa
                  </button>
                  <button
                    type="button"
                    class="inline-flex items-center gap-1 rounded-full border border-rose-200 bg-white px-2.5 py-1.5 text-[11px] font-semibold uppercase tracking-wide text-rose-600 shadow-sm transition hover:border-rose-300 hover:bg-rose-50 whitespace-nowrap"
                    @click="confirmDelete(service)"
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
          <span>Đang hiển thị {{ services.length }} / {{ totalElements }} dịch vụ y tế</span>
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

    <!-- Modal Thêm/Sửa Dịch Vụ -->
    <Transition name="fade">
      <div
        v-if="formModalOpen"
        class="fixed inset-0 z-[90] flex items-center justify-center bg-slate-900/50 backdrop-blur-sm px-4"
      >
        <div class="w-full max-w-4xl max-h-[90vh] overflow-y-auto rounded-3xl border border-emerald-100 bg-white p-6 shadow-xl">
          <div class="flex items-start justify-between">
            <div>
              <p class="text-xs font-semibold uppercase tracking-[0.35em] text-emerald-500">
                {{ modalMode === 'create' ? 'Thêm dịch vụ' : 'Chỉnh sửa dịch vụ' }}
              </p>
              <h2 class="mt-1 text-xl font-semibold text-slate-900">
                {{ modalMode === 'create' ? 'Tạo dịch vụ y tế mới' : 'Cập nhật thông tin dịch vụ' }}
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

          <form class="mt-6 space-y-6" @submit.prevent="submitForm">
            <div class="rounded-2xl border border-slate-200 bg-slate-50/50 p-5">
              <h3 class="text-sm font-semibold text-slate-700 mb-4">Thông tin cơ bản</h3>
              <div class="grid gap-4 sm:grid-cols-2">
                <div class="space-y-1.5">
                  <label class="text-xs font-semibold uppercase tracking-wide text-slate-500" for="service-code">
                    Mã dịch vụ *
                  </label>
                  <input
                    id="service-code"
                    v-model="formState.code"
                    type="text"
                    autocomplete="off"
                    class="w-full rounded-xl border border-slate-200 px-4 py-2 text-sm text-slate-700 shadow-sm transition focus:border-emerald-400 focus:outline-none focus:ring-4 focus:ring-emerald-100/80"
                    placeholder="VD: DV-001"
                    maxlength="80"
                  />
                </div>
                <div class="space-y-1.5">
                  <label class="text-xs font-semibold uppercase tracking-wide text-slate-500" for="service-name">
                    Tên dịch vụ *
                  </label>
                  <input
                    id="service-name"
                    v-model="formState.name"
                    type="text"
                    class="w-full rounded-xl border border-slate-200 px-4 py-2 text-sm text-slate-700 shadow-sm transition focus:border-emerald-400 focus:outline-none focus:ring-4 focus:ring-emerald-100/80"
                    placeholder="Xét nghiệm tổng quát"
                    maxlength="150"
                  />
                </div>
              </div>
              <div class="grid gap-4 sm:grid-cols-2 mt-4">
                <div class="space-y-1.5">
                  <label class="text-xs font-semibold uppercase tracking-wide text-slate-500" for="service-price">
                    Đơn giá *
                  </label>
                  <input
                    id="service-price"
                    v-model.number="formState.basePrice"
                    type="number"
                    min="0"
                    step="1000"
                    class="w-full rounded-xl border border-slate-200 px-4 py-2 text-sm text-slate-700 shadow-sm transition focus:border-emerald-400 focus:outline-none focus:ring-4 focus:ring-emerald-100/80"
                    placeholder="150000"
                  />
                </div>
                <div class="space-y-1.5">
                  <label class="text-xs font-semibold uppercase tracking-wide text-slate-500" for="service-room">
                    Phòng thực hiện *
                  </label>
                  <select
                    id="service-room"
                    v-model.number="formState.clinicRoomId"
                    class="w-full rounded-xl border border-slate-200 px-4 py-2 text-sm text-slate-700 shadow-sm transition focus:border-emerald-400 focus:outline-none focus:ring-4 focus:ring-emerald-100/80"
                  >
                    <option :value="0">Chọn phòng khám</option>
                    <option v-for="room in clinicRooms" :key="room.id" :value="room.id">
                      {{ room.name }} ({{ room.code }})
                    </option>
                  </select>
                </div>
              </div>
            </div>

            <div class="rounded-2xl border border-slate-200 bg-slate-50/50 p-5">
              <div class="flex items-center justify-between mb-4">
                <h3 class="text-sm font-semibold text-slate-700">Chỉ số liên quan</h3>
                <button
                  type="button"
                  class="inline-flex items-center gap-1.5 rounded-full bg-sky-600 px-4 py-1.5 text-xs font-semibold uppercase tracking-wide text-white shadow-sm transition hover:bg-sky-700"
                  @click="openTemplateSelectorModal"
                >
                  <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" class="h-3.5 w-3.5" fill="none" stroke="currentColor" stroke-width="2">
                    <path stroke-linecap="round" stroke-linejoin="round" d="M12 5v14m7-7H5" />
                  </svg>
                  Chọn chỉ số
                </button>
              </div>

              <div v-if="mappingsList.filter(m => !m.isDeleted).length === 0" class="text-center py-6 text-sm text-slate-500">
                Chưa có chỉ số nào. Nhấn "Chọn chỉ số" để thêm.
              </div>

              <div v-else class="space-y-3">
                <div
                  v-for="(mapping, index) in mappingsList"
                  :key="index"
                  v-show="!mapping.isDeleted"
                  class="rounded-xl border border-slate-200 bg-white p-4"
                >
                  <div class="flex items-start justify-between mb-2">
                    <div class="flex-1">
                      <div class="flex items-center gap-2">
                        <span class="text-sm font-semibold text-slate-900">{{ mapping.template.name }}</span>
                        <span class="text-xs text-slate-500">({{ mapping.template.code }})</span>
                        <span v-if="mapping.isNew" class="inline-flex items-center rounded-full bg-sky-100 px-2 py-0.5 text-[10px] font-semibold uppercase tracking-wide text-sky-700">Mới</span>
                      </div>
                      <div class="mt-1 grid grid-cols-2 gap-2 text-xs text-slate-600">
                        <div><span class="font-medium">Đơn vị:</span> {{ mapping.template.unit || '—' }}</div>
                        <div><span class="font-medium">Chuẩn:</span> {{ mapping.template.normalMin ?? '—' }} - {{ mapping.template.normalMax ?? '—' }}</div>
                      </div>
                    </div>
                    <button
                      type="button"
                      class="flex h-7 w-7 items-center justify-center rounded-full border border-rose-200 text-rose-600 transition hover:bg-rose-50"
                      @click="removeMapping(mapping.templateId)"
                    >
                      <svg xmlns="http://www.w3.org/2000/svg" class="h-3.5 w-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">
                        <path stroke-linecap="round" stroke-linejoin="round" d="m15 9-6 6m0-6 6 6" />
                      </svg>
                    </button>
                  </div>
                  <div class="flex items-center gap-2 mt-2">
                    <input
                      :id="`mapping-required-${index}`"
                      v-model="mapping.required"
                      type="checkbox"
                      class="h-4 w-4 rounded border-slate-300 text-emerald-600 focus:ring-2 focus:ring-emerald-100"
                    />
                    <label :for="`mapping-required-${index}`" class="text-xs text-slate-600 cursor-pointer">
                      Chỉ số bắt buộc
                    </label>
                  </div>
                </div>
              </div>
            </div>

            <p
              v-if="formError"
              class="rounded-2xl border border-rose-100 bg-rose-50/90 px-4 py-3 text-sm text-rose-600"
            >
              {{ formError }}
            </p>

            <div class="flex items-center justify-end gap-3">
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
                <span>{{ modalMode === 'create' ? 'Thêm dịch vụ' : 'Lưu thay đổi' }}</span>
              </button>
            </div>
          </form>
        </div>
      </div>
    </Transition>

    <!-- Modal Chọn Template -->
    <Transition name="fade">
      <div
        v-if="templateSelectorOpen"
        class="fixed inset-0 z-[95] flex items-center justify-center bg-slate-900/60 backdrop-blur-sm px-4"
      >
        <div class="w-full max-w-3xl max-h-[80vh] overflow-y-auto rounded-3xl border border-sky-100 bg-white p-6 shadow-xl">
          <div class="flex items-start justify-between mb-4">
            <div>
              <h3 class="text-lg font-semibold text-slate-900">Chọn chỉ số từ danh sách</h3>
              <p class="text-sm text-slate-600 mt-1">Chọn các chỉ số cần thiết cho dịch vụ này</p>
            </div>
            <button
              type="button"
              class="flex h-9 w-9 items-center justify-center rounded-full border border-slate-200 text-slate-500 transition hover:border-slate-300 hover:text-slate-700"
              @click="closeTemplateSelectorModal"
            >
              <svg xmlns="http://www.w3.org/2000/svg" class="h-4.5 w-4.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.8">
                <path stroke-linecap="round" stroke-linejoin="round" d="m16 8-8 8m0-8 8 8" />
              </svg>
            </button>
          </div>

          <div class="relative mb-4">
            <svg class="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" fill="none" stroke="currentColor" stroke-width="1.5" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" d="m21 21-4.35-4.35m0 0A7.35 7.35 0 1 0 6.3 6.3a7.35 7.35 0 0 0 10.35 10.35Z" />
            </svg>
            <input
              v-model.trim="templateSearchTerm"
              type="search"
              class="w-full rounded-full border border-slate-200 bg-white py-2.5 pl-9 pr-4 text-sm text-slate-700 shadow-sm transition focus:border-sky-400 focus:outline-none focus:ring-4 focus:ring-sky-100/80"
              placeholder="Tìm chỉ số theo mã, tên hoặc danh mục..."
            />
          </div>

          <div v-if="loadingTemplates" class="text-center py-8 text-sm text-slate-500">
            Đang tải danh sách chỉ số...
          </div>

          <div v-else-if="filteredTemplates.length === 0" class="text-center py-8 text-sm text-slate-500">
            Không tìm thấy chỉ số phù hợp.
          </div>

          <div v-else class="space-y-2 max-h-[400px] overflow-y-auto">
            <div
              v-for="template in filteredTemplates"
              :key="template.id"
              class="flex items-start gap-3 rounded-xl border border-slate-200 bg-slate-50/50 p-3 transition hover:border-sky-300 hover:bg-sky-50/30 cursor-pointer"
              @click="toggleTemplate(template)"
            >
              <input
                type="checkbox"
                :checked="isTemplateSelected(template.id)"
                class="mt-1 h-4 w-4 rounded border-slate-300 text-sky-600 focus:ring-2 focus:ring-sky-100"
                @click.stop="toggleTemplate(template)"
              />
              <div class="flex-1">
                <div class="flex items-center gap-2">
                  <span class="text-sm font-semibold text-slate-900">{{ template.name }}</span>
                  <span class="text-xs text-slate-500">({{ template.code }})</span>
                  <span v-if="template.category" class="inline-flex items-center rounded-full bg-slate-200 px-2 py-0.5 text-[10px] font-medium text-slate-700">
                    {{ template.category }}
                  </span>
                </div>
                <div class="mt-1 grid grid-cols-3 gap-2 text-xs text-slate-600">
                  <div><span class="font-medium">Đơn vị:</span> {{ template.unit || '—' }}</div>
                  <div><span class="font-medium">Chuẩn:</span> {{ template.normalMin ?? '—' }} - {{ template.normalMax ?? '—' }}</div>
                  <div><span class="font-medium">Cảnh báo:</span> {{ template.criticalMin ?? '—' }} - {{ template.criticalMax ?? '—' }}</div>
                </div>
              </div>
            </div>
          </div>

          <div class="flex items-center justify-end gap-3 mt-6 pt-4 border-t border-slate-200">
            <button
              type="button"
              class="inline-flex items-center gap-2 rounded-full bg-sky-600 px-6 py-2 text-xs font-semibold uppercase tracking-wide text-white shadow-sm transition hover:bg-sky-700"
              @click="confirmTemplateSelection"
            >
              Xác nhận ({{ selectedTemplateIds.size }} chỉ số)
            </button>
          </div>
        </div>
      </div>
    </Transition>

    <!-- Modal Chi Tiết -->
    <Transition name="fade">
      <div
        v-if="detailModalOpen && detailService"
        class="fixed inset-0 z-[90] flex items-center justify-center bg-slate-900/50 backdrop-blur-sm px-4"
      >
        <div class="w-full max-w-3xl max-h-[90vh] overflow-y-auto rounded-3xl border border-emerald-100 bg-white p-6 shadow-xl">
          <div class="flex items-start justify-between">
            <div>
              <p class="text-xs font-semibold uppercase tracking-[0.35em] text-emerald-500">Chi tiết dịch vụ</p>
              <h2 class="mt-2 text-xl font-semibold text-slate-900">{{ detailService.name }}</h2>
              <p class="text-sm text-slate-500">Mã dịch vụ: {{ detailService.code }}</p>
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
              <span class="w-32 shrink-0 text-xs font-semibold uppercase tracking-wide text-slate-500">Mã dịch vụ</span>
              <span class="font-semibold text-slate-900">{{ detailService.code }}</span>
            </div>
            <div class="flex items-start gap-3">
              <span class="w-32 shrink-0 text-xs font-semibold uppercase tracking-wide text-slate-500">Tên dịch vụ</span>
              <span>{{ detailService.name }}</span>
            </div>
            <div class="flex items-start gap-3">
              <span class="w-32 shrink-0 text-xs font-semibold uppercase tracking-wide text-slate-500">Đơn giá</span>
              <span class="font-semibold text-emerald-600">{{ formatCurrency(detailService.basePrice) }}</span>
            </div>
            <div class="flex items-start gap-3">
              <span class="w-32 shrink-0 text-xs font-semibold uppercase tracking-wide text-slate-500">Phòng thực hiện</span>
              <span>{{ detailService.clinicRoom?.name || '—' }}</span>
            </div>
            <div class="flex items-start gap-3">
              <span class="w-32 shrink-0 text-xs font-semibold uppercase tracking-wide text-slate-500">Tạo lúc</span>
              <span>{{ formatDateTime(detailService.createdAt) }}</span>
            </div>
            <div class="flex items-start gap-3">
              <span class="w-32 shrink-0 text-xs font-semibold uppercase tracking-wide text-slate-500">Cập nhật</span>
              <span>{{ formatDateTime(detailService.updatedAt) }}</span>
            </div>
          </div>

          <div class="mt-6 pt-6 border-t border-slate-200">
            <h3 class="text-sm font-semibold text-slate-700 mb-3">Chỉ số liên quan</h3>

            <div v-if="loadingDetailMappings" class="text-center py-4 text-sm text-slate-500">
              Đang tải chỉ số...
            </div>

            <div v-else-if="detailMappings.length === 0" class="text-center py-4 text-sm text-slate-500">
              Không có chỉ số nào.
            </div>

            <div v-else class="space-y-3">
              <div
                v-for="mapping in detailMappings"
                :key="mapping.id"
                class="rounded-xl border border-slate-200 bg-slate-50/50 p-3 text-xs"
              >
                <div class="flex items-center justify-between mb-2">
                  <div>
                    <span class="text-sm font-semibold text-slate-900">{{ mapping.indicatorTemplate.name }}</span>
                    <span class="ml-2 text-xs text-slate-500">({{ mapping.indicatorTemplate.code }})</span>
                  </div>
                  <span
                    class="inline-flex items-center gap-1 rounded-full px-2 py-0.5 text-[10px] font-semibold uppercase tracking-wide"
                    :class="mapping.required ? 'bg-emerald-100 text-emerald-700' : 'bg-slate-100 text-slate-600'"
                  >
                    {{ mapping.required ? 'Bắt buộc' : 'Tùy chọn' }}
                  </span>
                </div>
                <div class="grid gap-2 sm:grid-cols-2 mt-2">
                  <div>
                    <span class="font-medium text-slate-500">Đơn vị:</span>
                    <span class="ml-2 text-slate-700">{{ mapping.indicatorTemplate.unit || '—' }}</span>
                  </div>
                  <div>
                    <span class="font-medium text-slate-500">Chuẩn:</span>
                    <span class="ml-2 text-slate-700">{{ mapping.indicatorTemplate.normalMin ?? '—' }} - {{ mapping.indicatorTemplate.normalMax ?? '—' }}</span>
                  </div>
                </div>
                <div class="grid gap-2 sm:grid-cols-2 mt-1">
                  <div>
                    <span class="font-medium text-slate-500">Cảnh báo:</span>
                    <span class="ml-2 text-slate-700">{{ mapping.indicatorTemplate.criticalMin ?? '—' }} - {{ mapping.indicatorTemplate.criticalMax ?? '—' }}</span>
                  </div>
                </div>
                <div v-if="mapping.indicatorTemplate.referenceNote" class="mt-2">
                  <span class="font-medium text-slate-500">Ghi chú:</span>
                  <p class="mt-1 text-slate-600 whitespace-pre-line">{{ mapping.indicatorTemplate.referenceNote }}</p>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </Transition>

    <!-- Modal Xóa -->
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
              <h3 class="text-lg font-semibold text-slate-900">Xóa dịch vụ y tế?</h3>
              <p class="mt-1 text-sm text-slate-600">
                Bạn sắp xóa dịch vụ <span class="font-semibold text-rose-600">{{ deleteTarget.name }}</span>. Hành động này không thể hoàn tác.
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
              <span>{{ deleting ? 'Đang xóa...' : 'Xóa dịch vụ' }}</span>
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
