<script setup lang="ts">
import { computed } from 'vue';
import { useRoute } from 'vue-router';
import {
  BanknotesIcon,
  BeakerIcon,
  BuildingOffice2Icon,
  CalendarIcon,
  CalendarDaysIcon,
  ClipboardDocumentListIcon,
  ClipboardDocumentCheckIcon,
  ClipboardDocumentIcon,
  ChartBarSquareIcon,
  HomeIcon,
  Squares2X2Icon,
  HeartIcon,
  UsersIcon,
  UserGroupIcon,
} from '@heroicons/vue/24/outline';
import { useAuthStore } from '@/stores/authStore';

const route = useRoute();
const authStore = useAuthStore();

const menuItems = [
  { label: 'Tổng quan', to: 'dashboard-home', icon: HomeIcon, roles: ['ADMIN', 'DOCTOR', 'RECEPTIONIST', 'PHARMACIST'] },
  { label: 'Yêu cầu đặt lịch', to: 'appointment-requests', icon: CalendarIcon, roles: ['RECEPTIONIST', 'ADMIN'] },
  { label: 'Khám bệnh', to: 'visits', icon: ClipboardDocumentListIcon, roles: ['RECEPTIONIST', 'ADMIN', 'DOCTOR'] },
  { label: 'Hàng chờ phát thuốc', to: 'prescription-queue', icon: ClipboardDocumentCheckIcon, roles: ['ADMIN', 'DOCTOR', 'PHARMACIST'] },
  { label: 'Thu ngân', to: 'billing', icon: BanknotesIcon, roles: ['RECEPTIONIST', 'ADMIN'] },
  { label: 'Bệnh nhân', to: 'patients', icon: UsersIcon, roles: ['ADMIN', 'DOCTOR', 'RECEPTIONIST'] },
  { label: 'Quản lý nhân viên', to: 'staffs', icon: UserGroupIcon, roles: ['ADMIN'] },
  { label: 'Quản lý phòng khám', to: 'clinic-rooms', icon: BuildingOffice2Icon, roles: ['ADMIN'] },
  { label: 'Quản lý dịch vụ khám', to: 'services', icon: ClipboardDocumentIcon, roles: ['ADMIN'] },
  { label: 'Quản lý mẫu chỉ số', to: 'indicator-templates', icon: Squares2X2Icon, roles: ['ADMIN'] },
  { label: 'Lịch làm việc', to: 'schedules', icon: CalendarDaysIcon, roles: ['ADMIN'] },
  { label: 'Danh mục bệnh', to: 'diseases', icon: HeartIcon, roles: ['ADMIN', 'DOCTOR'] },
  { label: 'Kho thuốc', to: 'medications', icon: BeakerIcon, roles: ['PHARMACIST', 'ADMIN'] },
  { label: 'Thống kê', to: 'analytics', icon: ChartBarSquareIcon, roles: ['ADMIN'] },
];

const visibleMenuItems = computed(() => menuItems.filter((item) => authStore.hasRole(item.roles)));
const isActive = (name: string) => route.name === name;
</script>

<template>
  <aside class="fixed left-0 top-16 z-30 h-[calc(100vh-64px)] w-64 border-r border-slate-200 bg-white">
    <nav class="flex flex-col gap-1 p-4">
      <RouterLink
        v-for="item in visibleMenuItems"
        :key="item.to"
        :to="{ name: item.to }"
        class="flex items-center gap-3 rounded-xl px-3 py-2 text-sm font-semibold text-slate-600 transition hover:bg-emerald-50 hover:text-emerald-700"
        :class="isActive(item.to) ? 'border-r-4 border-emerald-600 bg-emerald-50 text-emerald-700 pr-2 shadow-sm' : ''"
      >
        <component :is="item.icon" class="h-5 w-5" />
        <span>{{ item.label }}</span>
      </RouterLink>
    </nav>
  </aside>
</template>
