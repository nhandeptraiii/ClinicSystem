<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import AdminHeader from '@/components/AdminHeader.vue';
import { useAuthStore } from '@/stores/authStore';
import { fetchAppointments, type AppointmentSummary } from '@/services/appointment.service';
import { fetchAppointmentRequests } from '@/services/appointmentRequest.service';

const authStore = useAuthStore();
const router = useRouter();

const userName = computed(() => authStore.user?.username ?? 'Quản trị viên');

const todayAppointmentCount = ref<number | null>(null);
const todaysConfirmedCount = ref<number | null>(null);
const todaysCheckedInCount = ref<number | null>(null);
const pendingRequestCount = ref<number | null>(null);
const summaryLoading = ref(false);
const summaryError = ref<string | null>(null);

const handleSignOut = async () => {
  await authStore.signOut();
  router.replace({ name: 'home' });
};

const loadDashboardSummary = async () => {
  summaryLoading.value = true;
  summaryError.value = null;
  try {
    const [appointmentsResponse, pendingRequestsResponse] = await Promise.all([
      fetchAppointments(),
      fetchAppointmentRequests({ status: 'PENDING' }),
    ]);

    const appointments = Array.isArray(appointmentsResponse) ? appointmentsResponse : [];
    const pendingRequests = Array.isArray(pendingRequestsResponse) ? pendingRequestsResponse : [];

    if (!Array.isArray(appointmentsResponse)) {
      console.warn('Unexpected appointments payload:', appointmentsResponse);
    }
    if (!Array.isArray(pendingRequestsResponse)) {
      console.warn('Unexpected appointmentRequests payload:', pendingRequestsResponse);
    }

    computeAppointmentMetrics(appointments);
    pendingRequestCount.value = pendingRequests.length;
  } catch (error: any) {
    console.error('Failed to load dashboard summary', error);
    const responseData = error?.response?.data;
    if (typeof responseData === 'string') {
      summaryError.value = responseData;
    } else if (responseData?.message) {
      summaryError.value = responseData.message;
    } else if (error?.message) {
      summaryError.value = error.message;
    } else {
      summaryError.value = 'Không thể tải dữ liệu tổng quan.';
    }
    todayAppointmentCount.value = null;
    todaysConfirmedCount.value = null;
    todaysCheckedInCount.value = null;
    pendingRequestCount.value = null;
  } finally {
    summaryLoading.value = false;
  }
};

const computeAppointmentMetrics = (appointments: AppointmentSummary[]) => {
  if (!Array.isArray(appointments)) {
    todayAppointmentCount.value = null;
    todaysConfirmedCount.value = null;
    todaysCheckedInCount.value = null;
    return;
  }
  const now = new Date();
  const todaysAppointments = appointments.filter((appointment) => {
    if (!appointment.scheduledAt) return false;
    const scheduledAt = new Date(appointment.scheduledAt);
    return (
      scheduledAt.getUTCFullYear() === now.getUTCFullYear() &&
      scheduledAt.getUTCMonth() === now.getUTCMonth() &&
      scheduledAt.getUTCDate() === now.getUTCDate()
    );
  });

  todayAppointmentCount.value = todaysAppointments.length;
  todaysConfirmedCount.value = todaysAppointments.filter((appointment) => appointment.status === 'CONFIRMED').length;
  todaysCheckedInCount.value = todaysAppointments.filter((appointment) => appointment.status === 'CHECKED_IN').length;
};

onMounted(() => {
  loadDashboardSummary();
});
</script>

<template>
  <div class="relative min-h-screen bg-gradient-to-br from-emerald-50 via-white to-white">
    <div class="pointer-events-none absolute inset-0 overflow-hidden">
      <div class="absolute -top-32 -right-48 h-96 w-96 rounded-full bg-emerald-200/30 blur-3xl"></div>
      <div class="absolute -bottom-28 -left-32 h-96 w-96 rounded-full bg-teal-200/30 blur-3xl"></div>
    </div>

    <AdminHeader :user-name="userName" @sign-out="handleSignOut" />
    <main class="relative mx-auto max-w-6xl px-6 py-12">
      <section class="grid gap-6 rounded-[32px] border border-emerald-100 bg-white/90 p-8 shadow-[0_24px_60px_-40px_rgba(13,148,136,0.6)] lg:grid-cols-[1.2fr_0.9fr]">
        <div>
          <h2 class="text-2xl font-semibold text-slate-900">Tổng quan hoạt động</h2>
          <p class="mt-3 text-sm leading-relaxed text-slate-600">
            Theo dõi lịch hẹn trong ngày, yêu cầu đặt lịch mới, tiến trình khám chữa bệnh và điều phối nhân sự nhanh chóng.
            Sử dụng các phân hệ bên phải để truy cập chi tiết.
          </p>

          <div class="mt-8 grid gap-4 sm:grid-cols-2">
            <div class="rounded-2xl border border-emerald-100 bg-emerald-50/60 p-4 text-emerald-700">
              <p class="text-xs font-semibold uppercase tracking-widest">Lịch hẹn hôm nay</p>
              <p class="mt-2 text-2xl font-semibold">
                <span v-if="summaryLoading">--</span>
                <span v-else>{{ todayAppointmentCount ?? 0 }}</span>
              </p>
              <p class="mt-2 text-xs text-emerald-600/80">
                <span v-if="summaryLoading">Đang tải dữ liệu lịch hẹn...</span>
                <span v-else-if="(todaysConfirmedCount ?? 0) + (todaysCheckedInCount ?? 0) > 0">
                  {{ todaysConfirmedCount ?? 0 }} đã xác nhận · {{ todaysCheckedInCount ?? 0 }} đang khám
                </span>
                <span v-else>Chưa có lịch hẹn trong ngày</span>
              </p>
            </div>
            <div class="rounded-2xl border border-emerald-100 bg-white p-4 text-slate-800 shadow-sm">
              <p class="text-xs font-semibold uppercase tracking-widest text-emerald-500">Yêu cầu đặt lịch cần xử lý</p>
              <p class="mt-2 text-2xl font-semibold">
                <span v-if="summaryLoading">--</span>
                <span v-else>{{ pendingRequestCount ?? 0 }}</span>
              </p>
              <p class="mt-2 text-xs text-slate-500">
                <span v-if="summaryLoading">Đang tải dữ liệu yêu cầu...</span>
                <span v-else-if="(pendingRequestCount ?? 0) > 0">
                  {{ pendingRequestCount }} yêu cầu đang chờ duyệt
                </span>
                <span v-else>Không có yêu cầu chờ xử lý</span>
              </p>
            </div>
          </div>
          <div v-if="summaryError" class="mt-4 rounded-xl border border-rose-200 bg-rose-50/80 px-4 py-3 text-sm text-rose-600">
            {{ summaryError }}
          </div>
        </div>

        <aside class="grid gap-4">
          <article class="rounded-2xl border border-emerald-100 bg-white p-5 shadow-sm">
            <p class="text-xs font-semibold uppercase tracking-[0.35em] text-emerald-500">Trợ giúp nhanh</p>
            <h3 class="mt-2 text-lg font-semibold text-slate-900">Theo dõi phòng khám mỗi ngày</h3>
            <ul class="mt-4 space-y-3 text-sm text-slate-600">
              <li class="flex items-start gap-2">
                <span class="mt-1 inline-flex h-2 w-2 flex-shrink-0 rounded-full bg-emerald-500"></span>
                Kiểm tra danh sách lịch hẹn mới và xác nhận nhân sự tiếp nhận
              </li>
              <li class="flex items-start gap-2">
                <span class="mt-1 inline-flex h-2 w-2 flex-shrink-0 rounded-full bg-emerald-500"></span>
                Theo dõi tiến độ khám từng bệnh nhân và cập nhật phiếu điều trị
              </li>
              <li class="flex items-start gap-2">
                <span class="mt-1 inline-flex h-2 w-2 flex-shrink-0 rounded-full bg-emerald-500"></span>
                Hoàn tất ghi nhận thanh toán, kết quả xét nghiệm và hồ sơ điện tử
              </li>
            </ul>
          </article>
          <article class="rounded-2xl border border-emerald-100 bg-emerald-50/70 p-5 text-emerald-800">
            <h3 class="text-sm font-semibold uppercase tracking-wide">Hotline nội bộ</h3>
            <p class="mt-2 text-2xl font-semibold">1900 363636</p>
            <p class="mt-1 text-xs text-emerald-700/80">Hỗ trợ điều phối ca trực, kỹ thuật hệ thống và trường hợp khẩn cấp.</p>
          </article>
        </aside>
      </section>

      <section class="mt-10 grid gap-5 rounded-[28px] border border-emerald-100 bg-white/90 p-8 shadow-[0_24px_60px_-45px_rgba(13,148,136,0.55)]">
        <div class="flex flex-col gap-4 md:flex-row md:items-center md:justify-between">
          <div>
            <h2 class="text-xl font-semibold text-slate-900">Phân hệ quản lý</h2>
            <p class="mt-1 text-sm text-slate-500">Chọn phân hệ bên dưới để bắt đầu điều hành.</p>
          </div>
          <RouterLink
            to="/booking"
            class="inline-flex items-center gap-2 rounded-full border border-emerald-200 bg-emerald-50 px-5 py-2 text-xs font-semibold uppercase tracking-wide text-emerald-600 transition hover:border-emerald-400 hover:bg-emerald-100"
          >
            Xem trang đặt lịch
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" class="h-4 w-4" fill="none" stroke="currentColor" stroke-width="1.8">
              <path stroke-linecap="round" stroke-linejoin="round" d="M9 5l7 7-7 7" />
            </svg>
          </RouterLink>
        </div>

        <div class="grid grid-cols-1 gap-4 md:grid-cols-2 lg:grid-cols-3">
          <RouterLink
            v-for="item in [
              { to: '/dashboard/appointment-requests', title: 'Yêu cầu đặt lịch', desc: 'Duyệt và phân luồng yêu cầu từ trang chủ.' },
              { to: '/dashboard/appointments', title: 'Lịch hẹn', desc: 'Tạo, cập nhật, sắp xếp phòng khám & bác sĩ.' },
              { to: '/dashboard/patients', title: 'Bệnh nhân', desc: 'Theo dõi hồ sơ, lịch sử và tài liệu đính kèm.' },
              { to: '/dashboard/doctors', title: 'Bác sĩ', desc: 'Quản lý đội ngũ chuyên môn và lịch làm việc.' },
              { to: '/dashboard/schedules', title: 'Lịch làm việc', desc: 'Điều phối ca trực và phòng ban hỗ trợ.' },
              { to: '/dashboard/visits', title: 'Khám & Điều trị', desc: 'Ghi nhận chỉ định, kết quả và tiến trình.' },
              { to: '/dashboard/medications', title: 'Kho thuốc', desc: 'Quản lý tồn kho, lô nhập và cấp phát.' },
              { to: '/dashboard/services', title: 'Danh mục dịch vụ', desc: 'Cập nhật dịch vụ y tế, giá và ưu đãi.' },
              { to: '/dashboard/billing', title: 'Thanh toán & Hóa đơn', desc: 'Theo dõi công nợ, xuất hóa đơn và báo cáo.' },
            ]"
            :key="item.to"
            :to="item.to"
            class="group rounded-2xl border border-emerald-100 bg-white p-6 shadow-sm transition hover:-translate-y-1 hover:border-emerald-300 hover:shadow-lg"
          >
            <h3 class="text-base font-semibold text-slate-900">{{ item.title }}</h3>
            <p class="mt-2 text-sm text-slate-600">{{ item.desc }}</p>
            <span class="mt-4 inline-flex items-center gap-2 text-xs font-semibold uppercase tracking-wide text-emerald-600 group-hover:text-emerald-700">
              Quản lý ngay
              <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" class="h-3.5 w-3.5" fill="none" stroke="currentColor" stroke-width="2">
                <path stroke-linecap="round" stroke-linejoin="round" d="M9 5l7 7-7 7" />
              </svg>
            </span>
          </RouterLink>
        </div>
      </section>
    </main>
  </div>
</template>



