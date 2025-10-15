import { computed, ref, watch } from 'vue';
import { defineStore } from 'pinia';
import { login, logout, type LoginPayload } from '@/services/auth.service';
import { setAuthHeader } from '@/services/http';

export interface AuthUser {
  username: string;
  roles: string[];
}

function decodeUserFromToken(token: string): AuthUser | null {
  try {
    const segments = token.split('.');
    if (segments.length < 2) {
      return null;
    }
    const payload = JSON.parse(atob(segments[1] ?? ''));
    const authorities = payload.authorities;
    const roles = Array.isArray(authorities)
      ? authorities
      : typeof authorities === 'string'
        ? authorities.split(',').map((role: string) => role.trim()).filter(Boolean)
        : [];

    return {
      username: payload.sub ?? payload.username ?? '',
      roles,
    };
  } catch (error) {
    console.warn('Failed to decode token payload', error);
    return null;
  }
}

export const useAuthStore = defineStore('auth', () => {
  const token = ref<string | null>(null);
  const user = ref<AuthUser | null>(null);
  const loading = ref(false);
  const error = ref<string | null>(null);

  const isAuthenticated = computed(() => Boolean(token.value));

  watch(token, (newToken) => {
    setAuthHeader(newToken);
    user.value = newToken ? decodeUserFromToken(newToken) : null;
  }, { immediate: true });

  async function signIn(payload: LoginPayload) {
    loading.value = true;
    error.value = null;
    try {
      const response = await login(payload);
      token.value = response.accessToken;
    } catch (err: any) {
      error.value = err?.response?.data?.message ?? 'Unable to sign in.';
      clearSession();
      throw err;
    } finally {
      loading.value = false;
    }
  }

  async function signOut() {
    try {
      await logout();
    } catch (err) {
      console.warn('Logout request failed', err);
    } finally {
      clearSession();
    }
  }

  function clearSession() {
    token.value = null;
    user.value = null;
    error.value = null;
    setAuthHeader(null);
  }

  return {
    token,
    user,
    loading,
    error,
    isAuthenticated,
    signIn,
    signOut,
    clearSession,
  };
}, {
  persist: {
    storage: localStorage,
    pick: ['token', 'user'],
  },
});
