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

const navItems = [
  { to: { name: 'dashboard' }, label: 'Tổng quan' },
  { to: { name: 'appointment-requests' }, label: 'Yêu cầu' },
  { to: { name: 'appointments' }, label: 'Lịch hẹn' },
  { to: { name: 'patients' }, label: 'Bệnh nhân' },
  { to: { name: 'staff' }, label: 'Nhân viên' },
  { to: { name: 'clinic-rooms' }, label: 'Phòng khám' },
  { to: { name: 'services' }, label: 'Dịch vụ' },
];

const isActive = (to: { name?: string }) => {
  if (!to.name) return false;
  return route.name === to.name || route.path.startsWith(String((router.resolve(to).href ?? '').replace('#', '')));
};
</script>

<template>
  <header class="border-b border-emerald-100 bg-white/85 backdrop-blur">
    <div class=" flex w-full px-8 flex-col gap-4 px-6 py-4 lg:flex-row lg:items-center lg:justify-between">
      <div class="flex items-center gap-4">
        <button
          class="flex items-center gap-3 rounded-full border border-transparent px-3 py-1 transition hover:border-emerald-200"
          @click="router.push({ name: 'dashboard' })"
        >
          <div class="text-left">
            <img :src="logoUrl" alt="Duyên Hạnh Clinic" class="h-12 w-auto rounded-full ring-1 ring-emerald-100" />
            <p class="text-sm font-semibold text-slate-900 text-center">Cổng quản trị</p>
          </div>
        </button>

        <nav class="hidden items-center gap-3 rounded-full border border-emerald-100 bg-emerald-50/60 px-1 py-2 text-sm font-semibold text-emerald-600 lg:flex">
          <RouterLink
            v-for="item in navItems"
            :key="String(item.label)"
            :to="item.to"
            class="rounded-full px-3 py-1 transition"
            :class="isActive(item.to) ? 'bg-white text-emerald-700 shadow-sm' : 'hover:bg-white/70 hover:text-emerald-700'"
          >
            {{ item.label }}
          </RouterLink>
        </nav>
      </div>
      <div class="rounded-2xl border border-emerald-100 bg-white/80 px-4 py-2 text-sm text-slate-600 shadow-sm">
            <span class="font-semibold text-slate-900">Xin chào,</span> {{ props.userName }}
      </div>

      <div class="flex flex-wrap items-center justify-between gap-3 lg:justify-end">
        <div class="flex flex-col-reverse items-end gap-2 text-sm text-slate-600">
          <div class="flex items-center gap-2">
            <RouterLink
              :to="{ name: 'profile' }"
              class="inline-flex items-center gap-2 rounded-full border border-emerald-200 bg-white px-4 py-1.5 text-xs font-semibold uppercase tracking-wide text-emerald-600 shadow-sm transition hover:border-emerald-300 hover:bg-emerald-50"
            >
              <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" class="h-4 w-4" fill="none" stroke="currentColor" stroke-width="1.8">
                <path stroke-linecap="round" stroke-linejoin="round" d="M5.121 17.804A7 7 0 0 1 12 14a7 7 0 0 1 6.879 3.804M12 13a5 5 0 1 0-5-5 5 5 0 0 0 5 5Z" />
              </svg>
              Trang cá nhân
            </RouterLink>
          </div>
        </div>
        <button
          class="inline-flex items-center gap-2 rounded-full border border-emerald-200 bg-white px-4 py-2 text-xs font-semibold uppercase tracking-wide text-emerald-600 shadow-sm transition hover:border-emerald-300 hover:bg-emerald-50 lg:ml-4"
          @click="emit('sign-out')"
        >
          <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" class="h-4 w-4" fill="none" stroke="currentColor" stroke-width="1.8">
            <path stroke-linecap="round" stroke-linejoin="round" d="m16 17 5-5-5-5m5 5H9m4 5v1a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1V6a1 1 0 0 1 1-1h7a1 1 0 0 1 1 1v1" />
          </svg>
          Đăng xuất
        </button>
      </div>
    </div>
  </header>
</template>
