export type AnonymousSessionToken=Readonly<{token:string;subjectRef:string;absoluteExpiresAt:string}>
export type AnonymousSessionSuccess=Readonly<{
  outcome:'AUTHENTICATED'
  projectCode:'ANONYMOUS_SESSION_CREATED'
  requestRef:string
  subjectRef:string
  token:string
  absoluteExpiresAt:string
  retryClass:'NONE'
}>

let current:AnonymousSessionToken|null=null
const object=(value:unknown):value is Record<string,unknown>=>typeof value==='object'&&value!==null&&!Array.isArray(value)
const exactKeys=(value:Record<string,unknown>,keys:string[])=>Object.keys(value).length===keys.length&&Object.keys(value).every(key=>keys.includes(key))
const strictRfc3339=(value:unknown):value is string=>typeof value==='string'&&/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d+)?(?:Z|[+-]\d{2}:\d{2})$/.test(value)&&Number.isFinite(Date.parse(value))
const cacheControl=(headers:unknown):string|null=>{
  if(!object(headers))return null
  const matches=Object.entries(headers).filter(([key])=>key.toLowerCase()==='cache-control')
  return matches.length===1&&typeof matches[0][1]==='string'?matches[0][1]:null
}

export function parseAnonymousSessionResponse(response:{statusCode:number;data:unknown;header?:unknown},requestRef:string,now=Date.now()):AnonymousSessionSuccess{
  const noStore=cacheControl(response.header)?.split(',').map(token=>token.trim().toLowerCase()).includes('no-store')===true
  const value=response.data
  if(response.statusCode!==201||!noStore||!object(value)||!exactKeys(value,['outcome','projectCode','requestRef','subjectRef','token','absoluteExpiresAt','retryClass'])
    ||value.outcome!=='AUTHENTICATED'||value.projectCode!=='ANONYMOUS_SESSION_CREATED'||value.requestRef!==requestRef||value.retryClass!=='NONE'
    ||typeof value.subjectRef!=='string'||!value.subjectRef.trim()||typeof value.token!=='string'||!value.token.trim()
    ||!strictRfc3339(value.absoluteExpiresAt)||Date.parse(value.absoluteExpiresAt)<=now)throw new Error('ANONYMOUS_SESSION_RESPONSE_INVALID')
  return value as unknown as AnonymousSessionSuccess
}

export function commitAnonymousSessionToken(value:AnonymousSessionToken,now=Date.now()):void{
  if(!value.token.trim()||!value.subjectRef.trim()||!strictRfc3339(value.absoluteExpiresAt)||Date.parse(value.absoluteExpiresAt)<=now)throw new Error('ANONYMOUS_SESSION_TOKEN_INVALID')
  current=Object.freeze({...value})
}

export function readAnonymousSessionToken(now=Date.now()):AnonymousSessionToken|null{
  if(!current||Date.parse(current.absoluteExpiresAt)<=now){current=null;return null}
  return current
}

export function resetAnonymousSessionTokenForTests():void{current=null}
