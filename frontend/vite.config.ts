import vue from '@vitejs/plugin-vue';
import { defineConfig } from 'vite';

/**
 * 서브패스 배포 지원.
 * 게이트웨이 뒤(`/chominjungum/`)에 놓을 때는 빌드 시 VITE_BASE 를 준다:
 *   VITE_BASE=/chominjungum/ VITE_API_BASE=/chominjungum npm run build
 * 그러면 API 도 같은 origin 상대경로가 되어 CORS 가 필요 없다.
 */
export default defineConfig({
  base: process.env.VITE_BASE ?? '/',
  plugins: [vue()],
  server: {
    port: 5180,
  },
});
