type ReleaseEnvironment = 'DEV' | 'TEST' | 'STAGE' | 'PROD'
const build = (import.meta as ImportMeta & { env?: Record<string, string | undefined> }).env ?? {}
export const WECHAT_DEVELOPMENT_ENV = build.VITE_CLOUD_ENV_ID || 'prod-d3g9ntdmsdf9d7877'
export const RELEASE_ENVIRONMENT = (build.VITE_RELEASE_ENV || 'PROD') as ReleaseEnvironment
const expectedService = `huaren-api-${RELEASE_ENVIRONMENT.toLowerCase()}`
export const WECHAT_DEVELOPMENT_SERVICE = build.VITE_CLOUD_SERVICE_NAME || expectedService
if (!/^(DEV|TEST|STAGE|PROD)$/.test(RELEASE_ENVIRONMENT) || WECHAT_DEVELOPMENT_SERVICE !== expectedService) {
  throw new Error('WECHAT_CLOUD_BUILD_IDENTITY_INVALID')
}

type Method = 'GET' | 'POST'
type Header = Record<string, string>
export interface CloudContainerResult { statusCode: number; data: unknown; header?: Header }
export interface WechatCloudPort {
  init(input: { env: string }): void
  callContainer(input: { config: { env: string }; header: Header; path: string; method: Method; data?: unknown }): Promise<CloudContainerResult>
}

let initialized = false
function cloud(): WechatCloudPort {
  const value = (globalThis as typeof globalThis & { wx?: { cloud?: WechatCloudPort } }).wx?.cloud
  if (!value) throw new Error('WECHAT_CLOUD_UNAVAILABLE')
  return value
}
function exactPath(path: string): boolean {
  return path.startsWith('/') && !path.includes('\\') && !path.includes('://') && !/[\r\n]/.test(path)
}
export async function callProjectApi(path: string, method: Method, data?: unknown, bearer?: string, extraHeaders:Header={}): Promise<CloudContainerResult> {
  if (!exactPath(path)) throw new Error('PROJECT_API_PATH_INVALID')
  const port = cloud()
  if (!initialized) { port.init({ env: WECHAT_DEVELOPMENT_ENV }); initialized = true }
  const header: Header = { 'X-WX-SERVICE': WECHAT_DEVELOPMENT_SERVICE, 'content-type': 'application/json', ...extraHeaders }
  if (bearer) header.Authorization = `Bearer ${bearer}`
  return port.callContainer({ config: { env: WECHAT_DEVELOPMENT_ENV }, header, path, method, ...(data === undefined ? {} : { data }) })
}
export function resetWechatDevelopmentTransportForTest(): void { initialized = false }
