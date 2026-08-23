import { defineConfig } from 'vite'
import uni from '@dcloudio/vite-plugin-uni'

export default defineConfig({
  base: process.env.VITE_H5_BASE || '/',
  plugins: [uni()],
})
