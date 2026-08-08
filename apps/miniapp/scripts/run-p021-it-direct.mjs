import { createHash, randomUUID } from 'node:crypto'
import { spawn } from 'node:child_process'
import { mkdir, readFile, rename, rm, writeFile } from 'node:fs/promises'
import { existsSync } from 'node:fs'
import { dirname, join, resolve } from 'node:path'

const BROWSER='C:/Program Files/Google/Chrome/Application/chrome.exe'
const BROWSER_SHA='115A87374083A409EB4B3D0204CBAEA8A0E27EEB264CB0B9D3E4C19C378F54D1'
const formalRunArgument=process.argv.find(value=>value.startsWith('--formal-run-id='))
const formalRunId=formalRunArgument?.slice('--formal-run-id='.length)??'P021-FORMAL-RUN-ID-REQUIRED'
const FORMAL={
  BaseUrl:'https://huaren-api-it-284852-10-1456291159.sh.run.tcloudbase.com',Database:'huarenzaimeng_it_vnext',RunId:formalRunId,
  ServiceVersion:'ARTIFACT_SHA256:3B1D2184C31D598A896BFD25DEC93F10CB9EC93EAF5F0B74919556DE8DDAA0A2',
  DatabaseIdentity:'huarenzaimeng_it_vnext|TENCENT64.site|*|16111ecc-8469-11f1-9125-b8cef6ab5f4c',
  OutputRoot:resolve('项目管理/正式交付/D4-开发计划与工程准备/证据/P021-IT'),Mode:'FORMAL',TokenFile:resolve('token.txt')
}
const shadowArgument=process.argv.find(value=>value.startsWith('--local-shadow-config='))
let settings=FORMAL
if(shadowArgument){
  const shadowRoot=resolve('.tmp-p021-whole-chain'),configPath=resolve(shadowArgument.slice('--local-shadow-config='.length))
  if(!(configPath===shadowRoot||configPath.startsWith(`${shadowRoot}\\`)))throw new Error('SHADOW_CONFIG_PATH_OUTSIDE_BOUNDARY')
  const candidate=JSON.parse(await readFile(configPath,'utf8'))
  const keys=['BaseUrl','Database','RunId','ServiceVersion','DatabaseIdentity','OutputRoot','Mode','TokenFile']
  if(JSON.stringify(Object.keys(candidate).sort())!==JSON.stringify(keys.sort()))throw new Error('SHADOW_CONFIG_KEYS_MISMATCH')
  const url=new URL(candidate.BaseUrl),outputRoot=resolve(candidate.OutputRoot),tokenFile=resolve(candidate.TokenFile)
  if(candidate.Mode!=='LOCAL_SHADOW'||url.protocol!=='http:'||url.hostname!=='127.0.0.1'||!/^P021-SHADOW-[A-Z0-9-]+$/.test(candidate.RunId)
    ||!String(candidate.ServiceVersion).startsWith('SHADOW_SERVICE:')||!String(candidate.DatabaseIdentity).startsWith('SHADOW_DATABASE:')
    ||!(outputRoot===shadowRoot||outputRoot.startsWith(`${shadowRoot}\\`))||!(tokenFile===shadowRoot||tokenFile.startsWith(`${shadowRoot}\\`)))throw new Error('SHADOW_BOUNDARY_INVALID')
  settings={...candidate,OutputRoot:outputRoot,TokenFile:tokenFile}
}
const BASE=settings.BaseUrl,DB=settings.Database,RUN=settings.RunId,SERVICE=settings.ServiceVersion,DATABASE_ID=settings.DatabaseIdentity
const shadow=settings.Mode==='LOCAL_SHADOW',root=settings.OutputRoot
if(!shadow&&!process.argv.includes('--self-test')&&!/^P021-IT-\d{8}-FINAL-\d{3}$/.test(RUN))throw new Error('FORMAL_RUN_ID_REQUIRED')
const finalDir=join(root,RUN), staging=join(root,`.staging-${RUN}`), blocked=join(root,`.blocked-${RUN}`)
const collector=resolve('apps/miniapp/scripts/collect-p021-it-page-actual.mjs')
const diagnosticDir=join(root,'.diagnostic-P021-IT01-20260808-001')
const qualificationDiagnosticDir=join(root,'.diagnostic-P021-QUALIFICATION-20260808-004')
const sha=v=>createHash('sha256').update(v).digest('hex').toUpperCase()
const canonical=v=>Array.isArray(v)?v.map(canonical):v&&typeof v==='object'?Object.fromEntries(Object.keys(v).sort().map(k=>[k,canonical(v[k])])):v
const canonicalJson=v=>JSON.stringify(canonical(v))
const exact=(v,keys,label)=>{if(!v||canonicalJson(Object.keys(v).sort())!==canonicalJson([...keys].sort()))throw new Error(`${label}_KEYS_MISMATCH`)}
function databaseIdentityMatches(actual, expected) {
  if (shadow) return actual === expected
  const actualParts=String(actual).split('|'),expectedParts=String(expected).split('|')
  if(actualParts.length!==4||expectedParts.length!==4||expectedParts[2]!=='*')return false
  const port=Number(actualParts[2])
  return actualParts[0]===expectedParts[0]&&actualParts[1]===expectedParts[1]
    &&Number.isInteger(port)&&port>0&&port<=65535&&actualParts[3]===expectedParts[3]
}

if(process.argv.includes('--self-test')){
  if(new URL(BASE).hostname!=='huaren-api-it-284852-10-1456291159.sh.run.tcloudbase.com'||DB!=='huarenzaimeng_it_vnext')throw new Error('BOUNDARY_INVALID')
  if(!databaseIdentityMatches('huarenzaimeng_it_vnext|TENCENT64.site|27203|16111ecc-8469-11f1-9125-b8cef6ab5f4c',DATABASE_ID)
    ||databaseIdentityMatches('huarenzaimeng_it_vnext|OTHER.site|27203|16111ecc-8469-11f1-9125-b8cef6ab5f4c',DATABASE_ID)
    ||databaseIdentityMatches('huarenzaimeng_it_vnext|TENCENT64.site|0|16111ecc-8469-11f1-9125-b8cef6ab5f4c',DATABASE_ID)
    ||databaseIdentityMatches('huarenzaimeng_it_vnext|TENCENT64.site|27203|WRONG-UUID',DATABASE_ID))throw new Error('DATABASE_IDENTITY_POLICY_INVALID')
  if(!existsSync(collector)||!existsSync(BROWSER))throw new Error('LOCAL_DEPENDENCY_MISSING')
  if(sha(await readFile(BROWSER))!==BROWSER_SHA)throw new Error('BROWSER_SHA_MISMATCH')
  process.stdout.write(JSON.stringify({ExecutionStatus:'PASS',FormalScenarioCount:0,CloudAccessed:false,DatabaseAccessed:false})+'\n')
  process.exit(0)
}

const tokenText=await readFile(settings.TokenFile,'utf8')
const token=k=>{const m=tokenText.match(new RegExp(`^\\s*${k}\\s*[:=]\\s*(\\S+)\\s*$`,'m'));if(!m)throw new Error(`TOKEN_MISSING_${k}`);return m[1]}
let buyer=token('HZ_IT_BUYER_SESSION_TOKEN'),cs=token('HZ_IT_ADMIN_CS_SESSION_TOKEN'),fin=token('HZ_IT_ADMIN_FIN_SESSION_TOKEN')
if(new Set([buyer,cs,fin]).size!==3)throw new Error('TOKENS_NOT_UNIQUE')
const observed=[]
async function http(path,t='',method='GET'){
  const url=new URL(path,BASE);if(url.origin!==new URL(BASE).origin)throw new Error('HOST_BOUNDARY_VIOLATION')
  const headers={accept:'application/json'};if(t)headers.cookie=`HZM_IT_SESSION=${t}`
  const response=await fetch(url,{method,headers,redirect:'error'});const text=await response.text();let body
  try{body=JSON.parse(text)}catch{throw new Error(`HTTP_JSON_INVALID_${response.status}`)}
  observed.push({method,url:url.href,status:response.status,requestHeaders:Object.keys(headers).sort()})
  return{path:url.pathname,status:response.status,cacheControl:response.headers.get('cache-control')??'',contentType:response.headers.get('content-type')??'',length:Buffer.byteLength(text),cookieSet:Boolean(t),body}
}
function strict(response,project){
  exact(response.body,['requestRef','outcome','projectCode','resourceRef','aggregateVersion','currentProjection','retryClass','nextPollAt'],'RESPONSE')
  if(response.body.projectCode!==project)throw new Error('PROJECT_CODE_MISMATCH')
  if(project==='ORDER_DETAIL_READ'){
    const p=response.body.currentProjection;exact(p,['orderRef','aggregateVersion','projectionVersion','stateCode','priceSnapshotSummary','confirmedItems','unknownItems','responsibilityCode','updatedAt','nextReviewPoint','timeline','allowedActions','supportRef'],'PROJECTION')
    exact(p.priceSnapshotSummary,['priceSnapshotRef','totalMinor','currency','displayVersion','maskedTarget','brandDisplayName','productDisplayName','targetValueDisplay','targetCurrency','validUntil'],'PRICE')
    p.timeline.forEach(x=>exact(x,['timelineItemRef','sequence','projectionVersion','stateCode','occurredAt','userMessageCode'],'TIMELINE'))
    p.allowedActions.forEach(x=>exact(x,['actionCode','enabled','actionBindingVersion','supportRef'],'ACTION'))
  }
}
function project(response,expected){if(response.status<200||response.status>=300||!response.cacheControl.includes('no-store')||response.body.projectCode!==expected){const error=new Error(`UNEXPECTED_HTTP_RESPONSE_${expected}`);error.details={Expected:{Http2xx:true,CacheControlContains:'no-store',ProjectCode:expected},Actual:{Path:response.path,HttpStatus:response.status,CacheControl:response.cacheControl,ContentType:response.contentType,ResponseLength:response.length,CookieSet:response.cookieSet,ProjectCode:response.body?.projectCode??null,Outcome:response.body?.outcome??null,RetryClass:response.body?.retryClass??null}};throw error}}
async function snapshot(){const r=await http('/internal/test-readonly/p021/snapshot',buyer);exact(r.body,['rowCount','rows','canonicalSha256'],'SNAPSHOT');if(r.body.rowCount!==7||r.body.rows.length!==7||sha(r.body.rows.join('\n'))!==r.body.canonicalSha256)throw new Error('DATABASE_SNAPSHOT_INVALID');return r.body}
async function page(scenario,subcase,pageKind,role,orderRef,view,delay,t){
  const out=join(staging,'page',scenario,subcase);await mkdir(out,{recursive:true})
  const args=[collector,'--scenario-id',scenario,'--subcase-id',subcase,'--page-kind',pageKind,'--role',role,'--base-url',BASE,'--order-ref',orderRef,'--expected-view-state',view,'--output-directory',out,'--browser-executable',BROWSER,'--browser-sha256',BROWSER_SHA,'--viewport-width',pageKind==='MINIAPP_P021'?'375':'1280','--viewport-height',pageKind==='MINIAPP_P021'?'812':'790','--delay-plan',delay]
  const child=spawn(process.execPath,args,{cwd:resolve('.'),stdio:['pipe','pipe','pipe'],windowsHide:true});child.stdin.end(JSON.stringify({BUYER:role==='BUYER'?t:null,CS:role==='CS'?t:null,FIN:role==='FIN'?t:null}))
  let stdout='',stderr='';child.stdout.on('data',x=>stdout+=x);child.stderr.on('data',x=>stderr+=x)
  const code=await new Promise((ok,fail)=>{child.on('error',fail);child.on('close',ok)})
  if(code!==0)throw new Error(`PAGE_${scenario}_${subcase}:${stderr.trim()}`);const actual=JSON.parse(stdout.trim())
  if(!actual.ExpectedMatched||actual.ExitCode!==0||actual.ExternalHosts.length||actual.WriteActionCount)throw new Error(`PAGE_ACTUAL_MISMATCH_${scenario}_${subcase}`)
  return actual
}
async function main(){
  if([finalDir,staging,blocked].some(existsSync))throw new Error('RUN_ID_NOT_FRESH');await mkdir(staging,{recursive:true})
  const nonce=randomUUID().replaceAll('-','').toUpperCase();const challenge=await http(`/internal/test-readonly/p021/challenge?nonce=${nonce}`,buyer)
  exact(challenge.body,['nonce','serviceVersion','mode','databaseIdentity'],'CHALLENGE');if(challenge.body.nonce!==nonce||challenge.body.serviceVersion!==SERVICE||challenge.body.mode!=='test-readonly'||!databaseIdentityMatches(challenge.body.databaseIdentity,DATABASE_ID))throw new Error('RUNTIME_IDENTITY_MISMATCH')
  const results={},before=await snapshot(),counterBefore=(await http('/internal/test-readonly/p021/counters',buyer)).body
  const progress=async scenario=>writeFile(join(staging,'progress.json'),JSON.stringify({RunId:RUN,LastCompletedScenario:scenario,CompletedScenarios:Object.keys(results),ExecutionStatus:'RUNNING'},null,2))
  const r1=await http('/api/v1/orders/IT-P021-AWAITING',buyer);project(r1,'ORDER_DETAIL_READ');strict(r1,'ORDER_DETAIL_READ');const db=(await http('/internal/test-readonly/p021/projection',buyer)).body;if(canonicalJson(db)!==canonicalJson(r1.body.currentProjection))throw new Error('IT01_DATABASE_PROJECTION_MISMATCH');results['P021-IT-01']={Api:r1,Page:await page('IT01','BUYER_READY','MINIAPP_P021','BUYER','IT-P021-AWAITING','READY','NONE',buyer)};await progress('P021-IT-01')
  const r2=[];for(const [path,t] of [['/api/v1/orders/IT-P021-AWAITING',''],['/api/v1/orders/IT-P021-CROSS-SUBJECT',buyer],['/api/v1/orders/IT-P021-NOT-FOUND',buyer],['/api/v1/orders/IT-P021-REVOKED',buyer]])r2.push(await http(path,t));r2.forEach(x=>{project(x,'ORDER_DETAIL_NOT_AVAILABLE');strict(x,'ORDER_DETAIL_NOT_AVAILABLE')});if(new Set(r2.map(x=>canonicalJson(x.body))).size!==1||new Set(r2.map(x=>x.status)).size!==1||new Set(r2.map(x=>x.length)).size!==1)throw new Error('IT02_NOT_SAME_SHAPE');results['P021-IT-02']={Api:r2,Page:[]};for(const [i,n] of ['UNAUTHENTICATED','CROSS_SUBJECT','NOT_FOUND','REVOKED'].entries())results['P021-IT-02'].Page.push(await page('IT02',n,'MINIAPP_P021','BUYER',['IT-P021-AWAITING','IT-P021-CROSS-SUBJECT','IT-P021-NOT-FOUND','IT-P021-REVOKED'][i],'NOT_AVAILABLE','NONE',i?buyer:''));await progress('P021-IT-02')
  const conflicts=[];for(const kind of ['PROJECTION_LOW_VERSION','ORDER_VERSION_CONFLICT','QUOTE_DIGEST_CONFLICT']){const x=await http(`/internal/test-readonly/p021/conflict/${kind}`,buyer,'POST');strict(x,'ORDER_DETAIL_READ_ERROR');conflicts.push(x)}if(canonicalJson(before)!==canonicalJson(await snapshot()))throw new Error('IT03_ROLLBACK_NOT_PROVEN');results['P021-IT-03']={Api:conflicts,Page:[await page('IT03','PROJECTION_LOW_VERSION','MINIAPP_P021','BUYER','IT-P021-AWAITING','INFORMATION_UPDATED','PROJECTION_LOW_VERSION',buyer),await page('IT03','ORDER_VERSION_CONFLICT','MINIAPP_P021','BUYER','IT-P021-AWAITING','READ_ERROR','ORDER_VERSION_CONFLICT',buyer),await page('IT03','QUOTE_DIGEST_CONFLICT','MINIAPP_P021','BUYER','IT-P021-AWAITING','INFORMATION_UPDATED','QUOTE_DIGEST_CONFLICT',buyer)]};await progress('P021-IT-03')
  results['P021-IT-04']=await page('IT04','LATE_LOWER_VERSION_AFTER_READY','MINIAPP_P021','BUYER','IT-P021-AWAITING','READY','LATE_LOWER_VERSION_AFTER_READY',buyer);await progress('P021-IT-04')
  const cr=await http('/admin-read/v1/orders/IT-P021-AWAITING',cs);project(cr,'ADMIN_ORDER_DETAIL_READ');if(/totalMinor|targetCurrency|sessionRef|authorization/.test(JSON.stringify(cr.body)))throw new Error('IT05_CROSS_ROLE_FIELD_VISIBLE');results['P021-IT-05']={Api:cr,Page:await page('IT05','CS_READY','ADMIN_A140','CS','IT-P021-AWAITING','READY','NONE',cs)};await progress('P021-IT-05')
  const fr=await http('/admin-read/v1/orders/IT-P021-AWAITING',fin),denied=await http('/admin-read/v1/orders/IT-P021-AWAITING');project(fr,'ADMIN_ORDER_DETAIL_READ');project(denied,'ADMIN_ORDER_DETAIL_NOT_AVAILABLE');if(/maskedTarget|sessionRef|authorization|projectSubjectRef/.test(JSON.stringify(fr.body)))throw new Error('IT06_CROSS_ROLE_FIELD_VISIBLE');results['P021-IT-06']={Api:[fr,denied],Page:[await page('IT06','FIN_READY','ADMIN_A140','FIN','IT-P021-AWAITING','READY','NONE',fin),await page('IT06','CONTENT_DENIED','ADMIN_A140','CONTENT','IT-P021-AWAITING','ACCESS_DENIED','NONE','')]};await progress('P021-IT-06')
  const counterAfter=(await http('/internal/test-readonly/p021/counters',buyer)).body,after=await snapshot();if(canonicalJson(before)!==canonicalJson(after))throw new Error('IT07_DATABASE_CHANGED');for(const k of Object.keys(counterAfter).filter(k=>k!=='QueryCall'))if(BigInt(counterAfter[k])!==BigInt(counterBefore[k]))throw new Error(`IT07_NON_QUERY_DELTA_${k}`);if(new Set(observed.map(x=>new URL(x.url).host)).size!==1)throw new Error('IT07_NETWORK_EGRESS_VIOLATION');results['P021-IT-07']={Before:before,After:after,CounterBefore:counterBefore,CounterAfter:counterAfter,Requests:observed};await progress('P021-IT-07')
  const executionStatus=shadow?'SHADOW_PASS':'PASS',consumable=!shadow
  await writeFile(join(staging,'index.json'),JSON.stringify({RunId:RUN,ExecutionStatus:executionStatus,ScenarioCount:7,Results:results,Consumable:consumable},null,2));await writeFile(join(staging,shadow?'SHADOW_READY.json':'READY.json'),JSON.stringify({RunId:RUN,ExecutionStatus:executionStatus,Consumable:consumable},null,2));await rename(staging,finalDir);process.stdout.write(JSON.stringify({RunId:RUN,ExecutionStatus:executionStatus,ScenarioCount:7,Consumable:consumable,EvidenceRoot:finalDir})+'\n')
}
async function diagnoseIt01(){if(existsSync(diagnosticDir))throw new Error('DIAGNOSTIC_ID_NOT_FRESH');await mkdir(diagnosticDir,{recursive:true});const response=await http('/api/v1/orders/IT-P021-AWAITING',buyer);const actual={Path:response.path,HttpStatus:response.status,CacheControl:response.cacheControl,ContentType:response.contentType,ResponseLength:response.length,CookieSet:response.cookieSet,ProjectCode:response.body?.projectCode??null,Outcome:response.body?.outcome??null,RetryClass:response.body?.retryClass??null};await writeFile(join(diagnosticDir,'result.json'),JSON.stringify({ExecutionStatus:'PASS',RequestCount:1,ReadOnly:true,DatabaseWritePerformed:false,Actual:actual},null,2));process.stdout.write(JSON.stringify(actual)+'\n')}
async function diagnoseQualification(){if(existsSync(qualificationDiagnosticDir))throw new Error('QUALIFICATION_DIAGNOSTIC_ID_NOT_FRESH');await mkdir(qualificationDiagnosticDir,{recursive:true});const response=await http('/internal/test-readonly/p021/qualification',buyer);if(response.status!==200||response.body?.projectCode!=='P021_QUALIFICATION_DIAGNOSTIC')throw new Error('QUALIFICATION_DIAGNOSTIC_RESPONSE_INVALID');const actual=response.body;await writeFile(join(qualificationDiagnosticDir,'result.json'),JSON.stringify({ExecutionStatus:'PASS',RequestCount:1,ReadOnly:true,DatabaseWritePerformed:false,Actual:actual},null,2));process.stdout.write(JSON.stringify(actual)+'\n')}
try{if(process.argv.includes('--diagnose-it01'))await diagnoseIt01();else if(process.argv.includes('--diagnose-qualification'))await diagnoseQualification();else await main()}catch(error){const diagnostic=process.argv.includes('--diagnose-it01')||process.argv.includes('--diagnose-qualification');const target=process.argv.includes('--diagnose-it01')?diagnosticDir:process.argv.includes('--diagnose-qualification')?qualificationDiagnosticDir:blocked;if(!diagnostic){await rm(blocked,{recursive:true,force:true});if(existsSync(staging))await rename(staging,blocked);else await mkdir(blocked,{recursive:true})}else await mkdir(target,{recursive:true});await writeFile(join(target,'BLOCKED.json'),JSON.stringify({RunId:RUN,ExecutionStatus:'BLOCKED',Consumable:false,Error:error.message,Details:error.details??null},null,2));console.error(error.message);process.exitCode=1}finally{buyer=null;cs=null;fin=null}
