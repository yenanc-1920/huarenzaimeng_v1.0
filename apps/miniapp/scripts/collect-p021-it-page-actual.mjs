import { createHash } from 'node:crypto'
import { createServer, request as httpRequest } from 'node:http'
import { request as httpsRequest } from 'node:https'
import { spawn } from 'node:child_process'
import { mkdtemp, mkdir, readFile, rm, writeFile } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { basename, join, resolve } from 'node:path'

const args=Object.fromEntries(process.argv.slice(2).map((value,index,array)=>value.startsWith('--')?[value.slice(2),array[index+1]]:null).filter(Boolean))
const required=['scenario-id','subcase-id','page-kind','role','base-url','order-ref','expected-view-state','output-directory','browser-executable','browser-sha256','viewport-width','viewport-height','delay-plan']
for(const key of required)if(!args[key])throw new Error(`P021_COLLECTOR_ARGUMENT_REQUIRED:${key}`)
const scenarioId=args['scenario-id'],subcaseId=args['subcase-id'],pageKind=args['page-kind'],role=args.role,delayPlan=args['delay-plan']
if(!['IT01','IT02','IT03','IT04','IT05','IT06'].includes(scenarioId)||!['MINIAPP_P021','ADMIN_A140'].includes(pageKind)||!['BUYER','CS','FIN','CONTENT'].includes(role))throw new Error('P021_COLLECTOR_ARGUMENT_INVALID')
const width=Number(args['viewport-width']),height=Number(args['viewport-height']),dpr=1,output=resolve(args['output-directory'])
const stdin=await new Promise(resolveInput=>{let value='';process.stdin.setEncoding('utf8');process.stdin.on('data',chunk=>value+=chunk);process.stdin.on('end',()=>resolveInput(value.trim()))})
const tokens=stdin?JSON.parse(stdin):{}
const selectedToken=role==='BUYER'?tokens.BUYER:role==='CS'?tokens.CS:role==='FIN'?tokens.FIN:null
const tokenRequired=!(scenarioId==='IT02'&&subcaseId==='UNAUTHENTICATED')&&role!=='CONTENT'
if(tokenRequired&&(typeof selectedToken!=='string'||!selectedToken))throw new Error('P021_COLLECTOR_TOKEN_MISSING')
await mkdir(output,{recursive:true})
const sha=value=>createHash('sha256').update(value).digest('hex').toUpperCase()
const fileSha=async path=>sha(await readFile(path))
const collectorSha=await fileSha(new URL(import.meta.url))
const browserSha=await fileSha(args['browser-executable'])
if(browserSha!==args['browser-sha256'].toUpperCase())throw new Error('P021_BROWSER_SHA_MISMATCH')
const temporary=await mkdtemp(join(tmpdir(),'p021-it-page-'))
const children=[];const proxyNetwork=[];const browserRequestHeaderNames=[];let proxy
const wait=ms=>new Promise(resolveWait=>setTimeout(resolveWait,ms))
async function waitHttp(url){for(let i=0;i<120;i++){try{const response=await fetch(url);if(response.status<500)return}catch{}await wait(250)}throw new Error('P021_LOCAL_SERVER_TIMEOUT')}
function run(command,argv,cwd,env={}){const child=spawn(command,argv,{cwd,env:{...process.env,...env},stdio:['ignore','pipe','pipe'],windowsHide:true});children.push(child);return child}
async function proxyRequest(req,res,target,token){
  const destination=new URL(req.url,target);const client=destination.protocol==='https:'?httpsRequest:httpRequest
  const incomingHeaderNames=Object.keys(req.headers).map(value=>value.toLowerCase()).sort()
  browserRequestHeaderNames.push({url:destination.href,method:req.method,headerNames:incomingHeaderNames})
  const headers={...req.headers,host:destination.host};delete headers.cookie
  if(token)headers.cookie=`HZM_IT_SESSION=${token}`
  const upstream=client(destination,{method:req.method,headers},reply=>{res.writeHead(reply.statusCode??502,reply.headers);reply.pipe(res)})
  upstream.on('error',error=>{res.writeHead(502,{'content-type':'text/plain'});res.end('proxy failure');proxyNetwork.push({url:destination.href,method:req.method,status:502,error:error.message,requestHeaderNames:incomingHeaderNames})})
  upstream.on('response',reply=>proxyNetwork.push({url:destination.href,method:req.method,status:reply.statusCode??0,requestHeaderNames:incomingHeaderNames,responseHeaderNames:Object.keys(reply.headers).sort()}))
  req.pipe(upstream)
}
async function startPage(){
  const isMini=pageKind==='MINIAPP_P021',vitePort=isMini?5184:5185
  const cwd=resolve(isMini?'apps/miniapp':'apps/admin-web')
  const argv=isMini?['run','dev:h5:p021-it','--','--host','127.0.0.1','--port',String(vitePort)]:['run','dev','--','--port',String(vitePort)]
  run('npm.cmd',argv,cwd)
  await waitHttp(`http://127.0.0.1:${vitePort}`)
  proxy=createServer((req,res)=>{
    if(req.url.startsWith('/api/v1/')||req.url.startsWith('/admin-read/'))return proxyRequest(req,res,args['base-url'],selectedToken)
    return proxyRequest(req,res,`http://127.0.0.1:${vitePort}`,null)
  })
  await new Promise((resolveServer,reject)=>proxy.listen(0,'127.0.0.1',resolveServer).once('error',reject))
  const port=proxy.address().port
  if(isMini){
    if(scenarioId==='IT03'||scenarioId==='IT04')return `http://127.0.0.1:${port}/#/pages/order/detail?itDelayPlan=${encodeURIComponent(delayPlan)}`
    const sessionMode=scenarioId==='IT02'&&subcaseId==='UNAUTHENTICATED'?'UNAUTHENTICATED':'AUTHORIZED'
    return `http://127.0.0.1:${port}/#/pages/order/detail?orderRef=${encodeURIComponent(args['order-ref'])}&itRealSession=${sessionMode}`
  }
  return `http://127.0.0.1:${port}/?p021ItOrderRef=${encodeURIComponent(args['order-ref'])}&p021ItRole=${role}`
}
async function cdp(){
  const profile=join(temporary,'profile');await mkdir(profile)
  const browser=run(args['browser-executable'],[`--headless=new`,`--remote-debugging-port=0`,`--user-data-dir=${profile}`,'--no-first-run','--disable-background-networking','--disable-component-update','--disable-sync','--disable-default-apps','about:blank'],process.cwd())
  const portFile=join(profile,'DevToolsActivePort');let port
  for(let i=0;i<120;i++){try{port=Number((await readFile(portFile,'utf8')).split(/\r?\n/)[0]);break}catch{await wait(100)}}
  if(!port)throw new Error('P021_CDP_PORT_TIMEOUT')
  const pages=await (await fetch(`http://127.0.0.1:${port}/json`)).json();const socket=new WebSocket(pages[0].webSocketDebuggerUrl);await new Promise((ok,fail)=>{socket.onopen=ok;socket.onerror=fail})
  const browserNetwork=[];let id=0;const pending=new Map();socket.onmessage=event=>{const msg=JSON.parse(event.data);if(msg.method==='Network.requestWillBeSent')browserNetwork.push({url:msg.params.request.url,method:msg.params.request.method});if(msg.id&&pending.has(msg.id)){const {ok,fail}=pending.get(msg.id);pending.delete(msg.id);msg.error?fail(new Error(msg.error.message)):ok(msg.result)}}
  const send=(method,params={})=>new Promise((ok,fail)=>{const call=++id;pending.set(call,{ok,fail});socket.send(JSON.stringify({id:call,method,params}))})
  return{browser,socket,send,browserNetwork}
}
let stdout='';let stderr=''
try{
  const pageUrl=await startPage();const {browser,socket,send,browserNetwork}=await cdp();await send('Page.enable');await send('Runtime.enable');await send('Network.enable');await send('Emulation.setDeviceMetricsOverride',{width,height,deviceScaleFactor:dpr,mobile:pageKind==='MINIAPP_P021'})
  await send('Page.navigate',{url:pageUrl})
  const readyExpression=pageKind==='MINIAPP_P021'?`document.querySelector('[data-it-completed="true"]')!==null`:`['READY','ACCESS_DENIED','UNAVAILABLE'].includes(document.querySelector('[data-page-id="UX-P021-ADMIN"]')?.dataset.readState)`
  for(let i=0;i<120;i++){const result=await send('Runtime.evaluate',{expression:readyExpression,returnByValue:true});if(result.result.value)break;if(i===119)throw new Error('P021_PAGE_READY_TIMEOUT');await wait(100)}
  const evaluated=await send('Runtime.evaluate',{expression:`(()=>{const root=document.querySelector('[data-page-id="${pageKind==='MINIAPP_P021'?'UX-P021':'UX-P021-ADMIN'}"]');const text=document.body.innerText;const actions=[...document.querySelectorAll('[data-action-code],button:not([disabled])')].map(x=>x.getAttribute('data-action-code')||x.textContent.trim());const labels=[...document.querySelectorAll('dt')].map(x=>x.textContent.trim());const fields=(document.querySelector('[data-visible-field-keys]')?.getAttribute('data-visible-field-keys')||'').split(',').filter(Boolean);return{url:location.href,viewState:root?.dataset.viewState||root?.dataset.readState||'',projectionVersion:Number(root?.dataset.projectionVersion||0),detailCount:Number(root?.dataset.detailCount||0),oldContentCount:root?.dataset.itOldContentRestored==='true'?1:0,writeActionCount:Number(document.querySelector('[data-write-eligibility]')?.dataset.writeEligibility||0),actions,labels,fields,text,html:root?.outerHTML||'',titleCount:[...document.querySelectorAll('h1,h2,[role="heading"]')].length}})()`,returnByValue:true})
  const dom=evaluated.result.value
  const screenshot=await send('Page.captureScreenshot',{format:'png',captureBeyondViewport:false});const png=Buffer.from(screenshot.data,'base64');const screenshotPath=join(output,'viewport.png');await writeFile(screenshotPath,png)
  const forbidden=['Authorization','Bearer','Cookie','HZ_TEST_ACCESS_TOKEN','HZ_CONTENT_ADMIN_TOKEN','requestPayment','Reloadly'];const findings=Object.fromEntries(forbidden.map(term=>[term,{count:dom.text.includes(term)?1:0,locations:dom.text.includes(term)?['body']:[]}]))
  const domIndex={pageId:pageKind==='MINIAPP_P021'?'UX-P021':'UX-P021-ADMIN',structureSha256:sha(dom.html),actions:dom.actions,visibleLabels:dom.labels,visibleFieldKeys:dom.fields,detailCount:dom.detailCount,titleCount:dom.titleCount,textSha256:sha(dom.text)};await writeFile(join(output,'dom-index.json'),JSON.stringify(domIndex,null,2))
  const baseHost=new URL(args['base-url']).host;const pageHost=new URL(pageUrl).host;const allowedHosts=[...new Set([baseHost,pageHost])].sort()
  const observedHosts=[...new Set([...browserNetwork,...proxyNetwork].map(item=>{try{return new URL(item.url).host}catch{return''}}).filter(Boolean))].sort()
  const externalHosts=observedHosts.filter(host=>!allowedHosts.includes(host)&&!host.startsWith('127.0.0.1'))
  const browserNetworkSafe=browserNetwork.map(item=>({url:item.url,method:item.method}))
  const proxyNetworkSafe=proxyNetwork.map(item=>({url:item.url,method:item.method,status:item.status,requestHeaderNames:item.requestHeaderNames,responseHeaderNames:item.responseHeaderNames??[],error:item.error??null}))
  await writeFile(join(output,'network-observation.json'),JSON.stringify({AllowedHosts:allowedHosts,BrowserNetwork:browserNetworkSafe,ProxyNetwork:proxyNetworkSafe,ExternalHosts:externalHosts,BrowserRequestHeaderNames:browserRequestHeaderNames},null,2))
  const forbiddenIdentityHeaders=['authorization','x-project-subject-ref','x-session-version','x-authorization-set-ref','x-authorization-evidence-version']
  const forbiddenIdentityHeaderFindings=Object.fromEntries(forbiddenIdentityHeaders.map(name=>[name,{count:browserRequestHeaderNames.filter(item=>item.headerNames.includes(name)).length,locations:browserRequestHeaderNames.filter(item=>item.headerNames.includes(name)).map(item=>item.url)}]))
  const visibleFieldKeys=dom.fields;const forbiddenFieldKeys=role==='CS'?['totalMinor','currency','targetValueDisplay','targetCurrency']:role==='FIN'?['maskedTarget','sessionRef','authorizationSetRef','authorizationEvidenceVersion']:role==='CONTENT'?['maskedTarget','totalMinor','currency','targetValueDisplay','targetCurrency','confirmedItems','unknownItems','timeline']:[]
  const forbiddenFieldFindings=Object.fromEntries(forbiddenFieldKeys.map(key=>[key,{count:visibleFieldKeys.filter(value=>value===key).length,locations:visibleFieldKeys.filter(value=>value===key).map(()=> 'data-visible-field-keys')}]))
  const queryCount=proxyNetworkSafe.filter(item=>item.method==='GET'&&(item.url.includes('/api/v1/orders/')||item.url.includes('/admin-read/v1/orders/'))).length
  const expectedQueryCount=scenarioId==='IT03'||scenarioId==='IT04'?0:scenarioId==='IT02'&&subcaseId==='UNAUTHENTICATED'?0:1
  const versionMatched=scenarioId!=='IT04'||(dom.viewState==='READY'&&dom.projectionVersion>=4)
  const expectedMatched=dom.viewState===args['expected-view-state']&&(dom.viewState==='READY'||dom.detailCount===0)&&dom.oldContentCount===0&&dom.writeActionCount===0&&queryCount===expectedQueryCount&&versionMatched&&externalHosts.length===0&&Object.values(findings).every(item=>item.count===0)&&Object.values(forbiddenIdentityHeaderFindings).every(item=>item.count===0)&&Object.values(forbiddenFieldFindings).every(item=>item.count===0)
  const actual={ScenarioId:scenarioId,SubcaseId:subcaseId,PageKind:pageKind,Role:role,ActualPageUrl:pageUrl,ViewState:dom.viewState,ProjectionVersion:dom.projectionVersion,DetailCount:dom.detailCount,OldContentCount:dom.oldContentCount,DomIndexRef:'dom-index.json',DomFields:domIndex,ScreenshotRef:'viewport.png',ScreenshotSha256:sha(png),NetworkTraceRef:'network-observation.json',AllowedHosts:allowedHosts,BrowserNetwork:browserNetworkSafe,ProxyNetwork:proxyNetworkSafe,BrowserRequestHeaderNames:browserRequestHeaderNames,QueryCount:queryCount,WriteActionCount:dom.writeActionCount,ExternalHosts:externalHosts,ForbiddenIdentityHeaderFindings:forbiddenIdentityHeaderFindings,ForbiddenFindings:findings,ForbiddenFieldFindings:forbiddenFieldFindings,StrictMapperAccepted:dom.viewState==='READY',ExpectedMatched:expectedMatched,ExitCode:expectedMatched?0:1,CollectorVersionSha:collectorSha,BusinessImplSha:sha(dom.html),BrowserSha:browserSha,Viewport:{width,height},Dpr:dpr,ProcessEvidenceRef:'process-evidence.json',StdoutRef:'stdout.txt',StderrRef:'stderr.txt',VisibleFieldKeys:visibleFieldKeys,ForbiddenFieldKeys:forbiddenFieldKeys}
  if(!expectedMatched)throw new Error('P021_PAGE_ACTUAL_MISMATCH')
  await writeFile(join(output,'actual.json'),JSON.stringify(actual,null,2));await writeFile(join(output,'stdout.txt'),JSON.stringify(actual));await writeFile(join(output,'stderr.txt'),'')
  await writeFile(join(output,'process-evidence.json'),JSON.stringify({ExitCode:0,TokenPersisted:false,TemporaryProfile:basename(temporary),BrowserSha:browserSha,ExternalHosts:externalHosts},null,2))
  stdout=JSON.stringify(actual);socket.close();browser.kill()
}catch(error){stderr=error instanceof Error?error.message:String(error);await writeFile(join(output,'stderr.txt'),stderr);process.exitCode=1}
finally{for(const child of children)if(!child.killed)child.kill();if(proxy)await new Promise(resolveClose=>proxy.close(resolveClose));await rm(temporary,{recursive:true,force:true})}
if(stderr)process.stderr.write(stderr+'\n');else process.stdout.write(stdout+'\n')
