<script setup lang="ts">
import { ref, computed } from 'vue';
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

const today = now.toISOString().slice(0, 10);

const toMinutes = (h: number, m: number) => h * 60 + m;
const toHHMM = (mins: number) => {
  const h = Math.floor(mins / 60).toString().padStart(2, '0');
  const m = (mins % 60).toString().padStart(2, '0');
  return `${h}:${m}`;
};
const makeSlots = (start: number, end: number, step = 20) => {
  const out: string[] = [];
  for (let t = start; t <= end; t += step) out.push(toHHMM(t));
  return out;
};

const MORNING_SLOTS = makeSlots(toMinutes(7, 30), toMinutes(11, 10));
const AFTERNOON_SLOTS = makeSlots(toMinutes(13, 0), toMinutes(19, 0));
const ALL_SLOTS = [...MORNING_SLOTS, ...AFTERNOON_SLOTS];

const preferredAtISO = computed(() => {
  if (!form.value.preferredDate || !form.value.preferredTime) return '';
  return `${form.value.preferredDate}T${form.value.preferredTime}:00`;
});

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
  <div class="rounded-2xl border border-slate-200 bg-white p-6 shadow">
    <h3 class="mb-1 text-lg font-semibold text-slate-900">Đặt lịch khám</h3>
    <p class="mb-6 text-sm text-slate-600">Các trường thông tin đều bắt buộc. Chúng tôi sẽ liên hệ sớm.</p>

    <form class="grid grid-cols-1 gap-4 sm:grid-cols-2" @submit.prevent="handleSubmit">
      <div class="sm:col-span-1">
        <label class="mb-1 block text-sm font-medium text-slate-700" for="fullName">Họ và tên</label>
        <input
          id="fullName"
          v-model="form.fullName"
          type="text"
          required
          class="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm shadow-sm focus:border-blue-500 focus:outline-none focus:ring-2 focus:ring-blue-100"
          placeholder="Nguyễn Văn A"
        />
      </div>

      <div class="sm:col-span-1">
        <label class="mb-1 block text-sm font-medium text-slate-700" for="phone">Số điện thoại</label>
        <input
          id="phone"
          v-model="form.phone"
          type="tel"
          required
          pattern="\d{10}"
          class="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm shadow-sm focus:border-blue-500 focus:outline-none focus:ring-2 focus:ring-blue-100"
          placeholder="0987654321"
        />
      </div>

      <div class="sm:col-span-1">
        <label class="mb-1 block text-sm font-medium text-slate-700" for="email">Email</label>
        <input
          id="email"
          v-model="form.email"
          type="email"
          required
          class="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm shadow-sm focus:border-blue-500 focus:outline-none focus:ring-2 focus:ring-blue-100"
          placeholder="you@example.com"
        />
      </div>

      <div class="sm:col-span-1">
        <label class="mb-1 block text-sm font-medium text-slate-700" for="dob">Ngày sinh </label>
        <input
          id="dob"
          v-model="form.dateOfBirth"
          type="date"
          :max="today"
          required
          class="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm shadow-sm focus:border-blue-500 focus:outline-none focus:ring-2 focus:ring-blue-100"
        />
      </div>

      <div class="sm:col-span-1">
        <label class="mb-1 block text-sm font-medium text-slate-700" for="preferredDate">Ngày mong muốn</label>
        <input
          id="preferredDate"
          v-model="form.preferredDate"
          type="date"
          :min="today"
          required
          class="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm shadow-sm focus:border-blue-500 focus:outline-none focus:ring-2 focus:ring-blue-100"
        />
      </div>

      <div class="sm:col-span-1">
        <label class="mb-1 block text-sm font-medium text-slate-700" for="preferredTime">Giờ mong muốn</label>
        <select
          id="preferredTime"
          v-model="form.preferredTime"
          required
          class="w-full rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm shadow-sm focus:border-blue-500 focus:outline-none focus:ring-2 focus:ring-blue-100"
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
        <label class="mb-1 block text-sm font-medium text-slate-700" for="symptoms">Mô tả triệu chứng (tuỳ chọn)</label>
        <textarea
          id="symptoms"
          v-model="form.symptomDescription"
          rows="4"
          required
          maxlength="1000"
          class="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm shadow-sm focus:border-blue-500 focus:outline-none focus:ring-2 focus:ring-blue-100"
          placeholder="Ví dụ: ho, sốt, đau họng trong 3 ngày..."
        />
      </div>

      <div class="sm:col-span-2 flex items-center gap-4">
        <button
          type="submit"
          class="rounded-lg bg-blue-600 px-4 py-2 text-sm font-semibold text-white shadow hover:bg-blue-700 disabled:opacity-60"
          :disabled="loading"
        >
          <span v-if="!loading">Gửi yêu cầu</span>
          <span v-else>Đang gửi...</span>
        </button>
        <p v-if="error" class="text-sm text-rose-600">{{ error }}</p>
        <p v-if="success" class="text-sm text-emerald-600">{{ success }}</p>
      </div>
    </form>
  </div>
</template>
