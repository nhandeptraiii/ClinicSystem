<script setup lang="ts">
import { RouterLink, useRoute, useRouter } from 'vue-router';
import logoUrl from '@/assets/LogoDuyenHanh.png';

const props = defineProps<{
  userName: string;
}>();

const emit = defineEmits<{
  (e: 'sign-out'): void;
}>();

const router = useRouter();
const route = useRoute();

const goHome = () => {
  if (route.path.startsWith('/dashboard')) {
    router.push({ name: 'home' });
  } else {
    router.push({ name: 'dashboard-home' });
  }
};

const handleSignOut = () => {
  emit('sign-out');
};
</script>

<template>
  <header class="fixed left-0 top-0 z-40 flex h-16 w-full items-center justify-between border-b border-emerald-100 bg-white/90 px-6 backdrop-blur">
    <button
      class="group flex items-center gap-3 rounded-full border border-transparent px-2 py-1 transition hover:border-emerald-200"
      @click="goHome"
    >
      <img :src="logoUrl" alt="Duyên Hạnh Clinic" class="h-12 w-auto rounded-full ring-1 ring-emerald-100" />
      <div class="flex flex-col items-start leading-tight text-left">
        <span class="text-sm font-semibold text-slate-900">Duyên Hạnh Clinic</span>
        <span class="text-[11px] font-semibold uppercase tracking-[0.28em] text-emerald-600">Cổng quản trị</span>
      </div>
    </button>

    <div class="flex items-center gap-3">
      <div class="rounded-2xl border border-emerald-100 bg-white/80 px-4 py-2 text-sm text-slate-600 shadow-sm">
        <span class="font-semibold text-slate-900">Xin chào,</span> {{ props.userName }}
      </div>

      <RouterLink
        :to="{ name: 'profile' }"
        class="inline-flex items-center gap-2 rounded-full border border-emerald-200 bg-white px-4 py-1.5 text-xs font-semibold uppercase tracking-wide text-emerald-600 shadow-sm transition hover:border-emerald-300 hover:bg-emerald-50"
      >
        <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" class="h-4 w-4" fill="none" stroke="currentColor" stroke-width="1.8">
          <path stroke-linecap="round" stroke-linejoin="round" d="M5.121 17.804A7 7 0 0 1 12 14a7 7 0 0 1 6.879 3.804M12 13a5 5 0 1 0-5-5 5 5 0 0 0 5 5Z" />
        </svg>
        Hồ sơ
      </RouterLink>

      <button
        class="inline-flex items-center gap-2 rounded-full border border-emerald-200 bg-white px-4 py-2 text-xs font-semibold uppercase tracking-wide text-emerald-600 shadow-sm transition hover:border-emerald-300 hover:bg-emerald-50"
        @click="handleSignOut"
      >
        <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" class="h-4 w-4" fill="none" stroke="currentColor" stroke-width="1.8">
          <path stroke-linecap="round" stroke-linejoin="round" d="m16 17 5-5-5-5m5 5H9m4 5v1a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1V6a1 1 0 0 1 1-1h7a1 1 0 0 1 1 1v1" />
        </svg>
        Đăng xuất
      </button>
    </div>
  </header>
</template>
