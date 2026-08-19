export interface WechatLoginPort {
  login(input: { success(result: { code?: string }): void; fail(): void }): void
}

/** Obtains a one-time code without exposing any persistence or logging capability. */
export function requestWechatOneTimeCode(port: WechatLoginPort): Promise<string> {
  return new Promise((resolve, reject) => port.login({
    success(result) {
      const code = typeof result.code === 'string' ? result.code.trim() : ''
      if (!code) { reject(new Error('WX_LOGIN_CODE_MISSING')); return }
      resolve(code)
    },
    fail() { reject(new Error('WX_LOGIN_FAILED')) },
  }))
}
