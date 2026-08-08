import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'

const forbiddenBrowserSecretNames = [
  'HZ_CONTENT_ADMIN_TOKEN',
  'HZ_TEST_ACCESS_TOKEN',
  'VITE_HZ_CONTENT_ADMIN_TOKEN',
  'VITE_HZ_TEST_ACCESS_TOKEN',
] as const

export default defineConfig(({ mode }) => {
  const environment = loadEnv(mode, process.cwd(), '')
  const configuredForbiddenName = forbiddenBrowserSecretNames.find((name) => environment[name])
  if (configuredForbiddenName) {
    throw new Error(`ADMIN_BROWSER_SECRET_CONFIGURATION_FORBIDDEN:${configuredForbiddenName}`)
  }

  return { plugins: [vue()] }
})
