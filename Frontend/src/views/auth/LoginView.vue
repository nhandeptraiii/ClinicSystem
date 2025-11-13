<script setup lang="ts">
import { onBeforeUnmount, ref } from "vue";
import { useRouter, useRoute } from "vue-router";
import { useAuthStore } from "@/stores/authStore";
import loginBackground from "@/assets/DangNhap/LoginBackground.png";

const authStore = useAuthStore();
const router = useRouter();
const route = useRoute();

const form = ref({
  username: "",
  password: "",
});

const showPassword = ref(false);
type ToastType = "success" | "error";
type ToastState = { type: ToastType; message: string };
const toast = ref<ToastState | null>(null);
let toastTimer: ReturnType<typeof setTimeout> | null = null;
let redirectTimer: ReturnType<typeof setTimeout> | null = null;

const clearToastTimer = () => {
  if (toastTimer) {
    clearTimeout(toastTimer);
    toastTimer = null;
  }
};

const showToast = (type: ToastType, message: string, duration = 4000) => {
  clearToastTimer();
  toast.value = { type, message };
  toastTimer = setTimeout(() => {
    toast.value = null;
    toastTimer = null;
  }, duration);
};

const dismissToast = () => {
  clearToastTimer();
  toast.value = null;
};

onBeforeUnmount(() => {
  clearToastTimer();
  if (redirectTimer) {
    clearTimeout(redirectTimer);
    redirectTimer = null;
  }
});

const extractErrorMessage = (input: unknown): string | null => {
  if (!input || typeof input !== "object") {
    return null;
  }
  const maybeAxiosError = input as {
    message?: string;
    response?: { data?: { message?: unknown; error?: string } };
  };
  const serverMessage = maybeAxiosError.response?.data?.message;
  if (typeof serverMessage === "string" && serverMessage.trim()) {
    return serverMessage;
  }
  if (Array.isArray(serverMessage) && serverMessage.length > 0) {
    const first = serverMessage[0];
    if (typeof first === "string" && first.trim()) {
      return first;
    }
  }
  const errorField = maybeAxiosError.response?.data?.error;
  if (typeof errorField === "string" && errorField.trim()) {
    return errorField;
  }
  const fallbackMessage = maybeAxiosError.message;
  if (typeof fallbackMessage === "string" && fallbackMessage.trim()) {
    return fallbackMessage;
  }
  return null;
};

const extractErrorCode = (input: unknown): string | null => {
  if (!input || typeof input !== "object") {
    return null;
  }
  const maybeAxiosError = input as { response?: { data?: { error?: string } } };
  const code = maybeAxiosError.response?.data?.error;
  return typeof code === "string" && code.trim() ? code : null;
};

const disabledAccountMessages = new Set([
  "Tài khoản đang tạm ngưng. Vui lòng liên hệ quản trị viên.",
  "Tài khoản đã bị khóa. Vui lòng liên hệ quản trị viên.",
]);

const handleSubmit = async () => {
  if (authStore.loading) return;
  try {
    await authStore.signIn({
      username: form.value.username,
      password: form.value.password,
    });
    showToast("success", "Đăng nhập thành công! Đang chuyển hướng...", 2500);
    const redirectPath = (route.query.redirect as string) ?? "/dashboard";
    redirectTimer = setTimeout(() => {
      router.replace(redirectPath);
    }, 1000);
  } catch (error) {
    const serverMessage = extractErrorMessage(error) ?? authStore.error ?? "";
    const errorCode = extractErrorCode(error);
    const isDisabledAccount =
      errorCode === "DisabledAccount" ||
      (serverMessage && disabledAccountMessages.has(serverMessage));
    const message = isDisabledAccount
      ? (serverMessage || "Tài khoản đang tạm ngưng. Vui lòng liên hệ quản trị viên.")
      : "Đăng nhập thất bại. Kiểm tra lại tài khoản và mật khẩu.";
    showToast("error", message, 4500);
  }
};
</script>

<template>
  <div
    class="relative flex min-h-screen bg-slate-900/80"
    :style="{ backgroundImage: `url(${loginBackground})`}"
  >
    <div class="absolute inset-0 bg-gradient-to-r from-teal-900/70 via-teal-800/55 to-emerald-900/30 mix-blend-multiply"></div>

    <div class="relative z-10 mx-auto flex w-full max-w-6xl flex-col justify-center px-6 py-16 lg:flex-row lg:items-center lg:justify-between lg:px-10">
      <div class="mt-10 w-full max-w-md rounded-[28px] border border-emerald-200/60 bg-white/95 p-8 shadow-[0_25px_65px_-35px_rgba(13,148,136,0.65)] backdrop-blur md:mt-0 lg:ml-auto">
        <div class="mb-6">
          <h2 class="text-2xl font-semibold text-slate-900">Đăng nhập hệ thống</h2>
          <p class="mt-1 text-sm text-slate-500">Sử dụng tài khoản được cấp để truy cập vào trang quản trị.</p>
        </div>

        <form class="space-y-6" @submit.prevent="handleSubmit">
          <div>
            <label class="mb-1 block text-sm font-semibold text-slate-700" for="username">Tên đăng nhập</label>
            <input
              id="username"
              v-model="form.username"
              type="text"
              required
              autocomplete="username"
              class="w-full rounded-xl border border-emerald-100 bg-white px-4 py-3 text-sm text-slate-900 shadow-sm transition focus:border-emerald-400 focus:outline-none focus:ring-4 focus:ring-emerald-100/80"
              placeholder="example@clinicsystem.com"
            />
          </div>

          <div>
            <label class="mb-1 block text-sm font-semibold text-slate-700" for="password">Mật khẩu</label>
            <div class="relative">
              <input
                id="password"
                v-model="form.password"
                :type="showPassword ? 'text' : 'password'"
                required
                autocomplete="current-password"
                class="w-full rounded-xl border border-emerald-100 bg-white px-4 py-3 pr-12 text-sm text-slate-900 shadow-sm transition focus:border-emerald-400 focus:outline-none focus:ring-4 focus:ring-emerald-100/80"
                placeholder="••••••••"
              />
            </div>
          </div>

          <button
            type="submit"
            class="flex w-full items-center justify-center rounded-full bg-emerald-600 px-6 py-3 text-sm font-semibold uppercase tracking-wide text-white shadow-sm transition hover:bg-emerald-700 focus:outline-none focus:ring-4 focus:ring-emerald-100 disabled:opacity-60"
            :disabled="authStore.loading"
          >
            <span v-if="!authStore.loading">Đăng nhập</span>
            <span v-else>Đang xử lý...</span>
          </button>

          <p v-if="authStore.error" class="rounded-lg border border-rose-200 bg-rose-50/80 px-4 py-3 text-sm text-rose-600">
            {{ authStore.error }}
          </p>
        </form>
      </div>
    </div>
  </div>

  <Teleport to="body">
    <Transition
      enter-active-class="transition duration-200"
      enter-from-class="translate-y-2 opacity-0"
      enter-to-class="translate-y-0 opacity-100"
      leave-active-class="transition duration-200"
      leave-from-class="translate-y-0 opacity-100"
      leave-to-class="translate-y-2 opacity-0"
    >
      <div
        v-if="toast"
        class="fixed top-6 right-6 z-[70] w-[min(320px,90vw)] rounded-2xl border px-5 py-4 shadow-xl backdrop-blur"
        :class="toast.type === 'success' ? 'border-emerald-200 bg-emerald-50/95 text-emerald-800' : 'border-rose-200 bg-rose-50/95 text-rose-700'"
      >
        <div class="flex items-start gap-3">
          <span
            class="mt-0.5 flex h-9 w-9 flex-shrink-0 items-center justify-center rounded-full"
            :class="toast.type === 'success' ? 'bg-emerald-100 text-emerald-600' : 'bg-rose-100 text-rose-600'"
          >
            <svg v-if="toast.type === 'success'" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" class="h-5 w-5" fill="none" stroke="currentColor" stroke-width="2">
              <path stroke-linecap="round" stroke-linejoin="round" d="m5 13 4 4L19 7" />
            </svg>
            <svg v-else xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" class="h-5 w-5" fill="none" stroke="currentColor" stroke-width="2">
              <path stroke-linecap="round" stroke-linejoin="round" d="m15 9-6 6m0-6 6 6" />
            </svg>
          </span>
          <div class="flex-1">
            <p class="text-sm font-semibold">
              {{ toast.type === 'success' ? 'Đăng nhập thành công' : 'Đăng nhập thất bại' }}
            </p>
            <p class="mt-1 text-sm leading-relaxed">{{ toast.message }}</p>
          </div>
          <button
            type="button"
            class="mt-1 flex h-8 w-8 items-center justify-center rounded-full bg-white/70 text-slate-500 transition hover:bg-white hover:text-slate-700"
            @click="dismissToast"
            aria-label="Đóng thông báo"
          >
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" class="h-4 w-4" fill="none" stroke="currentColor" stroke-width="2">
              <path stroke-linecap="round" stroke-linejoin="round" d="m16 8-8 8m0-8 8 8" />
            </svg>
          </button>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>
