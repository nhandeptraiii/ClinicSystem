<script setup lang="ts">
import { ref, computed, watch } from 'vue';
import { createAppointmentRequest, type AppointmentRequestPayload } from '@/services/appointmentRequest.service';

const emitting = defineEmits<{ (e: 'submitted'): void }>();

const now = new Date();
const form = ref({
  fullName: '',
  phone: '',
  email: '',
  dateOfBirth: '', // YYYY-MM-DD
  preferredDate: '', // YYYY-MM-DD
  preferredTime: '', // HH:mm
  symptomDescription: '',
});

const loading = ref(false);
const success = ref<string | null>(null);
const error = ref<string | null>(null);
type ToastState = { type: 'success' | 'error'; message: string };
const toast = ref<ToastState | null>(null);
let toastTimer: ReturnType<typeof setTimeout> | undefined;

type InvalidMessageMap = Partial<{
  required: string;
  patternMismatch: string;
  typeMismatch: string;
  rangeUnderflow: string;
  rangeOverflow: string;
}>;

const today = now.toISOString().slice(0, 10);

const toMinutes = (h: number, m: number) => h * 60 + m;
const toHHMM = (mins: number) => {
  const h = Math.floor(mins / 60).toString().padStart(2, '0');
  const m = (mins % 60).toString().padStart(2, '0');
  return `${h}:${m}`;
};
const makeSlots = (start: number, end: number, step = 30) => {
  const out: string[] = [];
  for (let t = start; t <= end; t += step) out.push(toHHMM(t));
  return out;
};

const MORNING_SLOTS = makeSlots(toMinutes(7, 30), toMinutes(10, 30));
const AFTERNOON_SLOTS = makeSlots(toMinutes(13, 0), toMinutes(17, 0));
const ALL_SLOTS = [...MORNING_SLOTS, ...AFTERNOON_SLOTS];

const preferredAtISO = computed(() => {
  if (!form.value.preferredDate || !form.value.preferredTime) return '';
  return `${form.value.preferredDate}T${form.value.preferredTime}:00`;
});

const handleInput = (event: Event) => {
  const target = event.target as HTMLInputElement | HTMLTextAreaElement | HTMLSelectElement;
  target.setCustomValidity('');
};

const onInvalid = (event: Event, messages: InvalidMessageMap) => {
  const target = event.target as HTMLInputElement | HTMLTextAreaElement | HTMLSelectElement;
  target.setCustomValidity('');
  const { validity } = target;

  if (validity.valueMissing && messages.required) {
    target.setCustomValidity(messages.required);
    return;
  }
  if (validity.patternMismatch && messages.patternMismatch) {
    target.setCustomValidity(messages.patternMismatch);
    return;
  }
  if (validity.typeMismatch && messages.typeMismatch) {
    target.setCustomValidity(messages.typeMismatch);
    return;
  }
  if (validity.rangeUnderflow && messages.rangeUnderflow) {
    target.setCustomValidity(messages.rangeUnderflow);
    return;
  }
  if (validity.rangeOverflow && messages.rangeOverflow) {
    target.setCustomValidity(messages.rangeOverflow);
  }
};

const clearToastTimer = () => {
  if (toastTimer) {
    clearTimeout(toastTimer);
    toastTimer = undefined;
  }
};

const resetMessageByType = (type: ToastState['type']) => {
  if (type === 'success') success.value = null;
  if (type === 'error') error.value = null;
};

const showToast = (type: ToastState['type'], message: string) => {
  clearToastTimer();
  toast.value = { type, message };
  const scheduledType = type;
  toastTimer = setTimeout(() => {
    if (toast.value?.type === scheduledType) {
      resetMessageByType(scheduledType);
      toast.value = null;
    }
    toastTimer = undefined;
  }, 4500);
};

watch([success, error], ([successMessage, errorMessage]) => {
  if (successMessage) {
    showToast('success', successMessage);
  } else if (errorMessage) {
    showToast('error', errorMessage);
  } else {
    clearToastTimer();
    toast.value = null;
  }
});

const dismissToast = () => {
  if (!toast.value) return;
  const currentType = toast.value.type;
  clearToastTimer();
  resetMessageByType(currentType);
  toast.value = null;
};

const validatePhone = (value: string) => /^\d{10}$/.test(value);

const handleSubmit = async () => {
  if (loading.value) return;
  success.value = null;
  error.value = null;

  // Required checks
  if (!form.value.fullName || !form.value.phone || !form.value.email || !form.value.dateOfBirth || !form.value.preferredDate || !form.value.preferredTime || !form.value.symptomDescription) {
    error.value = 'Vui lòng nhập đầy đủ thông tin bắt buộc';
    return;
  }
  if (!validatePhone(form.value.phone)) {
    error.value = 'Số điện thoại phải gồm đúng 10 chữ số';
    return;
  }
  if (!ALL_SLOTS.includes(form.value.preferredTime)) {
    error.value = 'Giờ khám không hợp lệ, vui lòng chọn lại';
    return;
  }

  const payload: AppointmentRequestPayload = {
    fullName: form.value.fullName.trim(),
    phone: form.value.phone.trim(),
    email: form.value.email.trim(),
    dateOfBirth: form.value.dateOfBirth,
    preferredAt: preferredAtISO.value,
    symptomDescription: form.value.symptomDescription.trim(),
  };

  try {
    loading.value = true;
    await createAppointmentRequest(payload);
    success.value = 'Gửi yêu cầu đặt lịch thành công! Chúng tôi sẽ liên hệ sớm.';
    emitting('submitted');
    // Reset các trường ngày/giờ và mô tả
    form.value.preferredDate = '';
    form.value.preferredTime = '';
    form.value.symptomDescription = '';
  } catch (e: any) {
    error.value = e?.response?.data?.message || 'Gửi yêu cầu thất bại. Vui lòng thử lại.';
  } finally {
    loading.value = false;
  }
};
</script>

<template>
  <div class="rounded-[26px] border border-emerald-100 bg-white/95 p-8 shadow-[0_24px_55px_-35px_rgba(13,148,136,0.55)] backdrop-blur">
    <div class="mb-8 flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
      <div>
        <h3 class="text-2xl font-semibold text-slate-900">Đặt lịch khám ngay</h3>
        <p class="mt-1 text-sm text-slate-600">Điền đầy đủ thông tin, chúng tôi sẽ liên hệ xác nhận trong giờ làm việc.</p>
      </div>
      <div class="rounded-full bg-emerald-50 px-4 py-1 text-sm font-semibold uppercase tracking-[0.2em] text-emerald-600">Ưu tiên xác nhận</div>
    </div>

    <form class="grid grid-cols-1 gap-6 sm:grid-cols-2" @submit.prevent="handleSubmit">
      <div class="sm:col-span-1">
        <label class="mb-1.5 block text-sm font-semibold text-slate-700" for="fullName">Họ và tên</label>
        <input
          id="fullName"
          v-model="form.fullName"
          type="text"
          required
          class="w-full rounded-xl border border-emerald-100 bg-white px-4 py-3 text-sm text-slate-900 shadow-sm transition focus:border-emerald-400 focus:outline-none focus:ring-4 focus:ring-emerald-100/80"
          placeholder="Nguyễn Văn A"
          @input="handleInput"
          @invalid="onInvalid($event, { required: 'Vui lòng nhập họ và tên.' })"
        />
      </div>

      <div class="sm:col-span-1">
        <label class="mb-1.5 block text-sm font-semibold text-slate-700" for="phone">Số điện thoại</label>
        <input
          id="phone"
          v-model="form.phone"
          type="tel"
          required
          pattern="\d{10}"
          class="w-full rounded-xl border border-emerald-100 bg-white px-4 py-3 text-sm text-slate-900 shadow-sm transition focus:border-emerald-400 focus:outline-none focus:ring-4 focus:ring-emerald-100/80"
          placeholder="0987654321"
          @input="handleInput"
          @invalid="onInvalid($event, { required: 'Vui lòng nhập số điện thoại.', patternMismatch: 'Số điện thoại phải gồm đúng 10 chữ số.' })"
        />
      </div>

      <div class="sm:col-span-1">
        <label class="mb-1.5 block text-sm font-semibold text-slate-700" for="email">Email</label>
        <input
          id="email"
          v-model="form.email"
          type="email"
          required
          class="w-full rounded-xl border border-emerald-100 bg-white px-4 py-3 text-sm text-slate-900 shadow-sm transition focus:border-emerald-400 focus:outline-none focus:ring-4 focus:ring-emerald-100/80"
          placeholder="you@example.com"
          @input="handleInput"
          @invalid="onInvalid($event, { required: 'Vui lòng nhập email liên hệ.', typeMismatch: 'Email chưa đúng định dạng. Vui lòng kiểm tra lại.' })"
        />
      </div>

      <div class="sm:col-span-1">
        <label class="mb-1.5 block text-sm font-semibold text-slate-700" for="dob">Ngày sinh</label>
        <input
          id="dob"
          v-model="form.dateOfBirth"
          type="date"
          :max="today"
          required
          class="w-full rounded-xl border border-emerald-100 bg-white px-4 py-3 text-sm text-slate-900 shadow-sm transition focus:border-emerald-400 focus:outline-none focus:ring-4 focus:ring-emerald-100/80"
          @input="handleInput"
          @invalid="onInvalid($event, { required: 'Vui lòng chọn ngày sinh.', rangeOverflow: 'Ngày sinh không được muộn hơn hôm nay.' })"
        />
      </div>

      <div class="sm:col-span-1">
        <label class="mb-1.5 block text-sm font-semibold text-slate-700" for="preferredDate">Ngày mong muốn</label>
        <input
          id="preferredDate"
          v-model="form.preferredDate"
          type="date"
          :min="today"
          required
          class="w-full rounded-xl border border-emerald-100 bg-white px-4 py-3 text-sm text-slate-900 shadow-sm transition focus:border-emerald-400 focus:outline-none focus:ring-4 focus:ring-emerald-100/80"
          @input="handleInput"
          @invalid="onInvalid($event, { required: 'Vui lòng chọn ngày khám.', rangeUnderflow: 'Ngày khám phải từ hôm nay trở đi.' })"
        />
      </div>

      <div class="sm:col-span-1">
        <label class="mb-1.5 block text-sm font-semibold text-slate-700" for="preferredTime">Giờ mong muốn</label>
        <select
          id="preferredTime"
          v-model="form.preferredTime"
          required
          class="w-full rounded-xl border border-emerald-100 bg-white px-4 py-3 text-sm text-slate-900 shadow-sm transition focus:border-emerald-400 focus:outline-none focus:ring-4 focus:ring-emerald-100/80"
          @input="handleInput"
          @change="handleInput"
          @invalid="onInvalid($event, { required: 'Vui lòng chọn giờ khám.' })"
        >
          <option disabled value="">Chọn giờ khám</option>
          <optgroup label="Buổi sáng">
            <option v-for="t in MORNING_SLOTS" :key="t" :value="t">{{ t }}</option>
          </optgroup>
          <optgroup label="Buổi chiều">
            <option v-for="t in AFTERNOON_SLOTS" :key="t" :value="t">{{ t }}</option>
          </optgroup>
        </select>
      </div>

      <div class="sm:col-span-2">
        <label class="mb-1.5 block text-sm font-semibold text-slate-700" for="symptoms">Ghi chú cho bác sĩ</label>
        <textarea
          id="symptoms"
          v-model="form.symptomDescription"
          rows="4"
          required
          maxlength="1000"
          class="w-full rounded-xl border border-emerald-100 bg-white px-4 py-3 text-sm text-slate-900 shadow-sm transition focus:border-emerald-400 focus:outline-none focus:ring-4 focus:ring-emerald-100/80"
          placeholder="Ví dụ: ho, sốt, đau họng trong 3 ngày..."
          @input="handleInput"
          @invalid="onInvalid($event, { required: 'Vui lòng mô tả triệu chứng hoặc nhu cầu khám.' })"
        />
      </div>

      <div class="sm:col-span-2 flex flex-wrap items-center justify-center gap-4">
        <button
          type="submit"
          class="inline-flex items-center justify-center rounded-full bg-emerald-600 px-6 py-3 text-sm font-semibold uppercase tracking-wide text-white shadow-sm transition hover:bg-emerald-700 disabled:cursor-not-allowed disabled:opacity-70 "
          :disabled="loading"
        >
          <span v-if="!loading">Gửi yêu cầu</span>
          <span v-else>Đang gửi...</span>
        </button>
      </div>
    </form>
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
        class="fixed top-6 right-6 z-[60] w-[min(320px,90vw)] rounded-2xl border px-5 py-4 shadow-xl backdrop-blur"
        :class="toast.type === 'success' ? 'border-emerald-200 bg-emerald-50/95 text-emerald-800' : 'border-rose-200 bg-rose-50/95 text-rose-700'"
      >
        <div class="flex items-start gap-3">
          <span
            class="mt-0.5 flex h-9 w-9 flex-shrink-0 items-center justify-center rounded-full"
            :class="toast.type === 'success' ? 'bg-emerald-100 text-emerald-600' : 'bg-rose-100 text-rose-600'"
          >
            <svg v-if="toast.type === 'success'" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" class="h-5 w-5" fill="none" stroke="currentColor" stroke-width="2">
              <path stroke-linecap="round" stroke-linejoin="round" d="m5 13 4 4L19 7" />
            </svg>
            <svg v-else xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" class="h-5 w-5" fill="none" stroke="currentColor" stroke-width="2">
              <path stroke-linecap="round" stroke-linejoin="round" d="m15 9-6 6m0-6 6 6" />
            </svg>
          </span>
          <div class="flex-1">
            <p class="text-sm font-semibold">{{ toast.type === 'success' ? 'Gửi yêu cầu thành công' : 'Không thể gửi yêu cầu' }}</p>
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
</template>
