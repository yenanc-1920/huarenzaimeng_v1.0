import type { ProjectSessionProjection, RecoveryResult } from './types'

const SESSION_KEY = 'projectSessionProjection'
const RECOVERY_CASE_KEY = 'pendingRecoveryCaseRef'
const guest = (sessionVersion=0): ProjectSessionProjection => ({ role:'GUEST',projectSubjectRef:null,sessionVersion,authorizationSetRef:null,
  authorizationEvidenceVersion:null,authorizedOrderRefs:[],issuedAt:null,expiresAt:null,semantics:'SERVER_PROJECTION_CACHE_NOT_AUTHORITY' })
const object = (value: unknown): value is Record<string, unknown> => typeof value === 'object' && value !== null
const exactKeys=(value:Record<string,unknown>,allowed:string[])=>Object.keys(value).length===allowed.length&&Object.keys(value).every((key)=>allowed.includes(key))

export interface SessionStorage {
  getStorageSync(key: string): unknown
  setStorageSync(key: string, value: unknown): void
}

export function readSessionProjection(storage: SessionStorage,now=Date.now()): ProjectSessionProjection {
  const value = storage.getStorageSync(SESSION_KEY)
  const fallbackVersion=object(value)&&Number.isInteger(value.sessionVersion)?value.sessionVersion as number:0
  if (!object(value) || !exactKeys(value,['role','projectSubjectRef','sessionVersion','authorizationSetRef','authorizationEvidenceVersion','authorizedOrderRefs','issuedAt','expiresAt','semantics'])
      || (value.role !== 'GUEST' && value.role !== 'BUYER') || !Number.isInteger(value.sessionVersion)
      || (value.sessionVersion as number) < 0 || !Array.isArray(value.authorizedOrderRefs)
      || !value.authorizedOrderRefs.every((ref) => typeof ref === 'string' && ref.length > 0)
      || new Set(value.authorizedOrderRefs).size !== value.authorizedOrderRefs.length
      || value.semantics !== 'SERVER_PROJECTION_CACHE_NOT_AUTHORITY'
      || (value.role === 'GUEST' && (value.projectSubjectRef!==null||value.authorizationSetRef!==null||value.authorizationEvidenceVersion!==null
        ||value.authorizedOrderRefs.length!==0||value.issuedAt!==null||value.expiresAt!==null))
      || (value.role === 'BUYER' && (typeof value.projectSubjectRef!=='string'||value.projectSubjectRef.length===0
        ||typeof value.authorizationSetRef!=='string'||value.authorizationSetRef.length===0
        ||typeof value.authorizationEvidenceVersion!=='string'||value.authorizationEvidenceVersion.length===0
        ||typeof value.issuedAt!=='string'||typeof value.expiresAt!=='string'||!Number.isFinite(Date.parse(value.issuedAt))
        ||!Number.isFinite(Date.parse(value.expiresAt))||Date.parse(value.issuedAt)>=Date.parse(value.expiresAt)||Date.parse(value.expiresAt)<=now))) return guest(fallbackVersion)
  return { role:value.role,projectSubjectRef:value.projectSubjectRef as string|null,sessionVersion:value.sessionVersion as number,
    authorizationSetRef:value.authorizationSetRef as string|null,authorizationEvidenceVersion:value.authorizationEvidenceVersion as string|null,
    authorizedOrderRefs:[...value.authorizedOrderRefs],issuedAt:value.issuedAt as string|null,expiresAt:value.expiresAt as string|null,
    semantics:'SERVER_PROJECTION_CACHE_NOT_AUTHORITY' }
}

export function storeBuyerSessionProjection(storage:SessionStorage,value:unknown,now=Date.now()):ProjectSessionProjection{
  storage.setStorageSync(SESSION_KEY,value)
  const projection=readSessionProjection(storage,now)
  if(projection.role!=='BUYER'){
    storage.setStorageSync(SESSION_KEY,guest(projection.sessionVersion))
    throw new Error('BUYER_SESSION_PROJECTION_INVALID')
  }
  return projection
}

export function applyRecoveryResult(storage: SessionStorage, result: RecoveryResult): ProjectSessionProjection {
  const projection: ProjectSessionProjection = result.outcome === 'RECOVERED'
    ? { role:'BUYER',projectSubjectRef:result.authorization.projectSubjectRef,sessionVersion:result.authorization.sessionVersion,
      authorizationSetRef:result.authorization.authorizationSetRef,authorizationEvidenceVersion:result.authorization.authorizationEvidenceVersion,
      authorizedOrderRefs:[...result.authorization.authorizedOrderRefs],issuedAt:result.authorization.issuedAt,expiresAt:result.authorization.expiresAt,
      semantics:'SERVER_PROJECTION_CACHE_NOT_AUTHORITY' }
    : guest(readSessionProjection(storage).sessionVersion)
  storage.setStorageSync(SESSION_KEY, projection)
  storage.setStorageSync(RECOVERY_CASE_KEY,result.outcome==='UNKNOWN'?result.recoveryCaseRef:'')
  return projection
}

export function readPendingRecoveryCaseRef(storage:SessionStorage):string|null{
  const value=storage.getStorageSync(RECOVERY_CASE_KEY)
  return typeof value==='string'&&value.length>0?value:null
}

export function revokeSessionForRecovery(storage:SessionStorage):ProjectSessionProjection{
  const revoked=guest(readSessionProjection(storage).sessionVersion)
  storage.setStorageSync(SESSION_KEY,revoked)
  return revoked
}

export function revokeOrderListAccess(storage:SessionStorage):ProjectSessionProjection{
  const revoked=guest(0)
  storage.setStorageSync(SESSION_KEY,revoked)
  return revoked
}

export async function performRecovery(storage:SessionStorage,request:()=>Promise<RecoveryResult>):Promise<RecoveryResult>{
  revokeSessionForRecovery(storage)
  const result=await request()
  applyRecoveryResult(storage,result)
  return result
}

export async function performRecoveryPoll(storage:SessionStorage,request:(recoveryCaseRef:string)=>Promise<RecoveryResult>):Promise<RecoveryResult>{
  const recoveryCaseRef=readPendingRecoveryCaseRef(storage)
  if(!recoveryCaseRef)throw new Error('RECOVERY_CASE_REF_REQUIRED')
  revokeSessionForRecovery(storage)
  const result=await request(recoveryCaseRef)
  if(result.recoveryCaseRef!==recoveryCaseRef)throw new Error('RECOVERY_CASE_REF_MISMATCH')
  applyRecoveryResult(storage,result)
  return result
}
