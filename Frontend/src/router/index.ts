import { createRouter, createWebHistory } from 'vue-router';
import DashboardView from '@/views/dashboard/DashboardView.vue';
import LoginView from '@/views/auth/LoginView.vue';
import HomeView from '@/views/home/HomeView.vue';
import { useAuthStore } from '@/stores/authStore';

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  scrollBehavior(_to, _from, savedPosition) {
    if (savedPosition) {
      return savedPosition;
    }
    return { top: 0 };
  },
  routes: [
    {
      path: '/login',
      name: 'login',
      component: LoginView,
      meta: { public: true },
    },
    {
      path: '/',
      name: 'home',
      component: HomeView,
      meta: { public: true },
    },
    { path: '/about', name: 'about', component: () => import('@/views/public/AboutView.vue'), meta: { public: true } },
    { path: '/specialties', name: 'specialties', component: () => import('@/views/public/SpecialtiesView.vue'), meta: { public: true } },
    { path: '/booking', name: 'booking', component: () => import('@/views/public/BookingView.vue'), meta: { public: true } },
    {
      path: '/dashboard',
      name: 'dashboard',
      component: DashboardView,
    },
    { path: '/dashboard/appointment-requests', name: 'appointment-requests', component: () => import('@/views/dashboard/AppointmentRequestsView.vue') },
    { path: '/dashboard/appointments', name: 'appointments', component: () => import('@/views/dashboard/AppointmentsView.vue') },
    { path: '/dashboard/patients', name: 'patients', component: () => import('@/views/dashboard/PatientsView.vue') },
    { path: '/dashboard/doctors', name: 'doctors', component: () => import('@/views/dashboard/DoctorsView.vue') },
    { path: '/dashboard/schedules', name: 'schedules', component: () => import('@/views/dashboard/SchedulesView.vue') },
    { path: '/dashboard/visits', name: 'visits', component: () => import('@/views/dashboard/VisitsView.vue') },
    { path: '/dashboard/medications', name: 'medications', component: () => import('@/views/dashboard/MedicationsView.vue') },
    { path: '/dashboard/services', name: 'services', component: () => import('@/views/dashboard/ServicesView.vue') },
    { path: '/dashboard/billing', name: 'billing', component: () => import('@/views/dashboard/BillingView.vue') },
    // Catch-all 404 route must be last
    {
      path: '/:pathMatch(.*)*',
      name: 'not-found',
      component: () => import('@/views/public/NotFoundView.vue'),
      meta: { public: true },
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
