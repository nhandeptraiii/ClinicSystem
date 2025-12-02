import { createRouter, createWebHistory } from 'vue-router';
import LoginView from '@/views/auth/LoginView.vue';
import HomeView from '@/views/home/HomeView.vue';
import AppointmentRequestsView from '@/views/dashboard/AppointmentRequestsView.vue';
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
    { path: '/diagnosis', name: 'diagnosis', component: () => import('@/views/public/DiagnosisView.vue'), meta: { public: true } },
    {
      path: '/dashboard',
      component: () => import('@/views/dashboard/DashboardView.vue'),
      meta: { requiresAuth: true },
      children: [
        { path: '', name: 'dashboard-home', component: () => import('@/views/dashboard/DashboardHomeView.vue') },
        { path: 'appointment-requests', name: 'appointment-requests', component: AppointmentRequestsView, meta: { roles: ['ADMIN', 'RECEPTIONIST', 'CASHIER'] } },
        { path: 'patients', name: 'patients', component: () => import('@/views/dashboard/PatientsView.vue'), meta: { roles: ['ADMIN', 'DOCTOR', 'RECEPTIONIST'] } },
        { path: 'staffs', name: 'staffs', component: () => import('@/views/dashboard/StaffManagementView.vue'), meta: { roles: ['ADMIN'] } },
        { path: 'clinic-rooms', name: 'clinic-rooms', component: () => import('@/views/dashboard/ClinicRoomsView.vue'), meta: { roles: ['ADMIN'] } },
        { path: 'schedules', name: 'schedules', component: () => import('@/views/dashboard/SchedulesView.vue'), meta: { roles: ['ADMIN'] } },
        { path: 'visits', name: 'visits', component: () => import('@/views/dashboard/VisitsView.vue'), meta: { roles: ['ADMIN', 'DOCTOR', 'RECEPTIONIST'] } },
        { path: 'visits/:id', name: 'visit-detail', component: () => import('@/views/dashboard/VisitDetailView.vue'), meta: { roles: ['ADMIN', 'DOCTOR', 'RECEPTIONIST'] } },
        { path: 'doctor/visits', name: 'doctor-visits', component: () => import('@/views/dashboard/VisitsView.vue'), meta: { roles: ['DOCTOR'] } },
        { path: 'medications', name: 'medications', component: () => import('@/views/dashboard/MedicationsView.vue'), meta: { roles: ['ADMIN', 'PHARMACIST'] } },
        { path: 'diseases', name: 'diseases', component: () => import('@/views/dashboard/DiseasesView.vue'), meta: { roles: ['ADMIN', 'DOCTOR'] } },
        { path: 'services', name: 'services', component: () => import('@/views/dashboard/ServicesView.vue'), meta: { roles: ['ADMIN'] } },
        { path: 'indicator-templates', name: 'indicator-templates', component: () => import('@/views/dashboard/IndicatorTemplatesView.vue'), meta: { roles: ['ADMIN'] } },
        { path: 'billing', name: 'billing', component: () => import('@/views/dashboard/BillingView.vue'), meta: { roles: ['ADMIN', 'RECEPTIONIST'] } },
        { path: 'analytics', name: 'analytics', component: () => import('@/views/dashboard/AnalyticsView.vue'), meta: { roles: ['ADMIN'] } },
        { path: 'profile', name: 'profile', component: () => import('@/views/dashboard/ProfileView.vue') },
      ],
    },
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

  const requiresAuth = to.matched.some((record) => record.meta.requiresAuth);
  const requiredRoles = to.matched
    .flatMap((record) => (record.meta && (record.meta as any).roles ? (record.meta as any).roles : []))
    .filter((role, index, arr) => arr.indexOf(role) === index);

  if (requiresAuth && !authStore.isAuthenticated) {
    next({ name: 'login', query: { redirect: to.fullPath } });
    return;
  }

  if (requiredRoles.length > 0 && !authStore.hasRole(requiredRoles as string[])) {
    next({ name: 'not-found' });
    return;
  }

  if (to.name === 'login' && authStore.isAuthenticated) {
    next({ name: 'dashboard-home' });
    return;
  }

  next();
});

export default router;
