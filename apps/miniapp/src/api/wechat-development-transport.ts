export const WECHAT_DEVELOPMENT_ENV = 'prod-d3g9ntdmsdf9d7877' as const
export const WECHAT_DEVELOPMENT_SERVICE = 'huaren-api-it' as const

type Method = 'GET' | 'POST'
type Header = Record<string, string>
export interface CloudContainerResult { statusCode: number; data: unknown; header?: Header }
export interface WechatCloudPort {
  init(input: { env: typeof WECHAT_DEVELOPMENT_ENV }): void
  callContainer(input: { config: { env: typeof WECHAT_DEVELOPMENT_ENV }; header: Header; path: string; method: Method; data?: unknown }): Promise<CloudContainerResult>
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
