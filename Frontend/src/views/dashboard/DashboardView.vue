<script setup lang="ts">
import { computed } from 'vue';
import { useRouter } from 'vue-router';
import AdminHeader from '@/components/AdminHeader.vue';
import AdminSidebar from '@/components/AdminSidebar.vue';
import { useAuthStore } from '@/stores/authStore';

const authStore = useAuthStore();
const router = useRouter();

const userName = computed(() => authStore.user?.username ?? 'Quản trị viên');

const handleSignOut = async () => {
  await authStore.signOut();
  router.replace({ name: 'home' });
};
</script>

<template>
  <div class="min-h-screen bg-slate-50">
    <AdminHeader :user-name="userName" @sign-out="handleSignOut" />

    <div class="flex pt-16">
      <AdminSidebar />
      <main class="ml-64 min-h-[calc(100vh-64px)] w-full bg-slate-50 p-6">
        <router-view />
      </main>
    </div>
  </div>
</template>
