<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import AdminHeader from '@/components/AdminHeader.vue';
import { useAuthStore } from '@/stores/authStore';
import { fetchCurrentUserProfile, updateCurrentUserProfile, type UserProfile } from '@/services/user.service';
import { useToast } from '@/composables/useToast';

const authStore = useAuthStore();
const router = useRouter();
const userName = computed(() => authStore.user?.username ?? 'Quản trị viên');

const profile = ref<UserProfile | null>(null);
const profileLoading = ref(false);
const profileError = ref<string | null>(null);

const roleLabels = computed(() =>
  (profile.value?.roles ?? [])
    .map((role) => role?.name)
    .filter((name): name is string => Boolean(name)),
);

const genderOptions = [
  { value: '', label: 'Chưa xác định' },
  { value: 'MALE', label: 'Nam' },
  { value: 'FEMALE', label: 'Nữ' },
  { value: 'OTHER', label: 'Khác' },
];

const editModalOpen = ref(false);
const editSubmitting = ref(false);
const editError = ref<string | null>(null);

const { toast, show: showToast, hide: hideToast } = useToast();

const editForm = reactive({
  fullName: '',
  phone: '',
  gender: '',
  dateOfBirth: '',
  currentPassword: '',
  newPassword: '',
  confirmPassword: '',
});

const toDateInput = (value?: string | null) => {
  if (!value) return '';
  return value.split('T')[0] ?? value;
};

const formatGender = (value?: string | null) => {
  switch ((value ?? '').toUpperCase()) {
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

const openEditModal = () => {
  if (!profile.value) {
    return;
  }
  editForm.fullName = profile.value.fullName ?? '';
  editForm.phone = profile.value.phone ?? '';
  editForm.gender = (profile.value.gender ?? '').toUpperCase();
  editForm.dateOfBirth = toDateInput(profile.value.dateOfBirth);
  editForm.currentPassword = '';
  editForm.newPassword = '';
  editForm.confirmPassword = '';
  editError.value = null;
  editModalOpen.value = true;
};

const closeEditModal = () => {
  editModalOpen.value = false;
  editSubmitting.value = false;
  editError.value = null;
  editForm.currentPassword = '';
  editForm.newPassword = '';
  editForm.confirmPassword = '';
};

const loadProfile = async () => {
  profileLoading.value = true;
  profileError.value = null;
  try {
    profile.value = await fetchCurrentUserProfile();
  } catch (error: any) {
    const message =
      error?.response?.data?.message ??
      error?.message ??
      'Không thể tải thông tin cá nhân. Vui lòng thử lại.';
    profileError.value = message;
    showToast('error', message);
  } finally {
    profileLoading.value = false;
  }
};

const handleEditSubmit = async () => {
  if (!profile.value) {
    return;
  }
  editError.value = null;

  const wantsPasswordChange =
    editForm.currentPassword.trim().length > 0 ||
    editForm.newPassword.trim().length > 0 ||
    editForm.confirmPassword.trim().length > 0;

  if (wantsPasswordChange) {
    if (!editForm.currentPassword.trim() || !editForm.newPassword.trim() || !editForm.confirmPassword.trim()) {
      const message = 'Vui lòng nhập đầy đủ thông tin mật khẩu.';
      editError.value = message;
      showToast('error', message);
      return;
    }
    if (editForm.newPassword.trim().length < 6) {
      const message = 'Mật khẩu mới phải có ít nhất 6 ký tự.';
      editError.value = message;
      showToast('error', message);
      return;
    }
    if (editForm.newPassword.trim() !== editForm.confirmPassword.trim()) {
      const message = 'Mật khẩu nhập lại không khớp.';
      editError.value = message;
      showToast('error', message);
      return;
    }
    if (editForm.currentPassword.trim() === editForm.newPassword.trim()) {
      const message = 'Mật khẩu mới phải khác mật khẩu hiện tại.';
      editError.value = message;
      showToast('error', message);
      return;
    }
  }

  editSubmitting.value = true;
  try {
    await updateCurrentUserProfile(profile.value.id, {
      fullName: editForm.fullName,
      phone: editForm.phone,
      gender: editForm.gender,
      dateOfBirth: editForm.dateOfBirth || null,
      password: wantsPasswordChange
        ? {
            currentPassword: editForm.currentPassword.trim(),
            newPassword: editForm.newPassword.trim(),
          }
        : null,
    });
    await loadProfile();
    showToast('success', 'Cập nhật thông tin thành công.');
    closeEditModal();
  } catch (error: any) {
    const message =
      error?.response?.data?.message ?? error?.message ?? 'Không thể cập nhật thông tin. Vui lòng thử lại.';
    editError.value = message;
    showToast('error', message);
  } finally {
    editSubmitting.value = false;
  }
};

const handleSignOut = async () => {
  await authStore.signOut();
  router.replace({ name: 'home' });
};

onMounted(() => {
  loadProfile();
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
              Trang cá nhân
            </span>
            <h1 class="mt-4 text-2xl font-semibold text-slate-900 md:text-3xl">Thông tin tài khoản của bạn</h1>
            <p class="mt-2 text-sm text-slate-600">
              Xem chi tiết hồ sơ, vai trò và cập nhật mật khẩu đăng nhập. Các thay đổi được áp dụng ngay lập tức.
            </p>
          </div>
          <div class="flex flex-col-reverse gap-3 sm:flex-row sm:items-center">
            <button
              type="button"
              class="inline-flex items-center justify-center gap-2 rounded-full border border-emerald-200 bg-white px-5 py-2 text-xs font-semibold uppercase tracking-wide text-emerald-600 shadow-sm transition hover:border-emerald-300 hover:bg-emerald-50 disabled:cursor-not-allowed disabled:opacity-60"
              :disabled="profileLoading"
              @click="loadProfile"
            >
              <svg xmlns="http://www.w3.org/2000/svg" class="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.8">
                <path stroke-linecap="round" stroke-linejoin="round" d="M4 4v5h.582m15.356 2A8.001 8.001 0 0 0 5.582 9H4m0 0a8.003 8.003 0 0 1 14.9-3m1.518 9H20v5h.004m-.586-2A8.001 8.001 0 0 1 5.518 15H4m0 0a8.003 8.003 0 0 0 14.9 3" />
              </svg>
              Làm mới
            </button>
            <button
              type="button"
              class="inline-flex items-center justify-center gap-2 rounded-full bg-emerald-600 px-5 py-2 text-xs font-semibold uppercase tracking-wide text-white shadow-sm transition hover:bg-emerald-700 disabled:cursor-not-allowed disabled:opacity-70"
              :disabled="profileLoading || !profile"
              @click="openEditModal"
            >
              <svg xmlns="http://www.w3.org/2000/svg" class="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.8">
                <path stroke-linecap="round" stroke-linejoin="round" d="M16.862 4.487 19.513 7.138m-2.651-2.651L7.5 16.5l-3 3 3-3 9.862-9.862Zm0 0 1.76-1.76a1.5 1.5 0 0 1 2.122 0l.378.378a1.5 1.5 0 0 1 0 2.122l-1.76 1.76m-2.5-2.5-9.862 9.862m0 0H4.5v-2.414" />
              </svg>
              Chỉnh sửa
            </button>
          </div>
        </div>

        <div v-if="profileError" class="mt-6 rounded-2xl border border-rose-100 bg-rose-50/90 px-5 py-4 text-sm text-rose-600">
          {{ profileError }}
        </div>

        <div v-if="!profileError" class="mt-6 rounded-3xl border border-emerald-100 bg-white/95 p-6 shadow-sm">
          <div v-if="profileLoading" class="grid gap-4 md:grid-cols-2">
            <div v-for="skeleton in 4" :key="skeleton" class="animate-pulse rounded-2xl border border-slate-100 bg-slate-50 p-5">
              <div class="h-4 w-32 rounded-full bg-slate-200/80"></div>
              <div class="mt-3 h-3 w-24 rounded-full bg-slate-200/60"></div>
              <div class="mt-4 h-3 w-full rounded-full bg-slate-200/40"></div>
            </div>
          </div>
          <div v-else-if="profile" class="grid gap-6 md:grid-cols-2">
            <div class="space-y-3">
              <p class="text-xs font-semibold uppercase tracking-wide text-emerald-500">Thông tin liên hệ</p>
              <div class="rounded-2xl border border-emerald-100 bg-emerald-50/40 p-5 text-sm text-slate-700">
                <div class="flex justify-between gap-4">
                  <span class="text-slate-500">Họ và tên:</span>
                  <span class="font-semibold text-slate-900">{{ profile.fullName || '—' }}</span>
                </div>
                <div class="mt-2 flex justify-between gap-4">
                  <span class="text-slate-500">Email:</span>
                  <span class="font-semibold text-slate-900 break-all">{{ profile.email }}</span>
                </div>
                <div class="mt-2 flex justify-between gap-4">
                  <span class="text-slate-500">Số điện thoại:</span>
                  <span class="font-semibold text-slate-900">{{ profile.phone || '—' }}</span>
                </div>
              </div>
            </div>

            <div class="space-y-3">
              <p class="text-xs font-semibold uppercase tracking-wide text-emerald-500">Thông tin bổ sung</p>
              <div class="rounded-2xl border border-emerald-100 bg-white p-5 shadow-sm text-sm text-slate-700">
                <div class="flex justify-between gap-4">
                  <span class="text-slate-500">Giới tính:</span>
                  <span class="font-semibold text-slate-900">{{ formatGender(profile.gender) }}</span>
                </div>
                <div class="mt-2 flex justify-between gap-4">
                  <span class="text-slate-500">Ngày sinh:</span>
                  <span class="font-semibold text-slate-900">{{ formatDate(profile.dateOfBirth) }}</span>
                </div>
                <div class="mt-2 flex justify-between gap-4">
                  <span class="text-slate-500">Trạng thái:</span>
                  <span class="font-semibold text-emerald-700">{{ profile.status ?? 'ACTIVE' }}</span>
                </div>
                <div class="mt-3">
                  <span class="text-slate-500">Vai trò:</span>
                  <div class="mt-2 flex flex-wrap gap-2">
                    <span
                      v-for="role in roleLabels"
                      :key="role"
                      class="inline-flex items-center rounded-full bg-emerald-50 px-3 py-1 text-xs font-semibold uppercase tracking-wide text-emerald-700"
                    >
                      {{ role }}
                    </span>
                    <span v-if="!roleLabels.length" class="text-xs text-slate-400">—</span>
                  </div>
                </div>
              </div>
            </div>

            <div class="space-y-3">
              <p class="text-xs font-semibold uppercase tracking-wide text-emerald-500">Lịch sử tài khoản</p>
              <div class="rounded-2xl border border-slate-100 bg-slate-50/60 p-5 text-sm text-slate-700">
                <div class="flex justify-between gap-4">
                  <span class="text-slate-500">Tạo lúc:</span>
                  <span class="font-semibold text-slate-900">{{ formatDateTime(profile.createdAt) }}</span>
                </div>
                <div class="mt-2 flex justify-between gap-4">
                  <span class="text-slate-500">Cập nhật gần nhất:</span>
                  <span class="font-semibold text-slate-900">{{ formatDateTime(profile.updatedAt) }}</span>
                </div>
              </div>
            </div>
          </div>
          <div v-else class="text-sm text-slate-500">Không có dữ liệu hồ sơ.</div>
        </div>
      </section>

      <Transition name="fade">
        <div
          v-if="editModalOpen"
          class="fixed inset-0 z-[80] flex items-center justify-center bg-slate-900/45 backdrop-blur-sm"
        >
          <div class="relative max-h-[90vh] w-[min(640px,92vw)] overflow-hidden rounded-[28px] border border-emerald-100 bg-white shadow-[0_32px_120px_-55px_rgba(13,148,136,0.75)]">
            <button
              type="button"
              class="absolute right-5 top-5 flex h-10 w-10 items-center justify-center rounded-full bg-white/80 text-slate-500 shadow-sm transition hover:bg-white hover:text-slate-700"
              @click="closeEditModal"
            >
              <svg xmlns="http://www.w3.org/2000/svg" class="h-4.5 w-4.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.8">
                <path stroke-linecap="round" stroke-linejoin="round" d="m16 8-8 8m0-8 8 8" />
              </svg>
            </button>

            <form class="max-h-[90vh] overflow-y-auto px-8 py-12" @submit.prevent="handleEditSubmit">
              <div class="flex flex-col gap-2">
                <p class="text-xs font-semibold uppercase tracking-[0.35em] text-emerald-500">Chỉnh sửa hồ sơ</p>
                <h2 class="text-2xl font-semibold text-slate-900">Cập nhật thông tin cá nhân</h2>
                <p class="text-sm text-slate-500">
                  Điều chỉnh họ tên, thông tin liên hệ và các dữ liệu bổ sung. Các thay đổi sẽ được ghi nhận ngay sau khi lưu.
                </p>
              </div>

              <div class="mt-6 grid gap-4 sm:grid-cols-2">
                <div class="sm:col-span-2">
                  <label class="mb-1 block text-xs font-semibold uppercase tracking-wide text-slate-500" for="profile-edit-fullname">Họ và tên</label>
                  <input
                    id="profile-edit-fullname"
                    v-model="editForm.fullName"
                    type="text"
                    class="w-full rounded-xl border border-slate-200 bg-white px-4 py-3 text-sm text-slate-800 shadow-sm transition focus:border-emerald-400 focus:outline-none focus:ring-4 focus:ring-emerald-100/80"
                    placeholder="Nhập họ và tên"
                  />
                </div>

                <div>
                  <label class="mb-1 block text-xs font-semibold uppercase tracking-wide text-slate-500" for="profile-edit-phone">Số điện thoại</label>
                  <input
                    id="profile-edit-phone"
                    v-model="editForm.phone"
                    type="tel"
                    maxlength="10"
                    class="w-full rounded-xl border border-slate-200 bg-white px-4 py-3 text-sm text-slate-800 shadow-sm transition focus:border-emerald-400 focus:outline-none focus:ring-4 focus:ring-emerald-100/80"
                    placeholder="0987123456"
                  />
                </div>

                <div>
                  <label class="mb-1 block text-xs font-semibold uppercase tracking-wide text-slate-500" for="profile-edit-gender">Giới tính</label>
                  <select
                    id="profile-edit-gender"
                    v-model="editForm.gender"
                    class="w-full rounded-xl border border-slate-200 bg-white px-4 py-3 text-sm text-slate-800 shadow-sm transition focus:border-emerald-400 focus:outline-none focus:ring-4 focus:ring-emerald-100/80"
                  >
                    <option v-for="option in genderOptions" :key="option.value" :value="option.value">
                      {{ option.label }}
                    </option>
                  </select>
                </div>

                <div>
                  <label class="mb-1 block text-xs font-semibold uppercase tracking-wide text-slate-500" for="profile-edit-dob">Ngày sinh</label>
                  <input
                    id="profile-edit-dob"
                    v-model="editForm.dateOfBirth"
                    type="date"
                    class="w-full rounded-xl border border-slate-200 bg-white px-4 py-3 text-sm text-slate-800 shadow-sm transition focus:border-emerald-400 focus:outline-none focus:ring-4 focus:ring-emerald-100/80"
                  />
                </div>
              </div>

              <div class="mt-6 rounded-2xl border border-emerald-100 bg-emerald-50/60 p-5">
                <p class="text-xs font-semibold uppercase tracking-wide text-emerald-600">Đổi mật khẩu (tùy chọn)</p>
                <p class="mt-1 text-sm text-emerald-700/90">
                  Nhập mật khẩu hiện tại và mật khẩu mới nếu bạn muốn thay đổi thông tin đăng nhập.
                </p>
                <div class="mt-4 grid gap-4 sm:grid-cols-2">
                  <div class="sm:col-span-2">
                    <label class="mb-1 block text-xs font-semibold uppercase tracking-wide text-slate-500" for="profile-edit-current-password">Mật khẩu hiện tại</label>
                    <input
                      id="profile-edit-current-password"
                      v-model="editForm.currentPassword"
                      type="password"
                      minlength="6"
                      class="w-full rounded-xl border border-slate-200 bg-white px-4 py-3 text-sm text-slate-800 shadow-sm transition focus:border-emerald-400 focus:outline-none focus:ring-4 focus:ring-emerald-100/80"
                      placeholder="Nhập mật khẩu hiện tại"
                    />
                  </div>
                  <div>
                    <label class="mb-1 block text-xs font-semibold uppercase tracking-wide text-slate-500" for="profile-edit-new-password">Mật khẩu mới</label>
                    <input
                      id="profile-edit-new-password"
                      v-model="editForm.newPassword"
                      type="password"
                      minlength="6"
                      class="w-full rounded-xl border border-slate-200 bg-white px-4 py-3 text-sm text-slate-800 shadow-sm transition focus:border-emerald-400 focus:outline-none focus:ring-4 focus:ring-emerald-100/80"
                      placeholder="Ít nhất 6 ký tự"
                    />
                  </div>
                  <div >
                    <label class="mb-1 block text-xs font-semibold uppercase tracking-wide text-slate-500" for="profile-edit-confirm-password">Nhập lại mật khẩu mới</label>
                    <input
                      id="profile-edit-confirm-password"
                      v-model="editForm.confirmPassword"
                      type="password"
                      minlength="6"
                      class="w-full rounded-xl border border-slate-200 bg-white px-4 py-3 text-sm text-slate-800 shadow-sm transition focus:border-emerald-400 focus:outline-none focus:ring-4 focus:ring-emerald-100/80"
                      placeholder="Nhập lại mật khẩu mới"
                    />
                  </div>
                </div>
              </div>

              <!-- <p v-if="editError" class="mt-4 rounded-2xl border border-rose-100 bg-rose-50/90 px-4 py-3 text-sm text-rose-600">
                {{ editError }}
              </p> -->

              <div class="mt-8 flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-end">
                <button
                  type="button"
                  class="inline-flex items-center justify-center gap-2 rounded-full border border-slate-200 bg-white px-5 py-2 text-xs font-semibold uppercase tracking-wide text-slate-600 shadow-sm transition hover:border-slate-300 hover:bg-slate-50"
                  @click="closeEditModal"
                >
                  Hủy bỏ
                </button>
                <button
                  type="submit"
                  class="inline-flex items-center justify-center gap-2 rounded-full bg-emerald-600 px-6 py-2.5 text-xs font-semibold uppercase tracking-wide text-white shadow-sm transition hover:bg-emerald-700 disabled:cursor-not-allowed disabled:opacity-70"
                  :disabled="editSubmitting"
                >
                  <svg v-if="editSubmitting" class="h-4 w-4 animate-spin" viewBox="0 0 24 24" fill="none">
                    <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
                    <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 0 1 8-8v4l3.5-3.5L12 1v4a7 7 0 0 0-7 7h-1z"></path>
                  </svg>
                  <span>Lưu thay đổi</span>
                </button>
              </div>
            </form>
          </div>
        </div>
      </Transition>
    </main>
  </div>

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
        class="fixed top-6 right-6 z-[80] w-[min(320px,90vw)] rounded-2xl border px-5 py-4 shadow-xl backdrop-blur"
        :class="toast.type === 'success'
          ? 'border-emerald-200 bg-emerald-50/95 text-emerald-800'
          : toast.type === 'warning'
            ? 'border-amber-200 bg-amber-50/95 text-amber-700'
            : toast.type === 'info'
              ? 'border-sky-200 bg-sky-50/95 text-sky-700'
              : 'border-rose-200 bg-rose-50/95 text-rose-700'"
      >
        <div class="flex items-start gap-3">
          <span
            class="mt-0.5 flex h-9 w-9 flex-shrink-0 items-center justify-center rounded-full"
            :class="toast.type === 'success'
              ? 'bg-emerald-100 text-emerald-600'
              : toast.type === 'warning'
                ? 'bg-amber-100 text-amber-600'
                : toast.type === 'info'
                  ? 'bg-sky-100 text-sky-600'
                  : 'bg-rose-100 text-rose-600'"
          >
            <svg v-if="toast.type === 'success'" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" class="h-5 w-5" fill="none" stroke="currentColor" stroke-width="2">
              <path stroke-linecap="round" stroke-linejoin="round" d="m5 13 4 4L19 7" />
            </svg>
            <svg v-else-if="toast.type === 'info'" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" class="h-5 w-5" fill="none" stroke="currentColor" stroke-width="2">
              <path stroke-linecap="round" stroke-linejoin="round" d="M13 16h-1v-4h-1m1-4h.01M12 18a6 6 0 1 0 0-12 6 6 0 0 0 0 12Z" />
            </svg>
            <svg v-else-if="toast.type === 'warning'" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" class="h-5 w-5" fill="none" stroke="currentColor" stroke-width="2">
              <path stroke-linecap="round" stroke-linejoin="round" d="M12 9v4m0 4h.01m-8.7 1h17.38c1.16 0 1.89-1.24 1.3-2.26L13.3 4.74c-.58-1.02-2.02-1.02-2.6 0L2.3 15.74C1.7 16.76 2.44 18 3.6 18Z" />
            </svg>
            <svg v-else xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" class="h-5 w-5" fill="none" stroke="currentColor" stroke-width="2">
              <path stroke-linecap="round" stroke-linejoin="round" d="m15 9-6 6m0-6 6 6" />
            </svg>
          </span>
          <div class="flex-1">
            <p class="text-sm font-semibold">
              {{ toast.type === 'success'
                ? 'Thành công'
                : toast.type === 'info'
                  ? 'Thông báo'
                  : toast.type === 'warning'
                    ? 'Lưu ý'
                    : 'Có lỗi xảy ra' }}
            </p>
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
</template>
