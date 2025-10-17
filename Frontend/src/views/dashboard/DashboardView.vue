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
      <div class="mb-6 grid gap-6 rounded-2xl bg-white p-6 shadow">
        <h2 class="text-lg font-semibold text-slate-900">Tổng quan</h2>
        <p class="text-sm text-slate-600">
          Chọn một phân hệ bên dưới để quản lý: lịch hẹn, yêu cầu đặt lịch, bệnh nhân, bác sĩ, khám/điều trị, kho thuốc, dịch vụ, hóa đơn...
        </p>
      </div>

      <div class="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
        <RouterLink
          to="/dashboard/appointment-requests"
          class="rounded-xl border border-slate-200 bg-white p-5 shadow hover:border-blue-300 hover:shadow-md"
        >
          <h3 class="mb-1 text-base font-semibold text-slate-900">Yêu cầu đặt lịch</h3>
          <p class="text-sm text-slate-600">Duyệt / từ chối yêu cầu từ trang chủ.</p>
        </RouterLink>

        <RouterLink
          to="/dashboard/appointments"
          class="rounded-xl border border-slate-200 bg-white p-5 shadow hover:border-blue-300 hover:shadow-md"
        >
          <h3 class="mb-1 text-base font-semibold text-slate-900">Lịch hẹn</h3>
          <p class="text-sm text-slate-600">Tạo, cập nhật, phân công bác sĩ/phòng.</p>
        </RouterLink>

        <RouterLink
          to="/dashboard/patients"
          class="rounded-xl border border-slate-200 bg-white p-5 shadow hover:border-blue-300 hover:shadow-md"
        >
          <h3 class="mb-1 text-base font-semibold text-slate-900">Bệnh nhân</h3>
          <p class="text-sm text-slate-600">Quản lý hồ sơ, lịch sử khám.</p>
        </RouterLink>

        <RouterLink
          to="/dashboard/doctors"
          class="rounded-xl border border-slate-200 bg-white p-5 shadow hover:border-blue-300 hover:shadow-md"
        >
          <h3 class="mb-1 text-base font-semibold text-slate-900">Bác sĩ</h3>
          <p class="text-sm text-slate-600">Danh sách bác sĩ và chuyên khoa.</p>
        </RouterLink>

        <RouterLink
          to="/dashboard/schedules"
          class="rounded-xl border border-slate-200 bg-white p-5 shadow hover:border-blue-300 hover:shadow-md"
        >
          <h3 class="mb-1 text-base font-semibold text-slate-900">Lịch làm việc</h3>
          <p class="text-sm text-slate-600">Xem và cập nhật lịch bác sĩ.</p>
        </RouterLink>

        <RouterLink
          to="/dashboard/visits"
          class="rounded-xl border border-slate-200 bg-white p-5 shadow hover:border-blue-300 hover:shadow-md"
        >
          <h3 class="mb-1 text-base font-semibold text-slate-900">Khám/Điều trị</h3>
          <p class="text-sm text-slate-600">Phiếu khám, chỉ định, kết quả.</p>
        </RouterLink>

        <RouterLink
          to="/dashboard/medications"
          class="rounded-xl border border-slate-200 bg-white p-5 shadow hover:border-blue-300 hover:shadow-md"
        >
          <h3 class="mb-1 text-base font-semibold text-slate-900">Kho thuốc</h3>
          <p class="text-sm text-slate-600">Thuốc, lô nhập, tồn kho.</p>
        </RouterLink>

        <RouterLink
          to="/dashboard/services"
          class="rounded-xl border border-slate-200 bg-white p-5 shadow hover:border-blue-300 hover:shadow-md"
        >
          <h3 class="mb-1 text-base font-semibold text-slate-900">Dịch vụ</h3>
          <p class="text-sm text-slate-600">Danh mục dịch vụ y tế.</p>
        </RouterLink>

        <RouterLink
          to="/dashboard/billing"
          class="rounded-xl border border-slate-200 bg-white p-5 shadow hover:border-blue-300 hover:shadow-md"
        >
          <h3 class="mb-1 text-base font-semibold text-slate-900">Hóa đơn</h3>
          <p class="text-sm text-slate-600">Tạo và quản lý hóa đơn.</p>
        </RouterLink>
      </div>
    </main>
  </div>
</template>
