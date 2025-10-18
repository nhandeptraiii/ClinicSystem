<script setup lang="ts">
import PublicHeader from '@/components/PublicHeader.vue';
</script>

<template>
  <div class="min-h-screen bg-slate-50">
    <PublicHeader />
    <section class="border-b border-slate-200 bg-white">
      <div class="mx-auto max-w-6xl px-6 py-12">
        <h1 class="mb-6 text-3xl font-bold tracking-tight text-slate-900">Chuyên khoa</h1>
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
  </div>
</template>

