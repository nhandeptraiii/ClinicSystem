<script setup lang="ts">
import logoUrl from '@/assets/LogoDuyenHanh.png';
const HOTLINE = '0332406049';
const EMAIL = 'contact@clinicsystem.com';
const ADDRESS = '36 Đường Cách mạng tháng 8, Phường Cái Khế, Thành Phố Cần Thơ';
</script>

<template>
  <footer class="border-t border-slate-200 bg-white">
    <div class="mx-auto max-w-7xl px-6 py-12">
      <div class="grid gap-10 md:grid-cols-3">
        <!-- Company / Contact -->
        <div>
          <img :src="logoUrl" alt="Clinic Logo" class="mb-4 h-12 w-auto" />
          <p class="mb-3 text-sm text-slate-700">{{ ADDRESS }}</p>
          <p class="mb-1 text-sm"><span class="text-slate-500">Email:</span> <a :href="`mailto:${EMAIL}`" class="text-slate-700 hover:underline">{{ EMAIL }}</a></p>
          <p class="mb-4 text-sm"><span class="text-slate-500">Điện thoại:</span> <a :href="`tel:${HOTLINE}`" class="font-semibold text-sky-700 hover:underline">{{ HOTLINE }}</a></p>

          <div class="mb-4 space-y-1 text-sm text-slate-700">
            <p>Chính sách bảo mật & Điều khoản sử dụng</p>
            <p>Chính sách bảo vệ thông tin khách hàng</p>
          </div>
          <div class="flex items-center gap-3">
            <a href="#" aria-label="Facebook" class="grid h-8 w-8 place-items-center rounded-full bg-slate-200 text-slate-700 hover:bg-slate-300">
              <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="currentColor" class="h-4 w-4"><path d="M13 3h4a1 1 0 011 1v3h-3a1 1 0 00-1 1v3h4l-1 4h-3v6h-4v-6H8v-4h3V8a5 5 0 015-5z"/></svg>
            </a>
            <a href="#" aria-label="Email" class="grid h-8 w-8 place-items-center rounded-full bg-slate-200 text-slate-700 hover:bg-slate-300">
              <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="currentColor" class="h-4 w-4"><path d="M2 6a2 2 0 012-2h16a2 2 0 012 2v.217l-10 5.555L2 6.217V6zm0 2.783V18a2 2 0 002 2h16a2 2 0 002-2V8.783l-9.445 5.245a2 2 0 01-2.11 0L2 8.783z"/></svg>
            </a>
            <a href="#" aria-label="YouTube" class="grid h-8 w-8 place-items-center rounded-full bg-slate-200 text-rose-600 hover:bg-slate-300">
              <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="currentColor" class="h-4 w-4"><path d="M23.498 6.186a3.003 3.003 0 00-2.115-2.126C19.596 3.5 12 3.5 12 3.5s-7.596 0-9.383.56A3.003 3.003 0 00.502 6.186C0 7.978 0 12 0 12s0 4.022.502 5.814a3.003 3.003 0 002.115 2.126C4.404 20.5 12 20.5 12 20.5s7.596 0 9.383-.56a3.003 3.003 0 002.115-2.126C24 16.022 24 12 24 12s0-4.022-.502-5.814zM9.75 15.568V8.432L15.818 12 9.75 15.568z"/></svg>
            </a>
          </div>
        </div>

        <!-- Right 2/3 -->
        <div class="md:col-span-2">
          <!-- Regions: Bắc | Nam -->
          <div class="grid gap-10 md:grid-cols-2">
            <!-- Miền Bắc -->
            <div class="md:border-l md:border-slate-200 md:pl-8">
              <h3 class="mb-4 border-b border-slate-200 pb-2 text-lg font-bold uppercase text-slate-900">Hệ thống phòng khám đa khoa Duyên Hạnh</h3>
              <div class="space-y-6 text-sm text-slate-700">
                <div>

                  <p>Giấy phép hoạt động số: 1752/SYT – GPHD/CL2</p>
                  <p>Số 36 Đường Cách mạng tháng 8, Phường Cái Khế, Thành Phố Cần Thơ</p>
                  <p class="mt-1">Thứ 2 - Thứ 7: 7h30 - 20h00</p>
                </div>
              </div>
            </div>

            <!-- Miền Nam (đổi thành Hỗ trợ khách hàng) -->
            <div class="md:border-l md:border-slate-200 md:pl-8">
              <h3 class="mb-4 border-b border-slate-200 pb-2 text-lg font-bold uppercase text-slate-900">Hỗ trợ khách hàng</h3>
              <ul class="space-y-2 text-sm text-slate-700">
                <li><a href="#" class="hover:underline">Hướng dẫn đặt lịch</a></li>
                <li><a href="#" class="hover:underline">Câu hỏi thường gặp</a></li>
                <li><a href="#" class="hover:underline">Liên hệ và góp ý</a></li>
                <li><a href="#" class="hover:underline">Chính sách bảo vệ thông tin</a></li>
                <li><a href="#" class="hover:underline">Chính sách bảo mật & Điều khoản</a></li>
              </ul>
            </div>
          </div>


          <!-- Accreditation / note -->
          <div class="mt-10 md:border-l md:border-slate-200 md:pl-8">
            <h3 class="mb-2 text-lg font-bold uppercase text-slate-900">Chứng nhận và liên kết</h3>
            <p class="text-sm text-slate-700">Hệ thống phòng khám tuân thủ tiêu chuẩn quản lý chất lượng, hợp tác chuyên môn với nhiều bệnh viện lớn nhằm đảm bảo chẩn đoán và điều trị hiệu quả.</p>
          </div>
        </div>

      </div>

      <div class="mt-5 border-t border-slate-200 py-3 text-center text-xs text-slate-500">
        © 2025 Phòng khám Đa khoa Duyên Hạnh. Bản quyền thuộc về Nhân Nguyễn.
      </div>
    </div>
  </footer>
</template>
