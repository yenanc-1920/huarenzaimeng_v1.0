import { spawn } from 'node:child_process'
import { createHash, randomUUID } from 'node:crypto'
import { access, mkdir, open, readFile, readdir, rename, rm, writeFile } from 'node:fs/promises'
import path from 'node:path'
import { fileURLToPath, pathToFileURL } from 'node:url'
import {
  assertLifeContentDualMatrixFixedInputs,
  computeLifeContentImplementationManifest,
  LIFE_CONTENT_EVIDENCE_OUTPUT_DIRECTORY,
  verifyLifeContentMatrixBindings,
} from './run-life-content-evidence.mjs'
import { LIFE_CONTENT_FIXED_INPUTS } from '../src/api/life-content-evidence.ts'

const scriptPath=fileURLToPath(import.meta.url)
const miniappRoot=path.resolve(path.dirname(scriptPath),'..')
const childRunnerPath=path.resolve(path.dirname(scriptPath),'run-life-content-evidence.mjs')
const MAX_CAPTURE_BYTES=4*1024*1024
const PROCESS_EVIDENCE_SCHEMA='D5_INF_FRONTEND_PROCESS_EVIDENCE_V1'
const AUTHORIZATION_KIND='D5_INF_FRONTEND_SINGLE_USE_APPROVAL_V1'

const sha256=(value)=>createHash('sha256').update(value).digest('hex').toUpperCase()
const clone=(value)=>JSON.parse(JSON.stringify(value))
const stable=(value)=>{
  if(Array.isArray(value))return value.map(stable)
  if(value&&typeof value==='object')return Object.fromEntries(Object.keys(value).sort().map((key)=>[key,stable(value[key])]))
  return value
}
const canonical=(value)=>JSON.stringify(stable(value))
const digest=(value)=>sha256(canonical(value))
const exists=async(target)=>access(target).then(()=>true).catch(()=>false)

function argumentValue(flag){const index=process.argv.indexOf(flag);return index>=0?process.argv[index+1]:null}

function collectSecretValues(value,key='',result=[]){
  if(Array.isArray(value)){for(const item of value)collectSecretValues(item,key,result);return result}
  if(value&&typeof value==='object'){
    for(const [childKey,childValue] of Object.entries(value))collectSecretValues(childValue,childKey,result)
    return result
  }
  if(/secret|token|password|credential|private|canary/i.test(key)&&typeof value==='string'&&value.length>0)result.push(value)
  return result
}

function validateAuthorizationForCoordinator(value,authorizationFile){
  if(!value||typeof value!=='object'||Array.isArray(value)||value.authorizationKind!==AUTHORIZATION_KIND)throw new Error('INVALID_COORDINATOR_AUTHORIZATION')
  if(typeof value.authorizationId!=='string'||!/^AUTH-D5-INF-[A-Za-z0-9-]{8,}$/.test(value.authorizationId))throw new Error('INVALID_COORDINATOR_AUTHORIZATION_ID')
  if(typeof value.executionRunId!=='string'||!/^D5-INF-01-FE-[A-Za-z0-9-]{8,}$/.test(value.executionRunId))throw new Error('INVALID_COORDINATOR_RUN_ID')
  if(!/^[A-F0-9]{64}$/.test(value.approvedImplementationSha256??''))throw new Error('INVALID_COORDINATOR_IMPLEMENTATION_SHA')
  assertLifeContentDualMatrixFixedInputs(value.approvedFixedInputs)
  if(canonical(value.approvedFixedInputs)!==canonical(LIFE_CONTENT_FIXED_INPUTS))throw new Error('COORDINATOR_FIXED_INPUTS_MISMATCH')
  const resolvedAuthorizationFile=path.resolve(authorizationFile)
  return Object.freeze({
    ...clone(value),authorizationFile:resolvedAuthorizationFile,
    childConsumptionMarker:`${resolvedAuthorizationFile}.consumed`,coordinatorConsumptionMarker:`${resolvedAuthorizationFile}.coordinator-consumed`,
  })
}

async function coordinatorPreflight(metadata){
  await verifyLifeContentMatrixBindings()
  assertLifeContentDualMatrixFixedInputs(metadata.approvedFixedInputs)
  const implementation=await computeLifeContentImplementationManifest()
  if(implementation.aggregateSha256!==metadata.approvedImplementationSha256)throw new Error('COORDINATOR_APPROVED_IMPLEMENTATION_SHA_MISMATCH')
  if(await exists(metadata.childConsumptionMarker)||await exists(metadata.coordinatorConsumptionMarker))throw new Error('COORDINATOR_AUTHORIZATION_ALREADY_CONSUMED')
  const formalDirectory=path.resolve(LIFE_CONTENT_EVIDENCE_OUTPUT_DIRECTORY),outputParent=path.dirname(formalDirectory)
  if(await exists(formalDirectory))throw new Error('COORDINATOR_FORMAL_DIRECTORY_ALREADY_EXISTS')
  const siblings=await readdir(outputParent).catch((error)=>error?.code==='ENOENT'?[]:Promise.reject(error))
  if(siblings.some((name)=>name.includes(metadata.executionRunId)))throw new Error('COORDINATOR_RUN_ID_ALREADY_USED')
  return {implementation,formalDirectory,outputParent}
}

async function consumeCoordinatorAuthorization(metadata){
  const handle=await open(metadata.coordinatorConsumptionMarker,'wx')
  try{await handle.writeFile(JSON.stringify({authorizationId:metadata.authorizationId,executionRunId:metadata.executionRunId,consumedAt:new Date().toISOString(),consumer:'PARENT_COORDINATOR'})+'\n','utf8')}
  finally{await handle.close()}
}

function sanitizedCommand(args,authorizationFile,stagingDirectory){
  const sanitized=args.map((value)=>value===authorizationFile?'[LOCAL_AUTHORIZATION_FILE]':value===stagingDirectory?'[UNPUBLISHED_STAGING_DIRECTORY]':value)
  return {executable:process.execPath,argv:sanitized,display:[process.execPath,...sanitized].join(' ')}
}

function captureChildProcess(args){
  return new Promise((resolve)=>{
    const startedAt=new Date().toISOString(),stdoutChunks=[],stderrChunks=[],streamErrors=[]
    let stdoutBytes=0,stderrBytes=0,overflow=false,spawnError=null,settled=false
    const child=spawn(process.execPath,args,{cwd:miniappRoot,windowsHide:true,stdio:['ignore','pipe','pipe']})
    const capture=(chunks,kind)=>(chunk)=>{
      const bytes=Buffer.from(chunk)
      if(kind==='stdout')stdoutBytes+=bytes.length;else stderrBytes+=bytes.length
      if(stdoutBytes+stderrBytes>MAX_CAPTURE_BYTES){overflow=true;child.kill();return}
      chunks.push(bytes)
    }
    child.stdout.on('data',capture(stdoutChunks,'stdout'));child.stderr.on('data',capture(stderrChunks,'stderr'))
    child.stdout.on('error',(error)=>streamErrors.push(`STDOUT_${error.code??error.name}`))
    child.stderr.on('error',(error)=>streamErrors.push(`STDERR_${error.code??error.name}`))
    child.on('error',(error)=>{spawnError=error.code??error.name})
    child.on('close',(exitCode,signal)=>{
      if(settled)return;settled=true
      resolve({startedAt,endedAt:new Date().toISOString(),exitCode,signal,spawnError,streamErrors,overflow,
        stdout:Buffer.concat(stdoutChunks),stderr:Buffer.concat(stderrChunks)})
    })
  })
}

function secretScan(capture,metadata){
  const stdoutText=capture.stdout.toString('utf8'),stderrText=capture.stderr.toString('utf8')
  const environmentSecrets=['HZ_TEST_ACCESS_TOKEN','HZ_CONTENT_ADMIN_TOKEN'].map((name)=>process.env[name]).filter((value)=>typeof value==='string'&&value.length>0)
  const secretValues=[...new Set([...collectSecretValues(metadata),...environmentSecrets])]
  const forbiddenNames=['HZ_TEST_ACCESS_TOKEN','HZ_CONTENT_ADMIN_TOKEN']
  const valueHits=secretValues.filter((value)=>stdoutText.includes(value)||stderrText.includes(value)).length
  const nameHits=forbiddenNames.filter((value)=>stdoutText.includes(value)||stderrText.includes(value)).length
  return {stdoutText,stderrText,valueHits,nameHits,forbiddenCount:valueHits+nameHits}
}

function parseSingleChildSummary(stdoutText){
  const lines=stdoutText.split(/\r?\n/).map((line)=>line.trim()).filter(Boolean)
  if(lines.length!==1)throw new Error('BLOCKED_OUTPUT_CAPTURE_LOST:CHILD_STDOUT_NOT_SINGLE_SUMMARY')
  try{return JSON.parse(lines[0])}catch{throw new Error('BLOCKED_OUTPUT_CAPTURE_LOST:CHILD_STDOUT_NOT_JSON')}
}

function verifyObjectDigest(value,digestField,errorCode){
  const expected=value[digestField],base=clone(value);delete base[digestField]
  if(typeof expected!=='string'||digest(base)!==expected)throw new Error(`BLOCKED_OUTPUT_CAPTURE_LOST:${errorCode}`)
  return expected
}

async function readAndVerifyChildResult(metadata,gate,stagingDirectory,summary){
  if(summary.executionRunId!==metadata.executionRunId||summary.authorizationId!==metadata.authorizationId||summary.frontendImplementationSha256!==gate.implementation.aggregateSha256)throw new Error('BLOCKED_OUTPUT_CAPTURE_LOST:CHILD_IDENTITY_OR_SHA_MISMATCH')
  if(summary.packageCount!==13||summary.parameterCount!==26||summary.unpublishedResultReady!==true)throw new Error('BLOCKED_OUTPUT_CAPTURE_LOST:CHILD_DENOMINATOR_MISMATCH')
  if(typeof summary.childToolVersions?.node!=='string'||typeof summary.childToolVersions?.platform!=='string'||typeof summary.childToolVersions?.architecture!=='string')throw new Error('BLOCKED_OUTPUT_CAPTURE_LOST:CHILD_TOOL_VERSION_MISSING')
  const files=(await readdir(stagingDirectory)).sort()
  if(files.length!==15||!files.includes('EvidencePackageIndex.json')||!files.includes('EvidencePackage.json'))throw new Error('BLOCKED_OUTPUT_CAPTURE_LOST:CHILD_FILE_SET_INCOMPLETE')
  const packages=[]
  for(let index=1;index<=13;index++){
    const id=`INF-EP-${String(index).padStart(3,'0')}`,file=`${id}.json`
    if(!files.includes(file))throw new Error(`BLOCKED_OUTPUT_CAPTURE_LOST:MISSING_${id}`)
    const value=JSON.parse(await readFile(path.join(stagingDirectory,file),'utf8'))
    if(value.evidencePackageId!==id||value.executionRunId!==metadata.executionRunId)throw new Error(`BLOCKED_OUTPUT_CAPTURE_LOST:PACKAGE_IDENTITY_${id}`)
    assertLifeContentDualMatrixFixedInputs(value.fixedInputs)
    if(canonical(value.fixedInputs)!==canonical(LIFE_CONTENT_FIXED_INPUTS))throw new Error(`BLOCKED_OUTPUT_CAPTURE_LOST:PACKAGE_FIXED_INPUTS_${id}`)
    const packageDigest=verifyObjectDigest(value,'evidencePackageDigest',`PACKAGE_DIGEST_${id}`)
    if(summary.packageDigests?.[id]!==packageDigest)throw new Error(`BLOCKED_OUTPUT_CAPTURE_LOST:PACKAGE_SUMMARY_DIGEST_${id}`)
    packages.push(value)
  }
  const index=JSON.parse(await readFile(path.join(stagingDirectory,'EvidencePackageIndex.json'),'utf8'))
  const bundle=JSON.parse(await readFile(path.join(stagingDirectory,'EvidencePackage.json'),'utf8'))
  assertLifeContentDualMatrixFixedInputs(index.fixedInputs);assertLifeContentDualMatrixFixedInputs(bundle.fixedInputs)
  if(canonical(index.fixedInputs)!==canonical(LIFE_CONTENT_FIXED_INPUTS)||canonical(bundle.fixedInputs)!==canonical(LIFE_CONTENT_FIXED_INPUTS))throw new Error('BLOCKED_OUTPUT_CAPTURE_LOST:INDEX_BUNDLE_FIXED_INPUTS')
  const indexDigest=verifyObjectDigest(index,'evidencePackageIndexDigest','INDEX_DIGEST')
  const bundleDigest=verifyObjectDigest(bundle,'evidencePackageDigest','BUNDLE_DIGEST')
  if(indexDigest!==summary.indexDigest)throw new Error('BLOCKED_OUTPUT_CAPTURE_LOST:INDEX_SUMMARY_DIGEST')
  if(bundleDigest!==summary.bundleDigest)throw new Error('BLOCKED_OUTPUT_CAPTURE_LOST:BUNDLE_SUMMARY_DIGEST')
  if(index.executionRunId!==metadata.executionRunId||bundle.executionRunId!==metadata.executionRunId||index.frontendImplementationSha256!==gate.implementation.aggregateSha256||bundle.frontendImplementation?.aggregateSha256!==gate.implementation.aggregateSha256)throw new Error('BLOCKED_OUTPUT_CAPTURE_LOST:INDEX_BUNDLE_IDENTITY')
  if(index.packageCount!==13||index.parameterCount!==26||bundle.packageCount!==13||bundle.parameterCount!==26)throw new Error('BLOCKED_OUTPUT_CAPTURE_LOST:INDEX_BUNDLE_DENOMINATOR')
  const packageDigestMap=Object.fromEntries(packages.map((item)=>[item.evidencePackageId,item.evidencePackageDigest]))
  if(Object.keys(summary.packageDigests??{}).length!==13||index.packages.length!==13)throw new Error('BLOCKED_OUTPUT_CAPTURE_LOST:PACKAGE_DIGEST_SET_SIZE')
  if(index.packages.some((item)=>packageDigestMap[item.evidencePackageId]!==item.evidencePackageDigest))throw new Error('BLOCKED_OUTPUT_CAPTURE_LOST:INDEX_PACKAGE_DIGEST_LINK')
  if(bundle.indexDigest!==indexDigest||canonical(bundle.packageDigests)!==canonical(packages.map((item)=>item.evidencePackageDigest)))throw new Error('BLOCKED_OUTPUT_CAPTURE_LOST:BUNDLE_DIGEST_LINK')
  return {packages,index,bundle,summary}
}

function redactText(value,metadata,stagingDirectory){
  const redactions=[metadata.authorizationFile,stagingDirectory,...collectSecretValues(metadata),process.env.HZ_TEST_ACCESS_TOKEN,process.env.HZ_CONTENT_ADMIN_TOKEN]
    .filter((item)=>typeof item==='string'&&item.length>0).sort((left,right)=>right.length-left.length)
  return redactions.reduce((textValue,secret)=>textValue.split(secret).join('[REDACTED]'),value)
}

function attachProcessEvidence(metadata,gate,capture,scan,command,childResult){
  const processEvidenceRef=`PROC-${metadata.executionRunId}-${sha256(metadata.authorizationId).slice(0,12)}`
  const packages=childResult.packages.map((source)=>{
    const value=clone(source);delete value.evidencePackageDigest;value.processEvidenceRef=processEvidenceRef;value.evidencePackageDigest=digest(value);return value
  })
  const packageDigests=Object.fromEntries(packages.map((item)=>[item.evidencePackageId,item.evidencePackageDigest]))
  const index=clone(childResult.index);delete index.evidencePackageIndexDigest;index.processEvidenceRef=processEvidenceRef
  index.packages=index.packages.map((item)=>({...item,evidencePackageDigest:packageDigests[item.evidencePackageId],processEvidenceRef}))
  index.evidencePackageIndexDigest=digest(index)
  const bundle=clone(childResult.bundle);delete bundle.evidencePackageDigest;bundle.processEvidenceRef=processEvidenceRef
  bundle.indexDigest=index.evidencePackageIndexDigest;bundle.packageDigests=packages.map((item)=>item.evidencePackageDigest);bundle.publicationState='READY';bundle.evidencePackageDigest=digest(bundle)
  const processEvidenceBase={
    schema:PROCESS_EVIDENCE_SCHEMA,processEvidenceRef,publicationState:'READY',executionRunId:metadata.executionRunId,
    authorizationId:metadata.authorizationId,frontendImplementationSha256:gate.implementation.aggregateSha256,
    fixedInputs:{...LIFE_CONTENT_FIXED_INPUTS},
    childCommand:command,startedAt:capture.startedAt,endedAt:capture.endedAt,
    childProcess:{exitCode:capture.exitCode,signal:capture.signal,spawnError:capture.spawnError,streamErrors:capture.streamErrors,
      stdout:{byteLength:capture.stdout.length,sha256:sha256(capture.stdout),utf8:redactText(scan.stdoutText,metadata,childResult.stagingDirectory)},
      stderr:{byteLength:capture.stderr.length,sha256:sha256(capture.stderr),utf8:redactText(scan.stderrText,metadata,childResult.stagingDirectory)}},
    toolVersions:{parentNode:process.version,child:childResult.summary.childToolVersions,platform:process.platform,architecture:process.arch},
    captureValidation:{osExitObserved:capture.exitCode!==null,stdoutObserved:Buffer.isBuffer(capture.stdout),stderrObserved:Buffer.isBuffer(capture.stderr),epipeDetected:false,forbiddenCount:scan.forbiddenCount},
    indexDigest:index.evidencePackageIndexDigest,bundleDigest:bundle.evidencePackageDigest,packageDigests,
  }
  const processEvidence={...processEvidenceBase,processEvidenceDigest:digest(processEvidenceBase)}
  return {packages,index,bundle,processEvidence,processEvidenceRef}
}

async function publishReadyAtomically(metadata,gate,coordinatorDirectory,finalEvidence){
  const publicationDirectory=path.join(gate.outputParent,`.D5-INF-01前端.publication-${randomUUID()}`)
  if(await exists(publicationDirectory)||await exists(gate.formalDirectory))throw new Error('BLOCKED_OUTPUT_CAPTURE_LOST:PUBLICATION_TARGET_EXISTS')
  await mkdir(publicationDirectory,{recursive:false})
  try{
    for(const item of finalEvidence.packages)await writeFile(path.join(publicationDirectory,`${item.evidencePackageId}.json`),JSON.stringify(item,null,2)+'\n','utf8')
    await writeFile(path.join(publicationDirectory,'EvidencePackageIndex.json'),JSON.stringify(finalEvidence.index,null,2)+'\n','utf8')
    await writeFile(path.join(publicationDirectory,'EvidencePackage.json'),JSON.stringify(finalEvidence.bundle,null,2)+'\n','utf8')
    await writeFile(path.join(publicationDirectory,'ProcessEvidence.json'),JSON.stringify(finalEvidence.processEvidence,null,2)+'\n','utf8')
    const files=await readdir(publicationDirectory)
    if(files.length!==16||!files.includes('ProcessEvidence.json'))throw new Error('BLOCKED_OUTPUT_CAPTURE_LOST:FINAL_FILE_SET_INCOMPLETE')
    const values=await Promise.all(files.map(async(file)=>JSON.parse(await readFile(path.join(publicationDirectory,file),'utf8'))))
    if(values.some((value)=>value.processEvidenceRef!==finalEvidence.processEvidenceRef))throw new Error('BLOCKED_OUTPUT_CAPTURE_LOST:PROCESS_EVIDENCE_REF_MISSING')
    for(const value of values){
      if(value.fixedInputs){assertLifeContentDualMatrixFixedInputs(value.fixedInputs);if(canonical(value.fixedInputs)!==canonical(LIFE_CONTENT_FIXED_INPUTS))throw new Error('BLOCKED_OUTPUT_CAPTURE_LOST:FINAL_FIXED_INPUTS_MISMATCH')}
      if(value.evidencePackageId?.startsWith('INF-EP-'))verifyObjectDigest(value,'evidencePackageDigest','FINAL_PACKAGE_DIGEST')
    }
    verifyObjectDigest(values.find((value)=>Object.hasOwn(value,'evidencePackageIndexDigest')),'evidencePackageIndexDigest','FINAL_INDEX_DIGEST')
    verifyObjectDigest(values.find((value)=>value.schema===PROCESS_EVIDENCE_SCHEMA),'processEvidenceDigest','FINAL_PROCESS_DIGEST')
    verifyObjectDigest(values.find((value)=>value.publicationState==='READY'&&Object.hasOwn(value,'packageDigests')&&!Object.hasOwn(value,'processEvidenceDigest')),'evidencePackageDigest','FINAL_BUNDLE_DIGEST')
    if(await exists(gate.formalDirectory))throw new Error('BLOCKED_OUTPUT_CAPTURE_LOST:FORMAL_DIRECTORY_RACE')
    await rename(publicationDirectory,gate.formalDirectory)
  }catch(error){await rm(publicationDirectory,{recursive:true,force:true});throw error}
  await rm(coordinatorDirectory,{recursive:true,force:true}).catch(()=>undefined)
}

export function buildLifeContentCoordinatorManifest(){
  return Object.freeze({mode:'PARENT_COORDINATOR_IMPLEMENTED_FINAL_RUN_NOT_EXECUTED',childRunner:'run-life-content-evidence.mjs',
    captures:['SANITIZED_COMMAND','STDOUT_BYTES','STDERR_BYTES','OS_EXIT','STARTED_AT','ENDED_AT'],
    failureDisposition:'BLOCKED_OUTPUT_CAPTURE_LOST_NO_PUBLISH_NO_RETRY',processEvidenceRequired:true,atomicReadyPublication:true})
}

async function coordinateFinalRun(authorizationFile){
  const metadata=validateAuthorizationForCoordinator(JSON.parse(await readFile(path.resolve(authorizationFile),'utf8')),authorizationFile)
  const gate=await coordinatorPreflight(metadata)
  await mkdir(gate.outputParent,{recursive:true})
  await consumeCoordinatorAuthorization(metadata)
  const coordinatorDirectory=path.join(gate.outputParent,`.D5-INF-01前端.coordinator-${randomUUID()}`)
  const stagingDirectory=path.join(coordinatorDirectory,'child-result')
  const childArgs=[childRunnerPath,'--child-run','--authorization-file',metadata.authorizationFile,'--staging-directory',stagingDirectory]
  await mkdir(coordinatorDirectory,{recursive:false})
  try{
    const command=sanitizedCommand(childArgs,metadata.authorizationFile,stagingDirectory)
    const capture=await captureChildProcess(childArgs)
    const scan=secretScan(capture,metadata)
    const epipeDetected=capture.streamErrors.some((value)=>value.includes('EPIPE'))||/EPIPE/.test(scan.stdoutText)||/EPIPE/.test(scan.stderrText)
    if(capture.overflow||capture.spawnError||capture.streamErrors.length||capture.signal!==null||capture.exitCode!==0||epipeDetected||scan.forbiddenCount!==0)throw new Error('BLOCKED_OUTPUT_CAPTURE_LOST:CHILD_PROCESS_CAPTURE_INVALID')
    if(!await exists(metadata.childConsumptionMarker))throw new Error('BLOCKED_OUTPUT_CAPTURE_LOST:CHILD_AUTHORIZATION_NOT_CONSUMED')
    const consumed=JSON.parse(await readFile(metadata.childConsumptionMarker,'utf8'))
    if(consumed.authorizationId!==metadata.authorizationId||consumed.executionRunId!==metadata.executionRunId)throw new Error('BLOCKED_OUTPUT_CAPTURE_LOST:CONSUMPTION_MARKER_MISMATCH')
    const summary=parseSingleChildSummary(scan.stdoutText)
    const childResult=await readAndVerifyChildResult(metadata,gate,stagingDirectory,summary)
    childResult.stagingDirectory=stagingDirectory
    const finalEvidence=attachProcessEvidence(metadata,gate,capture,scan,command,childResult)
    await publishReadyAtomically(metadata,gate,coordinatorDirectory,finalEvidence)
    return {executionRunId:metadata.executionRunId,authorizationId:metadata.authorizationId,frontendImplementationSha256:gate.implementation.aggregateSha256,
      publicationState:'READY',processEvidenceRef:finalEvidence.processEvidenceRef,indexDigest:finalEvidence.index.evidencePackageIndexDigest,bundleDigest:finalEvidence.bundle.evidencePackageDigest}
  }catch(error){
    if(await exists(coordinatorDirectory))await rm(coordinatorDirectory,{recursive:true,force:true})
    if(error instanceof Error&&error.message.startsWith('BLOCKED_OUTPUT_CAPTURE_LOST'))throw error
    throw new Error(`BLOCKED_OUTPUT_CAPTURE_LOST:${error instanceof Error?error.message:'UNKNOWN_COORDINATOR_FAILURE'}`)
  }
}

async function main(){
  const args=new Set(process.argv.slice(2))
  if(args.has('--preflight')){const matrixBindings=await verifyLifeContentMatrixBindings();console.log(JSON.stringify({...buildLifeContentCoordinatorManifest(),matrixBindings}));return}
  if(!args.has('--final-run'))throw new Error('PARENT_COORDINATOR_REQUIRES_EXPLICIT_FINAL_RUN')
  const authorizationFile=argumentValue('--authorization-file')
  if(!authorizationFile)throw new Error('PARENT_COORDINATOR_REQUIRES_AUTHORIZATION_FILE')
  const result=await coordinateFinalRun(authorizationFile)
  console.log(JSON.stringify(result))
}

if(process.argv[1]&&pathToFileURL(path.resolve(process.argv[1])).href===import.meta.url){
  main().catch((error)=>{console.error(error instanceof Error?error.message:String(error));process.exitCode=1})
}
