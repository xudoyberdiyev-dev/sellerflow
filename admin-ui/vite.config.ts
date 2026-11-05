import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// Superadmin panel /admin/ ostida Spring Boot orqali beriladi
export default defineConfig({
  base: '/admin/',
  plugins: [react()],
  build: { outDir: 'dist', emptyOutDir: true, sourcemap: false },
  server: { proxy: { '/admin/api': 'http://localhost:8080' } },
})
