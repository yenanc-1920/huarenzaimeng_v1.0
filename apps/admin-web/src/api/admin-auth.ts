export const ADMIN_AUTH_PATH = '/admin-auth/v1'

async function jsonRequest(path: string, body: object, headers: Record<string, string> = {}) {
  const response = await fetch(`${ADMIN_AUTH_PATH}${path}`, {
    method: 'POST', credentials: 'include',
    headers: { Accept: 'application/json', 'Content-Type': 'application/json', ...headers },
    body: JSON.stringify(body),
  })
  const payload = await response.json().catch(() => null) as { code?: string } | null
  if (!response.ok) throw new Error(payload?.code ?? 'REQUEST_FAILED')
  return payload
}

export const loginAdmin = (username: string, password: string) => jsonRequest('/login', { username, password })
export const logoutAdmin = () => jsonRequest('/logout', {})

export async function initializationStatus(): Promise<'AVAILABLE' | 'CLOSED'> {
  const response = await fetch(`${ADMIN_AUTH_PATH}/initialization`, { credentials: 'include', headers: { Accept: 'application/json' } })
  if (!response.ok) return 'CLOSED'
  const payload = await response.json() as { status?: unknown }
  return payload.status === 'AVAILABLE' ? 'AVAILABLE' : 'CLOSED'
}

export const initializeAdmin = (username: string, displayName: string, password: string, bootstrapToken: string) =>
  jsonRequest('/bootstrap', { username, displayName, password }, { 'X-Admin-Bootstrap-Token': bootstrapToken })
