import { computed, ref, watch } from 'vue';

export type ToastType = 'success' | 'error' | 'info' | 'warning';

export interface ToastState {
  type: ToastType;
  message: string;
}

export interface UseToastOptions {
  autoCloseMs?: number;
}

export const useToast = (options: UseToastOptions = {}) => {
  const autoCloseMs = options.autoCloseMs ?? 4500;
  const toast = ref<ToastState | null>(null);
  const visible = computed(() => toast.value !== null);

  let timer: ReturnType<typeof setTimeout> | null = null;

  const clearTimer = () => {
    if (timer) {
      clearTimeout(timer);
      timer = null;
    }
  };

  const hide = () => {
    clearTimer();
    toast.value = null;
  };

  const show = (type: ToastType, message: string, duration = autoCloseMs) => {
    clearTimer();
    toast.value = { type, message };
    if (duration > 0) {
      const scheduledType = type;
      timer = setTimeout(() => {
        if (toast.value?.type === scheduledType) {
          toast.value = null;
        }
        timer = null;
      }, duration);
    }
  };

  watch(
    toast,
    (state) => {
      if (!state) {
        clearTimer();
      }
    },
    { flush: 'post' },
  );

  return {
    toast,
    visible,
    show,
    hide,
  };
};
