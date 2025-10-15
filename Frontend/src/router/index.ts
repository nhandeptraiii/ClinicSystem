import { createRouter, createWebHistory } from 'vue-router';
import DashboardView from '@/views/dashboard/DashboardView.vue';
import LoginView from '@/views/auth/LoginView.vue';
import { useAuthStore } from '@/stores/authStore';

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/login',
      name: 'login',
      component: LoginView,
      meta: { public: true },
    },
    {
      path: '/',
      name: 'dashboard',
      component: DashboardView,
    },
  ],
});

router.beforeEach((to, _from, next) => {
  const authStore = useAuthStore();

  if (!to.meta.public && !authStore.isAuthenticated) {
    next({ name: 'login', query: { redirect: to.fullPath } });
    return;
  }

  if (to.name === 'login' && authStore.isAuthenticated) {
    next({ name: 'dashboard' });
    return;
  }

  next();
});

export default router;
