<script setup lang="ts">
import PublicHeader from '@/components/PublicHeader.vue';
</script>

<template>
  <div class="min-h-screen bg-slate-50">
    <PublicHeader />
    <section class="border-b border-slate-200 bg-white">
      <div class="mx-auto max-w-6xl px-6 py-12">
        <h1 class="mb-3 text-3xl font-bold tracking-tight text-slate-900">Giới thiệu</h1>
        <p class="mb-6 text-slate-700">Phòng khám cung cấp dịch vụ khám chữa bệnh toàn diện với đội ngũ bác sĩ tận tâm và trang thiết bị hiện đại.</p>
        <div class="grid gap-6 md:grid-cols-3">
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
      </div>
    </section>
  </div>
</template>

