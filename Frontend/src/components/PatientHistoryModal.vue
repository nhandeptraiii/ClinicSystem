<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import type { Patient } from '@/services/patient.service';
import {
  fetchVisitPage,
  fetchServiceOrders,
  type PatientVisit,
  type ServiceOrder,
} from '@/services/visit.service';
import { fetchPrescriptions } from '@/services/prescription.service';

const props = defineProps<{
  open: boolean;
  patient: Patient | null;
}>();

const emit = defineEmits<{
  (e: 'update:open', value: boolean): void;
  (e: 'closed'): void;
}>();

const historyLoading = ref(false);
const historyError = ref<string | null>(null);
const visitHistory = ref<PatientVisit[]>([]);
const expandedHistoryVisitId = ref<number | null>(null);
const expandedServiceResultIds = ref<Set<number>>(new Set());

const displayValue = (value?: string | null) => {
  if (value === undefined || value === null) return 'Không có';
  if (typeof value === 'string' && value.trim() === '') return 'Không có';
  return value;
};

const formatDateTime = (input?: string | null) => {
  if (!input) return '—';
  try {
    return new Intl.DateTimeFormat('vi-VN', {
      dateStyle: 'short',
      timeStyle: 'short',
      hour12: false,
    }).format(new Date(input));
  } catch (_error) {
    return input;
  }
};

const visitStatusLabel = (status?: string | null) => {
  const map: Record<string, string> = {
    OPEN: 'Đang khám',
    IN_PROGRESS: 'Đang xử lý',
    COMPLETED: 'Đã hoàn tất',
    CANCELLED: 'Đã hủy',
  };
  return status ? map[status] ?? 'Không rõ' : 'Không rõ';
};

const serviceOrderStatusLabel = (status?: string | null) => {
  const map: Record<string, string> = {
    PENDING: 'Chờ xử lý',
    SCHEDULED: 'Đã lên lịch',
    IN_PROGRESS: 'Đang thực hiện',
    COMPLETED_WITH_RESULT: 'Hoàn tất (có kết quả)',
    COMPLETED: 'Hoàn tất',
    CANCELLED: 'Đã hủy',
  };
  return status ? map[status] ?? 'Không rõ' : 'Không rõ';
};

const prescriptionStatusLabel = (status?: string | null) => {
  const map: Record<string, string> = {
    WAITING: 'Chờ cấp phát',
    DISPENSED: 'Đã cấp phát',
    ON_HOLD: 'Tạm dừng',
  };
  return status ? map[status] ?? 'Không rõ' : 'Không rõ';
};

const indicatorBadgeClass = (evaluation?: string | null) => {
  const level = evaluation ? evaluation.toUpperCase() : '';
  const isWarning = level === 'LOW' || level === 'HIGH';
  return [
    'rounded-full border px-2.5 py-1 text-[11px] font-semibold uppercase tracking-wide',
    isWarning
      ? 'border-amber-300 bg-amber-50 text-amber-700'
      : 'border-emerald-200 bg-white text-emerald-700',
  ];
};

const evaluationTextClass = (evaluation?: string | null) => {
  const level = evaluation ? evaluation.toUpperCase() : '';
  return ['ml-1', level === 'LOW' || level === 'HIGH' ? 'text-amber-700' : 'text-emerald-700'];
};

const indicatorEvaluationLabel = (evaluation?: string | null) => {
  if (!evaluation) return '';
  const level = evaluation.toUpperCase();
  const map: Record<string, string> = {
    NORMAL: 'Bình thường',
    HIGH: 'Cao',
    LOW: 'Thấp',
  };
  return map[level] ?? evaluation;
};

const serviceOrderResultSummary = (svc: ServiceOrder) => {
  const first = svc?.indicatorResults && svc.indicatorResults.length > 0 ? svc.indicatorResults[0] : null;
  if (!first) {
    return serviceOrderStatusLabel(svc.status);
  }
  const unit = displayValue(first.unitSnapshot || first.indicatorTemplate?.unit);
  const evalLabel = indicatorEvaluationLabel(first.evaluation);
  const evalText = evalLabel ? ` (${evalLabel})` : '';
  return `Giá trị: ${first.measuredValue ?? 'Không có'} ${unit}${evalText}`;
};

const loadVisitHistory = async (patientId: number) => {
  historyLoading.value = true;
  historyError.value = null;
  try {
    const response = await fetchVisitPage({ patientId, page: 0, size: 50 });
    const items = response.items ?? [];
    const enriched = await Promise.all(
      items.map(async (visit) => {
        if (!visit.id) return visit;
        try {
          const [serviceOrders, prescriptions] = await Promise.all([
            fetchServiceOrders(visit.id).catch(() => []),
            fetchPrescriptions(visit.id).catch(() => []),
          ]);
          return { ...visit, serviceOrders, prescriptions };
        } catch (_err) {
          return { ...visit, serviceOrders: visit.serviceOrders ?? [], prescriptions: visit.prescriptions ?? [] };
        }
      }),
    );
    visitHistory.value = enriched
      .slice()
      .sort((a, b) => {
        const aDate = a.primaryAppointment?.scheduledAt || a.createdAt || '';
        const bDate = b.primaryAppointment?.scheduledAt || b.createdAt || '';
        return new Date(aDate).getTime() - new Date(bDate).getTime();
      });
  } catch (err: any) {
    historyError.value = err?.message ?? 'Không thể tải lịch sử khám.';
    visitHistory.value = [];
  } finally {
    historyLoading.value = false;
  }
};

const resetState = () => {
  historyError.value = null;
  visitHistory.value = [];
  expandedHistoryVisitId.value = null;
  expandedServiceResultIds.value = new Set();
};

watch(
  () => props.open,
  (val) => {
    if (val && props.patient?.id) {
      void loadVisitHistory(props.patient.id);
    }
    if (!val) {
      resetState();
    }
  },
  { immediate: true },
);

const toggleServiceResult = (orderId?: number) => {
  if (!orderId) return;
  const next = new Set(expandedServiceResultIds.value);
  if (next.has(orderId)) {
    next.delete(orderId);
  } else {
    next.add(orderId);
  }
  expandedServiceResultIds.value = next;
};

const closeModal = () => {
  emit('update:open', false);
  emit('closed');
  resetState();
};

const hasData = computed(() => visitHistory.value.length > 0);
</script>

<template>
  <Transition name="fade">
    <div
      v-if="open && patient"
      class="fixed inset-0 z-[92] flex items-center justify-center bg-slate-900/50 backdrop-blur-sm px-4"
    >
      <div class="w-full max-w-3xl max-h-[90vh] overflow-y-auto rounded-3xl border border-emerald-100 bg-white p-6 shadow-xl">
        <div class="flex items-start justify-between">
          <div>
            <p class="text-xs font-semibold uppercase tracking-[0.35em] text-emerald-500">Lịch sử khám</p>
            <h2 class="mt-2 text-xl font-semibold text-slate-900">{{ patient.fullName }}</h2>
            <p class="text-sm text-slate-500">Mã: {{ patient.code }} | ID: {{ patient.id }}</p>
          </div>
          <button
            type="button"
            class="flex h-9 w-9 items-center justify-center rounded-full border border-slate-200 text-slate-500 transition hover:border-slate-300 hover:text-slate-700"
            @click="closeModal"
          >
            <svg xmlns="http://www.w3.org/2000/svg" class="h-4.5 w-4.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.8">
              <path stroke-linecap="round" stroke-linejoin="round" d="m16 8-8 8m0-8 8 8" />
            </svg>
          </button>
        </div>

        <div v-if="historyLoading" class="mt-6 text-center py-8 text-sm text-slate-500">
          <svg class="mx-auto h-6 w-6 animate-spin text-emerald-500" viewBox="0 0 24 24" fill="none">
            <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
            <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 0 1 8-8v4l3.5-3.5L12 1v4a7 7 0 0 0-7 7H4z"></path>
          </svg>
          <p class="mt-2">Đang tải lịch sử khám...</p>
        </div>

        <p
          v-else-if="historyError"
          class="mt-6 rounded-2xl border border-rose-100 bg-rose-50/90 px-4 py-3 text-sm text-rose-600"
        >
          {{ historyError }}
        </p>

        <div v-else-if="!hasData" class="mt-8 rounded-3xl border border-dashed border-emerald-200 bg-emerald-50/40 p-10 text-center text-emerald-700">
          <svg xmlns="http://www.w3.org/2000/svg" class="mx-auto h-12 w-12 text-emerald-400" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.6">
            <path stroke-linecap="round" stroke-linejoin="round" d="M12 8v4m0 4h.01m0-14a10 10 0 1 0 0 20 10 10 0 0 0 0-20Z" />
          </svg>
          <h3 class="mt-4 text-lg font-semibold">Chưa có lịch sử khám</h3>
          <p class="mt-2 text-sm text-emerald-700/80">Bệnh nhân chưa có lần khám nào hoặc dữ liệu chưa được đồng bộ.</p>
        </div>

        <div v-else class="mt-6 space-y-3">
          <div
            v-for="(visit, idx) in visitHistory"
            :key="visit.id"
            class="rounded-2xl border border-emerald-100 bg-emerald-50/50 px-4 py-3 shadow-sm"
          >
            <div class="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
              <div class="space-y-1">
                <p class="text-xs font-semibold uppercase tracking-wide text-emerald-600">Lần khám {{ idx + 1 }}</p>
                <p class="text-sm font-semibold text-slate-900">
                  {{ formatDateTime(visit.primaryAppointment?.scheduledAt || visit.createdAt) }}
                </p>
                <p class="text-sm text-slate-600">
                  Bác sĩ:
                  <span class="font-semibold">
                    {{
                      visit.primaryAppointment?.doctor?.account?.fullName ||
                      visit.primaryAppointment?.doctor?.account?.username ||
                      'Không có'
                    }}
                  </span>
                </p>
              </div>
              <div class="flex flex-col items-start gap-1 text-xs sm:items-end">
                <span class="rounded-full border border-emerald-200 bg-white px-3 py-1 font-semibold uppercase tracking-wide text-emerald-700">
                  {{ visitStatusLabel(visit.status) }}
                </span>
                <span class="inline-flex items-center gap-1 rounded-full border border-emerald-200 bg-white px-3 py-1 text-[11px] font-semibold uppercase tracking-wide text-emerald-700">
                  <svg xmlns="http://www.w3.org/2000/svg" class="h-3.5 w-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.8">
                    <path stroke-linecap="round" stroke-linejoin="round" d="M12 6v12m6-6H6" />
                  </svg>
                  {{
                    visit.primaryAppointment?.clinicRoom?.name ||
                    visit.primaryAppointment?.clinicRoom?.code ||
                    'Không có'
                  }}
                </span>
              </div>
            </div>
            <div class="mt-2 grid gap-2 sm:grid-cols-2">
              <p class="text-sm text-slate-700">
                <span class="font-semibold text-slate-900">Chẩn đoán ban đầu:</span>
                <span class="ml-1 text-slate-700">{{ displayValue(visit.provisionalDiagnosis) }}</span>
              </p>
              <p class="text-sm text-slate-700">
                <span class="font-semibold text-slate-900">Bệnh xác định:</span>
                <span class="ml-1">
                  {{
                    visit.diseases && visit.diseases.length > 0
                      ? visit.diseases.map((d) => d.name).join(', ')
                      : 'Không có'
                  }}
                </span>
              </p>
            </div>
            <div class="mt-2 grid gap-2 sm:grid-cols-2">
              <p class="text-sm text-slate-700">
                <span class="font-semibold text-slate-900">Dịch vụ:</span>
                <span class="ml-1">
                  {{
                    visit.serviceOrders && visit.serviceOrders.length > 0
                      ? `${visit.serviceOrders.length} dịch vụ`
                      : 'Không có'
                  }}
                </span>
              </p>
              <p class="text-sm text-slate-700">
                <span class="font-semibold text-slate-900">Đơn thuốc:</span>
                <span class="ml-1">
                  {{
                    visit.prescriptions && visit.prescriptions.length > 0
                      ? `${visit.prescriptions.length} đơn`
                      : 'Không có'
                  }}
                </span>
              </p>
            </div>
            <div class="mt-3 flex items-center justify-end">
              <button
                type="button"
                class="inline-flex items-center gap-1 rounded-full border border-emerald-200 bg-white px-3 py-1 text-[11px] font-semibold uppercase tracking-wide text-emerald-700 shadow-sm transition hover:border-emerald-300 hover:bg-emerald-50"
                @click="expandedHistoryVisitId = expandedHistoryVisitId === visit.id ? null : visit.id"
              >
                <svg
                  xmlns="http://www.w3.org/2000/svg"
                  class="h-3.5 w-3.5"
                  fill="none"
                  viewBox="0 0 24 24"
                  stroke="currentColor"
                  stroke-width="1.8"
                >
                  <path stroke-linecap="round" stroke-linejoin="round" d="M12 6v12m6-6H6" />
                </svg>
                {{ expandedHistoryVisitId === visit.id ? 'Ẩn chi tiết' : 'Xem chi tiết' }}
              </button>
            </div>

            <div
              v-if="expandedHistoryVisitId === visit.id"
              class="mt-3 space-y-3 rounded-xl border border-emerald-100 bg-white/90 p-3 text-sm text-slate-700"
            >
              <div>
                <p class="text-xs font-semibold uppercase tracking-wide text-emerald-600">Dịch vụ đã chỉ định</p>
                <div v-if="visit.serviceOrders && visit.serviceOrders.length > 0" class="mt-2 space-y-2">
                  <div
                    v-for="svc in visit.serviceOrders"
                    :key="svc.id"
                    class="space-y-2 rounded-lg border border-emerald-100 bg-emerald-50/50 px-3 py-2"
                  >
                    <div class="flex items-center justify-between">
                      <div>
                        <p class="font-semibold text-slate-900">
                          {{ displayValue(svc.medicalService?.name) }}
                        </p>
                        <p class="text-xs text-slate-600">
                          Mã: {{ displayValue(svc.medicalService?.code) }}
                        </p>
                      </div>
                      <button
                        v-if="svc.indicatorResults && svc.indicatorResults.length > 0"
                        type="button"
                        class="inline-flex items-center gap-1 rounded-full border border-emerald-200 bg-white px-2 py-1 text-[11px] font-semibold uppercase tracking-wide text-emerald-700 transition hover:border-emerald-300 hover:bg-emerald-50"
                        @click="toggleServiceResult(svc.id)"
                      >
                        <svg xmlns="http://www.w3.org/2000/svg" class="h-3.5 w-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.8">
                          <path stroke-linecap="round" stroke-linejoin="round" d="M19 9l-7 7-7-7" />
                        </svg>
                        {{ expandedServiceResultIds.has(svc.id || -1) ? 'Ẩn kết quả' : 'Xem kết quả' }}
                      </button>
                    </div>
                    <div
                      v-if="svc.indicatorResults && svc.indicatorResults.length > 0 && expandedServiceResultIds.has(svc.id || -1)"
                      class="space-y-1 rounded-lg border border-emerald-100 bg-white/90 px-3 py-2"
                    >
                      <p class="text-xs font-semibold uppercase tracking-wide text-emerald-600">Kết quả chỉ số</p>
                      <div
                        v-for="res in svc.indicatorResults"
                        :key="res.id"
                        class="rounded-md border border-slate-100 bg-emerald-50/60 px-2 py-1.5 text-xs text-slate-700"
                      >
                        <div class="flex items-center justify-between gap-2">
                          <span class="font-semibold text-slate-900">
                            {{ displayValue(res.indicatorTemplate?.name || res.indicatorNameSnapshot) }}
                          </span>
                          <span :class="indicatorBadgeClass(res.evaluation)">
                            Giá trị: {{ res.measuredValue ?? 'Không có' }}
                            <span class="ml-1">{{ displayValue(res.unitSnapshot || res.indicatorTemplate?.unit) }}</span>
                            <span v-if="res.evaluation" :class="evaluationTextClass(res.evaluation)">({{ indicatorEvaluationLabel(res.evaluation) }})</span>
                          </span>
                        </div>
                        <div v-if="res.note" class="text-[11px] text-slate-600">Ghi chú: {{ res.note }}</div>
                      </div>
                    </div>
                  </div>
                </div>
                <p v-else class="mt-2 text-sm text-slate-600">Không có dịch vụ</p>
              </div>

              <div>
                <p class="text-xs font-semibold uppercase tracking-wide text-emerald-600">Đơn thuốc</p>
                <div v-if="visit.prescriptions && visit.prescriptions.length > 0" class="mt-2 space-y-1.5">
                  <div
                    v-for="pres in visit.prescriptions"
                    :key="pres.id"
                    class="space-y-2 rounded-lg border border-emerald-100 bg-emerald-50/50 px-3 py-2"
                  >
                    <div class="flex items-center justify-between gap-3">
                      <p class="font-semibold text-slate-900">Đơn #{{ pres.id }}</p>
                      <p class="text-xs text-slate-600">
                        Ngày kê: {{ formatDateTime(pres.issuedAt) }}
                      </p>
                      <span class="rounded-full border border-emerald-200 bg-white px-2.5 py-1 text-[11px] font-semibold uppercase tracking-wide text-emerald-700">
                        {{ prescriptionStatusLabel(pres.status) }}
                      </span>
                    </div>
                    <div v-if="pres.items && pres.items.length > 0" class="space-y-1 text-xs text-slate-700">
                      <div
                        v-for="(item, idx) in pres.items"
                        :key="item.id ?? idx"
                        class="rounded-lg border border-slate-100 bg-white px-2 py-1.5"
                      >
                        <p class="font-semibold text-slate-900">
                          {{ displayValue(item.medication?.name || item.medicationName) }}
                        </p>
                        <p class="text-[11px] text-slate-600">
                          SL: {{ item.quantity ?? 'Không có' }} |
                          Liều: {{ displayValue(item.dosage) }} |
                          Tần suất: {{ displayValue(item.frequency) }}
                        </p>
                      </div>
                    </div>
                    <p v-else class="text-xs text-slate-600">Không có chi tiết thuốc</p>
                  </div>
                </div>
                <p v-else class="mt-2 text-sm text-slate-600">Không có đơn thuốc</p>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </Transition>
</template>
