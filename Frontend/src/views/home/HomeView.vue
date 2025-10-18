<script setup lang="ts">
import { ref, onMounted } from 'vue';
import AppointmentRequestForm from '@/components/AppointmentRequestForm.vue';
import { useAuthStore } from '@/stores/authStore';
import { useRouter } from 'vue-router';
import { fetchDoctors, type Doctor } from '@/services/doctor.service';
import PublicHeader from '@/components/PublicHeader.vue';

const authStore = useAuthStore();
const router = useRouter();

const doctors = ref<Doctor[]>([]);
const loadingDoctors = ref(false);

onMounted(async () => {
  try {
    loadingDoctors.value = true;
    const list = await fetchDoctors();
    doctors.value = (list ?? []).slice(0, 3);
    console.debug('[Home] Doctors loaded:', doctors.value);
  } catch (e) {
    doctors.value = [];
  } finally {
    loadingDoctors.value = false;
  }
});
</script>

<template>
  <div class="min-h-screen bg-slate-50">
    <PublicHeader />

    <!-- Hero -->
    <section id="trang-chu" class="border-b border-slate-200 bg-gradient-to-b from-white to-slate-50">
      <div class="mx-auto grid max-w-6xl items-center gap-8 px-6 py-12 lg:grid-cols-2 lg:py-16">
        <div>
          <h2 class="mb-3 text-3xl font-bold tracking-tight text-slate-900">Dịch vụ khám chữa bệnh toàn diện</h2>
          <p class="mb-6 text-slate-600">
            Đội ngũ bác sĩ giàu kinh nghiệm, trang thiết bị hiện đại. Đặt lịch trực tuyến nhanh chóng và thuận tiện.
          </p>
          <div class="flex gap-3">
            <a href="#dat-lich" class="rounded-lg bg-blue-600 px-4 py-2 text-sm font-semibold text-white shadow hover:bg-blue-700">Đặt lịch ngay</a>
            <a href="#gioi-thieu" class="rounded-lg border border-slate-300 px-4 py-2 text-sm font-semibold text-slate-700 hover:bg-white">Tìm hiểu thêm</a>
          </div>
        </div>
        <div class="hidden h-56 rounded-2xl bg-slate-200 lg:block"></div>
      </div>
    </section>

    <!-- About -->
    <section id="gioi-thieu" class="border-b border-slate-200 bg-white">
      <div class="mx-auto grid max-w-6xl gap-6 px-6 py-10 md:grid-cols-3">
        <div class="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
          <h3 class="mb-2 text-base font-semibold text-slate-900">Bác sĩ tận tâm</h3>
          <p class="text-sm text-slate-600">Tư vấn kỹ lưỡng, điều trị theo phác đồ an toàn và hiệu quả.</p>
        </div>
        <div class="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
          <h3 class="mb-2 text-base font-semibold text-slate-900">Trang thiết bị</h3>
          <p class="text-sm text-slate-600">Thiết bị hiện đại giúp chẩn đoán chính xác và nhanh chóng.</p>
        </div>
        <div class="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
          <h3 class="mb-2 text-base font-semibold text-slate-900">Quy trình nhanh gọn</h3>
          <p class="text-sm text-slate-600">Đặt lịch trực tuyến, nhận thông báo xác nhận và nhắc lịch.</p>
        </div>
      </div>
    </section>

    <!-- Specialties -->
    <section id="chuyen-khoa" class="border-b border-slate-200 bg-white">
      <div class="mx-auto max-w-6xl px-6 py-10">
        <h2 class="mb-6 text-2xl font-bold text-slate-900">Chuyên khoa</h2>
        <div class="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          <div class="rounded-xl border border-slate-200 bg-white p-5 shadow-sm">
            <h3 class="text-base font-semibold text-slate-900">Nội tổng quát</h3>
            <p class="mt-1 text-sm text-slate-600">Khám và điều trị bệnh lý người lớn.</p>
          </div>
          <div class="rounded-xl border border-slate-200 bg-white p-5 shadow-sm">
            <h3 class="text-base font-semibold text-slate-900">Nhi khoa</h3>
            <p class="mt-1 text-sm text-slate-600">Chăm sóc sức khoẻ trẻ em.</p>
          </div>
          <div class="rounded-xl border border-slate-200 bg-white p-5 shadow-sm">
            <h3 class="text-base font-semibold text-slate-900">Tai - Mũi - Họng</h3>
            <p class="mt-1 text-sm text-slate-600">Chẩn đoán và điều trị chuyên sâu.</p>
          </div>
          <div class="rounded-xl border border-slate-200 bg-white p-5 shadow-sm">
            <h3 class="text-base font-semibold text-slate-900">Da liễu</h3>
            <p class="mt-1 text-sm text-slate-600">Điều trị các bệnh lý về da.</p>
          </div>
          <div class="rounded-xl border border-slate-200 bg-white p-5 shadow-sm">
            <h3 class="text-base font-semibold text-slate-900">Răng - Hàm - Mặt</h3>
            <p class="mt-1 text-sm text-slate-600">Chăm sóc răng miệng toàn diện.</p>
          </div>
          <div class="rounded-xl border border-slate-200 bg-white p-5 shadow-sm">
            <h3 class="text-base font-semibold text-slate-900">Xét nghiệm</h3>
            <p class="mt-1 text-sm text-slate-600">Hỗ trợ chẩn đoán chính xác.</p>
          </div>
        </div>
      </div>
    </section>

    <!-- Doctors -->
    <section class="border-b border-slate-200 bg-white">
      <div class="mx-auto max-w-6xl px-6 py-10">
        <h2 class="mb-6 text-2xl font-bold text-slate-900">Đội ngũ bác sĩ</h2>
        <div class="grid gap-6 sm:grid-cols-2 lg:grid-cols-3">
          <div
            v-for="doc in doctors"
            :key="doc.id"
            class="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm"
          >
            <div class="mb-3 h-28 w-28 rounded-full bg-slate-200"></div>
            <h3 class="text-base font-semibold text-slate-900">{{ doc.account?.fullName || 'Bác sĩ' }}</h3>
            <p class="text-sm text-slate-600">{{ doc.specialty || 'Bác sĩ đa khoa' }}</p>
          </div>
          <div v-if="!loadingDoctors && doctors.length === 0" class="text-sm text-slate-600">
            Chưa có thông tin bác sĩ.
          </div>
        </div>
      </div>
    </section>

    <!-- Booking -->
    <section id="dat-lich" class="bg-slate-50">
      <div class="mx-auto grid max-w-6xl gap-8 px-6 py-12 lg:grid-cols-2">
        <div>
          <h2 class="mb-2 text-2xl font-bold text-slate-900">Đặt lịch khám</h2>
          <p class="mb-3 text-sm text-slate-600">Điền thông tin bên cạnh, chúng tôi sẽ gọi xác nhận sớm nhất.</p>
          <div class="mb-6 rounded-xl border border-slate-200 bg-white p-4 text-sm text-slate-700 shadow-sm">
            <p class="mb-2 font-medium text-slate-900">Giờ làm việc</p>
            <ul class="space-y-1">
              <li>• Buổi sáng: 07:30 – 11:30 </li>
              <li>• Buổi chiều: 13:00 – 20:00 </li>
            </ul>
          </div>
          <ul class="space-y-3 text-sm text-slate-700">
            <li>• Khám tổng quát, nội, nhi, tai-mũi-họng...</li>
            <li>• Dịch vụ xét nghiệm và chẩn đoán hình ảnh</li>
            <li>• Tư vấn điều trị và chăm sóc sau khám</li>
          </ul>
        </div>
        <AppointmentRequestForm @submitted="() => {}" />
      </div>
    </section>

    <!-- Footer -->
    <footer class="border-t border-slate-200 bg-white">
      <div class="mx-auto flex max-w-6xl items-center justify-between px-6 py-6 text-sm text-slate-600">
        <p>© 2025 Phòng Khám Sức Khỏe</p>
        <p>Hotline: 1900 123 456</p>
      </div>
    </footer>
  </div>
</template>
