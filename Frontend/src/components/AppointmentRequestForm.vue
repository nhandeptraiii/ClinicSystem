<script setup lang="ts">
import { ref, computed, watch, onMounted, onBeforeUnmount } from 'vue';
import { createAppointmentRequest, type AppointmentRequestPayload } from '@/services/appointmentRequest.service';
import type { ToastType } from '@/composables/useToast';

type ToastPayload = {
  type: ToastType;
  message: string;
};

const props = withDefaults(
  defineProps<{
    initialSymptomDescription?: string;
  }>(),
  {
    initialSymptomDescription: '',
  }
);

const emitting = defineEmits<{
  (e: 'submitted'): void;
  (e: 'notify', payload: ToastPayload): void;
}>();

const now = new Date();
const form = ref({
  fullName: '',
  phone: '',
  email: '',
  dateOfBirth: '', // YYYY-MM-DD
  preferredDate: '', // YYYY-MM-DD
  preferredTime: '', // HH:mm
  symptomDescription: props.initialSymptomDescription ?? '',
});

const loading = ref(false);
const recaptchaSiteKey = import.meta.env.VITE_RECAPTCHA_SITE_KEY ?? '';
const recaptchaToken = ref('');
const recaptchaWidgetId = ref<number | null>(null);
const recaptchaContainerRef = ref<HTMLDivElement | null>(null);
const recaptchaLoading = ref(false);
const recaptchaFailed = ref(false);
let recaptchaScriptPromise: Promise<void> | null = null;

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

const SYMPTOM_MAX_LENGTH = 1000;

type FieldKey = 'fullName' | 'phone' | 'email' | 'dateOfBirth' | 'preferredDate' | 'preferredTime' | 'symptomDescription';

const preferredAtISO = computed(() => {
  if (!form.value.preferredDate || !form.value.preferredTime) return '';
  return `${form.value.preferredDate}T${form.value.preferredTime}:00`;
});

const invalidFields = ref<Set<FieldKey>>(new Set());

const updateInvalidFields = (updater: (current: Set<FieldKey>) => Set<FieldKey>) => {
  invalidFields.value = updater(new Set(invalidFields.value));
};

const markInvalid = (field: FieldKey) => {
  updateInvalidFields((current) => {
    current.add(field);
    return current;
  });
};

const clearInvalid = (field: FieldKey) => {
  if (!invalidFields.value.has(field)) return;
  updateInvalidFields((current) => {
    current.delete(field);
    return current;
  });
};

const isFieldInvalid = (field: FieldKey) => invalidFields.value.has(field);

const resetInvalidFields = () => {
  invalidFields.value = new Set();
};

const handleInput = (field: FieldKey, event: Event) => {
  const target = event.target as HTMLInputElement | HTMLTextAreaElement | HTMLSelectElement;
  target.setCustomValidity('');
  clearInvalid(field);
};

const sanitizePhoneValue = (value: string) => value.replace(/\D/g, '').slice(0, 10);

const handlePhoneInput = (event: Event) => {
  const target = event.target as HTMLInputElement;
  const sanitized = sanitizePhoneValue(target.value);
  if (sanitized !== target.value) {
    target.value = sanitized;
  }
  form.value.phone = sanitized;
  handleInput('phone', event);
};

const onInvalid = (field: FieldKey, event: Event, messages: InvalidMessageMap) => {
  const target = event.target as HTMLInputElement | HTMLTextAreaElement | HTMLSelectElement;
  event.preventDefault();
  target.setCustomValidity('');
  const { validity } = target;

  let toastMessage = '';
  if (validity.valueMissing && messages.required) {
    toastMessage = messages.required;
  }
  if (!toastMessage && validity.patternMismatch && messages.patternMismatch) {
    toastMessage = messages.patternMismatch;
  }
  if (!toastMessage && validity.typeMismatch && messages.typeMismatch) {
    toastMessage = messages.typeMismatch;
  }
  if (!toastMessage && validity.rangeUnderflow && messages.rangeUnderflow) {
    toastMessage = messages.rangeUnderflow;
  }
  if (!toastMessage && validity.rangeOverflow && messages.rangeOverflow) {
    toastMessage = messages.rangeOverflow;
  }

  markInvalid(field);
  emitToast('error', toastMessage || 'Thông tin chưa hợp lệ, vui lòng kiểm tra lại.');
};

const emitToast = (type: ToastType, message: string) => {
  emitting('notify', { type, message });
};

const validatePhone = (value: string) => /^0\d{9}$/.test(value);

const isTimeSelectable = (date: string, time: string) => {
  if (!date || !time || !ALL_SLOTS.includes(time)) return false;
  if (date !== today) return true;

  const [hourStr = '0', minuteStr = '0'] = time.split(':');
  const slotMinutes = toMinutes(Number(hourStr), Number(minuteStr));
  const nowTime = new Date();
  const currentMinutes = toMinutes(nowTime.getHours(), nowTime.getMinutes());
  return slotMinutes >= currentMinutes;
};

const availableSlots = computed(() => {
  if (!form.value.preferredDate) {
    return {
      morning: MORNING_SLOTS,
      afternoon: AFTERNOON_SLOTS,
    };
  }

  const filterByDate = (slots: string[]) => slots.filter((slot) => isTimeSelectable(form.value.preferredDate, slot));

  return {
    morning: filterByDate(MORNING_SLOTS),
    afternoon: filterByDate(AFTERNOON_SLOTS),
  };
});

const hasAvailableSlots = computed(() => availableSlots.value.morning.length > 0 || availableSlots.value.afternoon.length > 0);

const ensureRecaptchaScript = () => {
  if (!recaptchaSiteKey || typeof window === 'undefined') {
    return Promise.resolve();
  }
  if ((window as any).grecaptcha) {
    return Promise.resolve();
  }
  if (!recaptchaScriptPromise) {
    recaptchaScriptPromise = new Promise((resolve, reject) => {
      const script = document.createElement('script');
      script.id = 'google-recaptcha-script';
      script.src = 'https://www.google.com/recaptcha/api.js?render=explicit';
      script.async = true;
      script.defer = true;
      script.onload = () => resolve();
      script.onerror = () => reject(new Error('Không thể tải reCAPTCHA.'));
      document.head.appendChild(script);
    });
  }
  return recaptchaScriptPromise;
};

const resetRecaptcha = () => {
  const grecaptcha = (window as any)?.grecaptcha;
  if (recaptchaWidgetId.value !== null && grecaptcha) {
    grecaptcha.reset(recaptchaWidgetId.value);
  }
  recaptchaToken.value = '';
};

const initRecaptcha = async () => {
  if (!recaptchaSiteKey || typeof window === 'undefined') {
    return;
  }
  try {
    await ensureRecaptchaScript();
  } catch (error) {
    recaptchaFailed.value = true;
    emitToast('error', 'Không thể tải reCAPTCHA. Vui lòng thử lại sau.');
    return;
  }
  const grecaptcha = (window as any)?.grecaptcha;
  if (!grecaptcha || !recaptchaContainerRef.value) {
    recaptchaFailed.value = true;
    return;
  }
  grecaptcha.ready(() => {
    recaptchaFailed.value = false;
    if (!recaptchaContainerRef.value) {
      return;
    }
    recaptchaWidgetId.value = grecaptcha.render(recaptchaContainerRef.value, {
      sitekey: recaptchaSiteKey,
      callback: (token: string) => {
        recaptchaToken.value = token;
      },
      'expired-callback': () => {
        recaptchaToken.value = '';
      },
      'error-callback': () => {
        recaptchaToken.value = '';
        recaptchaFailed.value = true;
      },
    });
  });
};

watch(
  () => [form.value.preferredDate, form.value.preferredTime],
  ([date, time]) => {
    const currentDate = date ?? '';
    const currentTime = time ?? '';
    if (!currentTime) return;
    if (!isTimeSelectable(currentDate, currentTime)) {
      form.value.preferredTime = '';
    }
  }
);

watch(
  () => props.initialSymptomDescription,
  (val) => {
    if (typeof val === 'string' && val !== form.value.symptomDescription) {
      form.value.symptomDescription = val;
    }
  }
);

onMounted(async () => {
  if (!recaptchaSiteKey) return;
  recaptchaLoading.value = true;
  await initRecaptcha();
  recaptchaLoading.value = false;
});

onBeforeUnmount(() => {
  resetRecaptcha();
});

const handleSubmit = async () => {
  if (loading.value) return;
  resetInvalidFields();

  const errors: string[] = [];
  const addError = (msg: string) => {
    if (msg && !errors.includes(msg)) errors.push(msg);
  };

  const { fullName, phone, dateOfBirth, preferredDate, preferredTime, symptomDescription } = form.value;
  const trimmedFullName = fullName?.trim() ?? '';
  const trimmedPhone = phone?.trim() ?? '';
  const trimmedSymptoms = symptomDescription?.trim() ?? '';

  if (!trimmedFullName) {
    markInvalid('fullName');
    addError('Vui lòng nhập họ và tên.');
  }
  if (!trimmedPhone) {
    markInvalid('phone');
    addError('Vui lòng nhập số điện thoại.');
  } else if (!validatePhone(trimmedPhone)) {
    markInvalid('phone');
    addError(
      trimmedPhone.startsWith('0')
        ? 'Số điện thoại phải gồm đúng 10 chữ số.'
        : 'Số điện thoại phải bắt đầu bằng số 0 và đủ 10 chữ số.'
    );
  }
  if (!dateOfBirth) {
    markInvalid('dateOfBirth');
    addError('Vui lòng chọn ngày sinh.');
  } else if (dateOfBirth > today) {
    markInvalid('dateOfBirth');
    addError('Ngày sinh không được muộn hơn hôm nay.');
  }
  if (!preferredDate) {
    markInvalid('preferredDate');
    addError('Vui lòng chọn ngày khám.');
  } else if (preferredDate < today) {
    markInvalid('preferredDate');
    addError('Ngày khám phải từ hôm nay trở đi.');
  }
  if (!preferredTime) {
    markInvalid('preferredTime');
    addError('Vui lòng chọn giờ khám.');
  }
  if (!trimmedSymptoms) {
    markInvalid('symptomDescription');
    addError('Vui lòng mô tả triệu chứng hoặc nhu cầu khám.');
  }

  if (preferredDate && preferredTime && !isTimeSelectable(preferredDate, preferredTime)) {
    markInvalid('preferredTime');
    addError(
      preferredDate === today
        ? 'Khung giờ đã trôi qua. Vui lòng chọn giờ khác hoặc ngày khác.'
        : 'Giờ khám không hợp lệ, vui lòng chọn lại'
    );
  }

  if (trimmedSymptoms.length > SYMPTOM_MAX_LENGTH) {
    markInvalid('symptomDescription');
    addError(`Ghi chú quá dài. Vui lòng rút gọn dưới ${SYMPTOM_MAX_LENGTH} ký tự.`);
  }

  if (recaptchaSiteKey && !recaptchaToken.value) {
    addError('Vui lòng xác minh bạn không phải robot.');
  }

  if (errors.length > 0) {
    emitToast('error', errors.join(' '));
    return;
  }

  const payload: AppointmentRequestPayload = {
    fullName: form.value.fullName.trim(),
    phone: form.value.phone.trim(),
    email: form.value.email.trim() || undefined,
    dateOfBirth: form.value.dateOfBirth,
    preferredAt: preferredAtISO.value,
    symptomDescription: form.value.symptomDescription.trim(),
    recaptchaToken: recaptchaToken.value || undefined,
  };

  try {
    loading.value = true;
    await createAppointmentRequest(payload);
    emitToast('success', 'Gửi yêu cầu đặt lịch thành công! Chúng tôi sẽ liên hệ sớm.');
    emitting('submitted');
    
    // Reset toàn bộ form
    form.value = {
      fullName: '',
      phone: '',
      email: '',
      dateOfBirth: '',
      preferredDate: '',
      preferredTime: '',
      symptomDescription: '',
    };
    resetInvalidFields();
    resetRecaptcha();
    
    // Reload trang sau 1.5 giây để người dùng thấy thông báo
    setTimeout(() => {
      window.location.reload();
    }, 1500);
  } catch (e: any) {
    emitToast('error', e?.response?.data?.message || 'Gửi yêu cầu thất bại. Vui lòng thử lại.');
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

    <form class="grid grid-cols-1 gap-6 sm:grid-cols-2" novalidate @submit.prevent="handleSubmit">
      <div class="sm:col-span-1">
        <label class="mb-1.5 block text-sm font-semibold text-slate-700" for="fullName">Họ và tên</label>
        <input
          id="fullName"
          v-model="form.fullName"
          type="text"
          required
          class="w-full rounded-xl border border-emerald-100 bg-white px-4 py-3 text-sm text-slate-900 shadow-sm transition focus:border-emerald-400 focus:outline-none focus:ring-4 focus:ring-emerald-100/80"
          :class="isFieldInvalid('fullName') ? 'border-rose-300 focus:border-rose-400 focus:ring-rose-100/80 shake-input' : ''"
          placeholder="Nguyễn Văn A"
          @input="handleInput('fullName', $event)"
          @invalid="onInvalid('fullName', $event, { required: 'Vui lòng nhập họ và tên.' })"
        />
      </div>

      <div class="sm:col-span-1">
        <label class="mb-1.5 block text-sm font-semibold text-slate-700" for="phone">Số điện thoại</label>
        <input
          id="phone"
          v-model="form.phone"
          type="tel"
          inputmode="numeric"
          maxlength="10"
          required
          pattern="\d{10}"
          class="w-full rounded-xl border border-emerald-100 bg-white px-4 py-3 text-sm text-slate-900 shadow-sm transition focus:border-emerald-400 focus:outline-none focus:ring-4 focus:ring-emerald-100/80"
          :class="isFieldInvalid('phone') ? 'border-rose-300 focus:border-rose-400 focus:ring-rose-100/80 shake-input' : ''"
          placeholder="Hãy nhập đúng 10 số"
          @input="handlePhoneInput"
          @invalid="onInvalid('phone', $event, { required: 'Vui lòng nhập số điện thoại.', patternMismatch: 'Số điện thoại phải gồm đúng 10 chữ số.' })"
        />
      </div>

      <div class="sm:col-span-1">
        <label class="mb-1.5 block text-sm font-semibold text-slate-700" for="email">Email (Tùy chọn)</label>
        <input
          id="email"
          v-model="form.email"
          type="email"
          class="w-full rounded-xl border border-emerald-100 bg-white px-4 py-3 text-sm text-slate-900 shadow-sm transition focus:border-emerald-400 focus:outline-none focus:ring-4 focus:ring-emerald-100/80"
          :class="isFieldInvalid('email') ? 'border-rose-300 focus:border-rose-400 focus:ring-rose-100/80 shake-input' : ''"
          placeholder="you@example.com"
          @input="handleInput('email', $event)"
          @invalid="onInvalid('email', $event, { typeMismatch: 'Email chưa đúng định dạng. Vui lòng kiểm tra lại.' })"
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
          :class="isFieldInvalid('dateOfBirth') ? 'border-rose-300 focus:border-rose-400 focus:ring-rose-100/80 shake-input' : ''"
          @input="handleInput('dateOfBirth', $event)"
          @invalid="onInvalid('dateOfBirth', $event, { required: 'Vui lòng chọn ngày sinh.', rangeOverflow: 'Ngày sinh không được muộn hơn hôm nay.' })"
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
          :class="isFieldInvalid('preferredDate') ? 'border-rose-300 focus:border-rose-400 focus:ring-rose-100/80 shake-input' : ''"
          @input="handleInput('preferredDate', $event)"
          @invalid="onInvalid('preferredDate', $event, { required: 'Vui lòng chọn ngày khám.', rangeUnderflow: 'Ngày khám phải từ hôm nay trở đi.' })"
        />
      </div>

      <div class="sm:col-span-1">
        <label class="mb-1.5 block text-sm font-semibold text-slate-700" for="preferredTime">Giờ mong muốn</label>
        <select
          id="preferredTime"
          v-model="form.preferredTime"
          required
          class="w-full rounded-xl border border-emerald-100 bg-white px-4 py-3 text-sm text-slate-900 shadow-sm transition focus:border-emerald-400 focus:outline-none focus:ring-4 focus:ring-emerald-100/80"
          :class="isFieldInvalid('preferredTime') ? 'border-rose-300 focus:border-rose-400 focus:ring-rose-100/80 shake-input' : ''"
          @input="handleInput('preferredTime', $event)"
          @change="handleInput('preferredTime', $event)"
          @invalid="onInvalid('preferredTime', $event, { required: 'Vui lòng chọn giờ khám.' })"
        >
          <option disabled value="">Chọn giờ khám</option>
          <option v-if="form.preferredDate === today && !hasAvailableSlots" disabled value="__no-slots">
            Không còn khung giờ phù hợp trong hôm nay
          </option>
          <optgroup v-if="availableSlots.morning.length" label="Buổi sáng">
            <option v-for="t in availableSlots.morning" :key="`morning-${t}`" :value="t">{{ t }}</option>
          </optgroup>
          <optgroup v-if="availableSlots.afternoon.length" label="Buổi chiều">
            <option v-for="t in availableSlots.afternoon" :key="`afternoon-${t}`" :value="t">{{ t }}</option>
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
          :class="isFieldInvalid('symptomDescription') ? 'border-rose-300 focus:border-rose-400 focus:ring-rose-100/80 shake-input' : ''"
          placeholder="Ví dụ: ho, sốt, đau họng trong 3 ngày..."
          @input="handleInput('symptomDescription', $event)"
          @invalid="onInvalid('symptomDescription', $event, { required: 'Vui lòng mô tả triệu chứng hoặc nhu cầu khám.' })"
        />
      </div>

      <div v-if="recaptchaSiteKey" class="sm:col-span-2">
        <label class="mb-1.5 block text-sm font-semibold text-slate-700">Xác minh bảo mật</label>
        <div
          ref="recaptchaContainerRef"
          class="rounded-xl border border-emerald-100 bg-emerald-50/70 px-4 py-3 text-sm text-slate-600"
        >
          <p v-if="recaptchaLoading">Đang tải reCAPTCHA...</p>
          <p v-else-if="recaptchaFailed" class="text-rose-600">
            Không thể tải reCAPTCHA. Vui lòng tải lại trang hoặc thử lại sau.
          </p>
        </div>
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

</template>

<style scoped>
@keyframes input-shake {
  0% {
    transform: translateX(0);
  }
  25% {
    transform: translateX(-3px);
  }
  50% {
    transform: translateX(3px);
  }
  75% {
    transform: translateX(-2px);
  }
  100% {
    transform: translateX(0);
  }
}

.shake-input {
  animation: input-shake 0.25s ease-in-out;
}
</style>
