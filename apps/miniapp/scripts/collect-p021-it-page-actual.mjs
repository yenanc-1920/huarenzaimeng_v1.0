import { createHash } from 'node:crypto'
import { createServer, request as httpRequest } from 'node:http'
import { request as httpsRequest } from 'node:https'
import { spawn } from 'node:child_process'
import { mkdtemp, mkdir, readFile, rm, writeFile } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { basename, dirname, join, resolve } from 'node:path'
import WebSocket from 'ws'

const args=Object.fromEntries(process.argv.slice(2).map((value,index,array)=>value.startsWith('--')?[value.slice(2),array[index+1]]:null).filter(Boolean))
if(args['host-preflight']==='true'){
  await runHostPreflight(args)
  process.exit(0)
}
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
const markStage=async stage=>writeFile(join(output,'collector-stage.txt'),`${new Date().toISOString()}|${stage}\n`)
await markStage('ARGUMENTS_VALIDATED')
const sha=value=>createHash('sha256').update(value).digest('hex').toUpperCase()
const fileSha=async path=>sha(await readFile(path))
const collectorSha=await fileSha(new URL(import.meta.url))
const browserSha=await fileSha(args['browser-executable'])
if(browserSha!==args['browser-sha256'].toUpperCase())throw new Error('P021_BROWSER_SHA_MISMATCH')
const temporary=await mkdtemp(join(tmpdir(),'p021-it-page-'))
const children=[];const proxyNetwork=[];const browserRequestHeaderNames=[];let proxy
const wait=ms=>new Promise(resolveWait=>setTimeout(resolveWait,ms))
async function reserveLoopbackPort(){
  const probe=createServer()
  await new Promise((ok,fail)=>probe.listen(0,'127.0.0.1',ok).once('error',fail))
  const port=probe.address().port
  await new Promise(ok=>probe.close(ok))
  return port
}
async function waitHttp(url,child,expectedMarker){
  // A cold uni-app H5 compile can take well over 40 seconds on Windows.
  // Keep a finite ceiling, but do not misclassify normal first compilation as a server failure.
  for(let i=0;i<720;i++){
    if(child.exitCode!==null)throw new Error(`P021_LOCAL_SERVER_EXITED:${child.exitCode}:${child.__stderr.slice(-1200)}`)
    try{
      const response=await fetch(url,{redirect:'manual',signal:AbortSignal.timeout(1000)})
      const body=await response.text()
      if(response.status===200&&body.includes(expectedMarker))return
    }catch{}
    await wait(250)
  }
  throw new Error(`P021_LOCAL_SERVER_TIMEOUT:${child.__stdout.slice(-1200)}:${child.__stderr.slice(-1200)}`)
}
function run(command,argv,cwd,env={}){
  const child=spawn(command,argv,{cwd,env:{...process.env,...env},stdio:['ignore','pipe','pipe'],windowsHide:true})
  child.__stdout='';child.__stderr=''
  child.stdout?.on('data',chunk=>child.__stdout+=chunk.toString())
  child.stderr?.on('data',chunk=>child.__stderr+=chunk.toString())
  children.push(child)
  return child
}
async function terminateProcessTree(child){
  if(!child||child.exitCode!==null||!child.pid)return
  if(process.platform==='win32'){
    const killer=spawn('taskkill.exe',['/PID',String(child.pid),'/T','/F'],{stdio:'ignore',windowsHide:true})
    await Promise.race([new Promise(ok=>killer.once('close',ok)),wait(3000)])
  }else child.kill('SIGTERM')
  await Promise.race([new Promise(ok=>child.once('exit',ok)),wait(3000)])
}
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
  await markStage('PAGE_SERVER_STARTING')
  const isMini=pageKind==='MINIAPP_P021',vitePort=await reserveLoopbackPort()
  const cwd=resolve(isMini?'apps/miniapp':'apps/admin-web')
  // Launch the JavaScript CLI directly. This avoids both Windows .cmd spawn
  // semantics and npm argument forwarding differences across hosts.
  const cli=isMini
    ?resolve(cwd,'node_modules/@dcloudio/vite-plugin-uni/bin/uni.js')
    :resolve(cwd,'node_modules/vite/bin/vite.js')
  const argv=isMini?['-p','h5','--host','127.0.0.1','--port',String(vitePort)]:['--host','127.0.0.1','--port',String(vitePort)]
  const pageServer=run(process.execPath,[cli,...argv],cwd,{BROWSER:'none'})
  await waitHttp(`http://127.0.0.1:${vitePort}/`,pageServer,'<html')
  await markStage(`PAGE_SERVER_READY:${vitePort}`)
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
  await markStage('CHROME_STARTING')
  const profile=join(temporary,'profile');await mkdir(profile)
  const browser=run(args['browser-executable'],[`--headless`,`--disable-gpu`,`--disable-gpu-sandbox`,`--use-gl=swiftshader`,`--remote-debugging-port=0`,`--remote-allow-origins=*`,`--user-data-dir=${profile}`,'--no-first-run','--disable-background-networking','--disable-component-update','--disable-sync','--disable-default-apps','about:blank'],process.cwd())
  const portFile=join(profile,'DevToolsActivePort');let port
  for(let i=0;i<120;i++){if(browser.exitCode!==null)throw new Error(`P021_CHROME_EXITED:${browser.exitCode}:${browser.__stderr.slice(-1200)}`);try{port=Number((await readFile(portFile,'utf8')).split(/\r?\n/)[0]);break}catch{await wait(100)}}
  if(!port)throw new Error('P021_CDP_PORT_TIMEOUT')
  if(browser.exitCode!==null)throw new Error(`P021_CHROME_EXITED:${browser.exitCode}:${browser.__stderr.slice(-1200)}`)
  await markStage(`CHROME_CDP_READY:${port}`)
  const pages=await (await fetch(`http://127.0.0.1:${port}/json`,{signal:AbortSignal.timeout(3000)})).json();const pageTarget=pages.find(target=>target.type==='page'&&!target.url.startsWith('chrome-extension://'));if(!pageTarget)throw new Error('P021_CDP_PAGE_TARGET_MISSING')
  const version=await (await fetch(`http://127.0.0.1:${port}/json/version`,{signal:AbortSignal.timeout(3000)})).json()
  if(!version.webSocketDebuggerUrl)throw new Error('P021_CDP_BROWSER_TARGET_MISSING')
  const socket=new WebSocket(version.webSocketDebuggerUrl);await Promise.race([new Promise((ok,fail)=>{socket.once('open',ok);socket.once('error',fail)}),new Promise((_,fail)=>setTimeout(()=>fail(new Error('P021_CDP_WEBSOCKET_TIMEOUT')),5000))])
  await markStage('CHROME_CDP_SOCKET_OPEN')
  const browserNetwork=[],runtimeErrors=[];let id=0;const pending=new Map();socket.on('message',data=>{const msg=JSON.parse(data.toString());if(msg.method==='Network.requestWillBeSent')browserNetwork.push({url:msg.params.request.url,method:msg.params.request.method});if(msg.method==='Runtime.exceptionThrown')runtimeErrors.push({type:'exception',text:msg.params.exceptionDetails?.text??'',description:msg.params.exceptionDetails?.exception?.description??''});if(msg.method==='Runtime.consoleAPICalled'&&['error','warning'].includes(msg.params.type))runtimeErrors.push({type:msg.params.type,args:(msg.params.args??[]).map(item=>item.value??item.description??'')});if(msg.id&&pending.has(msg.id)){const {ok,fail,timer}=pending.get(msg.id);clearTimeout(timer);pending.delete(msg.id);msg.error?fail(new Error(msg.error.message)):ok(msg.result)}})
  const rawSend=(method,params={},sessionId)=>new Promise((ok,fail)=>{const call=++id;const timer=setTimeout(()=>{pending.delete(call);fail(new Error(`P021_CDP_COMMAND_TIMEOUT:${method}`))},5000);pending.set(call,{ok,fail,timer});socket.send(JSON.stringify({id:call,method,params,...(sessionId?{sessionId}:{})}))})
  const attached=await rawSend('Target.attachToTarget',{targetId:pageTarget.id,flatten:true})
  if(!attached.sessionId)throw new Error('P021_CDP_SESSION_MISSING')
  const send=(method,params={})=>rawSend(method,params,attached.sessionId)
  return{browser,socket,send,browserNetwork,runtimeErrors}
}
let stdout='';let stderr=''
try{
  const pageUrl=await startPage();const {browser,socket,send,browserNetwork,runtimeErrors}=await cdp();await send('Page.enable');await send('Runtime.enable');await send('Network.enable');await send('Emulation.setDeviceMetricsOverride',{width,height,deviceScaleFactor:dpr,mobile:pageKind==='MINIAPP_P021'})
  await markStage('PAGE_NAVIGATING')
  await send('Page.navigate',{url:pageUrl})
  const readyExpression=pageKind==='MINIAPP_P021'?`document.querySelector('[data-it-completed="true"]')!==null`:`['READY','ACCESS_DENIED','UNAVAILABLE'].includes(document.querySelector('[data-page-id="UX-P021-ADMIN"]')?.dataset.readState)`
  for(let i=0;i<120;i++){
    const result=await send('Runtime.evaluate',{expression:readyExpression,returnByValue:true})
    if(result.result.value)break
    if(i===119){
      const diagnostic=await send('Runtime.evaluate',{expression:`({url:location.href,title:document.title,text:document.body?.innerText?.slice(0,800)||'',bodyHtml:document.body?.innerHTML?.slice(0,2400)||'',scripts:[...document.scripts].map(item=>item.src||item.textContent?.slice(0,120)||''),resources:performance.getEntriesByType('resource').map(item=>item.name).slice(0,40)})`,returnByValue:true})
      throw new Error(`P021_PAGE_READY_TIMEOUT:${JSON.stringify({...diagnostic.result.value,runtimeErrors})}`)
    }
    await wait(100)
  }
  await markStage('PAGE_READY')
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
  stdout=JSON.stringify(actual);socket.close()
}catch(error){stderr=error instanceof Error?error.message:String(error);await writeFile(join(output,'stderr.txt'),stderr);process.exitCode=1}
finally{
  await markStage('CLEANUP_STARTING')
  for(const child of [...children].reverse())await terminateProcessTree(child)
  if(proxy)await new Promise(resolveClose=>proxy.close(resolveClose))
  let cleanupError
  for(let attempt=0;attempt<20;attempt++){
    try{await rm(temporary,{recursive:true,force:true});cleanupError=undefined;break}
    catch(error){cleanupError=error;await wait(150)}
  }
  if(cleanupError)throw cleanupError
  await markStage('CLEANUP_FINISHED')
}
if(stderr)process.stderr.write(stderr+'\n');else process.stdout.write(stdout+'\n')

async function runHostPreflight(preflightArgs){
  const required=['output-directory','browser-executable','browser-sha256']
  for(const key of required)if(!preflightArgs[key])throw new Error(`P021_HOST_PREFLIGHT_ARGUMENT_REQUIRED:${key}`)
  const digest=value=>createHash('sha256').update(value).digest('hex').toUpperCase()
  const browserBytes=await readFile(preflightArgs['browser-executable'])
  const browserSha=digest(browserBytes)
  if(browserSha!==preflightArgs['browser-sha256'].toUpperCase())throw new Error('P021_HOST_PREFLIGHT_BROWSER_SHA_MISMATCH')
  const npmCli=join(dirname(process.execPath),'node_modules','npm','bin','npm-cli.js')
  await readFile(npmCli)
  const npmProbe=spawn(process.execPath,[npmCli,'--version'],{stdio:['ignore','pipe','pipe'],windowsHide:true})
  let npmStdout='',npmStderr=''
  npmProbe.stdout.on('data',chunk=>npmStdout+=chunk)
  npmProbe.stderr.on('data',chunk=>npmStderr+=chunk)
  const npmExitCode=await new Promise((ok,fail)=>{npmProbe.on('error',fail);npmProbe.on('close',ok)})
  if(npmExitCode!==0||!/^\d+\.\d+\.\d+$/.test(npmStdout.trim()))throw new Error(`P021_HOST_PREFLIGHT_NPM_JS_ENTRY_FAILED:${npmStderr.trim()}`)
  const outputDirectory=resolve(preflightArgs['output-directory'])
  await mkdir(outputDirectory,{recursive:true})
  const temporaryDirectory=await mkdtemp(join(tmpdir(),'p021-host-preflight-'))
  const profile=join(temporaryDirectory,'profile')
  await mkdir(profile)
  let server,browser,socket
  const browserNetwork=[]
  try{
    server=createServer((_request,response)=>{
      response.writeHead(200,{'content-type':'text/html; charset=utf-8','cache-control':'no-store'})
      response.end('<!doctype html><html lang="zh-CN"><head><meta charset="utf-8"><title>P021 本地主机预演</title></head><body><main role="main" data-host-preflight="ready"><h1>P021 本地主机预演</h1><p role="status">本地零场景链已就绪</p><button type="button">只读确认</button></main></body></html>')
    })
    await new Promise((ok,fail)=>server.listen(0,'127.0.0.1',ok).once('error',fail))
    const localPort=server.address().port
    const pageUrl=`http://127.0.0.1:${localPort}/host-preflight`
    browser=spawn(preflightArgs['browser-executable'],['--headless=new','--remote-debugging-port=0',`--user-data-dir=${profile}`,'--no-first-run','--disable-background-networking','--disable-component-update','--disable-sync','--disable-default-apps','--disable-breakpad','--disable-crash-reporter',pageUrl],{stdio:['ignore','pipe','pipe'],windowsHide:true})
    const portFile=join(profile,'DevToolsActivePort')
    let debugPort
    for(let index=0;index<120;index++){
      try{debugPort=Number((await readFile(portFile,'utf8')).split(/\r?\n/)[0]);break}catch{await new Promise(ok=>setTimeout(ok,100))}
    }
    if(!debugPort)throw new Error('P021_HOST_PREFLIGHT_CDP_PORT_TIMEOUT')
    const pages=await (await fetch(`http://127.0.0.1:${debugPort}/json`)).json()
    const pageTarget=pages.find(target=>target.type==='page'&&!target.url.startsWith('chrome-extension://'))
    if(!pageTarget)throw new Error('P021_HOST_PREFLIGHT_PAGE_TARGET_MISSING')
    socket=new WebSocket(pageTarget.webSocketDebuggerUrl)
    await new Promise((ok,fail)=>{socket.onopen=ok;socket.onerror=fail})
    let callId=0
    const pending=new Map()
    socket.onmessage=event=>{
      const message=JSON.parse(event.data)
      if(message.method==='Network.requestWillBeSent')browserNetwork.push(message.params.request.url)
      if(message.id&&pending.has(message.id)){
        const handlers=pending.get(message.id);pending.delete(message.id)
        message.error?handlers.fail(new Error(message.error.message)):handlers.ok(message.result)
      }
    }
    const send=(method,params={})=>new Promise((ok,fail)=>{const id=++callId;pending.set(id,{ok,fail});socket.send(JSON.stringify({id,method,params}))})
    await send('Page.enable');await send('Runtime.enable');await send('Network.enable')
    await send('Emulation.setDeviceMetricsOverride',{width:800,height:600,deviceScaleFactor:1,mobile:false})
    await send('Page.navigate',{url:pageUrl})
    let observed,lastDomState
    for(let index=0;index<100;index++){
      const result=await send('Runtime.evaluate',{expression:`(()=>{const root=document.querySelector('[data-host-preflight="ready"]');return root?{ready:true,title:document.title,heading:document.querySelector('h1')?.textContent,status:document.querySelector('[role="status"]')?.textContent,button:document.querySelector('button')?.textContent}:{ready:false,url:location.href,title:document.title,html:document.documentElement?.outerHTML?.slice(0,500)||''}})()`,returnByValue:true})
      lastDomState=result.result.value
      if(result.result.value?.ready){observed=result.result.value;break}
      await new Promise(ok=>setTimeout(ok,50))
    }
    if(!observed?.ready)throw new Error(`P021_HOST_PREFLIGHT_DOM_TIMEOUT|${JSON.stringify(lastDomState)}`)
    const screenshot=await send('Page.captureScreenshot',{format:'png',captureBeyondViewport:false})
    const png=Buffer.from(screenshot.data,'base64')
    const externalHosts=browserNetwork.filter(url=>{try{return new URL(url).hostname!=='127.0.0.1'}catch{return !url.startsWith('about:')&&!url.startsWith('data:')}})
    if(externalHosts.length)throw new Error('P021_HOST_PREFLIGHT_EXTERNAL_NETWORK')
    await writeFile(join(outputDirectory,'viewport.png'),png)
    const actual={ExecutionStatus:'PASS',FormalScenarioCount:0,FormalTestStarted:false,EvidenceWritten:false,CloudAccessed:false,DatabaseAccessed:false,NpmJsEntrypointVersion:npmStdout.trim(),PageUrl:pageUrl,Viewport:{width:800,height:600,dpr:1},Dom:observed,BrowserSha256:browserSha,ScreenshotSha256:digest(png),NetworkUrls:browserNetwork,ExternalHosts:externalHosts}
    await writeFile(join(outputDirectory,'host-preflight.json'),JSON.stringify(actual,null,2))
    process.stdout.write(JSON.stringify(actual)+'\n')
  }finally{
    if(socket)socket.close()
    if(browser&&!browser.killed){
      const exited=new Promise(ok=>browser.once('exit',ok))
      browser.kill()
      await Promise.race([exited,new Promise(ok=>setTimeout(ok,3000))])
    }
    if(server)await new Promise(ok=>server.close(ok))
    let cleanupError
    for(let attempt=0;attempt<20;attempt++){
      try{await rm(temporaryDirectory,{recursive:true,force:true});cleanupError=undefined;break}
      catch(error){cleanupError=error;await new Promise(ok=>setTimeout(ok,150))}
    }
    if(cleanupError)throw cleanupError
  }
}
