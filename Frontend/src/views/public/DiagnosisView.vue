<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import PublicHeader from '@/components/PublicHeader.vue';
import PublicFooter from '@/components/PublicFooter.vue';
import { analyzeSymptoms, fetchModelSymptoms, type DiagnosisResponse, type DiseasePrediction } from '@/services/diagnosis.service';
import { symptomGroups } from '@/config/diagnosisSymptoms';
import { diseaseDictionary } from '@/config/diseaseTranslations';
import { useToast, type ToastType } from '@/composables/useToast';

type DecoratedPrediction = DiseasePrediction & {
  nameVi: string;
  nameEn: string;
  severity: 'low' | 'medium' | 'high';
  noteVi?: string;
};

const router = useRouter();
const selectedSymptomValues = ref<string[]>([]);
const symptomSearch = ref('');
const isLoading = ref(false);
const diagnosisResult = ref<DiagnosisResponse | null>(null);
const collapsedGroups = ref<Record<string, boolean>>(Object.fromEntries(symptomGroups.map((g) => [g.id, true])));
const showResultsModal = ref(false);
const { toast, show: showToast, hide: hideToast } = useToast({ autoCloseMs: 5000 });

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

const allSymptoms = computed(() => symptomGroups.flatMap((group) => group.symptoms));

const filteredSymptomGroups = computed(() => {
  const keyword = symptomSearch.value.trim().toLowerCase();
  if (!keyword) return symptomGroups;
  return symptomGroups
    .map((group) => ({
      ...group,
      symptoms: group.symptoms.filter(
        (symptom) =>
          symptom.labelVi.toLowerCase().includes(keyword) ||
          symptom.value.toLowerCase().includes(keyword) ||
          (symptom.labelEn ?? '').toLowerCase().includes(keyword)
      ),
    }))
    .filter((group) => group.symptoms.length > 0);
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

const formatPercent = (value: number) => `${Math.round(Math.min(Math.max(value ?? 0, 0), 1) * 100)}%`;

const handleAnalyze = async () => {
  if (selectedSymptomValues.value.length < 3) {
    diagnosisResult.value = null;
    showResultsModal.value = false;
    showToast('error', 'Vui lòng chọn ít nhất 3 triệu chứng để hệ thống phân tích chính xác hơn.');
    return;
  }
  isLoading.value = true;
  diagnosisResult.value = null;
  try {
    const payload = {
      symptoms: [...selectedSymptomValues.value],
      topK: 10,
    };
    const response = await analyzeSymptoms(payload);
    diagnosisResult.value = response;
    showResultsModal.value = Boolean(response?.predictions?.length);
  } catch (err: unknown) {
    const maybeError = err as { response?: { data?: { detail?: string; message?: string } } };
    const message =
      maybeError.response?.data?.detail ??
      (typeof maybeError.response?.data?.message === 'string' ? maybeError.response?.data?.message : null) ??
      'Không thể phân tích triệu chứng vào lúc này. Vui lòng thử lại sau.';
    showToast('error', message);
    showResultsModal.value = false;
  } finally {
    isLoading.value = false;
  }
};

const resetSelections = () => {
  selectedSymptomValues.value = [];
  diagnosisResult.value = null;
  showResultsModal.value = false;
};

const basePredictions = computed(() => diagnosisResult.value?.predictions ?? []);

const filteredPredictions = computed<DiseasePrediction[]>(() => {
  return basePredictions.value.filter((p) => p.probability >= 0.1);
});

const decoratedPredictions = computed<DecoratedPrediction[]>(() => {
  const makeTitle = (text: string) => text.replace(/\w\S*/g, (word) => word.charAt(0).toUpperCase() + word.slice(1));
  return filteredPredictions.value.map((p) => {
    const info = diseaseDictionary[p.disease] ?? diseaseDictionary[p.disease.toLowerCase()];
    const severity = normalizeSeverity(info?.severity ?? p.severity);
    return {
      ...p,
      severity,
      nameVi: info?.nameVi ?? makeTitle(p.disease),
      nameEn: info?.nameEn ?? makeTitle(p.disease),
      noteVi: info?.noteVi,
    };
  });
});

const bookingNote = computed(() => {
  const lines: string[] = [];
  const selectedLabels = allSymptoms.value
    .filter((s) => selectedSymptomValues.value.includes(s.value))
    .map((s) => s.labelVi || s.value);

  if (selectedLabels.length) {
    lines.push('Triệu chứng bệnh nhân khai báo:');
    lines.push(`- ${selectedLabels.join(', ')}`);
    lines.push('');
  }

  if (decoratedPredictions.value.length) {
    lines.push('Gợi ý từ hệ thống (không thay thế chẩn đoán của bác sĩ):');
    decoratedPredictions.value.forEach((p) => {
      lines.push(`- ${p.nameVi} (~${formatPercent(p.probability)})`);
    });
  }

  return lines.join('\n');
});

const goToBooking = () => {
  if (!selectedSymptomValues.value.length && !decoratedPredictions.value.length) {
    showToast('error', 'Vui lòng chọn ít nhất một triệu chứng để tư vấn và đặt lịch.');
    return;
  }
  router.push({
    name: 'booking',
    query: {
      fromDiagnosis: 'true',
      note: bookingNote.value,
    },
  });
};

const closeResultsModal = () => {
  showResultsModal.value = false;
};

const toggleGroup = (groupId: string) => {
  collapsedGroups.value = { ...collapsedGroups.value, [groupId]: !collapsedGroups.value[groupId] };
};

const isGroupCollapsed = (groupId: string) => {
  if (symptomSearch.value.trim()) return false;
  return Boolean(collapsedGroups.value[groupId]);
};

onMounted(async () => {
  try {
    const modelSymptoms = await fetchModelSymptoms();
    const localValues = allSymptoms.value.map((s) => s.value.toLowerCase());
    const localSet = new Set(localValues);
    const modelSet = new Set((modelSymptoms ?? []).map((s) => s.toLowerCase()));

    const extraOnFrontend = localValues.filter((v) => !modelSet.has(v));
    const missingOnFrontend = Array.from(modelSet).filter((v) => !localSet.has(v));

    if (extraOnFrontend.length === 0 && missingOnFrontend.length === 0) {
      console.info('[Diagnosis] Frontend symptoms are in sync with model.');
    } else {
      if (extraOnFrontend.length) {
        console.error(
          '[Diagnosis] Frontend has symptoms not supported by model (will be ignored by model):',
          extraOnFrontend
        );
      }
      if (missingOnFrontend.length) {
        console.warn(
          '[Diagnosis] Model supports symptoms missing on frontend (UI missing options):',
          missingOnFrontend
        );
      }
    }
  } catch (error) {
    console.error('[Diagnosis] Cannot validate symptom sync with AI model:', error);
  }
});
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
                <p class="text-sm font-semibold uppercase tracking-[0.25em] text-emerald-600">AI chuẩn đoán bệnh</p>
                <h1 class="mt-2 text-3xl font-bold text-slate-900">Chuẩn đoán sơ bộ bằng AI</h1>
                <p class="mt-3 text-base text-slate-600">
                  Chọn những triệu chứng nổi bật mà bạn gặp phải, hệ thống sẽ đề xuất các bệnh lý có xác suất cao kèm
                  mức độ nghiêm trọng và gợi ý đặt lịch khám.
                </p>
              </div>
              <div class="grid w-full gap-3 sm:grid-cols-[auto,1fr] sm:items-center">
                 <div class="rounded-2xl bg-emerald-100/80 px-4 py-3 text-sm font-semibold text-emerald-700 text-center sm:text-left flex h-full min-h-[88px] flex-col items-center justify-center">
                  <span class="text-lg font-bold">{{ selectedSymptomValues.length }}</span>
                  <span class="text-sm font-semibold">triệu chứng</span>
                </div>
                <div class="rounded-2xl border border-slate-200 bg-slate-50/60 p-4">
                  <label class="mb-2 block text-sm font-semibold text-slate-700" for="symptom-search">
                    Tìm triệu chứng
                  </label>
                  <input
                    id="symptom-search"
                    v-model="symptomSearch"
                    type="text"
                    placeholder="Tìm triệu chứng (vd: sốt, ho, đau bụng...)"
                    class="w-full rounded-xl border border-slate-200 px-3 py-2 text-sm focus:border-emerald-500 focus:ring-emerald-500"
                  />
                </div>
              </div>
            </div>
            <div class="mt-6 space-y-4">
              <p class="flex items-start gap-2 rounded-2xl border border-amber-100 bg-amber-50/70 p-4 text-base font-medium text-amber-800">
                <svg xmlns="http://www.w3.org/2000/svg" class="mt-0.5 h-5 w-5" viewBox="0 0 20 20" fill="currentColor">
                  <path
                    fill-rule="evenodd"
                    d="M10 18a8 8 0 100-16 8 8 0 000 16zm-.75-5.25a.75.75 0 011.5 0v1.5a.75.75 0 01-1.5 0v-1.5zm0-6.5a.75.75 0 011.5 0v4a.75.75 0 01-1.5 0v-4z"
                    clip-rule="evenodd"
                  />
                </svg>
                <span>{{ disclaimerText }}</span>
              </p>
              
              <div class="space-y-6">
                <div
                  v-for="group in filteredSymptomGroups"
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
            </div>
          </div>

          <div class="rounded-3xl border border-slate-200 bg-white/95 p-6 shadow">
            <h3 class="text-lg font-semibold text-slate-900">Gợi ý chăm sóc</h3>
            <ul class="mt-3 space-y-2 text-sm text-slate-600">
              <li>• Uống đủ nước, nghỉ ngơi hợp lý và theo dõi sát thay đổi cơ thể.</li>
              <li>• Nếu đã có bệnh nền, hãy báo cho bác sĩ để được hướng dẫn riêng.</li>
              <li>• Liên hệ hotline phòng khám hoặc đặt lịch trực tuyến ngay khi có dấu hiệu trở nặng.</li>
            </ul>
            <p class="mt-4 rounded-2xl border border-emerald-100 bg-emerald-50/80 p-4 text-sm font-semibold uppercase tracking-wide text-emerald-700">
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
        <div class="flex items-center justify-between border-b border-slate-200 px-6 py-5">
          <div>
            <p class="text-sm font-semibold uppercase tracking-[0.25em] text-emerald-600">Bảng kết quả</p>
            <h3 class="text-2xl font-bold text-slate-900">Dự đoán từ AI (Bước 2/2)</h3>
            <p class="text-sm font-semibold text-slate-600">
              Đây chỉ là dự đoán từ AI. Để chắc chắn, hãy đặt lịch khám với phòng khám chúng tôi ở nút đặt lịch khám ngay bên phải.
            </p>
          </div>
          <div class="flex items-center gap-3">
            <button
              class="inline-flex items-center gap-2 rounded-full bg-emerald-600 px-4 py-2 text-xs font-semibold uppercase tracking-wide text-white shadow hover:bg-emerald-700 disabled:opacity-70"
              type="button"
              :disabled="false"
              @click="goToBooking"
            >
              Đặt lịch khám
            </button>
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
        </div>

        <div class="h-[calc(85vh-64px)] overflow-auto px-6 py-4">
          <div v-if="decoratedPredictions.length" class="overflow-hidden rounded-2xl border border-slate-100 bg-slate-50/70 shadow-sm">
            <table class="min-w-full divide-y divide-slate-200 text-sm">
              <thead class="bg-slate-100/70 text-left text-xs font-semibold uppercase tracking-wide text-slate-600">
                <tr>
                  <th class="px-4 py-3">#</th>
                  <th class="px-4 py-3">Bệnh</th>
                  <th class="px-4 py-3">Xác suất</th>
                  <th class="px-4 py-3">Mức độ</th>
                  <th class="px-4 py-3">Ghi chú</th>
                </tr>
              </thead>
              <tbody class="divide-y divide-slate-200 bg-white">
                <tr v-for="(prediction, idx) in decoratedPredictions" :key="prediction.disease" class="align-top">
                  <td class="px-4 py-3 text-slate-600">{{ idx + 1 }}</td>
                  <td class="px-4 py-3">
                    <p class="font-semibold text-slate-900">{{ prediction.nameVi }}</p>
                    <p class="text-xs text-slate-500">{{ prediction.nameEn }}</p>
                  </td>
                  <td class="px-4 py-3 text-slate-800">{{ formatPercent(prediction.probability) }}</td>
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
                </tr>
              </tbody>
            </table>
          </div>
          <div
            v-else
            class="rounded-2xl border border-dashed border-amber-200 bg-amber-50/80 p-6 text-center text-sm font-semibold text-amber-800"
          >
            Mô hình AI chưa đủ dữ liệu tin cậy (tỉ lệ quá thấp) để gợi ý bệnh cụ thể. Vui lòng xem xét thêm triệu chứng hoặc đặt lịch khám để được tư vấn trực tiếp.
          </div>
        </div>

        <div class="flex items-center justify-between border-t border-slate-200 bg-white px-6 py-5">
          <p class="text-sm font-semibold uppercase tracking-wide text-amber-700">
            Đây chỉ là dự đoán từ AI. Nếu triệu chứng nặng hoặc kéo dài, hãy đặt lịch khám để được bác sĩ đánh giá trực tiếp.
          </p>
          <button
            class="inline-flex items-center gap-2 rounded-full bg-emerald-600 px-4 py-2 text-xs font-semibold uppercase tracking-wide text-white shadow hover:bg-emerald-700 disabled:opacity-70"
            type="button"
            :disabled="false"
            @click="goToBooking"
          >
            Đặt lịch khám ngay
          </button>
        </div>
      </div>
    </div>
  </transition>

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
              <path stroke-linecap="round" stroke-linejoin="round" d="M12 8v4m0 4h.01M21 12a9 9 0 1 1-18 0 9 9 0 0 1 18 0Z" />
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
              <path stroke-linecap="round" stroke-linejoin="round" d="M12 9v4m0 4h.01M10.29 3.86 1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0Z" />
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
              <path stroke-linecap="round" stroke-linejoin="round" d="M13 16h-1v-4h-1m1-4h.01M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18Z" />
            </svg>
          </span>
          <div class="flex-1">
            <p class="text-sm font-semibold">{{ toastVisuals.title }}</p>
            <p class="mt-1 text-sm leading-relaxed">{{ toast?.message }}</p>
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
