<script setup lang="ts">
import PublicHeader from '@/components/PublicHeader.vue';
import PublicFooter from '@/components/PublicFooter.vue';
import logoUrl from '@/assets/LogoDuyenHanh.png';
</script>

<template>
  <div class="min-h-screen bg-slate-50">
    <PublicHeader />

    <section class="border-b border-slate-200 bg-gradient-to-b from-white to-slate-50">
      <div class="mx-auto flex max-w-6xl flex-col items-center px-6 py-16 text-center">
        <div class="mb-6 grid h-24 w-24 place-items-center rounded-full ring-2 ring-emerald-600 bg-white shadow">
          <img :src="logoUrl" alt="Clinic Logo" class="h-14 w-14 object-contain" />
        </div>
        <p class="mb-2 text-sm font-semibold tracking-wide text-emerald-600">404</p>
        <h1 class="mb-3 text-3xl font-bold tracking-tight text-slate-900">Trang bạn tìm không tồn tại</h1>
        <p class="mb-8 max-w-2xl text-sm text-slate-600">
          Có vẻ như bạn đã đi nhầm đường. Bạn có thể quay về trang chủ hoặc đặt lịch khám ngay bây giờ.
        </p>
        <div class="flex flex-wrap justify-center gap-3">
          <RouterLink
            to="/"
            class="rounded-lg bg-emerald-600 px-4 py-2 text-sm font-semibold text-white shadow hover:bg-emerald-700"
          >
            Về trang chủ
          </RouterLink>
          <RouterLink
            to="/booking"
            class="rounded-lg border border-emerald-600 px-4 py-2 text-sm font-semibold text-emerald-700 hover:bg-emerald-50"
          >
            Đặt lịch khám
          </RouterLink>
        </div>
      </div>
    </section>

    <PublicFooter />
  </div>

</template>

