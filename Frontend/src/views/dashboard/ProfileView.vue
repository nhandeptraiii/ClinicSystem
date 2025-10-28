<script setup lang="ts">
import { computed, onMounted, onBeforeUnmount, reactive, ref, watch } from 'vue';
import { useRouter } from 'vue-router';
import AdminHeader from '@/components/AdminHeader.vue';
import { useAuthStore } from '@/stores/authStore';
import { fetchCurrentUserProfile, updateCurrentUserProfile, uploadCurrentUserAvatar, type UserProfile } from '@/services/user.service';
import {
  fetchMyWorkSchedule,
  updateMyWorkSchedule,
  type WorkScheduleDay,
  type DayOfWeekKey,
  WORK_DAY_KEYS,
} from '@/services/workSchedule.service';
import { fetchClinicRooms, type ClinicRoom } from '@/services/clinicRoom.service';
import { http } from '@/services/http';
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

const WEEK_DAY_META: Array<{ key: DayOfWeekKey; label: string; short: string }> = [
  { key: 'MONDAY', label: 'Thứ 2', short: 'T2' },
  { key: 'TUESDAY', label: 'Thứ 3', short: 'T3' },
  { key: 'WEDNESDAY', label: 'Thứ 4', short: 'T4' },
  { key: 'THURSDAY', label: 'Thứ 5', short: 'T5' },
  { key: 'FRIDAY', label: 'Thứ 6', short: 'T6' },
  { key: 'SATURDAY', label: 'Thứ 7', short: 'T7' },
];

const SHIFT_META: Record<'morning' | 'afternoon', { label: string; time: string }> = {
  morning: { label: 'Buổi sáng', time: '08:00 - 12:00' },
  afternoon: { label: 'Buổi chiều', time: '13:00 - 17:00' },
};

type ShiftKey = 'morning' | 'afternoon';

interface DayScheduleState {
  morning: boolean;
  afternoon: boolean;
}

const createEmptyScheduleRecord = () =>
  WORK_DAY_KEYS.reduce((acc, key) => {
    acc[key] = { morning: false, afternoon: false };
    return acc;
  }, {} as Record<DayOfWeekKey, DayScheduleState>);

const workScheduleState = reactive<Record<DayOfWeekKey, DayScheduleState>>(createEmptyScheduleRecord());
const scheduleBaseline = ref<string>('');
const scheduleLoading = ref(false);
const scheduleSaving = ref(false);
const scheduleError = ref<string | null>(null);
const scheduleModalOpen = ref(false);
const clinicRooms = ref<ClinicRoom[]>([]);
const clinicRoomsLoading = ref(false);
const clinicRoomsError = ref<string | null>(null);
const selectedClinicRoomId = ref<number | null>(null);
const selectedClinicRoomMeta = ref<{ name: string | null; code: string | null; floor?: string | number | null } | null>(null);

const hasDoctorRole = (value: UserProfile | null) =>
  (value?.roles ?? []).some((role) => role?.name?.toUpperCase() === 'DOCTOR');

const isDoctor = computed(() => hasDoctorRole(profile.value));

const editForm = reactive({
  fullName: '',
  phone: '',
  gender: '',
  dateOfBirth: '',
  currentPassword: '',
  newPassword: '',
  confirmPassword: '',
});
const passwordSectionOpen = ref(false);

const selectedAvatarFile = ref<File | null>(null);
const avatarInputRef = ref<HTMLInputElement | null>(null);
const avatarPreview = ref<string | null>(null);
let previewObjectUrl: string | null = null;

const resetAvatarPreview = () => {
  if (previewObjectUrl) {
    URL.revokeObjectURL(previewObjectUrl);
    previewObjectUrl = null;
  }
  avatarPreview.value = null;
  selectedAvatarFile.value = null;
  if (avatarInputRef.value) {
    avatarInputRef.value.value = '';
  }
};

const handleAvatarFileChange = (event: Event) => {
  const target = event.target as HTMLInputElement;
  const files = target.files;
  if (files && files[0]) {
    const file = files[0];
    const maxSize = 5 * 1024 * 1024; // 5MB
    if (file.size > maxSize) {
      const message = 'Ảnh đại diện tối đa 5MB. Vui lòng chọn ảnh nhỏ hơn.';
      editError.value = message;
      showToast('error', message);
      resetAvatarPreview();
      return;
    }
    selectedAvatarFile.value = file;
  } else {
    resetAvatarPreview();
  }
};

watch(selectedAvatarFile, (file) => {
  if (previewObjectUrl) {
    URL.revokeObjectURL(previewObjectUrl);
    previewObjectUrl = null;
  }
  if (file) {
    previewObjectUrl = URL.createObjectURL(file);
    avatarPreview.value = previewObjectUrl;
  } else {
    avatarPreview.value = null;
  }
});

watch(
  clinicRooms,
  (rooms) => {
    if (!isDoctor.value) return;

    if (!rooms.length) {
      selectedClinicRoomId.value = null;
      selectedClinicRoomMeta.value = null;
      return;
    }

    if (selectedClinicRoomId.value != null) {
      const matched = rooms.find((room) => room.id === selectedClinicRoomId.value);
      if (matched) {
        selectedClinicRoomMeta.value = {
          name: matched.name ?? null,
          code: matched.code ?? null,
          floor: matched.floor ?? null,
        };
        return;
      }
    }

    if (selectedClinicRoomId.value == null) {
      const firstRoom = rooms[0];
      if (firstRoom) {
        selectedClinicRoomId.value = firstRoom.id ?? null;
        selectedClinicRoomMeta.value = {
          name: firstRoom.name ?? null,
          code: firstRoom.code ?? null,
          floor: firstRoom.floor ?? null,
        };
      } else {
        selectedClinicRoomMeta.value = null;
      }
    } else {
      selectedClinicRoomMeta.value = null;
    }
  },
  { flush: 'post' },
);

watch(
  selectedClinicRoomId,
  (id) => {
    if (!isDoctor.value) return;
    if (id == null) {
      selectedClinicRoomMeta.value = null;
      return;
    }
    const matched = clinicRooms.value.find((room) => room.id === id);
    if (matched) {
      selectedClinicRoomMeta.value = {
        name: matched.name ?? null,
        code: matched.code ?? null,
        floor: matched.floor ?? null,
      };
    }
  },
  { flush: 'post' },
);

const normalizeWorkDays = (days: WorkScheduleDay[] | null | undefined, defaultFullWhenEmpty: boolean) => {
  const mapped = new Map<DayOfWeekKey, WorkScheduleDay>();
  let clinicRoomId: number | null = null;
  let clinicRoomName: string | null = null;
  let clinicRoomCode: string | null = null;
  (days ?? []).forEach((item) => {
    if (!item?.dayOfWeek) return;
    const key = item.dayOfWeek.toUpperCase() as DayOfWeekKey;
    if (!WORK_DAY_KEYS.includes(key)) return;
    if (clinicRoomId == null && item.clinicRoomId != null) {
      clinicRoomId = item.clinicRoomId;
      clinicRoomName = item.clinicRoomName ?? null;
      clinicRoomCode = item.clinicRoomCode ?? null;
    }
    mapped.set(key, {
      dayOfWeek: key,
      morning: Boolean(item.morning),
      afternoon: Boolean(item.afternoon),
      clinicRoomId: item.clinicRoomId ?? clinicRoomId,
      clinicRoomName: item.clinicRoomName ?? clinicRoomName,
      clinicRoomCode: item.clinicRoomCode ?? clinicRoomCode,
    });
  });
  const useDefault = defaultFullWhenEmpty && mapped.size === 0;
  return WORK_DAY_KEYS.map<WorkScheduleDay>((key) => {
    const existing = mapped.get(key);
    if (existing) {
      if (existing.clinicRoomId == null && clinicRoomId != null) {
        existing.clinicRoomId = clinicRoomId;
        existing.clinicRoomName = clinicRoomName ?? null;
        existing.clinicRoomCode = clinicRoomCode ?? null;
      }
      return existing;
    }
    return {
      dayOfWeek: key,
      morning: useDefault,
      afternoon: useDefault,
      clinicRoomId,
      clinicRoomName,
      clinicRoomCode,
    };
  });
};

const setScheduleState = (
  days: WorkScheduleDay[] | null | undefined,
  options: { defaultFullWhenEmpty?: boolean; updateBaseline?: boolean; preserveClinicRoom?: boolean } = {},
) => {
  const normalized = normalizeWorkDays(days, options.defaultFullWhenEmpty ?? false);
  normalized.forEach(({ dayOfWeek, morning, afternoon }) => {
    workScheduleState[dayOfWeek].morning = Boolean(morning);
    workScheduleState[dayOfWeek].afternoon = Boolean(afternoon);
  });
  const firstWithRoom = normalized.find((item) => item.clinicRoomId != null);
  if (firstWithRoom && firstWithRoom.clinicRoomId != null) {
    selectedClinicRoomId.value = firstWithRoom.clinicRoomId;
    selectedClinicRoomMeta.value = {
      name: firstWithRoom.clinicRoomName ?? null,
      code: firstWithRoom.clinicRoomCode ?? null,
    };
  } else if (!options.preserveClinicRoom) {
    selectedClinicRoomId.value = null;
    selectedClinicRoomMeta.value = null;
  }
  if (options.updateBaseline) {
    scheduleBaseline.value = JSON.stringify({
      clinicRoomId: selectedClinicRoomId.value,
      days: normalized.map((item) => ({
        dayOfWeek: item.dayOfWeek,
        morning: item.morning,
        afternoon: item.afternoon,
      })),
    });
  }
  return normalized;
};

const getCurrentSchedulePayload = (): WorkScheduleDay[] =>
  WORK_DAY_KEYS.map((key) => ({
    dayOfWeek: key,
    morning: workScheduleState[key].morning,
    afternoon: workScheduleState[key].afternoon,
    clinicRoomId: selectedClinicRoomId.value,
  }));

const scheduleDirty = computed(() => {
  if (!scheduleBaseline.value) {
    return false;
  }
  return (
    JSON.stringify({
      clinicRoomId: selectedClinicRoomId.value,
      days: getCurrentSchedulePayload().map((item) => ({
        dayOfWeek: item.dayOfWeek,
        morning: item.morning,
        afternoon: item.afternoon,
      })),
    }) !== scheduleBaseline.value
  );
});

const scheduleHasAnyShift = computed(() =>
  getCurrentSchedulePayload().some((item) => item.morning || item.afternoon),
);

const scheduleMissingRoom = computed(
  () => isDoctor.value && scheduleHasAnyShift.value && !selectedClinicRoomId.value,
);

const scheduleSaveDisabled = computed(
  () =>
    scheduleSaving.value ||
    scheduleLoading.value ||
    !scheduleDirty.value ||
    (isDoctor.value && scheduleMissingRoom.value),
);

const scheduleInfoMessage = computed(() => {
  if (!scheduleHasAnyShift.value) {
    return 'Bạn chưa chọn ca làm việc nào trong tuần này.';
  }
  if (scheduleMissingRoom.value) {
    return 'Vui lòng chọn phòng khám áp dụng cho lịch làm việc.';
  }
  return null;
});

const selectedClinicRoom = computed(() => {
  if (selectedClinicRoomId.value == null) {
    return null;
  }
  return clinicRooms.value.find((room) => room.id === selectedClinicRoomId.value) ?? null;
});

const selectedClinicRoomDisplay = computed(() => {
  if (!isDoctor.value) {
    return 'Mặc định theo hệ thống';
  }
  if (clinicRoomsLoading.value) {
    return 'Đang tải...';
  }
  const room = selectedClinicRoom.value;
  if (room) {
    const floorSuffix = room.floor ? ` · Tầng ${room.floor}` : '';
    return `${room.name} (${room.code})${floorSuffix}`;
  }
  if (selectedClinicRoomMeta.value?.name) {
    const codeSuffix = selectedClinicRoomMeta.value.code ? ` (${selectedClinicRoomMeta.value.code})` : '';
    const floorSuffix = selectedClinicRoomMeta.value.floor ? ` · Tầng ${selectedClinicRoomMeta.value.floor}` : '';
    return `${selectedClinicRoomMeta.value.name}${codeSuffix}${floorSuffix}`;
  }
  return 'Chưa chọn';
});

const scheduleSummary = computed(() =>
  WEEK_DAY_META.map(({ key, label, short }) => {
    const state = workScheduleState[key];
    const hasMorning = Boolean(state?.morning);
    const hasAfternoon = Boolean(state?.afternoon);
    const hasAny = hasMorning || hasAfternoon;
    const statusLabel =
      hasMorning && hasAfternoon
        ? 'Buổi sáng & chiều'
        : hasMorning
          ? SHIFT_META.morning.label
          : hasAfternoon
            ? SHIFT_META.afternoon.label
            : 'Nghỉ';
    const timeLabel =
      hasMorning && hasAfternoon
        ? `${SHIFT_META.morning.time} · ${SHIFT_META.afternoon.time}`
        : hasMorning
          ? SHIFT_META.morning.time
          : hasAfternoon
            ? SHIFT_META.afternoon.time
            : 'Không có ca';
    return {
      key,
      label,
      short,
      hasMorning,
      hasAfternoon,
      hasAny,
      statusLabel,
      timeLabel,
    };
  }),
);

const resetScheduleToBaseline = () => {
  if (!scheduleBaseline.value) return;
  try {
    const parsed = JSON.parse(scheduleBaseline.value) as {
      clinicRoomId: number | null;
      days: WorkScheduleDay[];
    };
    selectedClinicRoomId.value = parsed.clinicRoomId ?? null;
    setScheduleState(parsed.days, { updateBaseline: false, preserveClinicRoom: true });
    scheduleError.value = null;
  } catch (error) {
    console.error('Không thể khôi phục lịch làm việc từ trạng thái gần nhất.', error);
  }
};

const closeScheduleModal = () => {
  if (scheduleModalOpen.value && scheduleDirty.value) {
    resetScheduleToBaseline();
  }
  scheduleError.value = null;
  scheduleModalOpen.value = false;
};

const ensureClinicRoomsLoaded = async () => {
  if (!isDoctor.value) return;
  if (clinicRooms.value.length || clinicRoomsLoading.value) return;
  clinicRoomsLoading.value = true;
  clinicRoomsError.value = null;
  try {
    clinicRooms.value = await fetchClinicRooms();
  } catch (error: any) {
    const message =
      error?.response?.data?.message ?? error?.message ?? 'Không thể tải danh sách phòng khám. Vui lòng thử lại.';
    clinicRoomsError.value = message;
    showToast('error', message);
  } finally {
    clinicRoomsLoading.value = false;
  }
};

const openScheduleModal = async () => {
  if (!isDoctor.value) return;
  await ensureClinicRoomsLoaded();
  scheduleError.value = null;
  scheduleModalOpen.value = true;
};

const applyDefaultFullSchedule = async () => {
  await ensureClinicRoomsLoaded();
  if (isDoctor.value) {
    WORK_DAY_KEYS.forEach((day) => {
      workScheduleState[day].morning = true;
      workScheduleState[day].afternoon = true;
    });
    if (selectedClinicRoomId.value == null) {
      const firstRoom = clinicRooms.value[0];
      selectedClinicRoomId.value = firstRoom?.id ?? null;
      if (firstRoom) {
        selectedClinicRoomMeta.value = {
          name: firstRoom.name ?? null,
          code: firstRoom.code ?? null,
          floor: firstRoom.floor ?? null,
        };
      }
    } else {
      const matched = clinicRooms.value.find((room) => room.id === selectedClinicRoomId.value);
      if (matched) {
        selectedClinicRoomMeta.value = {
          name: matched.name ?? null,
          code: matched.code ?? null,
          floor: matched.floor ?? null,
        };
      }
    }
  } else {
    setScheduleState(null, { defaultFullWhenEmpty: true });
  }
  scheduleError.value = null;
};

const clearWorkSchedule = () => {
  const emptySchedule = WORK_DAY_KEYS.map<WorkScheduleDay>((key) => ({
    dayOfWeek: key,
    morning: false,
    afternoon: false,
  }));
  setScheduleState(emptySchedule);
  scheduleError.value = null;
};

const toggleScheduleSlot = (day: DayOfWeekKey, shift: ShiftKey) => {
  if (scheduleSaving.value) return;
  const state = workScheduleState[day];
  if (!state) return;
  const next = !state[shift];
  state[shift] = next;
  scheduleError.value = null;
};

const applyFixedScheduleForNonDoctor = () => {
  setScheduleState(null, { defaultFullWhenEmpty: true, updateBaseline: true });
  selectedClinicRoomId.value = null;
  scheduleError.value = null;
};

const loadWorkSchedule = async () => {
  await ensureClinicRoomsLoaded();
  scheduleLoading.value = true;
  scheduleError.value = null;
  try {
    const response = await fetchMyWorkSchedule();
    setScheduleState(response, { defaultFullWhenEmpty: false, updateBaseline: true });
    if (!selectedClinicRoomId.value && clinicRooms.value.length) {
      selectedClinicRoomId.value = clinicRooms.value[0]?.id ?? null;
    }
  } catch (error: any) {
    const message =
      error?.response?.data?.message ??
      error?.message ??
      'Không thể tải lịch làm việc hiện tại. Vui lòng thử lại.';
    scheduleError.value = message;
    showToast('error', message);
  } finally {
    scheduleLoading.value = false;
  }
};

const saveWorkSchedule = async () => {
  if (scheduleSaveDisabled.value) {
    return;
  }
  await ensureClinicRoomsLoaded();
  if (scheduleMissingRoom.value) {
    const message = 'Vui lòng chọn phòng khám trước khi lưu.';
    scheduleError.value = message;
    showToast('error', message);
    return;
  }
  scheduleSaving.value = true;
  scheduleError.value = null;
  try {
    const payload = { days: getCurrentSchedulePayload(), clinicRoomId: selectedClinicRoomId.value };
    const updated = await updateMyWorkSchedule(payload);
    setScheduleState(updated, { defaultFullWhenEmpty: false, updateBaseline: true });
    if (!selectedClinicRoomId.value && clinicRooms.value.length) {
      selectedClinicRoomId.value = clinicRooms.value[0]?.id ?? null;
    }
    showToast('success', 'Đã cập nhật lịch làm việc của bạn.');
    if (scheduleModalOpen.value) {
      closeScheduleModal();
    }
  } catch (error: any) {
    const message =
      error?.response?.data?.message ??
      error?.message ??
      'Không thể cập nhật lịch làm việc. Vui lòng thử lại.';
    scheduleError.value = message;
    showToast('error', message);
  } finally {
    scheduleSaving.value = false;
  }
};

const rawBaseUrl =
  http.defaults.baseURL ?? (typeof window !== 'undefined' ? window.location.origin : '');
const apiBaseUrl = rawBaseUrl.replace(/\/$/, '');

const resolvedAvatarUrl = computed(() => {
  if (avatarPreview.value) {
    return avatarPreview.value;
  }
  const url = profile.value?.avatarUrl ?? '';
  if (!url) {
    return null;
  }
  if (/^https?:\/\//i.test(url)) {
    return url;
  }
  return `${apiBaseUrl}/${url.replace(/^\/+/, '')}`;
});

const avatarInitial = computed(() => {
  const name = profile.value?.fullName?.trim();
  if (name) {
    return name.charAt(0).toUpperCase();
  }
  return profile.value?.email?.charAt(0).toUpperCase() ?? 'U';
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
  passwordSectionOpen.value = false;
  resetAvatarPreview();
  editModalOpen.value = true;
};

const closeEditModal = () => {
  editModalOpen.value = false;
  editSubmitting.value = false;
  editError.value = null;
  resetAvatarPreview();
  editForm.currentPassword = '';
  editForm.newPassword = '';
  editForm.confirmPassword = '';
  passwordSectionOpen.value = false;
};

const loadProfile = async () => {
  profileLoading.value = true;
  profileError.value = null;
  try {
    const fetched = await fetchCurrentUserProfile();
    profile.value = fetched;
    if (hasDoctorRole(fetched)) {
      await loadWorkSchedule();
    } else {
      clinicRooms.value = [];
      clinicRoomsError.value = null;
      applyFixedScheduleForNonDoctor();
    }
  } catch (error: any) {
    const message =
      error?.response?.data?.message ??
      error?.message ??
      'Không thể tải thông tin cá nhân. Vui lòng thử lại.';
    profileError.value = message;
    showToast('error', message);
    applyFixedScheduleForNonDoctor();
    clinicRooms.value = [];
    clinicRoomsError.value = null;
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

    if (selectedAvatarFile.value) {
      try {
        const updated = await uploadCurrentUserAvatar(selectedAvatarFile.value);
        profile.value = updated;
      } catch (error: any) {
        const message =
          error?.response?.data?.message ?? error?.message ?? 'Không thể cập nhật ảnh đại diện. Vui lòng thử lại.';
        editError.value = message;
        showToast('error', message);
        return;
      }
    } else {
      profile.value = await fetchCurrentUserProfile();
    }

    if (profile.value) {
      if (hasDoctorRole(profile.value)) {
        await loadWorkSchedule();
      } else {
        applyFixedScheduleForNonDoctor();
      }
    }

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

const togglePasswordSection = () => {
  passwordSectionOpen.value = !passwordSectionOpen.value;
  if (!passwordSectionOpen.value) {
    editForm.currentPassword = '';
    editForm.newPassword = '';
    editForm.confirmPassword = '';
  }
};

onBeforeUnmount(() => {
  resetAvatarPreview();
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
          <div v-if="profile" class="flex flex-col items-center gap-4 border-b border-emerald-100 pb-6 md:flex-row md:items-center md:gap-6">
            <div class="relative h-24 w-24 flex-shrink-0 overflow-hidden rounded-full border border-emerald-100 bg-emerald-50 shadow-sm">
              <img
                v-if="resolvedAvatarUrl"
                :src="resolvedAvatarUrl"
                alt="Avatar"
                class="h-full w-full object-cover"
              />
              <div v-else class="flex h-full w-full items-center justify-center bg-emerald-100 text-xl font-semibold text-emerald-700">
                {{ avatarInitial }}
              </div>
            </div>
            <div class="text-center md:text-left">
              <p class="text-lg font-semibold text-slate-900">{{ profile.fullName || profile.email }}</p>
              <p class="mt-1 text-sm text-slate-500">Ảnh đại diện có thể được cập nhật trong phần chỉnh sửa hồ sơ.</p>
            </div>
          </div>

          <div v-if="profileLoading" class="grid gap-4 md:grid-cols-2">
            <div v-for="skeleton in 4" :key="skeleton" class="animate-pulse rounded-2xl border border-slate-100 bg-slate-50 p-5">
              <div class="h-4 w-32 rounded-full bg-slate-200/80"></div>
              <div class="mt-3 h-3 w-24 rounded-full bg-slate-200/60"></div>
              <div class="mt-4 h-3 w-full rounded-full bg-slate-200/40"></div>
            </div>
          </div>
          <div v-else-if="profile" class="grid gap-6 md:grid-cols-2">
            <div class="space-y-3">
              <p class="text-sm mt-3 font-semibold uppercase tracking-wide text-emerald-500">Thông tin liên hệ</p>
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
              <p class="text-sm mt-3 font-semibold uppercase tracking-wide text-emerald-500">Thông tin bổ sung</p>
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
              <p class="text-sm font-semibold uppercase tracking-wide text-emerald-500">Lịch sử tài khoản</p>
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

            <div class="md:col-span-2">
              <div class="rounded-3xl border border-emerald-100 bg-white p-6 shadow-sm">
                <div class="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
                  <div>
                    <p class="text-xs font-semibold uppercase tracking-wide text-emerald-500">Lịch làm việc</p>
                    <h3 class="mt-1 text-lg font-semibold text-slate-900">
                      {{ isDoctor ? 'Tổng quan ca làm việc' : 'Lịch làm việc cố định' }}
                    </h3>
                    <p class="mt-1 text-sm text-slate-500">
                      {{ isDoctor
                        ? 'Xem nhanh các ca làm việc đã đăng ký trong tuần cùng phòng khám áp dụng.'
                        : 'Ca làm việc mặc định: 08:00 - 17:00 từ Thứ 2 đến Thứ 7. Liên hệ quản trị viên nếu cần điều chỉnh.' }}
                    </p>
                  </div>
                  <div v-if="isDoctor" class="flex flex-wrap items-center gap-2">
                    <button
                      type="button"
                      class="inline-flex items-center justify-center gap-2 rounded-full bg-emerald-600 px-5 py-2 text-xs font-semibold uppercase tracking-wide text-white shadow-sm transition hover:bg-emerald-700 disabled:cursor-not-allowed disabled:opacity-70"
                      :disabled="scheduleLoading"
                      @click="openScheduleModal"
                    >
                      <svg xmlns="http://www.w3.org/2000/svg" class="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.8">
                        <path stroke-linecap="round" stroke-linejoin="round" d="M16.862 4.487 19.513 7.138m-2.651-2.651L7.5 16.5l-3 3 3-3 9.862-9.862Zm0 0 1.76-1.76a1.5 1.5 0 0 1 2.122 0l.378.378a1.5 1.5 0 0 1 0 2.122l-1.76 1.76m-2.5-2.5-9.862 9.862m0 0H4.5v-2.414" />
                      </svg>
                      Chỉnh sửa lịch
                    </button>
                  </div>
                </div>

                <div class="mt-6 rounded-2xl border border-emerald-100 bg-emerald-50/40 p-5 text-sm text-slate-700">
                  <div class="flex justify-between gap-4">
                    <span class="text-slate-500">Phòng khám áp dụng:</span>
                    <span class="text-right font-semibold text-slate-900">{{ selectedClinicRoomDisplay }}</span>
                  </div>
                  <p v-if="isDoctor && clinicRoomsError" class="mt-2 text-xs text-rose-600">
                    {{ clinicRoomsError }}
                  </p>
                  <p
                    v-else-if="isDoctor && !clinicRoomsLoading && !clinicRooms.length"
                    class="mt-2 text-xs text-amber-600"
                  >
                    Hiện chưa có phòng khám nào. Vui lòng thêm phòng trước khi cấu hình lịch.
                  </p>
                  <p v-else-if="!isDoctor" class="mt-2 text-xs text-slate-500">
                    Lịch làm việc mặc định áp dụng cho vai trò hiện tại.
                  </p>
                </div>

                <div class="mt-6">
                  <div v-if="scheduleLoading" class="grid gap-3 sm:grid-cols-2">
                    <div
                      v-for="day in WORK_DAY_KEYS"
                      :key="`schedule-skeleton-${day}`"
                      class="animate-pulse rounded-2xl border border-slate-100 bg-slate-50 p-4"
                    >
                      <div class="h-4 w-24 rounded-full bg-slate-200/80"></div>
                      <div class="mt-3 h-3 w-full rounded-full bg-slate-200/50"></div>
                      <div class="mt-2 h-3 w-3/4 rounded-full bg-slate-200/40"></div>
                    </div>
                  </div>
                  <template v-else>
                    <div v-if="isDoctor">
                      <div v-if="scheduleHasAnyShift" class="grid gap-3 sm:grid-cols-2">
                        <div
                          v-for="day in scheduleSummary"
                          :key="`schedule-summary-${day.key}`"
                          class="rounded-2xl border border-slate-200 bg-white/90 px-5 py-4 shadow-sm transition hover:border-emerald-200 hover:bg-emerald-50/60"
                        >
                          <div class="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
                            <div>
                              <p class="text-sm font-semibold text-slate-900">
                                {{ day.label }}
                                <span class="ml-2 rounded-full bg-emerald-100 px-2 py-0.5 text-[11px] font-semibold text-emerald-600">{{ day.short }}</span>
                              </p>
                              <p class="mt-1 text-xs text-slate-500">{{ day.statusLabel }}</p>
                            </div>
                            <div class="text-right text-xs font-medium" :class="day.hasAny ? 'text-emerald-600' : 'text-slate-400'">
                              {{ day.hasAny ? day.timeLabel : '—' }}
                            </div>
                          </div>
                        </div>
                      </div>
                      <div
                        v-else
                        class="rounded-2xl border border-amber-200 bg-amber-50/80 px-5 py-4 text-sm text-amber-700"
                      >
                        Chưa có ca làm việc nào được đăng ký trong tuần này.
                      </div>
                    </div>
                    <div
                      v-else
                      class="rounded-2xl border border-emerald-100 bg-emerald-50/60 px-5 py-4 text-sm text-emerald-700"
                    >
                      <p>
                        Lịch làm việc cho vai trò hiện tại được thiết lập cố định:
                        <span class="font-semibold text-emerald-800">08:00 - 17:00</span>,
                        từ <span class="font-semibold text-emerald-800">Thứ 2</span> đến <span class="font-semibold text-emerald-800">Thứ 7</span>.
                      </p>
                      <p class="mt-2 text-xs text-emerald-600/80">
                        Vui lòng liên hệ quản trị viên nếu bạn cần điều chỉnh lịch làm việc đặc biệt.
                      </p>
                    </div>
                  </template>
                </div>

                <p v-if="scheduleInfoMessage && isDoctor" class="mt-4 text-xs text-amber-600">
                  {{ scheduleInfoMessage }}
                </p>
                <p
                  v-if="scheduleError && isDoctor && !scheduleModalOpen"
                  class="mt-4 rounded-2xl border border-rose-100 bg-rose-50/90 px-4 py-3 text-sm text-rose-600"
                >
                  {{ scheduleError }}
                </p>
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

              <div class="mt-6 flex flex-wrap items-center gap-4 rounded-2xl border border-emerald-100 bg-emerald-50/60 p-5">
                <div class="relative h-20 w-20 flex-shrink-0 overflow-hidden rounded-full border border-emerald-100 bg-emerald-50 shadow-sm">
                  <img
                    v-if="resolvedAvatarUrl"
                    :src="resolvedAvatarUrl"
                    alt="Avatar preview"
                    class="h-full w-full object-cover"
                  />
                  <div v-else class="flex h-full w-full items-center justify-center bg-emerald-100 text-lg font-semibold text-emerald-700">
                    {{ avatarInitial }}
                  </div>
                </div>
                <div class="flex-1">
                  <label class="text-xs font-semibold uppercase tracking-wide text-emerald-600" for="profile-edit-avatar">Ảnh đại diện</label>
                  <p class="mt-1 text-xs text-emerald-700/80">Chọn ảnh mới (PNG, JPG, JPEG, GIF, WEBP; tối đa ~5MB).</p>
                  <div class="mt-2 flex flex-wrap items-center gap-3">
                    <label
                      for="profile-edit-avatar"
                      class="inline-flex cursor-pointer items-center gap-2 rounded-full border border-emerald-200 bg-white px-4 py-2 text-xs font-semibold uppercase tracking-wide text-emerald-600 shadow-sm transition hover:border-emerald-300 hover:bg-emerald-50"
                    >
                      <svg xmlns="http://www.w3.org/2000/svg" class="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.8">
                        <path stroke-linecap="round" stroke-linejoin="round" d="M12 4v16m8-8H4" />
                      </svg>
                      Chọn ảnh
                    </label>
                    <button
                      v-if="selectedAvatarFile"
                      type="button"
                      class="inline-flex items-center gap-2 rounded-full border border-emerald-200 bg-white px-4 py-2 text-xs font-semibold uppercase tracking-wide text-emerald-600 shadow-sm transition hover:border-emerald-300 hover:bg-emerald-50"
                      @click="resetAvatarPreview"
                    >
                      <svg xmlns="http://www.w3.org/2000/svg" class="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.8">
                        <path stroke-linecap="round" stroke-linejoin="round" d="m19 7-10 10-4-4" />
                      </svg>
                      Bỏ chọn
                    </button>
                  </div>
                  <input
                    id="profile-edit-avatar"
                    type="file"
                    accept="image/*"
                    ref="avatarInputRef"
                    class="hidden"
                    @change="handleAvatarFileChange"
                  />
                </div>
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
                <button
                  type="button"
                  class="flex w-full items-center justify-between text-left text-xs font-semibold uppercase tracking-wide text-emerald-600"
                  @click="togglePasswordSection"
                >
                  <span>Đổi mật khẩu (tùy chọn)</span>
                  <svg
                    xmlns="http://www.w3.org/2000/svg"
                    viewBox="0 0 24 24"
                    class="h-4 w-4 transform transition"
                    :class="passwordSectionOpen ? 'rotate-90' : ''"
                    fill="none"
                    stroke="currentColor"
                    stroke-width="1.8"
                  >
                    <path stroke-linecap="round" stroke-linejoin="round" d="M9 5l7 7-7 7" />
                  </svg>
                </button>
                <p class="mt-1 text-sm text-emerald-700/90">
                  Nhập mật khẩu hiện tại và mật khẩu mới nếu bạn muốn thay đổi thông tin đăng nhập.
                </p>
                <Transition
                  enter-active-class="transition duration-200 ease-out"
                  enter-from-class="opacity-0 -translate-y-1"
                  enter-to-class="opacity-100 translate-y-0"
                  leave-active-class="transition duration-150 ease-in"
                  leave-from-class="opacity-100 translate-y-0"
                  leave-to-class="opacity-0 -translate-y-1"
                >
                  <div v-if="passwordSectionOpen" class="mt-4 grid gap-4 sm:grid-cols-2">
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
                    <div>
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
                </Transition>
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
      <Transition name="fade">
        <div
          v-if="scheduleModalOpen"
          class="fixed inset-0 z-[85] flex items-center justify-center bg-slate-900/45 backdrop-blur-sm"
        >
          <div class="relative max-h-[90vh] w-[min(760px,92vw)] overflow-hidden rounded-[28px] border border-emerald-100 bg-white shadow-[0_32px_120px_-55px_rgba(13,148,136,0.75)]">
            <button
              type="button"
              class="absolute right-5 top-5 flex h-10 w-10 items-center justify-center rounded-full bg-white/80 text-slate-500 shadow-sm transition hover:bg-white hover:text-slate-700"
              @click="closeScheduleModal"
            >
              <svg xmlns="http://www.w3.org/2000/svg" class="h-4.5 w-4.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.8">
                <path stroke-linecap="round" stroke-linejoin="round" d="m16 8-8 8m0-8 8 8" />
              </svg>
            </button>

            <div class="max-h-[90vh] overflow-y-auto px-8 py-12">
              <div class="flex flex-col gap-2">
                <p class="text-xs font-semibold uppercase tracking-[0.35em] text-emerald-500">Chỉnh sửa lịch làm việc</p>
                <h2 class="text-2xl font-semibold text-slate-900">Điều chỉnh ca làm việc trong tuần</h2>
                <p class="text-sm text-slate-500">
                  Chọn ca làm việc buổi sáng và chiều cho từng ngày từ Thứ 2 đến Thứ 7. Các thay đổi sẽ áp dụng sau khi lưu.
                </p>
              </div>

              <div class="mt-6 flex flex-wrap items-center gap-2">
                <button
                  type="button"
                  class="inline-flex items-center gap-2 rounded-full border border-emerald-200 bg-white px-4 py-2 text-xs font-semibold uppercase tracking-wide text-emerald-600 shadow-sm transition hover:border-emerald-300 hover:bg-emerald-50 disabled:cursor-not-allowed disabled:opacity-60"
                  :disabled="scheduleSaving"
                  @click="applyDefaultFullSchedule"
                >
                  <svg xmlns="http://www.w3.org/2000/svg" class="h-3.5 w-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.8">
                    <path stroke-linecap="round" stroke-linejoin="round" d="M4 12h16m-8-8v16" />
                  </svg>
                  8h - 17h cả tuần
                </button>
                <button
                  type="button"
                  class="inline-flex items-center gap-2 rounded-full border border-rose-200 bg-white px-4 py-2 text-xs font-semibold uppercase tracking-wide text-rose-600 shadow-sm transition hover:border-rose-300 hover:bg-rose-50 disabled:cursor-not-allowed disabled:opacity-60"
                  :disabled="scheduleSaving"
                  @click="clearWorkSchedule"
                >
                  <svg xmlns="http://www.w3.org/2000/svg" class="h-3.5 w-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.8">
                    <path stroke-linecap="round" stroke-linejoin="round" d="m6 18 12-12M6 6l12 12" />
                  </svg>
                  Để trống
                </button>
              </div>

              <div class="mt-6 space-y-4">
                <div class="flex flex-col gap-1">
                  <label class="text-xs font-semibold uppercase tracking-wide text-slate-500" for="profile-schedule-clinic-room">
                    Phòng khám áp dụng
                  </label>
                  <select
                    id="profile-schedule-clinic-room"
                    v-model="selectedClinicRoomId"
                    :disabled="clinicRoomsLoading || !clinicRooms.length || scheduleSaving"
                    class="w-full rounded-xl border border-slate-200 bg-white px-4 py-2.5 text-sm text-slate-800 shadow-sm transition focus:border-emerald-400 focus:outline-none focus:ring-4 focus:ring-emerald-100/80 disabled:cursor-not-allowed disabled:opacity-70"
                  >
                    <option :value="null">Chọn phòng khám</option>
                    <option v-for="room in clinicRooms" :key="`schedule-modal-clinic-room-${room.id}`" :value="room.id">
                      {{ room.name }} ({{ room.code }}){{ room.floor ? ` · Tầng ${room.floor}` : '' }}
                    </option>
                  </select>
                  <p v-if="clinicRoomsLoading" class="text-xs text-slate-500">Đang tải danh sách phòng khám...</p>
                  <p
                    v-else-if="!clinicRoomsLoading && !clinicRooms.length"
                    class="text-xs text-amber-600"
                  >
                    Hiện chưa có phòng khám nào. Vui lòng thêm phòng trước khi cấu hình lịch.
                  </p>
                </div>

                <p
                  v-if="clinicRoomsError"
                  class="rounded-2xl border border-amber-200 bg-amber-50/90 px-4 py-3 text-xs text-amber-700"
                >
                  {{ clinicRoomsError }}
                </p>

                <div v-if="scheduleLoading" class="grid gap-3 sm:grid-cols-2">
                  <div
                    v-for="day in WORK_DAY_KEYS"
                    :key="`schedule-modal-skeleton-${day}`"
                    class="animate-pulse rounded-2xl border border-slate-100 bg-slate-50 p-4"
                  >
                    <div class="h-4 w-24 rounded-full bg-slate-200/80"></div>
                    <div class="mt-3 h-3 w-full rounded-full bg-slate-200/50"></div>
                    <div class="mt-2 h-3 w-3/4 rounded-full bg-slate-200/40"></div>
                  </div>
                </div>
                <div v-else class="space-y-4">
                  <div
                    v-for="day in WEEK_DAY_META"
                    :key="`schedule-modal-day-${day.key}`"
                    class="rounded-2xl border border-slate-200 bg-white/90 px-5 py-4 shadow-sm transition hover:border-emerald-200 hover:bg-emerald-50/50"
                  >
                    <div class="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
                      <div>
                        <p class="text-sm font-semibold text-slate-900">
                          {{ day.label }}
                          <span class="ml-2 rounded-full bg-emerald-100 px-2 py-0.5 text-[11px] font-semibold text-emerald-600">{{ day.short }}</span>
                        </p>
                        <p class="text-xs text-slate-500">Chọn ca làm việc cho ngày này.</p>
                      </div>
                      <div class="flex flex-wrap items-center gap-2">
                        <button
                          type="button"
                          class="inline-flex flex-col items-center justify-center rounded-2xl border px-4 py-2 text-xs font-semibold transition focus:outline-none focus-visible:ring-2 focus-visible:ring-emerald-400"
                          :class="workScheduleState[day.key].morning
                            ? 'border-emerald-300 bg-emerald-50 text-emerald-700 shadow-sm'
                            : 'border-slate-200 bg-white text-slate-600 hover:border-emerald-200 hover:bg-emerald-50/60'"
                          :aria-pressed="workScheduleState[day.key].morning"
                          :disabled="scheduleSaving"
                          @click="toggleScheduleSlot(day.key, 'morning')"
                        >
                          <span>{{ SHIFT_META.morning.label }}</span>
                          <span class="mt-0.5 text-[11px] font-normal tracking-wide text-slate-500">{{ SHIFT_META.morning.time }}</span>
                        </button>
                        <button
                          type="button"
                          class="inline-flex flex-col items-center justify-center rounded-2xl border px-4 py-2 text-xs font-semibold transition focus:outline-none focus-visible:ring-2 focus-visible:ring-emerald-400"
                          :class="workScheduleState[day.key].afternoon
                            ? 'border-emerald-300 bg-emerald-50 text-emerald-700 shadow-sm'
                            : 'border-slate-200 bg-white text-slate-600 hover:border-emerald-200 hover:bg-emerald-50/60'"
                          :aria-pressed="workScheduleState[day.key].afternoon"
                          :disabled="scheduleSaving"
                          @click="toggleScheduleSlot(day.key, 'afternoon')"
                        >
                          <span>{{ SHIFT_META.afternoon.label }}</span>
                          <span class="mt-0.5 text-[11px] font-normal tracking-wide text-slate-500">{{ SHIFT_META.afternoon.time }}</span>
                        </button>
                      </div>
                    </div>
                  </div>
                </div>
              </div>

              <p v-if="scheduleInfoMessage" class="mt-4 text-xs text-amber-600">
                {{ scheduleInfoMessage }}
              </p>
              <p
                v-if="scheduleError"
                class="mt-4 rounded-2xl border border-rose-100 bg-rose-50/90 px-4 py-3 text-sm text-rose-600"
              >
                {{ scheduleError }}
              </p>

              <div class="mt-6 flex flex-wrap items-center justify-end gap-3">
                <button
                  type="button"
                  class="inline-flex items-center gap-2 rounded-full border border-slate-200 bg-white px-5 py-2 text-xs font-semibold uppercase tracking-wide text-slate-600 shadow-sm transition hover:border-slate-300 hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-60"
                  :disabled="scheduleSaving || !scheduleDirty"
                  @click="resetScheduleToBaseline"
                >
                  Hoàn tác
                </button>
                <button
                  type="button"
                  class="inline-flex items-center gap-2 rounded-full bg-emerald-600 px-6 py-2 text-xs font-semibold uppercase tracking-wide text-white shadow-sm transition hover:bg-emerald-700 disabled:cursor-not-allowed disabled:opacity-70"
                  :disabled="scheduleSaveDisabled"
                  @click="saveWorkSchedule"
                >
                  <svg
                    v-if="scheduleSaving"
                    class="h-4 w-4 animate-spin"
                    viewBox="0 0 24 24"
                    fill="none"
                  >
                    <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
                    <path
                      class="opacity-75"
                      fill="currentColor"
                      d="M4 12a8 8 0 0 1 8-8v4l3.5-3.5L12 1v4a7 7 0 0 0-7 7H4z"
                    ></path>
                  </svg>
                  <span>{{ scheduleSaving ? 'Đang lưu...' : 'Lưu lịch làm việc' }}</span>
                </button>
              </div>
            </div>
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
