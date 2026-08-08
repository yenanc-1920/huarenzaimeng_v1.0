import { loadAdminPage } from './admin-read.ts'
import type { AdminDataMode, AdminReadState, AdminRole, PageId } from '../domain/admin.ts'
import { loadP021AdminOrderDetail, type P021AdminReadState, type P021AdminRequestedRole } from './admin-order-detail.ts'

export interface AdminReadRequest {
  mode: AdminDataMode | null
  pageId: PageId
  syntheticRole: AdminRole
}

export function createAdminOrderDetailController(sink: (state: P021AdminReadState) => void, loader = loadP021AdminOrderDetail) {
  let generation = 0
  return {
    async refresh(orderRef: string, role: P021AdminRequestedRole) {
      const currentGeneration = ++generation
      sink({ status: 'LOADING', data: null, message: '正在读取订单详情' })
      let nextState: P021AdminReadState
      try { nextState = await loader(orderRef, role) }
      catch { nextState = { status: 'UNAVAILABLE', data: null, message: '订单详情读取失败' } }
      if (currentGeneration !== generation) return false
      sink(nextState)
      return true
    },
  }
}

type StateSink = (state: AdminReadState) => void
type ReadLoader = typeof loadAdminPage

export function createAdminReadController(sink: StateSink, loader: ReadLoader = loadAdminPage) {
  let generation = 0

  return {
    async refresh(request: AdminReadRequest) {
      const currentGeneration = ++generation
      sink({ status: 'LOADING', data: null, message: '正在读取只读数据' })

      let nextState: AdminReadState
      if (!request.mode) {
        nextState = { status: 'UNAVAILABLE', data: null, message: '数据模式配置不受支持' }
      } else {
        try {
          nextState = await loader(request.mode, request.pageId, request.syntheticRole)
        } catch {
          nextState = { status: 'UNAVAILABLE', data: null, message: '后台只读服务连接失败' }
        }
      }

      if (currentGeneration !== generation) return false
      sink(nextState)
      return true
    },
  }
}
