<script setup lang="ts">
import PublicHeader from '@/components/PublicHeader.vue';
import PublicFooter from '@/components/PublicFooter.vue';

import iconNoiCoXuongKhop from '@/assets/chuyenkhoa/icon-noicoxuongkhop.png';
import iconNoiThanKinh from '@/assets/chuyenkhoa/icon-noithankinh.png';
import iconChanDoanHinhAnh from '@/assets/chuyenkhoa/icon-ngoaitonghop.png';
import iconChanThuongChinhHinh from '@/assets/chuyenkhoa/icon-chanthuongchinhhinh.png';
import iconXetNghiem from '@/assets/chuyenkhoa/icon-xetnghiem.png';
import iconNoiTieuHoa from '@/assets/chuyenkhoa/icon-tieuhoa.png';
import iconTaiMuiHong from '@/assets/chuyenkhoa/icon-taimuihong.png';
import iconChuyenKhoaNgoai from '@/assets/chuyenkhoa/icon-ngoai.png';

const specialties = [
  { title: 'Nội - Cơ Xương Khớp', icon: iconNoiCoXuongKhop },
  { title: 'Nội Thần Kinh', icon: iconNoiThanKinh },
  { title: 'Chẩn Đoán Hình Ảnh', icon: iconChanDoanHinhAnh },
  { title: 'Chấn Thương Chỉnh Hình', icon: iconChanThuongChinhHinh },
  { title: 'Xét Nghiệm', icon: iconXetNghiem },
  { title: 'Nội Tiêu Hóa', icon: iconNoiTieuHoa },
  { title: 'Tai Mũi Họng', icon: iconTaiMuiHong },
  { title: 'Chuyên Khoa Ngoại', icon: iconChuyenKhoaNgoai },
];

const specialtyHighlights = [
  {
    title: 'Đội ngũ bác sĩ chuyên sâu',
    description:
      'Mỗi chuyên khoa quy tụ các bác sĩ, cố vấn y khoa nhiều năm kinh nghiệm, luôn cập nhật phác đồ và công nghệ điều trị mới nhất.',
  },
  {
    title: 'Quy trình cá nhân hóa',
    description:
      'Người bệnh được sàng lọc, tư vấn tiền khám, theo dõi sau điều trị phù hợp với từng nhu cầu cụ thể.',
  },
  {
    title: 'Liên thông đa chuyên khoa',
    description:
      'Các chuyên khoa phối hợp chặt chẽ giúp chẩn đoán chính xác hơn, rút ngắn thời gian nằm viện và chi phí điều trị.',
  },
];

const specialtyServices = [
  {
    title: 'Tầm soát thoái hóa khớp nâng cao',
    specialty: 'Nội - Cơ Xương Khớp',
    detail:
      'Kết hợp xét nghiệm marker viêm, chụp MRI tiền triệu cùng phác đồ phục hồi chức năng theo dõi bằng ứng dụng di động.',
  },
  {
    title: 'Chẩn đoán sớm bệnh lý thần kinh',
    specialty: 'Nội Thần Kinh',
    detail:
      'Ứng dụng đo điện não vi tính và test nhận thức giúp phát hiện rối loạn tiền đình, sa sút trí tuệ ở giai đoạn sớm.',
  },
  {
    title: 'Gói nội soi tiêu hóa không đau',
    specialty: 'Nội Tiêu Hóa',
    detail:
      'Nội soi ống mềm thế hệ mới, gây mê nhẹ, trả kết quả trong ngày và tư vấn dinh dưỡng cá nhân hóa ngay sau thủ thuật.',
  },
  {
    title: 'Trung tâm xét nghiệm đạt chuẩn ISO 15189',
    specialty: 'Xét Nghiệm',
    detail:
      'Tất cả mẫu xét nghiệm được chuẩn hóa, kiểm định định kỳ giúp bảo đảm độ chính xác tối đa và thời gian trả kết quả nhanh.',
  },
];

const specialtyFaq = [
  {
    question: 'Làm sao để xác định chuyên khoa phù hợp với triệu chứng của tôi?',
    answer:
      'Bạn có thể liên hệ đường dây tư vấn để được điều dưỡng sàng lọc triệu chứng hoặc gửi yêu cầu đặt lịch, chúng tôi sẽ gợi ý chuyên khoa phù hợp.',
  },
  {
    question: 'Có thể kết hợp khám nhiều chuyên khoa trong cùng một buổi không?',
    answer:
      'Hệ thống đặt lịch cho phép ghép lịch liên chuyên khoa, tiết kiệm thời gian di chuyển và đồng bộ kết quả cận lâm sàng.',
  },
  {
    question: 'Các chuyên khoa có hỗ trợ tư vấn trực tuyến trước khi đến khám?',
    answer:
      'Một số chuyên khoa như Nội - Cơ Xương Khớp, Nội Thần Kinh và Tai Mũi Họng cung cấp tư vấn video để bác sĩ nắm thông tin ban đầu và chuẩn bị kế hoạch điều trị.',
  },
];
</script>

<template>
  <div class="min-h-screen bg-slate-50">
    <PublicHeader />
    <section class="bg-emerald-50/40">
      <div class="mx-auto max-w-6xl px-6 py-16">
        <h1 class="mb-12 text-center text-3xl font-bold uppercase tracking-wide text-emerald-600">Danh sách chuyên khoa</h1>
        <ul class="grid gap-6 sm:grid-cols-2 lg:grid-cols-4">
          <li
            v-for="specialty in specialties"
            :key="specialty.title"
            class="rounded-3xl bg-white p-8 text-center shadow-sm ring-1 ring-emerald-100 transition hover:-translate-y-1 hover:shadow-lg"
          >
            <div class="mx-auto mb-6 flex h-24 w-24 items-center justify-center rounded-full border-2 border-emerald-500 bg-white">
              <img :src="specialty.icon" :alt="specialty.title" class="h-14 w-14 object-contain" />
            </div>
            <p class="text-sm font-semibold uppercase tracking-wide text-slate-700">{{ specialty.title }}</p>
          </li>
        </ul>
      </div>
    </section>
    <section class="bg-white">
      <div class="mx-auto max-w-6xl px-6 py-16">
        <h2 class="mb-10 text-center text-2xl font-semibold uppercase tracking-wide text-slate-900">Điểm nổi bật của các chuyên khoa</h2>
        <div class="grid gap-6 md:grid-cols-3">
          <article
            v-for="highlight in specialtyHighlights"
            :key="highlight.title"
            class="rounded-3xl border border-emerald-100 bg-emerald-50/60 p-6 text-left shadow-sm"
          >
            <h3 class="text-lg font-semibold text-emerald-700">{{ highlight.title }}</h3>
            <p class="mt-3 text-sm leading-relaxed text-slate-600">{{ highlight.description }}</p>
          </article>
        </div>
      </div>
    </section>
    <section class="border-t border-slate-200 bg-slate-100/60">
      <div class="mx-auto max-w-6xl px-6 py-16">
        <div class="mb-10 text-center">
          <span class="mb-2 inline-block text-xs font-semibold uppercase tracking-[0.3em] text-emerald-600">Chương trình chuyên sâu</span>
          <h2 class="text-2xl font-semibold text-slate-900">Dịch vụ tiêu biểu theo từng chuyên khoa</h2>
        </div>
        <ul class="space-y-6">
          <li
            v-for="service in specialtyServices"
            :key="service.title"
            class="rounded-3xl bg-white p-6 shadow-sm ring-1 ring-emerald-100 transition hover:-translate-y-1 hover:shadow-lg"
          >
            <div class="flex flex-col gap-2 md:flex-row md:items-center md:justify-between">
              <h3 class="text-lg font-semibold text-slate-900">{{ service.title }}</h3>
              <span class="inline-flex rounded-full bg-emerald-100 px-3 py-1 text-xs font-semibold uppercase tracking-wide text-emerald-600">
                {{ service.specialty }}
              </span>
            </div>
            <p class="mt-3 text-sm leading-relaxed text-slate-600">{{ service.detail }}</p>
          </li>
        </ul>
      </div>
    </section>
    <section class="bg-white">
      <div class="mx-auto max-w-5xl px-6 py-16">
        <div class="mb-8 text-center">
          <h2 class="text-2xl font-semibold text-slate-900">Câu hỏi thường gặp</h2>
          <p class="mt-2 text-sm text-slate-600">Giải đáp nhanh những thắc mắc phổ biến khi lựa chọn chuyên khoa tại phòng khám.</p>
        </div>
        <div class="space-y-4">
          <details
            v-for="item in specialtyFaq"
            :key="item.question"
            class="group rounded-2xl border border-slate-200 bg-slate-50/60 p-5 shadow-sm"
          >
            <summary class="cursor-pointer text-sm font-semibold text-slate-800 marker:text-emerald-500 group-open:text-emerald-600">
              {{ item.question }}
            </summary>
            <p class="mt-3 text-sm leading-relaxed text-slate-600">{{ item.answer }}</p>
          </details>
        </div>
      </div>
    </section>
    <PublicFooter />
  </div>
</template>
