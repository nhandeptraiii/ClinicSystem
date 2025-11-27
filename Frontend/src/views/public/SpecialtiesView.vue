<script setup lang="ts">
import PublicHeader from '@/components/PublicHeader.vue';
import PublicFooter from '@/components/PublicFooter.vue';

import iconNoiCoXuongKhop from '@/assets/chuyenkhoa/icon-noicoxuongkhop.png';
import iconNoiThanKinh from '@/assets/chuyenkhoa/icon-noithankinh.png';
import iconNoiTieuHoa from '@/assets/chuyenkhoa/icon-tieuhoa.png';
import iconTaiMuiHong from '@/assets/chuyenkhoa/icon-taimuihong.png';
import iconTietNieu from '@/assets/chuyenkhoa/icon-tietnieu.png';
import iconDaLieu from '@/assets/chuyenkhoa/icon-dalieu.png';


const specialties = [
  { title: 'Cơ Xương Khớp', icon: iconNoiCoXuongKhop },
  { title: 'Nội Tổng Quát', icon: iconNoiThanKinh },
  // { title: 'Chẩn Đoán Hình Ảnh', icon: iconChanDoanHinhAnh },
  { title: 'Da Liễu', icon: iconDaLieu },
  // { title: 'Xét Nghiệm', icon: iconXetNghiem },
  { title: 'Nội Tiêu Hóa', icon: iconNoiTieuHoa },
  { title: 'Tai Mũi Họng', icon: iconTaiMuiHong },
  { title: 'Tiết Niệu', icon: iconTietNieu },
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
    title: 'Tầm soát thoái hóa khớp & loãng xương',
    specialty: 'Cơ Xương Khớp',
    detail:
      'Đo mật độ xương, xét nghiệm marker viêm, siêu âm khớp và thiết kế bài tập phục hồi cá nhân hóa cho người thoái hóa, đau mỏi lâu ngày.',
  },
  {
    title: 'Khám sức khỏe tổng quát & quản lý bệnh mạn',
    specialty: 'Nội Tổng Quát',
    detail:
      'Sàng lọc tăng huyết áp, đái tháo đường, mỡ máu; tư vấn dinh dưỡng, lộ trình theo dõi định kỳ với hồ sơ điện tử đồng bộ.',
  },
  {
    title: 'Điều trị mụn & bệnh lý da mãn tính',
    specialty: 'Da Liễu',
    detail:
      'Quy trình khám da liễu chuẩn y khoa, peel/light therapy, kê đơn cá nhân hóa và theo dõi online cho mụn, viêm da, dị ứng.',
  },
  {
    title: 'Nội soi tiêu hóa không đau & quản lý HP',
    specialty: 'Nội Tiêu Hóa',
    detail:
      'Nội soi gây mê nhẹ, xét nghiệm HP nhanh, tư vấn chế độ ăn cho viêm loét dạ dày – trào ngược – viêm gan mật, trả kết quả trong ngày.',
  },
  {
    title: 'Khám chuyên sâu Tai Mũi Họng cho gia đình',
    specialty: 'Tai Mũi Họng',
    detail:
      'Soi tai mũi họng, test dị ứng, điều trị viêm xoang – viêm họng tái phát, hướng dẫn vệ sinh tai mũi họng an toàn cho trẻ em.',
  },
  {
    title: 'Sàng lọc sỏi thận & viêm tiết niệu',
    specialty: 'Tiết Niệu',
    detail:
      'Siêu âm, xét nghiệm nước tiểu, tư vấn uống nước – ăn nhạt, phát hiện sớm sỏi, viêm bàng quang, rối loạn tiểu tiện và theo dõi tái phát.',
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
        <ul class="grid gap-6 sm:grid-cols-2 lg:grid-cols-3">
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
