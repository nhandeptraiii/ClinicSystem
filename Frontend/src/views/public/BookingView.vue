<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useRoute } from 'vue-router';
import PublicHeader from '@/components/PublicHeader.vue';
import AppointmentRequestForm from '@/components/AppointmentRequestForm.vue';
import PublicFooter from '@/components/PublicFooter.vue';
import bookingHeroImage from '@/assets/DatLich/DatLich.png';
import { useToast, type ToastType } from '@/composables/useToast';

type ToastVisual = {
  title: string;
  container: string;
  icon: string;
  iconType: 'success' | 'error' | 'warning' | 'info';
};

type ToastPayload = {
  type: ToastType;
  message: string;
};

const { toast, show: showToast, hide: hideToast } = useToast();

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

const handleFormToast = ({ type, message }: ToastPayload) => {
  showToast(type, message);
};

const route = useRoute();
const symptomNote = ref('');

onMounted(() => {
  const noteFromDiagnosis = route.query.note;
  if (typeof noteFromDiagnosis === 'string') {
    symptomNote.value = noteFromDiagnosis;
  }
});
</script>

<template>
  <div class="min-h-screen bg-white">
    <PublicHeader />
    <main class="bg-white">
      <section class="relative overflow-hidden bg-gradient-to-b from-emerald-50 via-white to-white">
        <div class="absolute inset-y-0 -right-40 hidden w-80 rounded-full bg-emerald-200/30 blur-3xl lg:block"></div>
        <div class="absolute inset-y-0 -left-40 hidden w-80 rounded-full bg-emerald-100/40 blur-3xl lg:block"></div>

        <div class="relative mx-auto max-w-6xl px-6 py-16 lg:py-20">
          <div class="grid gap-12 lg:grid-cols-[1.05fr_1fr]">
            <div class="flex flex-col gap-10">
              <div class="max-w-xl">
                <span class="inline-flex items-center gap-2 rounded-full bg-white px-4 py-2 text-xs font-semibold uppercase tracking-[0.35em] text-emerald-600 shadow-sm">
                  Đặt lịch trực tuyến
                </span>
                <figure class="mt-6 overflow-hidden rounded-[32px] border border-emerald-100 bg-white shadow-[0_24px_55px_-35px_rgba(13,148,136,0.55)]">
                  <img
                    :src="bookingHeroImage"
                    alt="Khách hàng đặt lịch khám tại phòng khám Duyên Hạnh"
                    class="block w-full object-cover sm:max-h-[360px]"
                  />
                </figure>
              </div>

              <div class="grid gap-6 md:grid-cols-2">
                <div class="rounded-3xl border border-emerald-100 bg-white/90 p-6 shadow-sm">
                  <div class="flex items-center gap-3">
                    <span class="flex h-11 w-11 items-center justify-center rounded-full bg-emerald-50 text-emerald-600">
                      <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" class="h-5 w-5" fill="none" stroke="currentColor" stroke-width="1.8">
                        <path stroke-linecap="round" stroke-linejoin="round" d="M8 3v2m8-2v2M5 7h14a1 1 0 0 1 1 1v12a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1V8a1 1 0 0 1 1-1Zm0 4h14m-9 4h4" />
                      </svg>
                    </span>
                    <div>
                      <p class="text-sm font-semibold uppercase tracking-wide text-emerald-600">Giờ làm việc</p>
                      <h3 class="text-lg font-semibold text-slate-900">Thứ 2 - Thứ 7</h3>
                    </div>
                  </div>
                  <ul class="mt-4 space-y-2 text-sm text-slate-600">
                    <li>• Sáng: 07:30 – 11:30</li>
                    <li>• Chiều: 13:00 – 20:00</li>
                    <li>• Số 36, đường Cách mạng tháng 8, phường Cái Khế, thành phố Cần Thơ</li>
                  </ul>
                </div>

                <div class="rounded-3xl border border-emerald-100 bg-white/90 p-6 shadow-sm">
                  <div class="flex items-center gap-3">
                    <span class="flex h-11 w-11 items-center justify-center rounded-full bg-emerald-50 text-emerald-600">
                      <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" class="h-5 w-5" fill="none" stroke="currentColor" stroke-width="1.8">
                        <path stroke-linecap="round" stroke-linejoin="round" d="M3 8.5 12 4l9 4.5-9 4.5-9-4.5Zm0 5L12 18l9-4.5M12 13.5V18" />
                      </svg>
                    </span>
                    <div>
                      <p class="text-sm font-semibold uppercase tracking-wide text-emerald-600">Dịch vụ nổi bật</p>
                      <h3 class="text-lg font-semibold text-slate-900">Khám chuyên sâu &amp; cận lâm sàng</h3>
                    </div>
                  </div>
                  <ul class="mt-4 space-y-2 text-sm text-slate-600">
                    <li>• Khám nội tổng quát, nhi, tai mũi họng...</li>
                    <li>• Xét nghiệm, chẩn đoán hình ảnh và phẫu thuật ngày</li>
                    <li>• Chăm sóc sau khám, theo dõi điều trị dài hạn</li>
                  </ul>
                  <a href="/specialties" class="mt-4 inline-flex items-center gap-2 text-sm font-semibold text-emerald-600 hover:text-emerald-700">
                    Xem chuyên khoa
                    <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" class="h-4 w-4" fill="none" stroke="currentColor" stroke-width="1.8">
                      <path stroke-linecap="round" stroke-linejoin="round" d="M9 5l7 7-7 7" />
                    </svg>
                  </a>
                </div>
              </div>

              <div class="flex flex-wrap gap-4 text-sm text-slate-600">
                <div class="flex items-center gap-2">
                  <span class="flex h-6 w-6 items-center justify-center rounded-full bg-emerald-100 text-emerald-600">
                    <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" class="h-3.5 w-3.5" fill="none" stroke="currentColor" stroke-width="2">
                      <path stroke-linecap="round" stroke-linejoin="round" d="m5 13 4 4L19 7" />
                    </svg>
                  </span>
                  Tư vấn cá nhân hóa từng trường hợp
                </div>
                <div class="flex items-center gap-2">
                  <span class="flex h-6 w-6 items-center justify-center rounded-full bg-emerald-100 text-emerald-600">
                    <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" class="h-3.5 w-3.5" fill="none" stroke="currentColor" stroke-width="2">
                      <path stroke-linecap="round" stroke-linejoin="round" d="M12 6v12m6-6H6" />
                    </svg>
                  </span>
                  Chủ động chọn khung giờ phù hợp
                </div>
              </div>
            </div>

            <AppointmentRequestForm :initial-symptom-description="symptomNote" @notify="handleFormToast" />
          </div>
        </div>
      </section>

      <section class="px-6 pb-20">
        <div class="mx-auto flex max-w-6xl flex-col gap-8 rounded-[32px] border border-emerald-100 bg-white/90 p-10 shadow-[0_24px_60px_-35px_rgba(13,148,136,0.45)] lg:flex-row lg:items-center lg:justify-between">
          <div class="max-w-xl">
            <h2 class="text-2xl font-semibold text-slate-900 md:text-3xl">Chuẩn bị tốt hơn cho buổi khám</h2>
            <p class="mt-3 text-base text-slate-600">
              Mang theo sổ khám bệnh, danh sách thuốc đang sử dụng và giấy tờ bảo hiểm (nếu có). Đội ngũ lễ tân sẽ hỗ trợ bạn từ khâu tiếp nhận đến khi ra về.
            </p>
          </div>
          <div class="flex flex-col gap-4 text-sm text-slate-700">
            <div class="flex items-center gap-3 rounded-2xl border border-emerald-100 bg-emerald-50/80 px-5 py-3">
              <span class="flex h-10 w-10 items-center justify-center rounded-full bg-white text-emerald-600">
                <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" class="h-5 w-5" fill="none" stroke="currentColor" stroke-width="1.8">
                  <path stroke-linecap="round" stroke-linejoin="round" d="M12 8v4l3 3M21 12a9 9 0 1 1-18 0 9 9 0 0 1 18 0Z" />
                </svg>
              </span>
              <div>
                <p class="font-semibold text-slate-900">Hotline hỗ trợ</p>
                <a href="tel:1900636373" class="text-emerald-600 hover:text-emerald-700">0332406049</a>
              </div>
            </div>
            <div class="flex items-center gap-3 rounded-2xl border border-emerald-100 bg-emerald-50/80 px-5 py-3">
              <span class="flex h-10 w-10 items-center justify-center rounded-full bg-white text-emerald-600">
                <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" class="h-5 w-5" fill="none" stroke="currentColor" stroke-width="1.8">
                  <path stroke-linecap="round" stroke-linejoin="round" d="M5 21V5a2 2 0 0 1 2-2h10a2 2 0 0 1 2 2v16l-7-3-7 3Z" />
                </svg>
              </span>
              <div>
                <p class="font-semibold text-slate-900">Tài liệu cần mang theo</p>
                <p>Sổ khám, danh sách thuốc, giấy tờ cá nhân.</p>
              </div>
            </div>
          </div>
        </div>
      </section>
    </main>
    <PublicFooter />
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
