<script setup lang="ts">
import { useAuthStore } from '@/stores/authStore';

const authStore = useAuthStore();
</script>

<template>
  <div class="min-h-screen bg-slate-100">
    <header class="border-b border-slate-200 bg-white">
      <div class="mx-auto flex max-w-6xl items-center justify-between px-6 py-4">
        <div>
          <h1 class="text-xl font-semibold text-slate-900">Clinic Dashboard</h1>
          <p class="text-sm text-slate-500">Welcome, {{ authStore.user?.username ?? 'User' }}</p>
        </div>
        <button
          class="rounded-lg bg-rose-500 px-4 py-2 text-sm font-semibold text-white shadow-sm hover:bg-rose-600"
          @click="authStore.signOut()"
        >
          Sign out
        </button>
      </div>
    </header>

    <main class="mx-auto max-w-6xl px-6 py-10">
      <div class="grid gap-6 rounded-2xl bg-white p-6 shadow">
        <h2 class="text-lg font-semibold text-slate-900">Overview</h2>
        <p class="text-sm text-slate-600">
          This is a placeholder dashboard. Build your appointment, visit, inventory, and billing screens here using
          the existing backend APIs.
        </p>
      </div>
    </main>
  </div>
</template>
