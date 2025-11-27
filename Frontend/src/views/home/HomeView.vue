<script setup lang="ts">
import { ref } from 'vue';
import PublicHeader from '@/components/PublicHeader.vue';
import PublicFooter from '@/components/PublicFooter.vue';

import bannerUrl from '@/assets/TrangChu/Banner1.png';
import facilityImageMain from '@/assets/TrangChu/CSVC_GPT.png';
import facilityImageSecondary from '@/assets/TrangChu/CSVC_GPT 1.png';

const hotline = '0332406049';
const facilityImages = [facilityImageMain, facilityImageSecondary];
const currentFacilityIndex = ref(0);

const specialtyFaq = [
  {
    question: 'Phòng khám nằm ở đâu và giờ làm việc thế nào?',
    answer:
      'Địa chỉ: 36 đường Cách Mạng Tháng 8, phường Cái Khế, quận Ninh Kiều, TP. Cần Thơ. Giờ làm việc: Thứ 2 – Thứ 7, sáng 07:30 – 11:30, chiều 13:00 – 20:00.',
  },
  {
    question: 'Phòng khám có những chuyên khoa và dịch vụ gì?',
    answer:
      'Hiện có 6 chuyên khoa: Cơ Xương Khớp, Nội Tổng Quát, Da Liễu, Nội Tiêu Hóa, Tai Mũi Họng, Tiết Niệu. Kèm các dịch vụ cận lâm sàng, nội soi, tư vấn dinh dưỡng và theo dõi sau khám.',
    action: { label: 'Xem danh sách chuyên khoa', to: '/specialties' },
  },
  {
    question: 'Tôi đặt lịch khám online như thế nào?',
    answer:
      'Bạn có thể đặt lịch qua trang Đặt lịch (/booking), chọn giờ mong muốn hoặc gọi hotline 0332406049. Sau khi gửi yêu cầu, lễ tân sẽ liên hệ xác nhận và sắp lịch bác sĩ phù hợp.',
    action: { label: 'Đặt lịch ngay', to: '/booking' },
  },
  {
    question: 'Cách dùng công cụ AI chuẩn đoán bệnh trên website?',
    answer:
      'Truy cập mục “AI chuẩn đoán bệnh”, chọn triệu chứng bạn gặp phải, hệ thống sẽ gợi ý chuyên khoa phù hợp và tạo ghi chú ngắn gọn đính kèm yêu cầu đặt lịch.',
    action: { label: 'Mở AI chuẩn đoán bệnh', to: '/diagnosis' },
  },
  {
    question: 'Có hỗ trợ thanh toán không tiền mặt không?',
    answer:
      'Phòng khám hỗ trợ thanh toán thẻ, chuyển khoản. Vui lòng thông tin trước khi khám để được hướng dẫn chi tiết.',
  },
];

const showPrevFacility = () => {
  currentFacilityIndex.value = (currentFacilityIndex.value - 1 + facilityImages.length) % facilityImages.length;
};

const showNextFacility = () => {
  currentFacilityIndex.value = (currentFacilityIndex.value + 1) % facilityImages.length;
};
</script>

<template>
  <div class="min-h-screen bg-white">
    <PublicHeader />
    <main class="bg-white">
      <section class="w-full overflow-hidden">
        <img :src="bannerUrl" alt="Banner phòng khám" class="block w-full object-cover" />
      </section>

      <section class="mt-6 px-4 pb-16 md:mt-12">
        <div class="mx-auto w-full max-w-5xl rounded-[24px] border border-emerald-100 bg-white shadow-[0_18px_45px_-35px_rgba(13,148,136,0.55)]">
          <div class="grid gap-2 md:grid-cols-3 md:gap-0">
            <article class="flex h-full flex-col gap-4 px-8 py-8 md:border-r md:border-slate-200">
              <div class="flex items-start gap-4">
                <span class="flex h-12 w-12 items-center justify-center rounded-full bg-emerald-50 text-emerald-600">
                  <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" class="h-5 w-5" fill="none" stroke="currentColor" stroke-width="1.6">
                    <path stroke-linecap="round" stroke-linejoin="round" d="M8 3v2m8-2v2M4 9h16m-1 10H5a1 1 0 0 1-1-1V8a1 1 0 0 1 1-1h14a1 1 0 0 1 1 1v10a1 1 0 0 1-1 1Zm-8-8h4v4h-4z" />
                  </svg>
                </span>
                <div>
                  <h3 class="text-lg font-semibold text-slate-900">Đặt lịch khám</h3>
                  <p class="mt-1 text-base text-slate-600">Đặt lịch hẹn với phòng khám của chúng tôi.</p>
                </div>
              </div>
              <RouterLink
                to="/booking"
                class="inline-flex min-w-[190px] items-center justify-between self-center rounded-full bg-emerald-600 px-6 py-3 text-xs font-semibold uppercase tracking-wide text-white shadow-sm transition hover:bg-emerald-700"
              >
                Đặt lịch ngay
                <svg xmlns="http://www.w3.org/2000/svg" class="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">
                  <path stroke-linecap="round" stroke-linejoin="round" d="M9 5l7 7-7 7" />
                </svg>
              </RouterLink>
            </article>

            <article class="flex h-full flex-col gap-4 px-8 py-8 md:border-r md:border-slate-200">
              <div class="flex items-start gap-4">
                <span class="flex h-12 w-12 items-center justify-center rounded-full bg-emerald-50 text-emerald-600">
                  <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" class="h-5 w-5" fill="none" stroke="currentColor" stroke-width="1.6">
                    <path stroke-linecap="round" stroke-linejoin="round" d="M12 12a5 5 0 1 0 0-10 5 5 0 0 0 0 10Zm0 0c-4.97 0-9 3.134-9 7v1h18v-1c0-3.866-4.03-7-9-7Z" />
                  </svg>
                </span>
              <div>
                <h3 class="text-lg font-semibold text-slate-900">Xem chuyên khoa</h3>
                <p class="mt-1 text-base text-slate-600">Khám phá danh mục chuyên khoa và dịch vụ chuyên sâu.</p>
              </div>
            </div>
            <RouterLink
                to="/specialties"
                class="inline-flex min-w-[190px] items-center justify-between self-center rounded-full border border-emerald-600 px-6 py-3 text-xs font-semibold uppercase tracking-wide text-emerald-600 transition hover:bg-emerald-600 hover:text-white"
            >
                Xem ngay
                <svg xmlns="http://www.w3.org/2000/svg" class="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">
                  <path stroke-linecap="round" stroke-linejoin="round" d="M9 5l7 7-7 7" />
                </svg>
              </RouterLink>
            </article>

            <article class="flex h-full flex-col gap-4 px-8 py-8">
              <div class="flex items-start gap-4">
                <span class="flex h-12 w-12 items-center justify-center rounded-full bg-emerald-50 text-emerald-600">
                  <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" class="h-5 w-5" fill="none" stroke="currentColor" stroke-width="1.6">
                    <path stroke-linecap="round" stroke-linejoin="round" d="M3 5.5 5.5 3l3.75 3.75-2.5 2.5A11.05 11.05 0 0 0 14.75 14l2.5-2.5L21 15.25 18.5 18a2 2 0 0 1-1.41.59c-7.57-.19-13.67-6.29-13.86-13.86A2 2 0 0 1 3 5.5Z" />
                  </svg>
                </span>
                <div>
                  <h3 class="text-lg font-semibold text-slate-900">Gọi tổng đài</h3>
                  <p class="mt-1 text-base text-slate-600">Hỏi về phương pháp điều trị của chúng tôi.</p>
                </div>
              </div>
              <a
                :href="`tel:${hotline.replace(/\\s/g, '')}`"
                class="inline-flex min-w-[190px] items-center justify-between self-center rounded-full border border-emerald-200 bg-emerald-50 px-6 py-3 text-xs font-semibold uppercase tracking-wide text-emerald-700 transition hover:border-emerald-500 hover:bg-emerald-600 hover:text-white"
              >
                Gọi {{ hotline }}
                <svg xmlns="http://www.w3.org/2000/svg" class="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">
                  <path stroke-linecap="round" stroke-linejoin="round" d="M9 5l7 7-7 7" />
                </svg>
              </a>
            </article>
          </div>
        </div>
      </section>

      <section class="px-4 pb-16">
        <div class="mx-auto w-full max-w-6xl">
          <div class="text-center">
            <p class="text-sm font-semibold uppercase tracking-[0.35em] text-emerald-600">Quy trình thăm khám</p>
            <h2 class="mt-2 text-3xl font-semibold text-slate-900 md:text-4xl">5 bước đồng hành cùng Duyên Hạnh</h2>
          </div>

          <div class="mt-12 flex flex-col items-center gap-8 md:flex-row md:items-stretch md:justify-between md:gap-4">
            <div class="flex flex-1 flex-col items-center rounded-2xl border border-emerald-100 bg-white px-6 py-8 text-center shadow-sm">
              <span class="flex h-12 w-12 items-center justify-center rounded-full bg-emerald-50 text-sm font-semibold uppercase tracking-widest text-emerald-600">01</span>
              <h3 class="mt-4 text-lg font-semibold text-slate-900">Đặt lịch khám</h3>
            </div>

            <div class="hidden h-0.5 items-center justify-center md:flex md:w-16">
              <span class="block h-full w-full rounded-full bg-emerald-200"></span>
            </div>

            <div class="flex flex-1 flex-col items-center rounded-2xl border border-emerald-100 bg-white px-6 py-8 text-center shadow-sm">
              <span class="flex h-12 w-12 items-center justify-center rounded-full bg-emerald-50 text-sm font-semibold uppercase tracking-widest text-emerald-600">02</span>
              <h3 class="mt-4 text-lg font-semibold text-slate-900">Tiếp nhận tổng quát</h3>
            </div>

            <div class="hidden h-0.5 items-center justify-center md:flex md:w-16">
              <span class="block h-full w-full rounded-full bg-emerald-200"></span>
            </div>

            <div class="flex flex-1 flex-col items-center rounded-2xl border border-emerald-100 bg-white px-6 py-8 text-center shadow-sm">
              <span class="flex h-12 w-12 items-center justify-center rounded-full bg-emerald-50 text-sm font-semibold uppercase tracking-widest text-emerald-600">03</span>
              <h3 class="mt-4 text-lg font-semibold text-slate-900">Tư vấn dịch vụ</h3>
            </div>

            <div class="hidden h-0.5 items-center justify-center md:flex md:w-16">
              <span class="block h-full w-full rounded-full bg-emerald-200"></span>
            </div>

            <div class="flex flex-1 flex-col items-center rounded-2xl border border-emerald-100 bg-white px-6 py-8 text-center shadow-sm">
              <span class="flex h-12 w-12 items-center justify-center rounded-full bg-emerald-50 text-sm font-semibold uppercase tracking-widest text-emerald-600">04</span>
              <h3 class="mt-4 text-lg font-semibold text-slate-900">Khám chuyên khoa</h3>
            </div>

            <div class="hidden h-0.5 items-center justify-center md:flex md:w-16">
              <span class="block h-full w-full rounded-full bg-emerald-200"></span>
            </div>

            <div class="flex flex-1 flex-col items-center rounded-2xl border border-emerald-100 bg-white px-6 py-8 text-center shadow-sm">
              <span class="flex h-12 w-12 items-center justify-center rounded-full bg-emerald-50 text-sm font-semibold uppercase tracking-widest text-emerald-600">05</span>
              <h3 class="mt-4 text-lg font-semibold text-slate-900">Nhận thuốc &amp; thanh toán</h3>
            </div>
          </div>
        </div>
      </section>

      <section class="px-4 pb-16">
        <div class="mx-auto w-full max-w-6xl">
          <h2 class="text-center text-3xl font-semibold text-slate-900 md:text-4xl">TẠI SAO NÊN CHỌN DUYÊN HẠNH</h2>
          <div class="mt-10 grid gap-8 md:grid-cols-2">
            <article class="flex gap-4 rounded-3xl border border-emerald-100 bg-white p-6 shadow-sm">
              <span class="flex h-14 w-14 flex-shrink-0 items-center justify-center rounded-full bg-emerald-50 text-emerald-600">
                <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" class="h-7 w-7" fill="none" stroke="currentColor" stroke-width="1.6">
                  <path stroke-linecap="round" stroke-linejoin="round" d="m12 17.27 6.18 3.73-1.64-7.03L22 9.24l-7.19-.61L12 2 9.19 8.63 2 9.24l5.46 4.73L5.82 21z" />
                </svg>
              </span>
              <div>
                <h3 class="text-xl font-semibold text-slate-900">Chẩn đoán tốt</h3>
                <p class="mt-2 text-base leading-relaxed text-slate-600">
                  Duyên Hạnh trang bị hệ thống thiết bị y tế hiện đại, giúp phát hiện sớm, chẩn đoán chính xác và giảm thiểu rủi ro trong quá trình điều trị. Đội ngũ kỹ thuật viên được đào tạo bài bản luôn theo dõi từng ca chụp chiếu để đảm bảo tiêu chuẩn an toàn cao. <span class="font-semibold text-emerald-700">Xem thêm &raquo;</span>
                </p>
              </div>
            </article>

            <article class="flex gap-4 rounded-3xl border border-emerald-100 bg-white p-6 shadow-sm">
              <span class="flex h-14 w-14 flex-shrink-0 items-center justify-center rounded-full bg-emerald-50 text-emerald-600">
                <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" class="h-7 w-7" fill="none" stroke="currentColor" stroke-width="1.6">
                  <path stroke-linecap="round" stroke-linejoin="round" d="M9 12.75 11.25 15 15 9.75m5 2.25a8 8 0 1 1-16 0 8 8 0 0 1 16 0Z" />
                </svg>
              </span>
              <div>
                <h3 class="text-xl font-semibold text-slate-900">Điều trị hiệu quả</h3>
                <p class="mt-2 text-base leading-relaxed text-slate-600">
                  Đội ngũ chuyên gia giàu kinh nghiệm luôn cập nhật phác đồ mới, bảo đảm mỗi khách hàng nhận được giải pháp điều trị tối ưu. Quy trình theo dõi sau điều trị được cá nhân hóa giúp bạn an tâm đồng hành cùng bác sĩ ở mọi giai đoạn. <span class="font-semibold text-emerald-700">Xem thêm &raquo;</span>
                </p>
              </div>
            </article>

            <article class="flex gap-4 rounded-3xl border border-emerald-100 bg-white p-6 shadow-sm">
              <span class="flex h-14 w-14 flex-shrink-0 items-center justify-center rounded-full bg-emerald-50 text-emerald-600">
                <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" class="h-7 w-7" fill="none" stroke="currentColor" stroke-width="1.6">
                  <path stroke-linecap="round" stroke-linejoin="round" d="M9.88 3.48 8.54 5.5 6 4.89l.6 2.53L5 9l2.39.55.15 2.61 2.16-1.71 2.31 1.41-.64-2.54L13.5 8l-2.33-1.28.25-2.59-2.02 1.35ZM9 14h6l1 7H8Zm3-4s4 0 6-2c-1-4-3-6-3-6H9s-2 2-3 6c2 2 6 2 6 2Z" />
                </svg>
              </span>
              <div>
                <h3 class="text-xl font-semibold text-slate-900">Giải pháp đồng bộ</h3>
                <p class="mt-2 text-base leading-relaxed text-slate-600">
                  Quy trình khám, tư vấn và chăm sóc được thiết kế trọn vẹn, phối hợp nhịp nhàng giữa các chuyên khoa để mang lại sự an tâm tuyệt đối. Hồ sơ bệnh án điện tử giúp việc chuyển tiếp thông tin giữa các bác sĩ diễn ra chính xác và nhanh chóng. <span class="font-semibold text-emerald-700">Xem thêm &raquo;</span>
                </p>
              </div>
            </article>

            <article class="flex gap-4 rounded-3xl border border-emerald-100 bg-white p-6 shadow-sm">
              <span class="flex h-14 w-14 flex-shrink-0 items-center justify-center rounded-full bg-emerald-50 text-emerald-600">
                <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" class="h-7 w-7" fill="none" stroke="currentColor" stroke-width="1.6">
                  <path stroke-linecap="round" stroke-linejoin="round" d="M12 18.5c7-4.5 7-10.5 0-15-7 4.5-7 10.5 0 15ZM6.5 15a17.1 17.1 0 0 1-1.87-.85M19.37 14.15A17.06 17.06 0 0 1 17.5 15M12 21a18.55 18.55 0 0 1-6-1m12 0a18.55 18.55 0 0 1-6 1m0 0v-3" />
                </svg>
              </span>
              <div>
                <h3 class="text-xl font-semibold text-slate-900">Tối ưu tài chính</h3>
                <p class="mt-2 text-base leading-relaxed text-slate-600">
                  Các gói dịch vụ đa dạng, minh bạch chi phí và quy trình nhanh gọn giúp khách hàng tiết kiệm thời gian lẫn ngân sách. Nhiều lựa chọn thanh toán linh hoạt, bao gồm hợp tác với bảo hiểm và trả góp 0%, hỗ trợ khách hàng chủ động tài chính. <span class="font-semibold text-emerald-700">Xem thêm &raquo;</span>
                </p>
              </div>
            </article>
          </div>
        </div>
      </section>

      <section class="px-4 pb-20">
        <div class="mx-auto w-full max-w-6xl">
          <div class="text-center md:text-left">
            <h2 class="text-3xl font-semibold text-slate-900 md:text-4xl">Cơ sở vật chất hiện đại</h2>
            <p class="mt-4 max-w-3xl text-base leading-relaxed text-slate-600 md:text-lg">
              Không gian khám chữa bệnh được thiết kế mở, thân thiện và áp dụng quy chuẩn vô trùng nghiêm ngặt.
              Các phòng chức năng đều được trang bị đầy đủ máy móc thế hệ mới nhằm đảm bảo trải nghiệm an toàn, thoải mái cho khách hàng.
            </p>
          </div>

          <div class="relative mx-auto mt-10 w-full max-w-4xl">
            <div class="overflow-hidden rounded-[32px] border border-emerald-100 shadow-[0_24px_60px_-30px_rgba(13,148,136,0.55)]">
              <img
                :src="facilityImages[currentFacilityIndex]"
                alt="Hình ảnh cơ sở vật chất phòng khám Duyên Hạnh"
                class="block h-full w-full object-cover"
              />
            </div>
            <button
              type="button"
              @click="showPrevFacility"
              class="absolute left-2 top-1/2 inline-flex h-11 w-11 -translate-y-1/2 items-center justify-center rounded-full border border-emerald-200 bg-white/80 text-emerald-600 backdrop-blur transition hover:border-emerald-500 hover:bg-emerald-600 hover:text-white md:left-6 md:h-12 md:w-12"
              aria-label="Xem hình trước"
            >
              <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" class="h-5 w-5" fill="none" stroke="currentColor" stroke-width="1.8">
                <path stroke-linecap="round" stroke-linejoin="round" d="m15 19-7-7 7-7" />
              </svg>
            </button>
            <button
              type="button"
              @click="showNextFacility"
              class="absolute right-2 top-1/2 inline-flex h-11 w-11 -translate-y-1/2 items-center justify-center rounded-full border border-emerald-200 bg-white/80 text-emerald-600 backdrop-blur transition hover:border-emerald-500 hover:bg-emerald-600 hover:text-white md:right-6 md:h-12 md:w-12"
              aria-label="Xem hình tiếp theo"
            >
              <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" class="h-5 w-5" fill="none" stroke="currentColor" stroke-width="1.8">
                <path stroke-linecap="round" stroke-linejoin="round" d="m9 5 7 7-7 7" />
              </svg>
            </button>
            <div class="absolute inset-x-0 -bottom-4 flex justify-center gap-2">
              <button
                v-for="(image, index) in facilityImages"
                :key="image"
                type="button"
                @click="currentFacilityIndex = index"
                :class="[
                  'h-2.5 w-12 rounded-full transition',
                  currentFacilityIndex === index ? 'bg-emerald-600' : 'bg-emerald-200 hover:bg-emerald-400'
                ]"
                :aria-label="`Chuyển tới hình số ${index + 1}`"
              />
            </div>
          </div>
        </div>
      </section>

      <section class="px-4 pb-16">
        <div class="mx-auto w-full max-w-5xl">
          <div class="mb-6 text-center">
            <p class="text-sm font-semibold uppercase tracking-[0.35em] text-emerald-600">Giải đáp nhanh</p>
            <h2 class="mt-2 text-3xl font-semibold text-slate-900 md:text-4xl">Câu hỏi thường gặp</h2>
            <p class="mt-2 text-base text-slate-600">Thông tin về địa chỉ, dịch vụ, đặt lịch online và AI chuẩn đoán bệnh.</p>
          </div>
          <div class="space-y-3">
            <details
              v-for="item in specialtyFaq"
              :key="item.question"
              class="group rounded-2xl border border-emerald-100 bg-white/80 p-4 shadow-sm"
            >
              <summary class="cursor-pointer text-sm font-semibold text-slate-900 marker:text-emerald-500 group-open:text-emerald-600">
                {{ item.question }}
              </summary>
              <p class="mt-2 text-sm leading-relaxed text-slate-700">{{ item.answer }}</p>
              <RouterLink
                v-if="item.action"
                :to="item.action.to"
                class="mt-3 inline-flex items-center gap-2 text-xs font-semibold uppercase tracking-wide text-emerald-600 hover:text-emerald-700"
              >
                {{ item.action.label }}
                <svg xmlns="http://www.w3.org/2000/svg" class="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">
                  <path stroke-linecap="round" stroke-linejoin="round" d="M9 5l7 7-7 7" />
                </svg>
              </RouterLink>
            </details>
          </div>
        </div>
      </section>

    </main>
    <PublicFooter />
  </div>
</template>
