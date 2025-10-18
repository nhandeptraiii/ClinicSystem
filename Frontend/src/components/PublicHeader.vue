<script setup lang="ts">
import { useRouter, useRoute, RouterLink } from 'vue-router';
import { useAuthStore } from '@/stores/authStore';
import logoUrl from '@/assets/LogoDuyenHanh.png';

const router = useRouter();
const route = useRoute();
const authStore = useAuthStore();

const HOTLINE = '0332406049';

const isActive = (name: string) => route.name === name;

const onLogoClick = () => {
  if (route.name === 'home') {
    window.scrollTo({ top: 0, behavior: 'smooth' });
  } else {
    router.push({ name: 'home' }).then(() => {
      window.scrollTo({ top: 0, behavior: 'smooth' });
    });
  }
};
</script>

<template>
  <header class="sticky top-0 z-50 border-b border-slate-200 bg-white/95 backdrop-blur">
    <div class="mx-auto flex max-w-7xl items-center justify-between px-6 py-3">
      <!-- Logo -->
      <button class="flex items-center gap-3" @click="onLogoClick">
        <img :src="logoUrl" alt="Clinic Logo" class="h-16 w-auto" />
      </button>

      <!-- Nav -->
      <nav class="hidden items-center gap-6 text-sm font-semibold md:flex">
        <RouterLink
          :to="{ name: 'home' }"
          :class="isActive('home') ? 'text-emerald-700' : 'text-slate-600 hover:text-emerald-700'"
        >TRANG CHỦ</RouterLink>
        <RouterLink
          :to="{ name: 'about' }"
          :class="isActive('about') ? 'text-emerald-700' : 'text-slate-600 hover:text-emerald-700'"
        >GIỚI THIỆU</RouterLink>
        <RouterLink
          :to="{ name: 'specialties' }"
          :class="isActive('specialties') ? 'text-emerald-700' : 'text-slate-600 hover:text-emerald-700'"
        >CHUYÊN KHOA</RouterLink>
        <RouterLink
          :to="{ name: 'booking' }"
          :class="isActive('booking') ? 'text-emerald-700' : 'text-slate-600 hover:text-emerald-700'"
        >ĐẶT LỊCH</RouterLink>
      </nav>

      <!-- Hotline + Auth quick links -->
      <div class="flex items-center gap-3">
        <a :href="`tel:${HOTLINE}`" class="flex items-center gap-2 rounded-full bg-rose-500 px-3 py-1.5 text-sm font-semibold text-white shadow hover:bg-rose-600">
          <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 20 20" fill="currentColor" class="h-4 w-4"><path d="M2.003 5.884c.06-1.07.993-1.92 2.064-1.82 1.27.12 2.77.41 3.96 1.6 1.19 1.19 1.48 2.69 1.6 3.96.1 1.07-.75 2.004-1.82 2.064-.87.049-1.69.258-2.45.61a11.036 11.036 0 005.72 5.72c.352-.76.561-1.58.61-2.45.06-1.07.993-1.92 2.064-1.82 1.27.12 2.77.41 3.96 1.6 1.19 1.19 1.48 2.69 1.6 3.96.1 1.07-.75 2.004-1.82 2.064-4.79.272-9.74-1.57-13.29-5.12C.433 10.91-1.41 5.96-1.137 1.17c.06-1.07.993-1.92 2.064-1.82 1.27.12 2.77.41 3.96 1.6 1.19 1.19 1.48 2.69 1.6 3.96.1 1.07-.75 2.004-1.82 2.064-.87.049-1.69.258-2.45.61a11.036 11.036 0 005.72 5.72c.352-.76.561-1.58.61-2.45z"/></svg>
          {{ HOTLINE }}
        </a>
        <button v-if="authStore.isAuthenticated" class="hidden rounded-lg border border-slate-300 px-3 py-1.5 text-xs font-semibold text-slate-700 hover:bg-slate-50 md:block" @click="router.push({ name: 'dashboard' })">Quản trị</button>
        <button v-else class="hidden rounded-lg border border-slate-300 px-3 py-1.5 text-xs font-semibold text-slate-700 hover:bg-slate-50 md:block" @click="router.push({ name: 'login' })">Đăng nhập</button>
      </div>
    </div>
  </header>
</template>
