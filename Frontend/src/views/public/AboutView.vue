<script setup lang="ts">
import { onMounted, ref } from 'vue';
import PublicHeader from '@/components/PublicHeader.vue';
import PublicFooter from '@/components/PublicFooter.vue';
import { fetchDoctors, type Doctor as DoctorEntity } from '@/services/doctor.service';

type DoctorCard = {
  id: number;
  name: string;
  specialty: string;
  avatar: string;
  biography: string;
  initial: string;
};

const doctorCards = ref<DoctorCard[]>([]);
const loading = ref(true);
const errorMessage = ref('');

const rawBaseUrl = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080';
const apiBaseUrl = rawBaseUrl.replace(/\/$/, '');

const resolveAvatarUrl = (url?: string | null) => {
  if (!url) return '';
  if (/^https?:\/\//i.test(url)) return url;
  return `${apiBaseUrl}/${url.replace(/^\/+/, '')}`;
};

const buildFallbackBio = (specialty: string) =>
  `Chuyên gia ${specialty.toLowerCase()} với cam kết theo dõi sát sao và cập nhật phác đồ điều trị hiện đại.`;

const buildInitial = (name?: string) => {
  if (name && name.trim()) {
    return name.trim().charAt(0).toUpperCase();
  }
  return 'B';
};

const mapDoctor = (doctor: DoctorEntity): DoctorCard => {
  const name = doctor.account?.fullName?.trim() || `Bác sĩ #${doctor.id}`;
  const specialty = doctor.specialty?.trim() || 'Đa khoa';
  const mapped: DoctorCard = {
    id: doctor.id,
    name,
    specialty,
    avatar: resolveAvatarUrl(doctor.account?.avatarUrl?.trim()),
    biography: doctor.biography?.trim() || buildFallbackBio(specialty),
    initial: buildInitial(doctor.account?.fullName),
  };
  return mapped;
};

const loadDoctors = async () => {
  loading.value = true;
  errorMessage.value = '';
  try {
    const data = await fetchDoctors();
    doctorCards.value = data.map(mapDoctor);
  } catch (error) {
    console.error('Failed to load doctors', error);
    errorMessage.value = 'Không thể tải danh sách bác sĩ. Vui lòng thử lại sau.';
  } finally {
    loading.value = false;
  }
};

onMounted(loadDoctors);
</script>

<template>
  <div class="min-h-screen bg-slate-50">
    <PublicHeader />

    <section class="border-b border-slate-200 bg-white">
      <div class="mx-auto max-w-5xl px-6 py-12 text-center">
        <p class="mb-2 text-sm font-semibold uppercase tracking-widest text-indigo-600">Đội ngũ bác sĩ</p>
        <h1 class="mb-4 text-3xl font-bold tracking-tight text-slate-900">Sự tận tâm đến từ những chuyên gia hàng đầu</h1>
        <p class="text-base text-slate-600">Mỗi bác sĩ đều trải qua quy trình tuyển chọn khắt khe, được huấn luyện bài bản và cập nhật kiến thức liên tục để đồng hành cùng sức khỏe của bạn.</p>
      </div>
    </section>

    <section class="bg-slate-50">
      <div class="mx-auto max-w-6xl px-6 py-12">
        <div v-if="loading" class="rounded-2xl border border-slate-200 bg-white p-6 text-center text-slate-600">
          Đang tải danh sách bác sĩ...
        </div>
        <div
          v-else-if="errorMessage"
          class="rounded-2xl border border-rose-200 bg-rose-50 p-6 text-center text-rose-600"
        >
          {{ errorMessage }}
        </div>
        <div
          v-else-if="!doctorCards.length"
          class="rounded-2xl border border-amber-200 bg-amber-50 p-6 text-center text-amber-700"
        >
          Chúng tôi sẽ cập nhật danh sách bác sĩ sớm nhất. Cảm ơn bạn đã quan tâm!
        </div>
        <div v-else class="grid gap-6 sm:grid-cols-2 lg:grid-cols-4">
          <article
            v-for="doctor in doctorCards"
            :key="doctor.id"
            class="group flex h-full flex-col items-center rounded-2xl border border-slate-200 bg-white p-6 text-center shadow-sm transition hover:-translate-y-1 hover:shadow-lg"
          >
            <div class="relative mb-4 flex h-24 w-24 items-center justify-center">
              <img
                v-if="doctor.avatar"
                :src="doctor.avatar"
                :alt="`Ảnh bác sĩ ${doctor.name}`"
                class="h-24 w-24 rounded-full object-cover ring-2 ring-slate-100"
                loading="lazy"
              />
              <div
                v-else
                class="inline-flex h-24 w-24 items-center justify-center rounded-full bg-emerald-50 text-2xl font-semibold uppercase text-emerald-600 ring-2 ring-emerald-100"
                aria-hidden="true"
              >
                {{ doctor.initial }}
              </div>
            </div>
            <h3 class="text-lg font-semibold text-slate-900">{{ doctor.name }}</h3>
            <p class="text-sm font-medium text-indigo-600">{{ doctor.specialty }}</p>
            <p class="mt-3 text-sm text-slate-600">
              {{ doctor.biography }}
            </p>
          </article>
        </div>
      </div>
    </section>

    <PublicFooter />
  </div>
</template>
