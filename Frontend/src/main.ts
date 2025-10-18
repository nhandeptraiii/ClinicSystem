import { createApp } from 'vue';
import { createPinia } from 'pinia';
import piniaPluginPersistedstate from 'pinia-plugin-persistedstate';
import App from './App.vue';
import router from './router';
import './style.css';
import { attachAuthInterceptor } from './services/http';
import { useAuthStore } from './stores/authStore';

const pinia = createPinia();
pinia.use(piniaPluginPersistedstate);

const app = createApp(App);
app.use(pinia);
app.use(router);

attachAuthInterceptor(() => useAuthStore());

app.mount('#app');

// Ensure reload brings user to top instead of preserving scroll
if ('scrollRestoration' in history) {
  history.scrollRestoration = 'manual';
}
window.scrollTo({ top: 0 });
