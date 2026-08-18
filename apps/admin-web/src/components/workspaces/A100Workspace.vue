<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { appendCustomerCaseEvent, createCustomerCase, loadCustomerCase, type CaseEventType, type WorkflowDetail, type CustomerCaseDetail, type CustomerCaseEvent } from '../../api/admin-workflow'
import type { AdminPageProjection } from '../../domain/admin'
type Projection = Extract<AdminPageProjection, { pageId: 'A100' }>
const props = defineProps<{ projection: Projection }>()
const emit = defineEmits<{ changed: [] }>()
const selectedRef = ref(props.projection.items[0]?.caseRef ?? '')
const mode = ref<'LIST'|'CREATE'|'FOLLOW_UP'>('LIST')
const detail = ref<WorkflowDetail<CustomerCaseDetail,CustomerCaseEvent>|null>(null)
const loadingDetail = ref(false); const submitting = ref(false); const error = ref(''); const success = ref('')
const createForm = reactive({ sourceType: '', issueType: '', relatedOrderRef: '', priorityCode: 'NORMAL', ownerRef: '', description: '' })
const followForm = reactive<{eventType: CaseEventType; note: string; ownerRef: string; evidenceRef: string}>({ eventType: 'FOLLOW_UP', note: '', ownerRef: '', evidenceRef: '' })
const selected = computed(() => props.projection.items.find(item => item.caseRef === selectedRef.value) ?? props.projection.items[0])
const ownerRequired = computed(() => ['CLAIM','ASSIGN'].includes(followForm.eventType))
const canCreate = computed(() => !submitting.value && createForm.sourceType.trim() && createForm.issueType.trim() && createForm.relatedOrderRef.trim() && createForm.priorityCode.trim() && createForm.description.trim())
const canFollow = computed(() => !submitting.value && detail.value && followForm.note.trim() && (!ownerRequired.value || followForm.ownerRef.trim()))
async function readDetail(){ if(!selectedRef.value){detail.value=null;return} loadingDetail.value=true;error.value='';try{detail.value=await loadCustomerCase(selectedRef.value)}catch(e){detail.value=null;error.value=e instanceof Error?e.message:'案件详情暂不可用'}finally{loadingDetail.value=false} }
watch(() => props.projection, value => { selectedRef.value=value.items[0]?.caseRef??''; mode.value='LIST' })
watch(selectedRef, readDetail, { immediate:true })
async function submitCreate(){if(!canCreate.value)return;submitting.value=true;error.value='';success.value='';try{const result=await createCustomerCase({...createForm,ownerRef:createForm.ownerRef||undefined});success.value=`案件 ${result.objectRef} 已创建`;mode.value='LIST';emit('changed')}catch(e){error.value=e instanceof Error?e.message:'案件创建失败'}finally{submitting.value=false}}
async function submitFollow(){if(!canFollow.value||!detail.value)return;submitting.value=true;error.value='';success.value='';try{await appendCustomerCaseEvent(detail.value.item.caseRef,{...followForm,expectedVersion:detail.value.item.version,ownerRef:followForm.ownerRef||undefined,evidenceRef:followForm.evidenceRef||undefined});await readDetail();success.value='跟进记录已追加';followForm.note='';followForm.evidenceRef='';emit('changed')}catch(e){error.value=e instanceof Error?e.message:'跟进提交失败'}finally{submitting.value=false}}
const eventLabel=(value:string)=>({CREATE:'创建案件',FOLLOW_UP:'记录跟进',CLAIM:'领取案件',ASSIGN:'分配负责人',ESCALATE:'升级处理',RESOLVE:'标记已解决',CLOSE:'关闭案件'}[value]??'处理记录')
const stateLabel=(value:string)=>({OPEN:'待处理',IN_PROGRESS:'处理中',RESOLVED:'已解决',CLOSED:'已关闭'}[value]??'状态无法确认')
const priorityLabel=(value:string)=>({NORMAL:'普通',HIGH:'较高',URGENT:'紧急'}[value]??'优先级无法确认')
</script>

<template>
  <section class="work-grid two-column" data-page-id="UX-A100" data-data-origin="CURRENT_ENVIRONMENT_DATABASE">
    <article class="card list-card">
      <div class="card-head"><div><p class="eyebrow">客服案件</p><h2>案件列表</h2></div><button class="primary" type="button" @click="mode='CREATE';error='';success=''">新增案件</button></div>
      <div v-if="projection.items.length===0" class="empty-panel">当前没有客服案件</div>
      <div v-else class="record-list" role="list"><button v-for="item in projection.items" :key="item.caseRef" class="record-row" :class="{selected:selectedRef===item.caseRef}" @click="selectedRef=item.caseRef;mode='LIST'"><span><b>{{item.caseRef}}</b><small>{{item.issueType}} · {{item.sourceType}}</small></span><em>{{stateLabel(item.state)}}</em></button></div>
    </article>
    <aside class="card detail-card">
      <template v-if="mode==='CREATE'">
        <div class="card-head"><div><p class="eyebrow">新增案件</p><h2>登记客服问题</h2></div><button class="secondary" @click="mode='LIST'">返回列表</button></div>
        <div class="form-grid"><label>来源<input v-model.trim="createForm.sourceType" maxlength="80" placeholder="填写问题来源"></label><label>问题类型<input v-model.trim="createForm.issueType" maxlength="80" placeholder="填写问题类型"></label><label>关联订单<input v-model.trim="createForm.relatedOrderRef" maxlength="120" placeholder="填写订单号"></label><label>优先级<select v-model="createForm.priorityCode"><option value="NORMAL">普通</option><option value="HIGH">较高</option><option value="URGENT">紧急</option></select></label><label>负责人（选填）<input v-model.trim="createForm.ownerRef" maxlength="120" placeholder="填写负责人编号"></label><label class="full-span">问题说明<textarea v-model.trim="createForm.description" maxlength="1000" placeholder="请描述用户问题和已确认事实"></textarea></label></div>
        <p v-if="error" class="form-error" role="alert">{{error}}</p><div class="form-actions"><button class="primary" :disabled="!canCreate" @click="submitCreate">{{submitting?'提交中':'创建案件'}}</button></div>
      </template>
      <template v-else>
        <div class="card-head"><div><p class="eyebrow">案件详情</p><h2>{{selected?.caseRef??'暂无案件'}}</h2></div><button class="primary" :disabled="!selectedRef||loadingDetail" @click="mode='FOLLOW_UP'">新增跟进</button></div>
        <div v-if="loadingDetail" class="empty-panel">正在读取案件详情</div><div v-else-if="error&&!detail" class="empty-panel" role="alert">{{error}}</div>
        <template v-else-if="detail"><dl><div><dt>问题类型</dt><dd>{{detail.item.issueType}}</dd></div><div><dt>来源</dt><dd>{{detail.item.sourceType}}</dd></div><div><dt>关联订单</dt><dd>{{detail.item.relatedOrderRef}}</dd></div><div><dt>优先级</dt><dd>{{priorityLabel(detail.item.priorityCode)}}</dd></div><div><dt>负责人</dt><dd>{{detail.item.ownerRef??'未分配'}}</dd></div><div><dt>状态</dt><dd>{{stateLabel(detail.item.state)}}</dd></div></dl>
          <section v-if="mode==='FOLLOW_UP'" class="workflow-form"><h3>追加跟进记录</h3><div class="form-grid"><label>操作<select v-model="followForm.eventType"><option value="FOLLOW_UP">记录跟进</option><option value="CLAIM">领取案件</option><option value="ASSIGN">分配负责人</option><option value="ESCALATE">升级处理</option><option value="RESOLVE">标记已解决</option><option value="CLOSE">关闭案件</option></select></label><label>负责人{{ownerRequired?'':'（选填）'}}<input v-model.trim="followForm.ownerRef" maxlength="120"></label><label>证据编号（选填）<input v-model.trim="followForm.evidenceRef" maxlength="120"></label><label class="full-span">跟进说明<textarea v-model.trim="followForm.note" maxlength="1000" placeholder="仅记录已确认事实，不修改交易终态"></textarea></label></div><div class="form-actions"><button class="secondary" @click="mode='LIST'">取消</button><button class="primary" :disabled="!canFollow" @click="submitFollow">{{submitting?'提交中':'保存跟进'}}</button></div></section>
          <section class="history-list"><h3>处理记录</h3><div v-if="detail.history.length===0" class="empty-panel">暂无跟进记录</div><article v-for="event in detail.history" :key="event.eventRef"><strong>{{eventLabel(event.eventType)}}</strong><p>{{event.note}}</p><small>{{event.actorRef}} · {{event.createdAt}}</small></article></section>
          <div class="safe-note"><strong>交易安全</strong><p>客服跟进只追加处理记录，不会修改订单、支付、充值或退款结果。</p></div></template>
        <p v-if="success" class="form-success" role="status">{{success}}</p><p v-if="error&&detail" class="form-error" role="alert">{{error}}</p>
      </template>
    </aside>
  </section>
</template>
