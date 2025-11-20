<script setup lang="ts">
import { computed, ref } from 'vue';
import { useRouter } from 'vue-router';
import PublicHeader from '@/components/PublicHeader.vue';
import PublicFooter from '@/components/PublicFooter.vue';
import { analyzeSymptoms, type DiagnosisResponse, type DiseasePrediction } from '@/services/diagnosis.service';

type SymptomChoice = {
  label: string;
  value: string;
};

const router = useRouter();

const availableSymptoms: SymptomChoice[] = [
  { label: 'Sốt', value: 'fever' },
  { label: 'Ho', value: 'cough' },
  { label: 'Khó thở', value: 'shortness of breath' },
  { label: 'Đau ngực nhói', value: 'sharp chest pain' },
  { label: 'Nặng ngực', value: 'chest tightness' },
  { label: 'Đau đầu', value: 'headache' },
  { label: 'Chóng mặt', value: 'dizziness' },
  { label: 'Tim đập nhanh', value: 'palpitations' },
  { label: 'Mệt mỏi', value: 'fatigue' },
  { label: 'Buồn nôn', value: 'nausea' },
  { label: 'Nôn ói', value: 'vomiting' },
  { label: 'Tiêu chảy', value: 'diarrhea' },
  { label: 'Đau bụng dưới', value: 'lower abdominal pain' },
  { label: 'Đau cơ', value: 'muscle pain' },
  { label: 'Đau khớp', value: 'joint pain' },
  { label: 'Đổ mồ hôi', value: 'sweating' },
  { label: 'Khó ngủ', value: 'insomnia' },
  { label: 'Khó thở dữ dội', value: 'difficulty breathing' },
  { label: 'Ho ra đờm', value: 'coughing up sputum' },
  { label: 'Sụt cân gần đây', value: 'recent weight loss' },
];

const selectedSymptoms = ref<string[]>([]);
const topK = ref(5);
const isLoading = ref(false);
const diagnosisResult = ref<DiagnosisResponse | null>(null);
const errorMessage = ref<string | null>(null);

const probabilityFormatter = new Intl.NumberFormat('vi-VN', {
  style: 'percent',
  minimumFractionDigits: 1,
  maximumFractionDigits: 1,
});

const disclaimerText = computed(
  () =>
    'Gợi ý đến từ mô hình AI nội bộ và chỉ mang tính tham khảo. Hãy liên hệ bác sĩ khi triệu chứng kéo dài hoặc trở nên nghiêm trọng.'
);

const severityBadgeClasses = (severity: string) => {
  switch (severity) {
    case 'nặng':
      return 'bg-rose-100 text-rose-700';
    case 'trung bình':
      return 'bg-amber-100 text-amber-700';
    default:
      return 'bg-emerald-100 text-emerald-700';
  }
};

const clampTopK = (value: number) => {
  if (!Number.isFinite(value)) {
    return 5;
  }
  return Math.min(10, Math.max(1, Math.trunc(value)));
};

const formatProbability = (value: number) => probabilityFormatter.format(Math.min(1, Math.max(0, value ?? 0)));

const handleAnalyze = async () => {
  if (!selectedSymptoms.value.length) {
    errorMessage.value = 'Vui lòng chọn ít nhất một triệu chứng.';
    diagnosisResult.value = null;
    return;
  }
  isLoading.value = true;
  errorMessage.value = null;
  diagnosisResult.value = null;
  try {
    const payload = {
      symptoms: [...selectedSymptoms.value],
      topK: clampTopK(topK.value),
    };
    const response = await analyzeSymptoms(payload);
    diagnosisResult.value = response;
  } catch (err: unknown) {
    const maybeError = err as { response?: { data?: { detail?: string; message?: string } } };
    errorMessage.value =
      maybeError.response?.data?.detail ??
      (typeof maybeError.response?.data?.message === 'string' ? maybeError.response?.data?.message : null) ??
      'Không thể phân tích triệu chứng vào lúc này. Vui lòng thử lại sau.';
  } finally {
    isLoading.value = false;
  }
};

const goToBooking = (prediction: DiseasePrediction) => {
  router.push({
    name: 'booking',
    query: { reason: prediction.disease },
  });
};

const resetSelections = () => {
  selectedSymptoms.value = [];
  diagnosisResult.value = null;
  errorMessage.value = null;
};
</script>

<template>
  <div class="min-h-screen bg-white">
    <PublicHeader />
    <main class="bg-gradient-to-b from-emerald-50/70 via-white to-white">
      <section class="mx-auto max-w-6xl px-6 py-12 lg:py-16">
        <div class="grid gap-10 lg:grid-cols-[1.1fr_0.9fr]">
          <div class="rounded-3xl border border-emerald-100 bg-white/95 p-8 shadow-xl">
            <div class="flex items-center justify-between gap-3">
              <div>
                <p class="text-sm font-semibold uppercase tracking-[0.25em] text-emerald-600">Tư vấn triệu chứng</p>
                <h1 class="mt-2 text-3xl font-bold text-slate-900">Chuẩn đoán sơ bộ bằng AI</h1>
                <p class="mt-3 text-base text-slate-600">
                  Chọn những triệu chứng nổi bật mà bạn gặp phải, hệ thống sẽ đề xuất các bệnh lý có xác suất cao kèm
                  mức độ nghiêm trọng và gợi ý đặt lịch khám.
                </p>
              </div>
              <div class="hidden rounded-2xl bg-emerald-100/80 p-4 text-sm font-semibold text-emerald-700 md:block">
                {{ selectedSymptoms.length }} triệu chứng
              </div>
            </div>
            <div class="mt-6 space-y-4">
              <p class="flex items-start gap-2 rounded-2xl border border-amber-100 bg-amber-50/70 p-4 text-sm text-amber-800">
                <svg xmlns="http://www.w3.org/2000/svg" class="mt-0.5 h-5 w-5" viewBox="0 0 20 20" fill="currentColor">
                  <path
                    fill-rule="evenodd"
                    d="M10 18a8 8 0 100-16 8 8 0 000 16zm-.75-5.25a.75.75 0 011.5 0v1.5a.75.75 0 01-1.5 0v-1.5zm0-6.5a.75.75 0 011.5 0v4a.75.75 0 01-1.5 0v-4z"
                    clip-rule="evenodd"
                  />
                </svg>
                <span>{{ disclaimerText }}</span>
              </p>
              <div class="rounded-2xl border border-slate-200 bg-slate-50/60 p-4">
                <div class="flex items-center justify-between text-sm font-semibold text-slate-700">
                  <span>Chọn số gợi ý</span>
                  <span>{{ clampTopK(topK) }} bệnh</span>
                </div>
                <input
                  v-model.number="topK"
                  type="range"
                  min="3"
                  max="10"
                  step="1"
                  class="mt-3 w-full accent-emerald-600"
                />
              </div>
              <div class="grid gap-3 sm:grid-cols-2">
                <label
                  v-for="symptom in availableSymptoms"
                  :key="symptom.value"
                  class="flex cursor-pointer items-center gap-3 rounded-2xl border border-slate-200 bg-white p-3 text-sm font-medium text-slate-700 shadow-sm transition hover:border-emerald-400"
                >
                  <input
                    v-model="selectedSymptoms"
                    type="checkbox"
                    :value="symptom.value"
                    class="h-4 w-4 rounded border-slate-300 text-emerald-600 focus:ring-emerald-500"
                  />
                  <span>{{ symptom.label }}</span>
                </label>
              </div>
              <div class="flex flex-wrap items-center gap-3 pt-3">
                <button
                  class="inline-flex items-center justify-center rounded-full bg-emerald-600 px-6 py-3 text-base font-semibold text-white shadow-lg shadow-emerald-500/30 transition hover:bg-emerald-700 disabled:cursor-not-allowed disabled:bg-slate-400"
                  :disabled="isLoading"
                  @click="handleAnalyze"
                >
                  <svg
                    v-if="isLoading"
                    class="mr-2 h-5 w-5 animate-spin text-white"
                    xmlns="http://www.w3.org/2000/svg"
                    fill="none"
                    viewBox="0 0 24 24"
                  >
                    <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4" />
                    <path
                      class="opacity-75"
                      fill="currentColor"
                      d="M4 12a8 8 0 018-8v4a4 4 0 00-4 4H4z"
                    />
                  </svg>
                  {{ isLoading ? 'Đang phân tích...' : 'Phân tích triệu chứng' }}
                </button>
                <button
                  class="text-sm font-semibold text-slate-600 underline-offset-4 hover:text-emerald-600 hover:underline"
                  type="button"
                  @click="resetSelections"
                >
                  Xóa lựa chọn
                </button>
              </div>
              <p v-if="errorMessage" class="rounded-2xl border border-rose-200 bg-rose-50/80 px-4 py-3 text-sm text-rose-700">
                {{ errorMessage }}
              </p>
            </div>
          </div>

          <div class="space-y-6">
            <div class="rounded-3xl border border-slate-200 bg-white/95 p-6 shadow-xl">
              <h2 class="text-xl font-semibold text-slate-900">Kết quả gợi ý</h2>
              <p class="mt-2 text-sm text-slate-500">Hệ thống so khớp hàng trăm triệu chứng để đưa ra danh sách bên dưới.</p>
              <div v-if="diagnosisResult?.predictions?.length" class="mt-6 space-y-4">
                <article
                  v-for="prediction in diagnosisResult.predictions"
                  :key="prediction.disease"
                  class="rounded-2xl border border-slate-100 bg-slate-50/70 p-5 shadow-sm"
                >
                  <div class="flex flex-wrap items-center justify-between gap-3">
                    <div>
                      <p class="text-lg font-semibold text-slate-900">{{ prediction.disease }}</p>
                      <p class="text-sm text-slate-600">Xác suất: {{ formatProbability(prediction.probability) }}</p>
                    </div>
                    <span class="rounded-full px-4 py-1 text-xs font-semibold uppercase tracking-wide" :class="severityBadgeClasses(prediction.severity)">
                      {{ prediction.severity }}
                    </span>
                  </div>
                  <div
                    v-if="prediction.shouldBookAppointment"
                    class="mt-4 rounded-2xl border border-rose-200 bg-rose-50/80 p-4 text-sm text-rose-700"
                  >
                    <div class="flex flex-wrap items-center justify-between gap-3">
                      <div>
                        <p class="font-semibold">Khuyến nghị đặt lịch khám</p>
                        <p class="text-rose-600">
                          Triệu chứng có dấu hiệu đáng lo. Hãy đặt lịch khám sớm để được bác sĩ đánh giá trực tiếp.
                        </p>
                      </div>
                      <button
                        class="rounded-full bg-rose-600 px-4 py-2 text-xs font-semibold uppercase tracking-wide text-white shadow hover:bg-rose-700"
                        type="button"
                        @click="goToBooking(prediction)"
                      >
                        Đặt lịch khám
                      </button>
                    </div>
                  </div>
                </article>
              </div>
              <div v-else class="mt-6 rounded-2xl border border-dashed border-slate-200 bg-white p-6 text-center text-sm text-slate-500">
                Hãy chọn triệu chứng và bấm "Phân tích triệu chứng" để xem gợi ý bệnh cùng mức độ nghiêm trọng.
              </div>
            </div>

            <div class="rounded-3xl border border-slate-200 bg-white/95 p-6 shadow">
              <h3 class="text-lg font-semibold text-slate-900">Gợi ý chăm sóc</h3>
              <ul class="mt-3 space-y-2 text-sm text-slate-600">
                <li>• Uống đủ nước, nghỉ ngơi hợp lý và theo dõi sát thay đổi cơ thể.</li>
                <li>• Nếu đã có bệnh nền, hãy báo cho bác sĩ để được hướng dẫn riêng.</li>
                <li>• Liên hệ hotline phòng khám hoặc đặt lịch trực tuyến ngay khi có dấu hiệu trở nặng.</li>
              </ul>
              <p class="mt-4 rounded-2xl border border-emerald-100 bg-emerald-50/80 p-4 text-xs font-semibold uppercase tracking-wide text-emerald-700">
                {{ disclaimerText }}
              </p>
            </div>
          </div>
        </div>
      </section>
    </main>
    <PublicFooter />
  </div>
</template>
