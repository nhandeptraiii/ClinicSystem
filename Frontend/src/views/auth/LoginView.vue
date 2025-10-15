<script setup lang="ts">
import { ref } from 'vue';
import { useRouter, useRoute } from 'vue-router';
import { useAuthStore } from '@/stores/authStore';

const authStore = useAuthStore();
const router = useRouter();
const route = useRoute();

const form = ref({
  username: '',
  password: '',
});

const showPassword = ref(false);

const handleSubmit = async () => {
  if (authStore.loading) return;

  try {
    await authStore.signIn({
      username: form.value.username,
      password: form.value.password,
    });
    const redirectPath = (route.query.redirect as string) ?? '/';
    router.replace(redirectPath);
  } catch (error) {
    // error state is handled inside store
  }
};
</script>

<template>
  <div class="flex min-h-screen items-center justify-center bg-slate-100 px-4 py-12">
    <div class="w-full max-w-md rounded-2xl bg-white p-8 shadow-xl">
      <h1 class="mb-6 text-2xl font-semibold text-slate-900">Clinic System</h1>
      <p class="mb-8 text-sm text-slate-500">
        Ðãng nh?p ð? truy c?p h? th?ng qu?n l? ph?ng khám.
      </p>

      <form class="space-y-6" @submit.prevent="handleSubmit">
        <div>
          <label class="mb-1 block text-sm font-medium text-slate-700" for="username">Tên ðãng nh?p</label>
          <input
            id="username"
            v-model="form.username"
            type="text"
            required
            autocomplete="username"
            class="w-full rounded-lg border border-slate-200 px-4 py-2.5 text-sm text-slate-900 shadow-sm focus:border-blue-500 focus:outline-none focus:ring-2 focus:ring-blue-100"
            placeholder="example@clinic.local"
          />
        </div>

        <div>
          <label class="mb-1 block text-sm font-medium text-slate-700" for="password">M?t kh?u</label>
          <div class="relative">
            <input
              id="password"
              v-model="form.password"
              :type="showPassword ? 'text' : 'password'"
              required
              autocomplete="current-password"
              class="w-full rounded-lg border border-slate-200 px-4 py-2.5 pr-12 text-sm text-slate-900 shadow-sm focus:border-blue-500 focus:outline-none focus:ring-2 focus:ring-blue-100"
              placeholder="••••••••"
            />
            <button
              type="button"
              class="absolute inset-y-0 right-0 flex items-center px-3 text-xs text-slate-500"
              @click="showPassword = !showPassword"
            >
              {{ showPassword ? '?n' : 'Hi?n' }}
            </button>
          </div>
        </div>

        <button
          type="submit"
          class="flex w-full items-center justify-center rounded-lg bg-blue-600 px-4 py-2.5 text-sm font-semibold text-white shadow hover:bg-blue-700 focus:outline-none focus:ring-2 focus:ring-blue-100"
          :disabled="authStore.loading"
        >
          <span v-if="!authStore.loading">Ðãng nh?p</span>
          <span v-else>Ðang x? l?...</span>
        </button>

        <p v-if="authStore.error" class="text-sm text-rose-600">
          {{ authStore.error }}
        </p>
      </form>
    </div>
  </div>
</template>
