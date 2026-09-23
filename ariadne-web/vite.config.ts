import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  const javaUrl = env.VITE_JAVA_URL || 'http://127.0.0.1:18080'
  return {
    plugins: [vue()],
    server: {
      proxy: {
        '/api': {
          target: javaUrl,
          changeOrigin: false,
          rewrite: (path) => path.replace(/^\/api/, ''),
        },
      },
    },
  }
})
