/// <reference types="vite/client" />
declare module '*.vue' {
  import type { DefineComponent } from 'vue'
  const component: DefineComponent<object, object, unknown>
  export default component
}

interface ImportMetaEnv {
  readonly VITE_ADMIN_DATA_MODE?: 'PROJECT_API_PROXY'
  readonly VITE_ADMIN_REENTRY_PATH?: string
  readonly VITE_ICP_FILING?: string
  readonly VITE_PUBLIC_SECURITY_FILING?: string
}
