import assert from 'node:assert/strict'
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { P021_V14_SUBCASES, P021_V16_SUBCASES, P021_VISUAL_EXPECTATIONS, P021_VISUAL_SCENARIO_IDS, assertP021V14ActualDomSet, assertP021VisualActual, runP021VisualScenario, runP021VisualSubcase } from '../src/dev/p021-visual-fixtures.ts'
import { createP021PageState } from '../src/domain/order-detail-flow.ts'

if(process.argv.slice(2).length!==1||process.argv[2]!=='--preflight')throw new Error('P021_VISUAL_FINAL_EXECUTION_NOT_AUTHORIZED')
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'..')
const page=fs.readFileSync(path.join(root,'src/pages/order/detail.vue'),'utf8')
assert.equal(P021_VISUAL_SCENARIO_IDS.length,16)
assert.equal(new Set(P021_VISUAL_SCENARIO_IDS).size,16)
assert.deepEqual(Object.keys(P021_VISUAL_EXPECTATIONS),[...P021_VISUAL_SCENARIO_IDS])
for(const scenarioId of P021_VISUAL_SCENARIO_IDS){
  const state=createP021PageState();await runP021VisualScenario(state,scenarioId)
  assert.equal(state.viewState,P021_VISUAL_EXPECTATIONS[scenarioId].expectedViewState,`${scenarioId} deterministic view state`)
  assert.equal(P021_VISUAL_EXPECTATIONS[scenarioId].executionStatus,'NOT_RUN')
  const expected=P021_VISUAL_EXPECTATIONS[scenarioId]
  assert.equal(expected.expectedDom.uniqueText.length,1,`${scenarioId} unique Chinese title`)
  assert.deepEqual(expected.expectedDom.keyboardOrder,expected.expectedDom.actions.map(action=>({REFRESH_ORDER_DETAIL:'重新读取',OPEN_SUPPORT:'联系客服核对',SAFE_BACK:'返回订单'})[action]),`${scenarioId} keyboard order must follow visible actions`)
  assert.equal(expected.expectedDom.focusVisible,true)
  assert.ok(expected.expectedDom.ax.status.name&&expected.expectedDom.ax.status.role)
  const [width,height]=expected.viewport.split('x').map(Number),dpr=2
  const comparatorProbe={titleMatches:[{text:expected.expectedTitle,count:1,locations:['[data-status-card],[data-status-panel]']}],viewport:{width,height},screenshot:{sha256:'PRECHECK_NON_EVIDENCE_SHA',width:width*dpr,height:height*dpr,dpr,fullViewport:true,cropped:false,padded:false},selectors:expected.expectedDom.selectors,actions:expected.expectedDom.actions,keyboardOrder:expected.expectedDom.keyboardOrder,focusVisible:true,ax:expected.expectedDom.ax,forbiddenFindings:Object.fromEntries(expected.forbiddenVisible.map(term=>[term,{count:0,locations:[]}])) ,requiredEvidence:Object.fromEntries(expected.requiredEvidence.map(field=>[field,'PRECHECK_SCHEMA_PRESENT']))}
  assert.equal(assertP021VisualActual(expected,comparatorProbe),true)
  assert.throws(()=>assertP021VisualActual(expected,{...comparatorProbe,titleMatches:[{text:expected.expectedTitle,count:2,locations:['a','b']}] }),/P021_VISUAL_ACTUAL_MISMATCH/)
  const forbiddenTerm=expected.forbiddenVisible[0]
  assert.throws(()=>assertP021VisualActual(expected,{...comparatorProbe,forbiddenFindings:{...comparatorProbe.forbiddenFindings,[forbiddenTerm]:{count:1,locations:['body[0]']}}}),/P021_VISUAL_ACTUAL_MISMATCH/)
}
const v14=[]
for(const parameterId of P021_V14_SUBCASES)v14.push(await runP021VisualSubcase('P021-V14',parameterId))
assert.equal(new Set(v14.map(item=>item.parameterId)).size,4)
assert.ok(v14.every(item=>item.before.detailNodeCount===1&&item.before.oldContentFingerprint&&item.viewState==='NOT_AVAILABLE'&&item.after.detailNodeCount===0&&item.after.oldContentFingerprintCount===0&&item.oldProjectionRestored===false))
assert.equal(new Set(v14.map(item=>item.domSignature)).size,1,'P021-V14 four machine subcases must have one indistinguishable DOM signature')
assert.equal(v14.find(item=>item.parameterId==='UNAUTHENTICATED').apiCallCount,0)
assert.ok(v14.filter(item=>item.parameterId!=='UNAUTHENTICATED').every(item=>item.apiCallCount===1))
const v14Expected=P021_VISUAL_EXPECTATIONS['P021-V14']
const v14DomProbe=Object.fromEntries(P021_V14_SUBCASES.map(parameterId=>[parameterId,{structure:v14Expected.expectedDom.selectors,uniqueText:[{text:v14Expected.expectedTitle,count:1,locations:['[data-status-panel]']}],actions:v14Expected.expectedDom.actions,forbiddenFindings:Object.fromEntries(v14Expected.forbiddenVisible.map(term=>[term,{count:0,locations:[]}]))}]))
assert.equal(assertP021V14ActualDomSet(v14Expected,v14DomProbe),true)
assert.throws(()=>assertP021V14ActualDomSet(v14Expected,{...v14DomProbe,REVOKED:{...v14DomProbe.REVOKED,actions:[]}}),/P021_V14_ACTUAL_DOM_MISMATCH/)
assert.throws(()=>assertP021V14ActualDomSet(v14Expected,{...v14DomProbe,REVOKED:{...v14DomProbe.REVOKED,uniqueText:[{text:v14Expected.expectedTitle,count:1,locations:['[data-status-card]']}]}}),/P021_V14_ACTUAL_DOM_NOT_ISOMORPHIC/)
const v16=[]
for(const parameterId of P021_V16_SUBCASES)v16.push(await runP021VisualSubcase('P021-V16',parameterId))
assert.equal(new Set(v16.map(item=>item.parameterId)).size,4)
assert.ok(v16.every(item=>item.before.detailNodeCount===1&&item.before.oldContentFingerprint&&item.after.detailNodeCount===0&&item.after.oldContentFingerprintCount===0&&item.oldProjectionRestored===false))
assert.equal(v16.find(item=>item.parameterId==='LOWER_VERSION').viewState,'INFORMATION_UPDATED')
assert.equal(v16.find(item=>item.parameterId==='SAME_VERSION_DIFFERENT_PROJECTION').viewState,'INFORMATION_UPDATED')
assert.equal(v16.find(item=>item.parameterId==='TIMELINE_CONFLICT').viewState,'READ_ERROR')
assert.equal(v16.find(item=>item.parameterId==='LATE_GENERATION').viewState,'NOT_AVAILABLE')
for(const token of ['data-page-id="UX-P021"','data-view-state','data-action-container="readonly"','role="list"','role="listitem"','role="status"','role="alert"'])assert.ok(page.includes(token),`P021 visual DOM contract missing ${token}`)
assert.match(page,/import\.meta\.env\.DEV/,'P021 fixture entry must be guarded by the DEV build constant')
assert.match(page,/import\('\.\.\/\.\.\/dev\/p021-visual-fixtures'\)/,'P021 fixture entry must use a dynamic import')
const evidenceSchema={status:'NOT_RUN',runId:null,ready:false,scenarioCount:16,index:null,packages:P021_VISUAL_SCENARIO_IDS.map(scenarioId=>({scenarioId,subcases:(scenarioId==='P021-V14'?P021_V14_SUBCASES:scenarioId==='P021-V16'?P021_V16_SUBCASES:['DEFAULT']).map(parameterId=>({parameterId,expectedDom:P021_VISUAL_EXPECTATIONS[scenarioId].expectedDom,actualDom:null})),expected:P021_VISUAL_EXPECTATIONS[scenarioId],actual:null,screenshot:null,domIndex:null,accessibility:null,processEvidence:null}))}
assert.equal(evidenceSchema.packages.length,16)
assert.ok(evidenceSchema.packages.every(item=>item.actual===null&&item.screenshot===null&&item.processEvidence===null))
assert.equal(evidenceSchema.packages.find(item=>item.scenarioId==='P021-V14').subcases.length,4)
assert.ok(evidenceSchema.packages.find(item=>item.scenarioId==='P021-V14').subcases.every(item=>item.actualDom===null))
console.log(JSON.stringify({preflight:'PASS',formalExecution:'NOT_RUN',runId:null,ready:false,scenarioCount:16,viewportDefault:'375x812',narrowViewport:'320x568',evidenceSchema},null,2))
