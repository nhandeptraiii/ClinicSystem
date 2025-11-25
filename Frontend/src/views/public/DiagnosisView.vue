<script setup lang="ts">
import { computed, ref } from 'vue';
import { useRouter } from 'vue-router';
import PublicHeader from '@/components/PublicHeader.vue';
import PublicFooter from '@/components/PublicFooter.vue';
import { analyzeSymptoms, type DiagnosisResponse, type DiseasePrediction } from '@/services/diagnosis.service';
import { symptomGroups, type SymptomItem } from '@/config/diagnosisSymptoms';
import { diseaseDictionary } from '@/config/diseaseTranslations';

type SymptomChoice = SymptomItem;
type DecoratedPrediction = DiseasePrediction & {
  nameVi: string;
  nameEn: string;
  severity: 'low' | 'medium' | 'high';
  noteVi?: string;
};

const router = useRouter();
const selectedSymptomValues = ref<string[]>([]);
const topK = ref(5);
const isLoading = ref(false);
const diagnosisResult = ref<DiagnosisResponse | null>(null);
const errorMessage = ref<string | null>(null);
const collapsedGroups = ref<Record<string, boolean>>(
  Object.fromEntries(symptomGroups.map((g) => [g.id, true]))
);
const showResultsModal = ref(false);

const probabilityFormatter = new Intl.NumberFormat('vi-VN', {
  style: 'percent',
  minimumFractionDigits: 1,
  maximumFractionDigits: 1,
});

const disclaimerText = computed(
  () =>
    'Gợi ý đến từ mô hình AI nội bộ và chỉ mang tính tham khảo. Hãy liên hệ bác sĩ khi triệu chứng kéo dài hoặc trở nên nghiêm trọng.'
);

const severityBadgeClasses = (severity?: string) => {
  switch (severity) {
    case 'high':
    case 'nặng':
      return 'bg-rose-100 text-rose-700';
    case 'medium':
    case 'trung bình':
      return 'bg-amber-100 text-amber-700';
    default:
      return 'bg-emerald-100 text-emerald-700';
  }
};

const severityLabelVi = (severity?: string) => {
  switch (severity) {
    case 'high':
    case 'nặng':
      return 'Nguy cơ cao';
    case 'medium':
    case 'trung bình':
      return 'Trung bình';
    default:
      return 'Nhẹ';
  }
};

const normalizeSeverity = (severity?: string): DecoratedPrediction['severity'] => {
  const normalized = severity?.toLowerCase();
  if (normalized === 'high' || normalized === 'nặng') {
    return 'high';
  }
  if (normalized === 'medium' || normalized === 'trung bình') {
    return 'medium';
  }
  return 'low';
};

const clampTopK = (value: number) => {
  if (!Number.isFinite(value)) {
    return 5;
  }
  return Math.min(10, Math.max(1, Math.trunc(value)));
};

const formatProbability = (value: number) => probabilityFormatter.format(Math.min(1, Math.max(0, value ?? 0)));

const handleAnalyze = async () => {
  if (!selectedSymptomValues.value.length) {
    errorMessage.value = 'Vui lòng chọn ít nhất một triệu chứng.';
    diagnosisResult.value = null;
    showResultsModal.value = false;
    return;
  }
  isLoading.value = true;
  errorMessage.value = null;
  diagnosisResult.value = null;
  try {
    const payload = {
      symptoms: [...selectedSymptomValues.value],
      topK: clampTopK(topK.value),
    };
    const response = await analyzeSymptoms(payload);
    diagnosisResult.value = response;
    showResultsModal.value = Boolean(response?.predictions?.length);
  } catch (err: unknown) {
    const maybeError = err as { response?: { data?: { detail?: string; message?: string } } };
    errorMessage.value =
      maybeError.response?.data?.detail ??
      (typeof maybeError.response?.data?.message === 'string' ? maybeError.response?.data?.message : null) ??
      'Không thể phân tích triệu chứng vào lúc này. Vui lòng thử lại sau.';
    showResultsModal.value = false;
  } finally {
    isLoading.value = false;
  }
};

const goToBooking = (prediction: DecoratedPrediction) => {
  router.push({
    name: 'booking',
    query: { reason: prediction.disease },
  });
};

const resetSelections = () => {
  selectedSymptomValues.value = [];
  diagnosisResult.value = null;
  errorMessage.value = null;
  showResultsModal.value = false;
};

const decoratedPredictions = computed<DecoratedPrediction[]>(() => {
  const makeTitle = (text: string) => text.replace(/\w\S*/g, (word) => word.charAt(0).toUpperCase() + word.slice(1));
  return (
    diagnosisResult.value?.predictions.map((p) => {
      const info = diseaseDictionary[p.disease] ?? diseaseDictionary[p.disease.toLowerCase()];
      const severity = normalizeSeverity(info?.severity ?? p.severity);
      return {
        ...p,
        severity,
        nameVi: info?.nameVi ?? makeTitle(p.disease),
        nameEn: info?.nameEn ?? makeTitle(p.disease),
        noteVi: info?.noteVi,
      };
    }) ?? []
  );
});

const closeResultsModal = () => {
  showResultsModal.value = false;
};

const toggleGroup = (groupId: string) => {
  collapsedGroups.value = { ...collapsedGroups.value, [groupId]: !collapsedGroups.value[groupId] };
};

const isGroupCollapsed = (groupId: string) => Boolean(collapsedGroups.value[groupId]);
</script>

<template>
  <div class="min-h-screen bg-white">
    <PublicHeader />
    <main class="bg-gradient-to-b from-emerald-50/70 via-white to-white">
      <section class="mx-auto max-w-6xl px-6 py-12 lg:py-16">
        <div class="space-y-8">
          <div class="grid gap-4 md:grid-cols-2">
            <div class="rounded-2xl border border-emerald-100 bg-emerald-50/70 p-4 shadow-sm">
              <p class="text-xs font-semibold uppercase tracking-[0.25em] text-emerald-600">Bước 1</p>
              <h2 class="mt-1 text-lg font-semibold text-slate-900">Chọn triệu chứng</h2>
              <p class="text-sm text-slate-600">
                Đánh dấu các triệu chứng bạn đang gặp. Bạn đã chọn {{ selectedSymptomValues.length }} mục.
              </p>
            </div>
            <div class="rounded-2xl border border-slate-200 bg-white/80 p-4 shadow-sm">
              <p class="text-xs font-semibold uppercase tracking-[0.25em] text-slate-600">Bước 2</p>
              <h2 class="mt-1 text-lg font-semibold text-slate-900">Xem gợi ý bệnh</h2>
              <p class="text-sm text-slate-600">
                Hệ thống hiển thị bảng kết quả và nút đặt lịch khám để bạn trao đổi trực tiếp với bác sĩ.
              </p>
            </div>
          </div>

          <div class="rounded-3xl border border-emerald-100 bg-white/95 p-8 shadow-xl">
            <div class="flex flex-wrap items-center justify-between gap-3">
              <div>
                <p class="text-sm font-semibold uppercase tracking-[0.25em] text-emerald-600">Tư vấn triệu chứng</p>
                <h1 class="mt-2 text-3xl font-bold text-slate-900">Chuẩn đoán sơ bộ bằng AI</h1>
                <p class="mt-3 text-base text-slate-600">
                  Chọn những triệu chứng nổi bật mà bạn gặp phải, hệ thống sẽ đề xuất các bệnh lý có xác suất cao kèm
                  mức độ nghiêm trọng và gợi ý đặt lịch khám.
                </p>
              </div>
              <div class="rounded-2xl bg-emerald-100/80 px-4 py-3 text-sm font-semibold text-emerald-700">
                {{ selectedSymptomValues.length }} triệu chứng
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
              <div class="space-y-6">
                <div
                  v-for="group in symptomGroups"
                  :key="group.id"
                  class="rounded-2xl border border-slate-200 bg-white/80 p-4 shadow-sm"
                >
                  <div class="mb-3 flex items-start justify-between gap-3">
                    <div>
                      <h3 class="text-sm font-semibold text-slate-800">{{ group.labelVi }}</h3>
                      <p v-if="group.descriptionVi" class="text-xs text-slate-500">{{ group.descriptionVi }}</p>
                    </div>
                    <div class="flex items-center gap-2">
                      <span class="text-xs font-semibold text-emerald-600">{{ group.symptoms.length }} mục</span>
                      <button
                        type="button"
                        class="flex h-8 w-8 items-center justify-center rounded-full border border-slate-200 text-slate-600 hover:bg-slate-100"
                        @click="toggleGroup(group.id)"
                        :aria-expanded="!isGroupCollapsed(group.id)"
                        :aria-controls="`symptom-group-${group.id}`"
                      >
                        <svg
                          xmlns="http://www.w3.org/2000/svg"
                          class="h-4 w-4 transition-transform"
                          :class="isGroupCollapsed(group.id) ? '' : 'rotate-180'"
                          fill="none"
                          viewBox="0 0 24 24"
                          stroke="currentColor"
                        >
                          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5" d="M19 9l-7 7-7-7" />
                        </svg>
                      </button>
                    </div>
                  </div>
                  <div
                    class="grid gap-2 md:grid-cols-3"
                    :id="`symptom-group-${group.id}`"
                    v-show="!isGroupCollapsed(group.id)"
                  >
                    <label
                      v-for="symptom in group.symptoms"
                      :key="symptom.value"
                      class="flex cursor-pointer items-center gap-2 rounded-xl border border-slate-200 bg-white px-3 py-2 text-sm hover:border-emerald-400"
                    >
                      <input
                        v-model="selectedSymptomValues"
                        type="checkbox"
                        :value="symptom.value"
                        class="h-4 w-4 rounded border-slate-300 text-emerald-600 focus:ring-emerald-500"
                      />
                      <span>{{ symptom.labelVi }}</span>
                    </label>
                  </div>
                </div>
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
                  v-if="diagnosisResult?.predictions?.length"
                  class="inline-flex items-center justify-center rounded-full border border-slate-200 bg-white px-4 py-3 text-sm font-semibold text-slate-700 shadow-sm transition hover:border-emerald-400 hover:text-emerald-700"
                  type="button"
                  @click="showResultsModal = true"
                >
                  Xem lại kết quả
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
      </section>
    </main>
    <PublicFooter />
  </div>

  <transition name="fade">
    <div
      v-if="showResultsModal"
      class="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/50 px-4 py-6 backdrop-blur"
      role="dialog"
      aria-modal="true"
    >
      <div class="relative h-[85vh] w-full max-w-5xl overflow-hidden rounded-3xl bg-white shadow-2xl">
        <div class="flex items-center justify-between border-b border-slate-200 px-6 py-4">
          <div>
            <p class="text-xs font-semibold uppercase tracking-[0.25em] text-emerald-600">Bảng kết quả</p>
            <h3 class="text-lg font-semibold text-slate-900">Dự đoán từ AI (Bước 2/2)</h3>
            <p class="text-xs text-slate-500">
              Đây chỉ là dự đoán từ AI. Để chắc chắn, hãy đặt lịch khám với phòng khám chúng tôi.
            </p>
          </div>
          <button
            class="flex h-9 w-9 items-center justify-center rounded-full border border-slate-200 text-slate-500 hover:bg-slate-100"
            type="button"
            @click="closeResultsModal"
            aria-label="Đóng"
          >
            <svg xmlns="http://www.w3.org/2000/svg" class="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5" d="M6 18L18 6M6 6l12 12" />
            </svg>
          </button>
        </div>

        <div class="h-[calc(85vh-64px)] overflow-auto px-6 py-4">
          <div v-if="decoratedPredictions.length" class="overflow-hidden rounded-2xl border border-slate-100 bg-slate-50/70 shadow-sm">
            <table class="min-w-full divide-y divide-slate-200 text-sm">
              <thead class="bg-slate-100/70 text-left text-xs font-semibold uppercase tracking-wide text-slate-600">
                <tr>
                  <th class="px-4 py-3">#</th>
                  <th class="px-4 py-3">Bệnh (vi / en)</th>
                  <th class="px-4 py-3">Xác suất</th>
                  <th class="px-4 py-3">Mức độ</th>
                  <th class="px-4 py-3">Ghi chú</th>
                  <th class="px-4 py-3 text-right">Hành động</th>
                </tr>
              </thead>
              <tbody class="divide-y divide-slate-200 bg-white">
                <tr v-for="(prediction, idx) in decoratedPredictions" :key="prediction.disease" class="align-top">
                  <td class="px-4 py-3 text-slate-600">{{ idx + 1 }}</td>
                  <td class="px-4 py-3">
                    <p class="font-semibold text-slate-900">{{ prediction.nameVi }}</p>
                    <p class="text-xs text-slate-500">{{ prediction.nameEn }}</p>
                  </td>
                  <td class="px-4 py-3 text-slate-800">{{ formatProbability(prediction.probability) }}</td>
                  <td class="px-4 py-3">
                    <span
                      class="rounded-full px-3 py-1 text-xs font-semibold uppercase tracking-wide"
                      :class="severityBadgeClasses(prediction.severity)"
                    >
                      {{ severityLabelVi(prediction.severity) }}
                    </span>
                  </td>
                  <td class="px-4 py-3 text-slate-700">
                    <span v-if="prediction.noteVi">{{ prediction.noteVi }}</span>
                    <span v-else class="text-slate-400">—</span>
                  </td>
                  <td class="px-4 py-3 text-right">
                    <button
                      class="inline-flex items-center rounded-full bg-emerald-600 px-3 py-2 text-xs font-semibold text-white shadow hover:bg-emerald-700"
                      type="button"
                      @click="goToBooking(prediction)"
                    >
                      Đặt lịch
                    </button>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
          <div
            v-else
            class="rounded-2xl border border-dashed border-slate-200 bg-white p-6 text-center text-sm text-slate-500"
          >
            Hãy chọn triệu chứng và bấm "Phân tích triệu chứng" để xem gợi ý bệnh cùng mức độ nghiêm trọng.
          </div>
        </div>

        <div class="flex items-center justify-between border-t border-slate-200 bg-white px-6 py-4">
          <p class="text-xs font-semibold uppercase tracking-wide text-amber-700">
            Đây chỉ là dự đoán từ AI. Nếu triệu chứng nặng hoặc kéo dài, hãy đặt lịch khám để được bác sĩ đánh giá trực tiếp.
          </p>
          <button
            class="inline-flex items-center gap-2 rounded-full bg-rose-600 px-4 py-2 text-xs font-semibold uppercase tracking-wide text-white shadow hover:bg-rose-700 disabled:opacity-70"
            type="button"
            :disabled="!decoratedPredictions.length"
            @click="decoratedPredictions.length && goToBooking(decoratedPredictions[0])"
          >
            Đặt lịch khám ngay
          </button>
        </div>
      </div>
    </div>
  </transition>
</template>
