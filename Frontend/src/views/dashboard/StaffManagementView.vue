<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue';
import { useRouter } from 'vue-router';
import AdminHeader from '@/components/AdminHeader.vue';
import { useAuthStore } from '@/stores/authStore';
import {
  createStaff,
  fetchStaff,
  fetchStaffRoles,
  type StaffCreatePayload,
  type StaffMember,
  type StaffRole,
  type StaffRoleDefinition,
  type StaffUpdatePayload,
  updateStaff,
} from '@/services/staff.service';

type RoleFilter = StaffRole | 'ALL';
type ModalMode = 'create' | 'edit';

const supportedRoles: StaffRole[] = ['ADMIN', 'DOCTOR', 'NURSE', 'CASHIER', 'PHARMACIST'];

const roleDisplayMap: Record<StaffRole, { label: string; badge: string; chip: string; desc: string }> = {
  ADMIN: {
    label: 'Admin',
    badge: 'bg-slate-900 text-white',
    chip: 'bg-slate-900/10 text-slate-800',
    desc: 'Quản trị toàn hệ thống.',
  },
  DOCTOR: {
    label: 'Bác sĩ',
    badge: 'bg-emerald-100 text-emerald-700',
    chip: 'bg-emerald-50 text-emerald-700',
    desc: 'Khám chữa bệnh, cập nhật hồ sơ.',
  },
  NURSE: {
    label: 'Điều dưỡng',
    badge: 'bg-sky-100 text-sky-700',
    chip: 'bg-sky-50 text-sky-700',
    desc: 'Hỗ trợ chăm sóc và theo dõi.',
  },
  CASHIER: {
    label: 'Thu ngân',
    badge: 'bg-amber-100 text-amber-700',
    chip: 'bg-amber-50 text-amber-700',
    desc: 'Xử lý thanh toán, hóa đơn.',
  },
  PHARMACIST: {
    label: 'Dược sĩ',
    badge: 'bg-purple-100 text-purple-700',
    chip: 'bg-purple-50 text-purple-700',
    desc: 'Quản lý kho thuốc.',
  },
};

const genderOptions = [
  { value: '', label: 'Chưa xác định' },
  { value: 'MALE', label: 'Nam' },
  { value: 'FEMALE', label: 'Nữ' },
  { value: 'OTHER', label: 'Khác' },
];

const statusOptions = [
  { value: 'ACTIVE', label: 'Đang hoạt động' },
  { value: 'INACTIVE', label: 'Tạm ngưng' },
  { value: 'BANNED', label: 'Bị khóa' },
];

const doctorRoleName: StaffRole = 'DOCTOR';

const authStore = useAuthStore();
const router = useRouter();

const userName = computed(() => authStore.user?.username ?? 'Quản trị viên');

const staff = ref<StaffMember[]>([]);
const staffLoading = ref(false);
const staffError = ref<string | null>(null);

const roles = ref<StaffRoleDefinition[]>([]);
const rolesLoading = ref(false);
const rolesError = ref<string | null>(null);

const selectedRole = ref<RoleFilter>('ALL');
const searchTerm = ref('');
const refreshedAt = ref<string | null>(null);

const modalOpen = ref(false);
const modalMode = ref<ModalMode>('create');
const modalSubmitting = ref(false);
const modalError = ref<string | null>(null);

const form = reactive({
  id: null as number | null,
  fullName: '',
  email: '',
  phone: '',
  gender: '',
  dateOfBirth: '',
  password: '',
  status: 'ACTIVE',
  roles: [] as StaffRole[],
  doctor: {
    specialty: '',
    licenseNumber: '',
    examinationRoom: '',
    biography: '',
  },
});

const isDoctorSelected = computed(() => form.roles.includes(doctorRoleName));

const roleFilters = computed(() => {
  const uniqueRoles = new Set<StaffRole>();
  staff.value.forEach((member) => {
    member.roles?.forEach((role) => {
      if (supportedRoles.includes(role)) {
        uniqueRoles.add(role);
      }
    });
  });
  return ['ALL', ...supportedRoles.filter((role) => uniqueRoles.has(role))] as RoleFilter[];
});

const filteredStaff = computed(() => {
  const keyword = searchTerm.value.trim().toLowerCase();
  return staff.value
    .filter((member) => {
      if (selectedRole.value !== 'ALL') {
        return member.roles?.some((role) => role === selectedRole.value);
      }
      return true;
    })
    .filter((member) => {
      if (!keyword) return true;
      const haystacks = [
        member.fullName,
        member.email,
        member.phone,
        member.doctor?.specialty,
        member.doctor?.licenseNumber,
      ]
        .filter(Boolean)
        .map((value) => value!.toLowerCase());
      return haystacks.some((value) => value.includes(keyword));
    })
    .sort((a, b) => (a.fullName || '').localeCompare(b.fullName || '', 'vi'));
});

const totalStaff = computed(() => staff.value.length);
const roleCounts = computed(() => {
  const counts: Record<StaffRole, number> = {
    ADMIN: 0,
    DOCTOR: 0,
    NURSE: 0,
    CASHIER: 0,
    PHARMACIST: 0,
  };
  staff.value.forEach((member) => {
    member.roles?.forEach((role) => {
      if (supportedRoles.includes(role)) {
        counts[role] += 1;
      }
    });
  });
  return counts;
});

const lastUpdatedLabel = computed(() => {
  if (!refreshedAt.value) return 'Chưa tải dữ liệu';
  return `Cập nhật ${formatFromNow(refreshedAt.value)}`;
});

const roleCards = computed(() =>
  supportedRoles.map((role) => ({
    role,
    count: roleCounts.value[role],
    ...roleDisplayMap[role],
  }))
);

const roleOptions = computed(() =>
  roles.value
    .filter((role) => supportedRoles.includes(role.name))
    .sort((a, b) => supportedRoles.indexOf(a.name) - supportedRoles.indexOf(b.name))
    .map((role) => ({
      name: role.name,
      label: roleDisplayMap[role.name].label,
      description: role.description ?? roleDisplayMap[role.name].desc,
    }))
);

const handleSignOut = async () => {
  await authStore.signOut();
  router.replace({ name: 'home' });
};

const ensureRolesLoaded = async () => {
  if (rolesLoading.value) return;
  rolesLoading.value = true;
  rolesError.value = null;
  try {
    const result = await fetchStaffRoles();
    roles.value = result.filter((role) => supportedRoles.includes(role.name));
  } catch (error) {
    rolesError.value = extractErrorMessage(error);
  } finally {
    rolesLoading.value = false;
  }
};

const loadStaff = async () => {
  staffLoading.value = true;
  staffError.value = null;
  try {
    const list = await fetchStaff();
    staff.value = Array.isArray(list)
      ? list.map((member) => ({
          ...member,
          roles: (member.roles ?? []).filter((role) => supportedRoles.includes(role)),
        }))
      : [];
    refreshedAt.value = new Date().toISOString();
  } catch (error) {
    staffError.value = extractErrorMessage(error);
    staff.value = [];
  } finally {
    staffLoading.value = false;
  }
};

const extractErrorMessage = (input: unknown) => {
  const fallback = 'Đã xảy ra lỗi. Vui lòng thử lại.';
  if (!input || typeof input !== 'object') return fallback;
  const maybeError = input as { message?: string; response?: { data?: { message?: unknown; error?: string } } };
  const responseMessage = maybeError.response?.data?.message;
  if (typeof responseMessage === 'string' && responseMessage.trim()) {
    return responseMessage;
  }
  if (Array.isArray(responseMessage) && responseMessage.length > 0) {
    const first = responseMessage[0];
    if (typeof first === 'string' && first.trim()) return first;
  }
  if (maybeError.response?.data?.error) {
    return maybeError.response.data.error;
  }
  if (maybeError.message) {
    return maybeError.message;
  }
  return fallback;
};

const resetForm = () => {
  form.id = null;
  form.fullName = '';
  form.email = '';
  form.phone = '';
  form.gender = '';
  form.dateOfBirth = '';
  form.password = '';
  form.status = 'ACTIVE';
  form.roles.splice(0, form.roles.length);
  form.doctor.specialty = '';
  form.doctor.licenseNumber = '';
  form.doctor.examinationRoom = '';
  form.doctor.biography = '';
};

const openCreateModal = () => {
  modalMode.value = 'create';
  resetForm();
  modalOpen.value = true;
};

const openEditModal = (member: StaffMember) => {
  modalMode.value = 'edit';
  modalError.value = null;
  form.id = member.id;
  form.fullName = member.fullName ?? '';
  form.email = member.email ?? '';
  form.phone = member.phone ?? '';
  form.gender = member.gender ?? '';
  form.dateOfBirth = member.dateOfBirth ?? '';
  form.password = '';
  form.status = member.status ?? 'ACTIVE';
  form.roles.splice(0, form.roles.length, ...(member.roles ?? []).filter((role): role is StaffRole => supportedRoles.includes(role)));
  form.doctor.specialty = member.doctor?.specialty ?? '';
  form.doctor.licenseNumber = member.doctor?.licenseNumber ?? '';
  form.doctor.examinationRoom = member.doctor?.examinationRoom ?? '';
  form.doctor.biography = member.doctor?.biography ?? '';
  modalOpen.value = true;
};

const closeModal = () => {
  modalOpen.value = false;
};

const submitModal = async () => {
  modalSubmitting.value = true;
  modalError.value = null;
  try {
    if (modalMode.value === 'create') {
      const payload = buildCreatePayload();
      await createStaff(payload);
    } else if (modalMode.value === 'edit' && form.id != null) {
      const payload = buildUpdatePayload();
      await updateStaff(form.id, payload);
    }
    await loadStaff();
    closeModal();
  } catch (error) {
    modalError.value = extractErrorMessage(error);
  } finally {
    modalSubmitting.value = false;
  }
};

const buildCreatePayload = (): StaffCreatePayload => {
  if (!form.password.trim()) {
    throw new Error('Vui lòng nhập mật khẩu cho nhân viên.');
  }
  const base: StaffCreatePayload = {
    fullName: form.fullName.trim(),
    email: form.email.trim(),
    password: form.password,
    roles: [...form.roles],
  };
  if (form.phone.trim()) base.phone = form.phone.trim();
  if (form.gender.trim()) base.gender = form.gender.trim();
  if (form.dateOfBirth.trim()) base.dateOfBirth = form.dateOfBirth.trim();
  if (isDoctorSelected.value) {
    base.doctor = {
      specialty: form.doctor.specialty.trim(),
      licenseNumber: form.doctor.licenseNumber.trim(),
      examinationRoom: form.doctor.examinationRoom.trim() || undefined,
      biography: form.doctor.biography.trim() || undefined,
    };
  }
  return base;
};

const buildUpdatePayload = (): StaffUpdatePayload => {
  const payload: StaffUpdatePayload = {
    fullName: form.fullName.trim() || undefined,
    email: form.email.trim() || undefined,
    status: form.status?.trim() || undefined,
    roles: [...form.roles],
  };
  payload.phone = form.phone.trim() || undefined;
  payload.gender = form.gender.trim() || undefined;
  payload.dateOfBirth = form.dateOfBirth.trim() || undefined;
  if (form.password.trim()) {
    payload.password = form.password;
  }
  if (isDoctorSelected.value) {
    payload.doctor = {
      specialty: form.doctor.specialty.trim(),
      licenseNumber: form.doctor.licenseNumber.trim(),
      examinationRoom: form.doctor.examinationRoom.trim() || undefined,
      biography: form.doctor.biography.trim() || undefined,
    };
  } else {
    payload.doctor = undefined;
  }
  return payload;
};

const toggleRole = (role: StaffRole, checked: boolean) => {
  const index = form.roles.indexOf(role);
  if (checked && index === -1) {
    form.roles.push(role);
  } else if (!checked && index >= 0) {
    form.roles.splice(index, 1);
  }
};

const formatDate = (value?: string | null) => {
  if (!value) return '—';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return '—';
  return new Intl.DateTimeFormat('vi-VN', { dateStyle: 'medium' }).format(date);
};

const formatDateTime = (value?: string | null) => {
  if (!value) return '—';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return '—';
  return new Intl.DateTimeFormat('vi-VN', { dateStyle: 'medium', timeStyle: 'short' }).format(date);
};

const formatFromNow = (value?: string | Date | null) => {
  const date = value instanceof Date ? value : value ? new Date(value) : null;
  if (!date || Number.isNaN(date.getTime())) return 'không xác định';
  const diffMs = Date.now() - date.getTime();
  if (diffMs < 0) return formatDateTime(date);
  const minutes = Math.round(diffMs / 60000);
  if (minutes < 1) return 'vừa xong';
  if (minutes < 60) return `${minutes} phút trước`;
  const hours = Math.round(minutes / 60);
  if (hours < 24) return `${hours} giờ trước`;
  const days = Math.round(hours / 24);
  if (days < 7) return `${days} ngày trước`;
  return formatDateTime(date);
};

const formatGender = (value?: string | null) => {
  switch (value) {
    case 'MALE':
      return 'Nam';
    case 'FEMALE':
      return 'Nữ';
    case 'OTHER':
      return 'Khác';
    default:
      return '—';
  }
};

watch(isDoctorSelected, (selected) => {
  if (!selected) {
    form.doctor.specialty = '';
    form.doctor.licenseNumber = '';
    form.doctor.examinationRoom = '';
    form.doctor.biography = '';
  }
});

watch(modalOpen, (open) => {
  if (!open) {
    modalError.value = null;
    form.password = '';
  }
});

onMounted(async () => {
  await Promise.all([ensureRolesLoaded(), loadStaff()]);
});
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
        <div class="flex flex-col gap-4 md:flex-row md:items-start md:justify-between">
          <div class="max-w-3xl">
            <span class="inline-flex items-center gap-2 rounded-full bg-emerald-50 px-4 py-1.5 text-xs font-semibold uppercase tracking-[0.3em] text-emerald-600">
              Trung tâm nhân sự
            </span>
            <h1 class="mt-4 text-2xl font-semibold text-slate-900 md:text-3xl">Quản lý nhân viên &amp; vai trò</h1>
            <p class="mt-2 text-sm text-slate-600">
              Theo dõi hồ sơ nhân sự, phân quyền truy cập hệ thống và quản lý thông tin hành nghề của bác sĩ.
            </p>
            <p class="mt-1 text-xs text-slate-400">{{ lastUpdatedLabel }}</p>
          </div>
          <div class="flex flex-col items-stretch justify-end gap-3 sm:flex-row md:flex-col">
            <button
              type="button"
              class="inline-flex items-center justify-center gap-2 rounded-full bg-emerald-600 px-5 py-2 text-xs font-semibold uppercase tracking-wide text-white shadow-sm transition hover:bg-emerald-700"
              @click="openCreateModal"
            >
              <svg xmlns="http://www.w3.org/2000/svg" class="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.8">
                <path stroke-linecap="round" stroke-linejoin="round" d="M12 5v14m7-7H5" />
              </svg>
              Thêm nhân viên
            </button>
            <button
              type="button"
              class="inline-flex items-center justify-center gap-2 rounded-full border border-emerald-200 bg-white px-5 py-2 text-xs font-semibold uppercase tracking-wide text-emerald-600 shadow-sm transition hover:border-emerald-300 hover:bg-emerald-50 disabled:opacity-50"
              :disabled="staffLoading"
              @click="loadStaff"
            >
              <svg xmlns="http://www.w3.org/2000/svg" class="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.8">
                <path stroke-linecap="round" stroke-linejoin="round" d="M4 4v5h.582m15.356 2A8.001 8.001 0 0 0 5.582 9H4m0 0a8.003 8.003 0 0 1 14.9-3m1.518 9H20v5h.004m-.586-2A8.001 8.001 0 0 1 5.518 15H4m0 0a8.003 8.003 0 0 0 14.9 3" />
              </svg>
              Làm mới
            </button>
          </div>
        </div>

        <div class="mt-8 grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
          <div class="rounded-2xl border border-emerald-100 bg-emerald-50/70 p-5 text-emerald-700 shadow-sm">
            <p class="text-xs font-semibold uppercase tracking-wide text-emerald-600/80">Tổng nhân viên</p>
            <p class="mt-2 text-3xl font-semibold text-emerald-800">{{ totalStaff }}</p>
            <p class="mt-1 text-xs text-emerald-600/80">Bao gồm tất cả các vai trò đã kích hoạt.</p>
          </div>
          <div
            v-for="card in roleCards"
            :key="card.role"
            class="rounded-2xl border bg-white/90 p-5 shadow-sm"
            :class="card.role === 'DOCTOR' ? 'border-emerald-100 text-emerald-700' : 'border-slate-200 text-slate-700'"
          >
            <p class="text-xs font-semibold uppercase tracking-wide text-slate-400">{{ card.label }}</p>
            <p class="mt-2 text-2xl font-semibold">{{ card.count }}</p>
            <p class="mt-1 text-xs text-slate-500">{{ card.desc }}</p>
          </div>
        </div>

        <div class="mt-8 flex flex-col gap-4 md:flex-row md:items-end md:justify-between">
          <div class="flex flex-wrap items-center gap-2">
            <button
              v-for="role in roleFilters"
              :key="role"
              type="button"
              class="inline-flex items-center gap-2 rounded-full border px-4 py-2 text-xs font-semibold uppercase tracking-wide transition"
              :class="selectedRole === role ? 'border-emerald-300 bg-emerald-50 text-emerald-700 shadow-sm' : 'border-emerald-100 bg-white text-emerald-600 hover:border-emerald-200 hover:bg-emerald-50/70'"
              @click="selectedRole = role"
            >
              <span>{{ role === 'ALL' ? 'Tất cả' : roleDisplayMap[role].label }}</span>
              <span class="rounded-full bg-emerald-100 px-2 py-0.5 text-[11px] font-semibold text-emerald-600">
                {{ role === 'ALL' ? totalStaff : roleCounts[role] }}
              </span>
            </button>
          </div>
          <div class="relative max-w-xs">
            <svg class="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" fill="none" stroke="currentColor" stroke-width="1.5" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" d="m21 21-4.35-4.35m0 0A7.35 7.35 0 1 0 6.3 6.3a7.35 7.35 0 0 0 10.35 10.35Z" />
            </svg>
            <input
              v-model.trim="searchTerm"
              type="search"
              placeholder="Tìm tên, email, chuyên khoa..."
              class="w-full rounded-full border border-emerald-100 bg-white py-2.5 pl-9 pr-4 text-sm text-slate-700 shadow-sm transition focus:border-emerald-400 focus:outline-none focus:ring-4 focus:ring-emerald-100/80"
            />
          </div>
        </div>

        <p v-if="staffError" class="mt-4 rounded-2xl border border-rose-100 bg-rose-50/90 px-4 py-3 text-sm text-rose-600">
          {{ staffError }}
        </p>
      </section>

      <section class="mt-10 rounded-[28px] border border-emerald-100 bg-white/95 p-6 shadow-[0_24px_55px_-45px_rgba(13,148,136,0.55)]">
        <template v-if="staffLoading && !staff.length">
          <div class="grid gap-4 md:grid-cols-2">
            <div v-for="skeleton in 4" :key="skeleton" class="animate-pulse rounded-2xl border border-slate-100 bg-slate-50 p-5">
              <div class="h-4 w-32 rounded-full bg-slate-200/80"></div>
              <div class="mt-3 h-3 w-24 rounded-full bg-slate-200/60"></div>
              <div class="mt-4 h-3 w-full rounded-full bg-slate-200/50"></div>
              <div class="mt-2 h-3 w-1/2 rounded-full bg-slate-200/30"></div>
            </div>
          </div>
        </template>

        <template v-else-if="filteredStaff.length === 0">
          <div class="rounded-3xl border border-dashed border-emerald-200 bg-emerald-50/40 p-10 text-center text-emerald-700">
            <svg xmlns="http://www.w3.org/2000/svg" class="mx-auto h-12 w-12 text-emerald-400" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.6">
              <path stroke-linecap="round" stroke-linejoin="round" d="M5 13l4 4L19 7" />
            </svg>
            <h3 class="mt-4 text-lg font-semibold">Chưa có nhân viên phù hợp bộ lọc</h3>
            <p class="mt-2 text-sm text-emerald-600/80">Thêm mới hoặc điều chỉnh bộ lọc tìm kiếm để xem danh sách.</p>
          </div>
        </template>

        <template v-else>
          <div class="grid gap-4 md:grid-cols-2">
            <article
              v-for="member in filteredStaff"
              :key="member.id"
              class="group relative rounded-2xl border border-slate-200 bg-white p-5 shadow-sm transition hover:-translate-y-1 hover:border-emerald-200 hover:shadow-lg"
            >
              <div class="flex items-start justify-between gap-4">
                <div>
                  <h3 class="text-lg font-semibold text-slate-900">{{ member.fullName }}</h3>
                  <p class="mt-1 text-sm text-slate-500">{{ member.email }}</p>
                  <p class="mt-1 text-sm text-slate-500">SĐT: {{ member.phone || '—' }}</p>
                </div>
                <button
                  type="button"
                  class="inline-flex items-center gap-2 rounded-full border border-emerald-200 bg-white px-3 py-1 text-xs font-semibold uppercase tracking-wide text-emerald-600 shadow-sm transition hover:border-emerald-300 hover:bg-emerald-50"
                  @click="openEditModal(member)"
                >
                  <svg xmlns="http://www.w3.org/2000/svg" class="h-3.5 w-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.8">
                    <path stroke-linecap="round" stroke-linejoin="round" d="M16.862 4.487 19.513 7.138m-2.651-2.651L7.5 16.5l-3 3 3-3 9.862-9.862Zm0 0 1.76-1.76a1.5 1.5 0 0 1 2.122 0l.378.378a1.5 1.5 0 0 1 0 2.122l-1.76 1.76m-2.5-2.5-9.862 9.862m0 0H4.5v-2.414" />
                  </svg>
                  Chỉnh sửa
                </button>
              </div>

              <div class="mt-4 flex flex-wrap gap-2">
                <span
                  v-for="role in member.roles"
                  :key="`${member.id}-${role}`"
                  class="rounded-full px-3 py-1 text-xs font-semibold"
                  :class="roleDisplayMap[role].chip"
                >
                  {{ roleDisplayMap[role].label }}
                </span>
              </div>

              <div class="mt-4 grid gap-2 text-sm text-slate-600">
                <div class="flex items-center gap-2">
                  <span class="text-slate-400">Giới tính:</span>
                  <span class="font-semibold text-slate-800">{{ formatGender(member.gender) }}</span>
                </div>
                <div class="flex items-center gap-2">
                  <span class="text-slate-400">Ngày sinh:</span>
                  <span class="font-semibold text-slate-800">{{ formatDate(member.dateOfBirth) }}</span>
                </div>
                <div class="flex items-center gap-2">
                  <span class="text-slate-400">Trạng thái:</span>
                  <span class="font-semibold text-emerald-700">{{ member.status || 'ACTIVE' }}</span>
                </div>
                <div class="flex items-center gap-2">
                  <span class="text-slate-400">Tạo lúc:</span>
                  <span class="font-semibold text-slate-800">{{ formatDateTime(member.createdAt) }}</span>
                </div>
              </div>

              <div v-if="member.doctor" class="mt-4 rounded-2xl border border-emerald-100 bg-emerald-50/60 p-4 text-sm text-emerald-700">
                <p class="text-xs font-semibold uppercase tracking-wide text-emerald-500">Thông tin bác sĩ</p>
                <p class="mt-2"><span class="font-semibold text-emerald-800">Chuyên khoa:</span> {{ member.doctor.specialty }}</p>
                <p class="mt-1"><span class="font-semibold text-emerald-800">Số giấy phép:</span> {{ member.doctor.licenseNumber }}</p>
                <p v-if="member.doctor.examinationRoom" class="mt-1">
                  <span class="font-semibold text-emerald-800">Phòng khám:</span> {{ member.doctor.examinationRoom }}
                </p>
                <p v-if="member.doctor.biography" class="mt-1 text-emerald-600/90">
                  {{ member.doctor.biography }}
                </p>
              </div>
            </article>
          </div>
        </template>
      </section>
    </main>

    <Transition name="fade">
      <div
        v-if="modalOpen"
        class="fixed inset-0 z-[75] flex items-center justify-center bg-slate-900/40 backdrop-blur-sm"
      >
        <div class="relative max-h-[90vh] w-[min(720px,92vw)] overflow-hidden rounded-[28px] border border-emerald-100 bg-white shadow-[0_32px_120px_-55px_rgba(13,148,136,0.75)]">
          <button
            type="button"
            class="absolute right-5 top-5 flex h-10 w-10 items-center justify-center rounded-full bg-white/80 text-slate-500 shadow-sm transition hover:bg-white hover:text-slate-700"
            @click="closeModal"
          >
            <svg xmlns="http://www.w3.org/2000/svg" class="h-4.5 w-4.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.8">
              <path stroke-linecap="round" stroke-linejoin="round" d="m16 8-8 8m0-8 8 8" />
            </svg>
          </button>

          <form class="max-h-[90vh] overflow-y-auto px-8 py-12" @submit.prevent="submitModal">
            <div class="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
              <div>
                <p class="text-xs font-semibold uppercase tracking-[0.35em] text-emerald-500">
                  {{ modalMode === 'create' ? 'Thêm nhân viên mới' : 'Cập nhật thông tin nhân viên' }}
                </p>
                <h2 class="mt-2 text-2xl font-semibold text-slate-900">
                  {{ modalMode === 'create' ? 'Tạo tài khoản nhân sự' : form.fullName }}
                </h2>
                <p class="mt-1 text-sm text-slate-500">
                  Điền thông tin cơ bản và chọn vai trò phù hợp. Khi chọn vai trò bác sĩ, yêu cầu bổ sung thông tin hành nghề.
                </p>
              </div>
              <span class="rounded-full bg-emerald-50 px-4 py-1 text-xs font-semibold uppercase tracking-wide text-emerald-700">
                {{ modalMode === 'create' ? 'Tạo mới' : 'Chế độ chỉnh sửa' }}
              </span>
            </div>

            <div class="mt-6 grid gap-4 sm:grid-cols-2">
              <div class="sm:col-span-2">
                <label class="mb-1 block text-xs font-semibold uppercase tracking-wide text-slate-500" for="staff-fullname">Họ và tên *</label>
                <input
                  id="staff-fullname"
                  v-model="form.fullName"
                  type="text"
                  required
                  class="w-full rounded-xl border border-slate-200 bg-white px-4 py-3 text-sm text-slate-800 shadow-sm transition focus:border-emerald-400 focus:outline-none focus:ring-4 focus:ring-emerald-100/80"
                  placeholder="Nguyễn Văn A"
                />
              </div>

              <div>
                <label class="mb-1 block text-xs font-semibold uppercase tracking-wide text-slate-500" for="staff-email">Email đăng nhập *</label>
                <input
                  id="staff-email"
                  v-model="form.email"
                  type="email"
                  required
                  class="w-full rounded-xl border border-slate-200 bg-white px-4 py-3 text-sm text-slate-800 shadow-sm transition focus:border-emerald-400 focus:outline-none focus:ring-4 focus:ring-emerald-100/80"
                  placeholder="example@clinic.vn"
                />
              </div>

              <div>
                <label class="mb-1 block text-xs font-semibold uppercase tracking-wide text-slate-500" for="staff-phone">Số điện thoại</label>
                <input
                  id="staff-phone"
                  v-model="form.phone"
                  type="tel"
                  maxlength="10"
                  class="w-full rounded-xl border border-slate-200 bg-white px-4 py-3 text-sm text-slate-800 shadow-sm transition focus:border-emerald-400 focus:outline-none focus:ring-4 focus:ring-emerald-100/80"
                  placeholder="0987123456"
                />
              </div>

              <div>
                <label class="mb-1 block text-xs font-semibold uppercase tracking-wide text-slate-500" for="staff-gender">Giới tính</label>
                <select
                  id="staff-gender"
                  v-model="form.gender"
                  class="w-full rounded-xl border border-slate-200 bg-white px-4 py-3 text-sm text-slate-800 shadow-sm transition focus:border-emerald-400 focus:outline-none focus:ring-4 focus:ring-emerald-100/80"
                >
                  <option v-for="option in genderOptions" :key="option.value" :value="option.value">
                    {{ option.label }}
                  </option>
                </select>
              </div>

              <div>
                <label class="mb-1 block text-xs font-semibold uppercase tracking-wide text-slate-500" for="staff-dob">Ngày sinh</label>
                <input
                  id="staff-dob"
                  v-model="form.dateOfBirth"
                  type="date"
                  class="w-full rounded-xl border border-slate-200 bg-white px-4 py-3 text-sm text-slate-800 shadow-sm transition focus:border-emerald-400 focus:outline-none focus:ring-4 focus:ring-emerald-100/80"
                />
              </div>

              <div>
                <label class="mb-1 block text-xs font-semibold uppercase tracking-wide text-slate-500" for="staff-status">Trạng thái</label>
                <select
                  id="staff-status"
                  v-model="form.status"
                  class="w-full rounded-xl border border-slate-200 bg-white px-4 py-3 text-sm text-slate-800 shadow-sm transition focus:border-emerald-400 focus:outline-none focus:ring-4 focus:ring-emerald-100/80"
                >
                  <option v-for="option in statusOptions" :key="option.value" :value="option.value">
                    {{ option.label }}
                  </option>
                </select>
              </div>

              <div class="sm:col-span-2" v-if="modalMode === 'create'">
                <label class="mb-1 block text-xs font-semibold uppercase tracking-wide text-slate-500" for="staff-password">Mật khẩu đăng nhập *</label>
                <input
                  id="staff-password"
                  v-model="form.password"
                  type="password"
                  minlength="6"
                  required
                  class="w-full rounded-xl border border-slate-200 bg-white px-4 py-3 text-sm text-slate-800 shadow-sm transition focus:border-emerald-400 focus:outline-none focus:ring-4 focus:ring-emerald-100/80"
                  placeholder="Ít nhất 6 ký tự"
                />
              </div>
              <div class="sm:col-span-2" v-else>
                <label class="mb-1 block text-xs font-semibold uppercase tracking-wide text-slate-500" for="staff-password-edit">Đặt lại mật khẩu (tuỳ chọn)</label>
                <input
                  id="staff-password-edit"
                  v-model="form.password"
                  type="password"
                  minlength="6"
                  class="w-full rounded-xl border border-slate-200 bg-white px-4 py-3 text-sm text-slate-800 shadow-sm transition focus:border-emerald-400 focus:outline-none focus:ring-4 focus:ring-emerald-100/80"
                  placeholder="Để trống nếu không đổi"
                />
              </div>
            </div>

            <div class="mt-6 rounded-2xl border border-emerald-100 bg-emerald-50/60 p-5">
              <p class="text-xs font-semibold uppercase tracking-wide text-emerald-600">Vai trò hệ thống</p>
              <p class="mt-1 text-sm text-emerald-700/90">
                Chọn một hoặc nhiều vai trò cho nhân viên. Vai trò sẽ quyết định quyền truy cập trên hệ thống.
              </p>
              <p v-if="rolesError" class="mt-2 text-xs text-rose-600">
                {{ rolesError }}
              </p>
              <div class="mt-4 grid gap-3 sm:grid-cols-2">
                <label
                  v-for="role in roleOptions"
                  :key="role.name"
                  class="flex cursor-pointer items-start gap-3 rounded-xl border border-emerald-100 bg-white px-4 py-3 text-sm text-slate-700 shadow-sm transition hover:border-emerald-200 hover:bg-emerald-50/70"
                >
                  <input
                    :id="`role-${role.name}`"
                    type="checkbox"
                    class="mt-1 h-4 w-4 rounded border-slate-300 text-emerald-600 focus:ring-emerald-500"
                    :checked="form.roles.includes(role.name as StaffRole)"
                    @change="toggleRole(role.name as StaffRole, ($event.target as HTMLInputElement).checked)"
                  />
                  <span>
                    <span class="font-semibold text-slate-900">{{ role.label }}</span>
                    <span class="mt-0.5 block text-xs text-slate-500">{{ role.description }}</span>
                  </span>
                </label>
              </div>
            </div>

            <div v-if="isDoctorSelected" class="mt-6 rounded-2xl border border-emerald-200 bg-emerald-50/80 p-5">
              <p class="text-xs font-semibold uppercase tracking-wide text-emerald-600">Thông tin hành nghề bác sĩ *</p>
              <div class="mt-4 grid gap-4 sm:grid-cols-2">
                <div class="sm:col-span-1">
                  <label class="mb-1 block text-xs font-semibold uppercase tracking-wide text-slate-500" for="doctor-specialty">Chuyên khoa *</label>
                  <input
                    id="doctor-specialty"
                    v-model="form.doctor.specialty"
                    type="text"
                    required
                    class="w-full rounded-xl border border-emerald-200 bg-white px-4 py-3 text-sm text-emerald-800 shadow-sm transition focus:border-emerald-400 focus:outline-none focus:ring-4 focus:ring-emerald-100/80"
                    placeholder="Ví dụ: Nội tổng quát"
                  />
                </div>
                <div class="sm:col-span-1">
                  <label class="mb-1 block text-xs font-semibold uppercase tracking-wide text-slate-500" for="doctor-license">Số giấy phép *</label>
                  <input
                    id="doctor-license"
                    v-model="form.doctor.licenseNumber"
                    type="text"
                    required
                    class="w-full rounded-xl border border-emerald-200 bg-white px-4 py-3 text-sm text-emerald-800 shadow-sm transition focus:border-emerald-400 focus:outline-none focus:ring-4 focus:ring-emerald-100/80"
                    placeholder="Số giấy phép hành nghề"
                  />
                </div>
                <div>
                  <label class="mb-1 block text-xs font-semibold uppercase tracking-wide text-slate-500" for="doctor-room">Phòng khám</label>
                  <input
                    id="doctor-room"
                    v-model="form.doctor.examinationRoom"
                    type="text"
                    class="w-full rounded-xl border border-emerald-200 bg-white px-4 py-3 text-sm text-emerald-800 shadow-sm transition focus:border-emerald-400 focus:outline-none focus:ring-4 focus:ring-emerald-100/80"
                    placeholder="Ví dụ: Phòng 203"
                  />
                </div>
                <div class="sm:col-span-2">
                  <label class="mb-1 block text-xs font-semibold uppercase tracking-wide text-slate-500" for="doctor-bio">Giới thiệu</label>
                  <textarea
                    id="doctor-bio"
                    v-model="form.doctor.biography"
                    rows="3"
                    class="w-full rounded-xl border border-emerald-200 bg-white px-4 py-3 text-sm text-emerald-800 shadow-sm transition focus:border-emerald-400 focus:outline-none focus:ring-4 focus:ring-emerald-100/80"
                    placeholder="Kinh nghiệm làm việc, chứng chỉ..."
                  ></textarea>
                </div>
              </div>
            </div>

            <p v-if="modalError" class="mt-4 rounded-2xl border border-rose-100 bg-rose-50/90 px-4 py-3 text-sm text-rose-600">
              {{ modalError }}
            </p>

            <div class="mt-8 flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-end">
              <button
                type="button"
                class="inline-flex items-center justify-center gap-2 rounded-full border border-slate-200 bg-white px-5 py-2 text-xs font-semibold uppercase tracking-wide text-slate-600 shadow-sm transition hover:border-slate-300 hover:bg-slate-50"
                @click="closeModal"
              >
                Huỷ bỏ
              </button>
              <button
                type="submit"
                class="inline-flex items-center justify-center gap-2 rounded-full bg-emerald-600 px-6 py-2.5 text-xs font-semibold uppercase tracking-wide text-white shadow-sm transition hover:bg-emerald-700 disabled:cursor-not-allowed disabled:opacity-70"
                :disabled="modalSubmitting"
              >
                <svg v-if="modalSubmitting" class="h-4 w-4 animate-spin" viewBox="0 0 24 24" fill="none">
                  <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
                  <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 0 1 8-8v4l3.5-3.5L12 1v4a7 7 0 0 0-7 7h-1z"></path>
                </svg>
                <span>{{ modalMode === 'create' ? 'Tạo nhân viên' : 'Lưu thay đổi' }}</span>
              </button>
            </div>
          </form>
        </div>
      </div>
    </Transition>
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
