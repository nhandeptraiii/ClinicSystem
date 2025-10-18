<script setup lang="ts">
import PublicHeader from '@/components/PublicHeader.vue';
import AppointmentRequestForm from '@/components/AppointmentRequestForm.vue';
</script>

<template>
  <div class="min-h-screen bg-slate-50">
    <PublicHeader />
    <section class="bg-slate-50">
      <div class="mx-auto grid max-w-6xl gap-8 px-6 py-12 lg:grid-cols-2">
        <div>
          <h1 class="mb-2 text-3xl font-bold text-slate-900">Đặt lịch khám</h1>
          <p class="mb-3 text-sm text-slate-600">Điền thông tin bên cạnh, chúng tôi sẽ gọi xác nhận sớm nhất.</p>
          <div class="mb-6 rounded-xl border border-slate-200 bg-white p-4 text-sm text-slate-700 shadow-sm">
            <p class="mb-2 font-medium text-slate-900">Giờ làm việc</p>
            <ul class="space-y-1">
              <li>• Buổi sáng: 07:30 – 11:10 (khung giờ cách 20 phút)</li>
              <li>• Nghỉ trưa: 11:30 – 13:00</li>
              <li>• Buổi chiều: 13:00 – 19:00 (khung giờ cách 20 phút)</li>
              <li>• Kết thúc ca chiều lúc 20:00</li>
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
  </div>
</template>

