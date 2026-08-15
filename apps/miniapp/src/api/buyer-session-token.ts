export type BuyerSessionToken = Readonly<{
  token: string
  subjectRef: string
  absoluteExpiresAt: string
}>

let current: BuyerSessionToken | null = null

function valid(value: BuyerSessionToken): boolean {
  return value.token.trim().length > 0
    && value.subjectRef.trim().length > 0
    && Number.isFinite(Date.parse(value.absoluteExpiresAt))
    && Date.parse(value.absoluteExpiresAt) > Date.now()
}

export function commitBuyerSessionToken(value: BuyerSessionToken): void {
  if (!valid(value)) throw new Error('BUYER_SESSION_TOKEN_INVALID')
  current = Object.freeze({ ...value })
}

export function readBuyerSessionToken(): BuyerSessionToken | null {
  if (!current || !valid(current)) {
    current = null
    return null
  }
  return current
}

export function requireBuyerBearerToken(): string {
  const token = readBuyerSessionToken()
  if (!token) throw new Error('BUYER_SESSION_REQUIRED')
  return token.token
}

export function clearBuyerSessionToken(): void {
  current = null
}

export function resetBuyerSessionTokenForTests(): void {
  current = null
}
