<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue';
import { useRouter } from 'vue-router';
import AdminHeader from '@/components/AdminHeader.vue';
import { useAuthStore } from '@/stores/authStore';
import { useToast, type ToastType } from '@/composables/useToast';
import { fetchStaff, type StaffMember, type StaffRole } from '@/services/staff.service';
import { fetchClinicRooms, type ClinicRoom } from '@/services/clinicRoom.service';
import {
  fetchStaffWorkSchedule,
  updateStaffWorkSchedule,
  type WorkScheduleDay,
  type DayOfWeekKey,
  WORK_DAY_KEYS,
} from '@/services/workSchedule.service';
import { http } from '@/services/http';

type ShiftKey = 'morning' | 'afternoon';

interface DayScheduleState {
  morning: boolean;
  afternoon: boolean;
}

const supportedRoles: StaffRole[] = ['ADMIN', 'DOCTOR', 'NURSE', 'CASHIER', 'PHARMACIST'];

const roleDisplayMap: Record<StaffRole, { label: string; desc: string }> = {
  ADMIN: { label: 'Admin', desc: 'Quản trị toàn hệ thống.' },
  DOCTOR: { label: 'Bác sĩ', desc: 'Khám chữa bệnh, cập nhật hồ sơ.' },
  NURSE: { label: 'Điều dưỡng', desc: 'Hỗ trợ chăm sóc và theo dõi.' },
  CASHIER: { label: 'Thu ngân', desc: 'Xử lý thanh toán, hóa đơn.' },
  PHARMACIST: { label: 'Dược sĩ', desc: 'Quản lý kho thuốc.' },
};

const WEEK_DAY_META: Array<{ key: DayOfWeekKey; label: string; short: string }> = [
  { key: 'MONDAY', label: 'Thứ 2', short: 'T2' },
  { key: 'TUESDAY', label: 'Thứ 3', short: 'T3' },
  { key: 'WEDNESDAY', label: 'Thứ 4', short: 'T4' },
  { key: 'THURSDAY', label: 'Thứ 5', short: 'T5' },
  { key: 'FRIDAY', label: 'Thứ 6', short: 'T6' },
  { key: 'SATURDAY', label: 'Thứ 7', short: 'T7' },
];

const SHIFT_META: Record<ShiftKey, { label: string; time: string }> = {
  morning: { label: 'Buổi sáng', time: '08:00 - 12:00' },
  afternoon: { label: 'Buổi chiều', time: '13:00 - 17:00' },
};

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

const staffItems = ref<StaffMember[]>([]);
const staffLoading = ref(false);
const staffError = ref<string | null>(null);

type NonDoctorRole = Exclude<StaffRole, 'DOCTOR'>;
const doctorSearchTerm = ref('');
const otherSearchTerm = ref('');
const otherRoleOptions = supportedRoles.filter((role) => role !== 'DOCTOR') as NonDoctorRole[];
const selectedOtherRole = ref<NonDoctorRole | 'ALL'>('ALL');
const otherRoleFilters = computed<(NonDoctorRole | 'ALL')[]>(() => ['ALL', ...otherRoleOptions]);

const selectedStaff = ref<StaffMember | null>(null);

const scheduleLoading = ref(false);
const scheduleSaving = ref(false);
const scheduleError = ref<string | null>(null);
const scheduleModalOpen = ref(false);
const scheduleBaseline = ref('');

const clinicRooms = ref<ClinicRoom[]>([]);
const clinicRoomsLoading = ref(false);
const clinicRoomsError = ref<string | null>(null);
const selectedClinicRoomId = ref<number | null>(null);

const scheduleClinicRoomFallback = reactive<{ name: string | null; code: string | null; floor: string | number | null }>({
  name: null,
  code: null,
  floor: null,
});

const createEmptyScheduleRecord = () =>
  WORK_DAY_KEYS.reduce((acc, key) => {
    acc[key] = { morning: false, afternoon: false };
    return acc;
  }, {} as Record<DayOfWeekKey, DayScheduleState>);

const scheduleState = reactive<Record<DayOfWeekKey, DayScheduleState>>(createEmptyScheduleRecord());

const resetScheduleState = () => {
  WORK_DAY_KEYS.forEach((day) => {
    scheduleState[day].morning = false;
    scheduleState[day].afternoon = false;
  });
  selectedClinicRoomId.value = null;
  scheduleClinicRoomFallback.name = null;
  scheduleClinicRoomFallback.code = null;
  scheduleClinicRoomFallback.floor = null;
  scheduleBaseline.value = '';
  scheduleError.value = null;
};

const hasDoctorRole = (member: StaffMember | null | undefined) => {
  if (!member?.roles?.length) return false;
  return member.roles.some((role) => role?.toUpperCase() === 'DOCTOR');
};

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
    scheduleState[dayOfWeek].morning = Boolean(morning);
    scheduleState[dayOfWeek].afternoon = Boolean(afternoon);
  });
  const firstWithRoom = normalized.find((item) => item.clinicRoomId != null);
  if (firstWithRoom && firstWithRoom.clinicRoomId != null) {
    selectedClinicRoomId.value = firstWithRoom.clinicRoomId;
    scheduleClinicRoomFallback.name = firstWithRoom.clinicRoomName ?? null;
    scheduleClinicRoomFallback.code = firstWithRoom.clinicRoomCode ?? null;
  } else if (!options.preserveClinicRoom) {
    selectedClinicRoomId.value = null;
    scheduleClinicRoomFallback.name = null;
    scheduleClinicRoomFallback.code = null;
  }
  if (!options.preserveClinicRoom && (!firstWithRoom || firstWithRoom.clinicRoomId == null)) {
    scheduleClinicRoomFallback.floor = null;
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
    morning: scheduleState[key].morning,
    afternoon: scheduleState[key].afternoon,
    clinicRoomId: selectedClinicRoomId.value,
  }));

const scheduleHasAnyShift = computed(() =>
  WORK_DAY_KEYS.some((day) => scheduleState[day].morning || scheduleState[day].afternoon),
);

const isDoctorSelected = computed(() => hasDoctorRole(selectedStaff.value));

const scheduleMissingRoom = computed(
  () => isDoctorSelected.value && scheduleHasAnyShift.value && !selectedClinicRoomId.value,
);

const scheduleDirty = computed(() => {
  if (!scheduleBaseline.value) return false;
  try {
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
  } catch (error) {
    console.error(error);
    return true;
  }
});

const scheduleSaveDisabled = computed(
  () =>
    scheduleSaving.value ||
    scheduleLoading.value ||
    !scheduleDirty.value ||
    (isDoctorSelected.value && scheduleMissingRoom.value),
);

const scheduleInfoMessage = computed(() => {
  if (!scheduleHasAnyShift.value) {
    return 'Chưa có ca làm việc nào được chọn cho nhân viên này.';
  }
  if (scheduleMissingRoom.value) {
    return 'Vui lòng chọn phòng khám áp dụng cho lịch này.';
  }
  return null;
});

const selectedClinicRoomDisplay = computed(() => {
  if (!selectedStaff.value) {
    return '—';
  }
  if (!isDoctorSelected.value) {
    return 'Mặc định theo hệ thống';
  }
  if (clinicRoomsLoading.value) {
    return 'Đang tải...';
  }
  const meta = selectedClinicRoomMeta.value;
  if (meta?.name) {
    const codeSuffix = meta.code ? ` (${meta.code})` : '';
    const floorSuffix = meta.floor ? ` · Tầng ${meta.floor}` : '';
    return `${meta.name}${codeSuffix}${floorSuffix}`;
  }
  if (meta?.code) {
    return meta.code;
  }
  return 'Chưa chọn';
});

const scheduleSummary = computed(() =>
  WEEK_DAY_META.map(({ key, label, short }) => {
    const state = scheduleState[key];
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

interface WorkScheduleEntry {
  loading: boolean;
  error: string | null;
  days: WorkScheduleDay[] | null;
  clinicRoomId: number | null;
  clinicRoomName: string | null;
  clinicRoomCode: string | null;
}

const workScheduleEntries = reactive<Record<number, WorkScheduleEntry>>({});

const ensureScheduleEntry = (id: number): WorkScheduleEntry => {
  if (!workScheduleEntries[id]) {
    workScheduleEntries[id] = {
      loading: false,
      error: null,
      days: null,
      clinicRoomId: null,
      clinicRoomName: null,
      clinicRoomCode: null,
    };
  }
  return workScheduleEntries[id];
};

const normalizeScheduleForDisplay = (days: WorkScheduleDay[] | null | undefined) => {
  const dayMap = new Map<DayOfWeekKey, WorkScheduleDay>();
  (days ?? []).forEach((item) => {
    if (!item?.dayOfWeek) return;
    const key = item.dayOfWeek.toUpperCase() as DayOfWeekKey;
    if (!WORK_DAY_KEYS.includes(key)) return;
    dayMap.set(key, item);
  });

  const summaries = WEEK_DAY_META.map(({ key, label, short }) => {
    const value = dayMap.get(key);
    const hasMorning = Boolean(value?.morning);
    const hasAfternoon = Boolean(value?.afternoon);
    const hasAny = hasMorning || hasAfternoon;
    const timeParts: string[] = [];
    if (hasMorning) timeParts.push(SHIFT_META.morning.time);
    if (hasAfternoon) timeParts.push(SHIFT_META.afternoon.time);
    return {
      key,
      label,
      short,
      hasMorning,
      hasAfternoon,
      hasAny,
      display: hasAny ? timeParts.join(' · ') : 'Nghỉ',
    };
  });

  const firstWithRoom = (days ?? []).find((item) => item.clinicRoomId != null);

  return {
    summaries,
    hasAnyShift: summaries.some((item) => item.hasAny),
    clinicRoomId: firstWithRoom?.clinicRoomId ?? null,
    clinicRoomName: firstWithRoom?.clinicRoomName ?? null,
    clinicRoomCode: firstWithRoom?.clinicRoomCode ?? null,
  };
};

const doctorStaff = computed(() => staffItems.value.filter((member) => hasDoctorRole(member)));
const otherStaff = computed(() => staffItems.value.filter((member) => !hasDoctorRole(member)));

const doctorCards = computed(() =>
  doctorStaff.value.map((member) => {
    const entry = ensureScheduleEntry(member.id);
    const overview = normalizeScheduleForDisplay(entry.days);
    const clinicDisplay = entry.clinicRoomName
      ? `${entry.clinicRoomName}${entry.clinicRoomCode ? ` (${entry.clinicRoomCode})` : ''}`
      : entry.clinicRoomCode || 'Chưa chọn';
    return {
      member,
      entry,
      overview,
      clinicDisplay,
    };
  }),
);

const normalizedDoctorSearch = computed(() => doctorSearchTerm.value.trim().toLowerCase());
const filteredDoctorCards = computed(() =>
  doctorCards.value.filter(({ member }) => {
    if (!normalizedDoctorSearch.value) return true;
    const haystack = [
      member.fullName ?? '',
      member.email ?? '',
      member.phone ?? '',
      member.doctor?.specialty ?? '',
    ]
      .join(' ')
      .toLowerCase();
    return haystack.includes(normalizedDoctorSearch.value);
  }),
);

const normalizedOtherSearch = computed(() => otherSearchTerm.value.trim().toLowerCase());
const filteredOtherStaff = computed(() =>
  otherStaff.value.filter((member) => {
    const matchesRole =
      selectedOtherRole.value === 'ALL' ||
      (member.roles ?? []).some((role) => role === selectedOtherRole.value);
    if (!matchesRole) return false;
    if (!normalizedOtherSearch.value) return true;
    const haystack = [member.fullName ?? '', member.email ?? '', member.phone ?? '']
      .join(' ')
      .toLowerCase();
    return haystack.includes(normalizedOtherSearch.value);
  }),
);

watch(
  doctorStaff,
  (doctors) => {
    const ids = doctors.map((doctor) => doctor.id);
    ids.forEach((id) => {
      ensureScheduleEntry(id);
    });
    Object.keys(workScheduleEntries).forEach((key) => {
      const id = Number(key);
      if (!ids.includes(id)) {
        delete workScheduleEntries[id];
      }
    });
  },
  { immediate: true },
);

const rawBaseUrl = http.defaults.baseURL ?? (typeof window !== 'undefined' ? window.location.origin : '');
const apiBaseUrl = rawBaseUrl.replace(/\/$/, '');

const resolveAvatarUrl = (url?: string | null): string | undefined => {
  if (!url) return undefined;
  if (/^https?:\/\//i.test(url)) return url;
  return `${apiBaseUrl}/${url.replace(/^\/+/, '')}`;
};

const getStaffAvatarUrl = (member?: StaffMember | null) => resolveAvatarUrl(member?.avatarUrl);
const getStaffInitial = (member?: StaffMember | null) => {
  const name = member?.fullName?.trim();
  if (name) return name.charAt(0).toUpperCase();
  return member?.email?.charAt(0).toUpperCase() ?? 'NV';
};

const selectedClinicRoomMeta = computed(() => {
  if (selectedClinicRoomId.value != null) {
    const found = clinicRooms.value.find((room) => room.id === selectedClinicRoomId.value);
    if (found) {
      return { name: found.name, code: found.code, floor: found.floor ?? null };
    }
  }
  if (scheduleClinicRoomFallback.name || scheduleClinicRoomFallback.code) {
    return {
      name: scheduleClinicRoomFallback.name ?? undefined,
      code: scheduleClinicRoomFallback.code ?? undefined,
      floor: scheduleClinicRoomFallback.floor ?? null,
    };
  }
  return null;
});

const extractErrorMessage = (input: unknown) => {
  const fallback = 'Đã xảy ra lỗi. Vui lòng thử lại.';
  if (!input || typeof input !== 'object') return fallback;
  const maybeError = input as { message?: string; response?: { data?: { message?: unknown; error?: string } } };
  const responseMessage = maybeError.response?.data?.message;
  if (typeof responseMessage === 'string' && responseMessage.trim()) return responseMessage;
  if (Array.isArray(responseMessage) && responseMessage.length > 0) {
    const first = responseMessage[0];
    if (typeof first === 'string' && first.trim()) return first;
  }
  if (maybeError.response?.data?.error) return maybeError.response.data.error;
  if (maybeError.message) return maybeError.message;
  return fallback;
};

const fetchWorkSchedule = async (member: StaffMember, options: { force?: boolean } = {}) => {
  if (!member?.id) return null;
  const entry = ensureScheduleEntry(member.id);
  if (entry.loading) return entry;
  if (!options.force && entry.days) return entry;
  entry.loading = true;
  entry.error = null;
  try {
    const response = await fetchStaffWorkSchedule(member.id);
    const normalized = normalizeWorkDays(response, false);
    entry.days = normalized;
    const firstWithRoom = normalized.find((item) => item.clinicRoomId != null);
    entry.clinicRoomId = firstWithRoom?.clinicRoomId ?? null;
    entry.clinicRoomName = firstWithRoom?.clinicRoomName ?? null;
    entry.clinicRoomCode = firstWithRoom?.clinicRoomCode ?? null;
  } catch (error) {
    entry.error = extractErrorMessage(error);
    entry.days = null;
    entry.clinicRoomId = null;
    entry.clinicRoomName = null;
    entry.clinicRoomCode = null;
  } finally {
    entry.loading = false;
  }
  return entry;
};

const preloadWorkSchedules = async (doctors: StaffMember[]) => {
  await Promise.all(
    doctors.map((doctor) =>
      fetchWorkSchedule(doctor).catch(() => {
        const entry = ensureScheduleEntry(doctor.id);
        entry.error = entry.error ?? 'Không thể tải lịch làm việc.';
      }),
    ),
  );
};

const loadStaff = async () => {
  staffLoading.value = true;
  staffError.value = null;
  try {
    const params = {
      page: 0,
      size: 200,
    };
    const result = await fetchStaff(params);
    const items = Array.isArray(result?.items) ? result.items : [];
    staffItems.value = items.map((member) => ({
      ...member,
      roles: (member.roles ?? []).filter((role) => supportedRoles.includes(role)),
    }));

    if (
      selectedStaff.value &&
      !staffItems.value.some((member) => member.id === selectedStaff.value?.id)
    ) {
      selectedStaff.value = null;
      resetScheduleState();
    }

    const doctors = staffItems.value.filter((member) => hasDoctorRole(member));
    await preloadWorkSchedules(doctors);
  } catch (error) {
    staffError.value = extractErrorMessage(error);
    staffItems.value = [];
    showToast('error', staffError.value || 'Không thể tải danh sách nhân viên.');
  } finally {
    staffLoading.value = false;
  }
};

const ensureClinicRoomsLoaded = async () => {
  if (clinicRooms.value.length || clinicRoomsLoading.value) return;
  clinicRoomsLoading.value = true;
  clinicRoomsError.value = null;
  try {
    clinicRooms.value = await fetchClinicRooms();
  } catch (error) {
    clinicRoomsError.value =
      extractErrorMessage(error) ?? 'Không thể tải danh sách phòng khám. Vui lòng thử lại.';
    showToast('error', clinicRoomsError.value);
  } finally {
    clinicRoomsLoading.value = false;
  }
};

const startEditSchedule = async (member: StaffMember) => {
  if (!hasDoctorRole(member) || scheduleSaving.value) return;
  selectedStaff.value = member;
  scheduleModalOpen.value = true;
  scheduleLoading.value = true;
  scheduleError.value = null;
  await ensureClinicRoomsLoaded();
  const entry = await fetchWorkSchedule(member, { force: true });
  const days = entry?.days ?? null;
  setScheduleState(days, { defaultFullWhenEmpty: false, updateBaseline: true });
  if (!selectedClinicRoomId.value && entry?.clinicRoomId != null) {
    selectedClinicRoomId.value = entry.clinicRoomId;
    scheduleClinicRoomFallback.name = entry.clinicRoomName ?? null;
    scheduleClinicRoomFallback.code = entry.clinicRoomCode ?? null;
  }
  scheduleError.value = entry?.error ?? null;
  scheduleLoading.value = false;
};

const setSelectedOtherRole = (role: NonDoctorRole | 'ALL') => {
  selectedOtherRole.value = role;
};

const resolveRoleLabel = (role: NonDoctorRole | 'ALL') =>
  role === 'ALL' ? 'Tất cả' : roleDisplayMap[role].label;

const toggleShift = (day: DayOfWeekKey, shift: ShiftKey) => {
  if (!isDoctorSelected.value || scheduleSaving.value) return;
  const state = scheduleState[day];
  if (!state) return;
  state[shift] = !state[shift];
  scheduleError.value = null;
};

const applyDefaultWorkSchedule = () => {
  if (!isDoctorSelected.value) return;
  WORK_DAY_KEYS.forEach((day) => {
    scheduleState[day].morning = true;
    scheduleState[day].afternoon = true;
  });
  if (!selectedClinicRoomId.value && clinicRooms.value.length) {
    selectedClinicRoomId.value = clinicRooms.value[0]?.id ?? null;
  }
  scheduleError.value = null;
};

const clearWorkSchedule = () => {
  if (!isDoctorSelected.value) return;
  WORK_DAY_KEYS.forEach((day) => {
    scheduleState[day].morning = false;
    scheduleState[day].afternoon = false;
  });
  scheduleError.value = null;
};

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
    console.error('Không thể khôi phục lịch làm việc.', error);
  }
};

const closeScheduleModal = () => {
  if (scheduleModalOpen.value && scheduleDirty.value) {
    resetScheduleToBaseline();
  }
  scheduleError.value = null;
  scheduleLoading.value = false;
  scheduleModalOpen.value = false;
};

const saveSchedule = async () => {
  if (!selectedStaff.value || scheduleSaveDisabled.value) return;
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
    const updated = await updateStaffWorkSchedule(selectedStaff.value.id, payload);
    setScheduleState(updated, {
      defaultFullWhenEmpty: !isDoctorSelected.value,
      updateBaseline: true,
    });
    if (isDoctorSelected.value) {
      const normalizedForCache = normalizeWorkDays(updated, false);
      const entry = ensureScheduleEntry(selectedStaff.value.id);
      entry.days = normalizedForCache;
      const firstWithRoom = normalizedForCache.find((item) => item.clinicRoomId != null);
      entry.clinicRoomId = firstWithRoom?.clinicRoomId ?? null;
      entry.clinicRoomName = firstWithRoom?.clinicRoomName ?? null;
      entry.clinicRoomCode = firstWithRoom?.clinicRoomCode ?? null;
      entry.error = null;
    }
    showToast('success', 'Đã cập nhật lịch làm việc cho nhân viên.');
    if (scheduleModalOpen.value) {
      closeScheduleModal();
    }
  } catch (error) {
    const message =
      extractErrorMessage(error) ?? 'Không thể cập nhật lịch làm việc. Vui lòng thử lại.';
    scheduleError.value = message;
    showToast('error', message);
  } finally {
    scheduleSaving.value = false;
  }
};

const handleSignOut = async () => {
  await authStore.signOut();
  router.replace({ name: 'home' });
};

watch(
  clinicRooms,
  (rooms) => {
    if (!rooms.length) {
      if (!isDoctorSelected.value) {
        return;
      }
      if (selectedClinicRoomId.value != null) {
        selectedClinicRoomId.value = null;
      }
      return;
    }

    if (selectedClinicRoomId.value != null) {
      const matched = rooms.find((room) => room.id === selectedClinicRoomId.value);
      if (matched) {
        scheduleClinicRoomFallback.name = matched.name ?? null;
        scheduleClinicRoomFallback.code = matched.code ?? null;
        scheduleClinicRoomFallback.floor = matched.floor ?? null;
        return;
      }
    }

    if (isDoctorSelected.value && selectedClinicRoomId.value == null) {
      const firstRoom = rooms[0];
      if (firstRoom) {
        selectedClinicRoomId.value = firstRoom.id ?? null;
        scheduleClinicRoomFallback.name = firstRoom.name ?? null;
        scheduleClinicRoomFallback.code = firstRoom.code ?? null;
        scheduleClinicRoomFallback.floor = firstRoom.floor ?? null;
      }
    }
  },
  { flush: 'post' },
);

watch(
  selectedClinicRoomId,
  (id) => {
    if (id == null) {
      scheduleClinicRoomFallback.floor = null;
      return;
    }
    const matched = clinicRooms.value.find((room) => room.id === id);
    if (matched) {
      scheduleClinicRoomFallback.name = matched.name ?? null;
      scheduleClinicRoomFallback.code = matched.code ?? null;
      scheduleClinicRoomFallback.floor = matched.floor ?? null;
    }
  },
  { flush: 'post' },
);

onMounted(async () => {
  await loadStaff();
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
          <div class="max-w-2xl">
            <span class="inline-flex items-center gap-2 rounded-full bg-emerald-50 px-4 py-1.5 text-xs font-semibold uppercase tracking-[0.3em] text-emerald-600">
              Lịch làm việc
            </span>
            <h1 class="mt-4 text-2xl font-semibold text-slate-900 md:text-3xl">Quản lý lịch làm việc nhân viên</h1>
            <p class="mt-2 text-sm text-slate-600">
              Chọn nhân viên cần điều chỉnh, thiết lập ca làm việc theo tuần và lưu lại. Bác sĩ có thể chọn ca sáng/chiều và phòng khám, những vai trò khác áp dụng lịch cố định 8h - 17h.
            </p>
          </div>
          <div class="flex flex-col gap-3 sm:flex-row sm:items-center">
            <button
              type="button"
              class="inline-flex items-center justify-center gap-2 rounded-full border border-emerald-200 bg-white px-5 py-2 text-xs font-semibold uppercase tracking-wide text-emerald-600 shadow-sm transition hover:border-emerald-300 hover:bg-emerald-50 disabled:cursor-not-allowed disabled:opacity-60"
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

        <p v-if="staffError" class="mt-6 rounded-2xl border border-rose-100 bg-rose-50/90 px-5 py-4 text-sm text-rose-600">
          {{ staffError }}
        </p>
      </section>

      <section class="mt-10 space-y-10">
        <div class="rounded-[28px] border border-emerald-100 bg-white/95 p-6 shadow-[0_24px_55px_-45px_rgba(13,148,136,0.55)]">
          <div class="flex flex-col gap-2 sm:flex-row sm:items-start sm:justify-between">
            <div>
              <h2 class="text-lg font-semibold text-slate-900 sm:text-xl">Danh sách bác sĩ</h2>
              <p class="mt-1 text-sm text-slate-600">
                Lịch làm việc chi tiết của từng bác sĩ hiển thị trực tiếp theo ngày trong tuần.
              </p>
            </div>
            <div class="text-xs font-semibold uppercase tracking-wide text-emerald-600">
              {{ filteredDoctorCards.length }} bác sĩ
            </div>
          </div>

          <div class="mt-4 flex justify-end">
            <div class="relative w-full max-w-sm">
              <svg class="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" fill="none" stroke="currentColor" stroke-width="1.5" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" d="m21 21-4.35-4.35m0 0A7.35 7.35 0 1 0 6.3 6.3a7.35 7.35 0 0 0 10.35 10.35Z" />
              </svg>
              <input
                v-model.trim="doctorSearchTerm"
                type="search"
                placeholder="Tìm bác sĩ theo tên, email hoặc chuyên khoa..."
                class="w-full rounded-full border border-emerald-100 bg-white py-2.5 pl-9 pr-4 text-sm text-slate-700 shadow-sm transition focus:border-emerald-400 focus:outline-none focus:ring-4 focus:ring-emerald-100/80"
              />
            </div>
          </div>

          <template v-if="staffLoading && !doctorCards.length">
            <div class="mt-6 grid gap-4 md:grid-cols-2">
              <div
                v-for="skeleton in 4"
                :key="`doctor-skeleton-${skeleton}`"
                class="animate-pulse rounded-2xl border border-slate-100 bg-slate-50 p-5"
              >
                <div class="h-4 w-32 rounded-full bg-slate-200/80"></div>
                <div class="mt-3 h-3 w-24 rounded-full bg-slate-200/60"></div>
                <div class="mt-4 h-3 w-full rounded-full bg-slate-200/40"></div>
              </div>
            </div>
          </template>
          <template v-else-if="!filteredDoctorCards.length">
            <div class="mt-6 rounded-3xl border border-dashed border-emerald-200 bg-emerald-50/40 p-10 text-center text-emerald-700">
              <svg xmlns="http://www.w3.org/2000/svg" class="mx-auto h-12 w-12 text-emerald-400" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.6">
                <path stroke-linecap="round" stroke-linejoin="round" d="M5 13l4 4L19 7" />
              </svg>
              <h3 class="mt-4 text-lg font-semibold">Không tìm thấy bác sĩ phù hợp</h3>
              <p class="mt-2 text-sm text-emerald-600/80">Điều chỉnh bộ lọc hoặc từ khóa để tiếp tục.</p>
            </div>
          </template>
          <template v-else>
            <div class="mt-6 grid gap-5 lg:grid-cols-2">
              <article
                v-for="doctor in filteredDoctorCards"
                :key="doctor.member.id"
                class="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm transition hover:border-emerald-200 hover:shadow-lg"
              >
                <div class="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
                  <div class="flex items-start gap-3">
                    <div class="relative flex h-14 w-14 flex-shrink-0 items-center justify-center overflow-hidden rounded-2xl border border-emerald-100 bg-emerald-50 text-sm font-semibold text-emerald-600 shadow-sm">
                      <img
                        v-if="getStaffAvatarUrl(doctor.member)"
                        :src="getStaffAvatarUrl(doctor.member)"
                        :alt="`Avatar of ${doctor.member.fullName}`"
                        class="h-full w-full object-cover"
                      />
                      <span v-else>{{ getStaffInitial(doctor.member) }}</span>
                    </div>
                    <div>
                      <h3 class="text-base font-semibold text-slate-900">{{ doctor.member.fullName }}</h3>
                      <p class="text-xs text-slate-500">{{ doctor.member.email }}</p>
                      <p class="mt-1 text-xs text-slate-500">SĐT: {{ doctor.member.phone || '—' }}</p>
                      <p
                        v-if="doctor.member.doctor?.specialty"
                        class="mt-2 inline-flex items-center rounded-full bg-emerald-50 px-3 py-1 text-[11px] font-semibold uppercase tracking-wide text-emerald-700"
                      >
                        {{ doctor.member.doctor.specialty }}
                      </p>
                    </div>
                  </div>
                  <button
                    type="button"
                    class="inline-flex items-center gap-2 rounded-full border border-emerald-200 bg-white px-4 py-2 text-xs font-semibold uppercase tracking-wide text-emerald-600 shadow-sm transition hover:border-emerald-300 hover:bg-emerald-50 disabled:cursor-not-allowed disabled:opacity-60"
                    :disabled="scheduleSaving || doctor.entry.loading"
                    @click="startEditSchedule(doctor.member)"
                  >
                    <svg xmlns="http://www.w3.org/2000/svg" class="h-3.5 w-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.8">
                      <path stroke-linecap="round" stroke-linejoin="round" d="M16.862 4.487 19.513 7.138m-2.651-2.651L7.5 16.5l-3 3 3-3 9.862-9.862Zm0 0 1.76-1.76a1.5 1.5 0 0 1 2.122 0l.378.378a1.5 1.5 0 0 1 0 2.122l-1.76 1.76m-2.5-2.5-9.862 9.862m0 0H4.5v-2.414" />
                    </svg>
                    Chỉnh sửa
                  </button>
                </div>

                <div class="mt-4 rounded-2xl border border-emerald-100 bg-emerald-50/60 px-4 py-3 text-xs text-emerald-700">
                  Phòng khám áp dụng:
                  <span class="font-semibold text-emerald-800">{{ doctor.clinicDisplay }}</span>
                </div>

                <div class="mt-4">
                  <div v-if="doctor.entry.loading" class="grid gap-2 sm:grid-cols-2">
                    <div
                      v-for="placeholder in 6"
                      :key="`doctor-day-skeleton-${doctor.member.id}-${placeholder}`"
                      class="animate-pulse rounded-xl border border-slate-100 bg-slate-50 px-3 py-3"
                    >
                      <div class="h-3 w-12 rounded-full bg-slate-200/80"></div>
                      <div class="mt-2 h-3 w-full rounded-full bg-slate-200/50"></div>
                    </div>
                  </div>
                  <div v-else-if="doctor.overview.hasAnyShift" class="grid gap-2 sm:grid-cols-2">
                    <div
                      v-for="day in doctor.overview.summaries"
                      :key="`doctor-day-${doctor.member.id}-${day.key}`"
                      class="flex items-center justify-between gap-2 rounded-xl border border-slate-200 bg-white px-3 py-2 text-xs text-slate-600"
                    >
                      <span class="font-semibold text-slate-900">{{ day.short }}</span>
                      <span class="text-right">{{ day.display }}</span>
                    </div>
                  </div>
                  <div v-else class="rounded-2xl border border-amber-200 bg-amber-50/80 px-4 py-3 text-xs text-amber-700">
                    Chưa đăng ký ca làm việc trong tuần này.
                  </div>
                </div>

                <p
                  v-if="doctor.entry.error"
                  class="mt-4 rounded-2xl border border-rose-100 bg-rose-50/90 px-4 py-3 text-xs text-rose-600"
                >
                  {{ doctor.entry.error }}
                </p>
              </article>
            </div>
          </template>
        </div>

        <div class="rounded-[28px] border border-emerald-100 bg-white/95 p-6 shadow-[0_24px_55px_-45px_rgba(13,148,136,0.55)]">
          <div class="flex flex-col gap-2 sm:flex-row sm:items-start sm:justify-between">
            <div>
              <h2 class="text-lg font-semibold text-slate-900 sm:text-xl">Nhân viên khác</h2>
              <p class="mt-1 text-sm text-slate-600">
                Các vai trò còn lại áp dụng lịch cố định 08:00 - 12:00 và 13:00 - 17:00 từ Thứ 2 đến Thứ 7.
              </p>
            </div>
            <div class="text-xs font-semibold uppercase tracking-wide text-emerald-600">
              {{ filteredOtherStaff.length }} nhân viên
            </div>
          </div>

          <div class="mt-4 flex flex-wrap items-center gap-2">
            <button
              v-for="role in otherRoleFilters"
              :key="`other-role-${role}`"
              type="button"
              class="inline-flex items-center gap-2 rounded-full border px-4 py-2 text-xs font-semibold uppercase tracking-wide transition"
              :class="selectedOtherRole === role ? 'border-emerald-300 bg-emerald-50 text-emerald-700 shadow-sm' : 'border-emerald-100 bg-white text-emerald-600 hover:border-emerald-200 hover:bg-emerald-50/70'"
              @click="setSelectedOtherRole(role)"
            >
              <span>{{ resolveRoleLabel(role) }}</span>
            </button>
          </div>
          <div class="mt-3">
            <div class="relative w-full max-w-sm">
              <svg class="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" fill="none" stroke="currentColor" stroke-width="1.5" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" d="m21 21-4.35-4.35m0 0A7.35 7.35 0 1 0 6.3 6.3a7.35 7.35 0 0 0 10.35 10.35Z" />
              </svg>
              <input
                v-model.trim="otherSearchTerm"
                type="search"
                placeholder="Tìm nhân viên theo tên, email hoặc số điện thoại..."
                class="w-full rounded-full border border-emerald-100 bg-white py-2.5 pl-9 pr-4 text-sm text-slate-700 shadow-sm transition focus:border-emerald-400 focus:outline-none focus:ring-4 focus:ring-emerald-100/80"
              />
            </div>
          </div>

          <template v-if="staffLoading && !filteredOtherStaff.length">
            <div class="mt-6 grid gap-4 md:grid-cols-2 xl:grid-cols-3">
              <div
                v-for="skeleton in 6"
                :key="`staff-skeleton-${skeleton}`"
                class="animate-pulse rounded-2xl border border-slate-100 bg-slate-50 p-5"
              >
                <div class="h-4 w-32 rounded-full bg-slate-200/80"></div>
                <div class="mt-3 h-3 w-24 rounded-full bg-slate-200/60"></div>
                <div class="mt-4 h-3 w-full rounded-full bg-slate-200/40"></div>
              </div>
            </div>
          </template>
          <template v-else-if="!filteredOtherStaff.length">
            <div class="mt-6 rounded-3xl border border-dashed border-emerald-200 bg-emerald-50/40 p-10 text-center text-emerald-700">
              <svg xmlns="http://www.w3.org/2000/svg" class="mx-auto h-12 w-12 text-emerald-400" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.6">
                <path stroke-linecap="round" stroke-linejoin="round" d="M5 13l4 4L19 7" />
              </svg>
              <h3 class="mt-4 text-lg font-semibold">Không có nhân viên nào phù hợp</h3>
              <p class="mt-2 text-sm text-emerald-600/80">Điều chỉnh bộ lọc hoặc tìm kiếm để tiếp tục.</p>
            </div>
          </template>
          <template v-else>
            <div class="mt-6 grid gap-4 md:grid-cols-2 xl:grid-cols-3">
              <article
                v-for="member in filteredOtherStaff"
                :key="member.id"
                class="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm transition hover:border-emerald-200 hover:shadow-lg"
              >
                <div class="flex items-start gap-3">
                  <div class="relative flex h-14 w-14 flex-shrink-0 items-center justify-center overflow-hidden rounded-2xl border border-emerald-100 bg-emerald-50 text-sm font-semibold text-emerald-600 shadow-sm">
                    <img
                      v-if="getStaffAvatarUrl(member)"
                      :src="getStaffAvatarUrl(member)"
                    :alt="`Avatar of ${member.fullName}`"
                      class="h-full w-full object-cover"
                    />
                    <span v-else>{{ getStaffInitial(member) }}</span>
                  </div>
                  <div>
                    <h3 class="text-base font-semibold text-slate-900">{{ member.fullName }}</h3>
                    <p class="text-xs text-slate-500">{{ member.email }}</p>
                    <p class="mt-1 text-xs text-slate-500">SĐT: {{ member.phone || '—' }}</p>
                    <div class="mt-2 flex flex-wrap gap-2">
                      <span
                        v-for="role in member.roles"
                        :key="`${member.id}-${role}`"
                        class="rounded-full bg-emerald-50 px-3 py-1 text-[11px] font-semibold uppercase tracking-wide text-emerald-700"
                      >
                        {{ roleDisplayMap[role].label }}
                      </span>
                    </div>
                  </div>
                </div>
                <div class="mt-4 rounded-2xl border border-emerald-100 bg-emerald-50/60 px-4 py-3 text-xs text-emerald-700">
                  Lịch cố định: <span class="font-semibold text-emerald-800">08:00 - 12:00 · 13:00 - 17:00</span> (Thứ 2 - Thứ 7)
                </div>
              </article>
            </div>
          </template>
        </div>

        <div
          v-if="!staffLoading"
          class="rounded-[28px] border border-emerald-100 bg-white px-4 py-3 text-sm text-slate-600 shadow-[0_24px_55px_-45px_rgba(13,148,136,0.55)] flex flex-col gap-2 md:flex-row md:items-center md:justify-between"
        >
          <span>Tổng số nhân viên: {{ staffItems.length }}</span>
          <span>Bác sĩ: {{ filteredDoctorCards.length }} · Nhân viên khác: {{ filteredOtherStaff.length }}</span>
        </div>
      </section>
    </main>

    <Transition name="fade">
      <div
        v-if="scheduleModalOpen"
        class="fixed inset-0 z-[85] flex items-center justify-center bg-slate-900/45 backdrop-blur-sm"
      >
        <div class="relative max-h-[90vh] w-[min(780px,92vw)] overflow-hidden rounded-[28px] border border-emerald-100 bg-white shadow-[0_32px_120px_-55px_rgba(13,148,136,0.75)]">
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
              <h2 class="text-2xl font-semibold text-slate-900">
                Điều chỉnh ca làm việc cho
                <span class="text-emerald-600">{{ selectedStaff?.fullName || 'bác sĩ' }}</span>
              </h2>
              <p class="text-sm text-slate-500">
                Chọn ca làm việc buổi sáng và chiều cho từng ngày trong tuần. Các thay đổi sẽ áp dụng sau khi lưu.
              </p>
            </div>

            <div class="mt-6 flex flex-wrap items-center gap-2">
              <button
                type="button"
                class="inline-flex items-center gap-2 rounded-full border border-emerald-200 bg-white px-4 py-2 text-xs font-semibold uppercase tracking-wide text-emerald-600 shadow-sm transition hover:border-emerald-300 hover:bg-emerald-50 disabled:cursor-not-allowed disabled:opacity-60"
                :disabled="scheduleSaving"
                @click="applyDefaultWorkSchedule"
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
                <label class="text-xs font-semibold uppercase tracking-wide text-slate-500" for="manager-schedule-clinic-room">
                  Phòng khám áp dụng
                </label>
                <select
                  id="manager-schedule-clinic-room"
                  v-model="selectedClinicRoomId"
                  :disabled="clinicRoomsLoading || !clinicRooms.length || scheduleSaving"
                  class="w-full rounded-xl border border-slate-200 bg-white px-4 py-2.5 text-sm text-slate-800 shadow-sm transition focus:border-emerald-400 focus:outline-none focus:ring-4 focus:ring-emerald-100/80 disabled:cursor-not-allowed disabled:opacity-70"
                >
                  <option :value="null">Chọn phòng khám</option>
                  <option v-for="room in clinicRooms" :key="`manager-clinic-room-${room.id}`" :value="room.id">
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

              <div v-if="scheduleLoading" class="grid gap-3 md:grid-cols-2">
                <div
                  v-for="day in WORK_DAY_KEYS"
                  :key="`schedule-modal-loading-${day}`"
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
                        :class="scheduleState[day.key].morning
                          ? 'border-emerald-300 bg-emerald-50 text-emerald-700 shadow-sm'
                          : 'border-slate-200 bg-white text-slate-600 hover:border-emerald-200 hover:bg-emerald-50/60'"
                        :aria-pressed="scheduleState[day.key].morning"
                        :disabled="scheduleSaving"
                        @click="toggleShift(day.key, 'morning')"
                      >
                        <span>{{ SHIFT_META.morning.label }}</span>
                        <span class="mt-0.5 text-[11px] font-normal tracking-wide text-slate-500">{{ SHIFT_META.morning.time }}</span>
                      </button>
                      <button
                        type="button"
                        class="inline-flex flex-col items-center justify-center rounded-2xl border px-4 py-2 text-xs font-semibold transition focus:outline-none focus-visible:ring-2 focus-visible:ring-emerald-400"
                        :class="scheduleState[day.key].afternoon
                          ? 'border-emerald-300 bg-emerald-50 text-emerald-700 shadow-sm'
                          : 'border-slate-200 bg-white text-slate-600 hover:border-emerald-200 hover:bg-emerald-50/60'"
                        :aria-pressed="scheduleState[day.key].afternoon"
                        :disabled="scheduleSaving"
                        @click="toggleShift(day.key, 'afternoon')"
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
            <div class="mt-6 flex flex-wrap items-center justify-end gap-3">
              <button
                type="button"
                class="inline-flex items-center gap-2 rounded-full border border-slate-200 bg-white px-5 py-2 text-xs font-semibold uppercase tracking-wide text-slate-600 shadow-sm transition hover:border-slate-300 hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-70"
                :disabled="scheduleSaving || !scheduleDirty"
                @click="resetScheduleToBaseline"
              >
                Hoàn tác
              </button>
              <button
                type="button"
                class="inline-flex items-center gap-2 rounded-full bg-emerald-600 px-6 py-2 text-xs font-semibold uppercase tracking-wide text-white shadow-sm transition hover:bg-emerald-700 disabled:cursor-not-allowed disabled:opacity-70"
                :disabled="scheduleSaveDisabled"
                @click="saveSchedule"
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
          class="fixed top-6 right-6 z-[90] w-[min(320px,90vw)] rounded-2xl border px-5 py-4 shadow-xl backdrop-blur"
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
