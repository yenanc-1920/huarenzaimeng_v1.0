import { mockCatalog, mockDirectory, mockDirectoryDetail, mockEligibility, mockOrders, mockProjection, mockQuote, mockRecovery, mockReportDirectoryError, mockSupport } from './mock'
import { buildContentErrorReport, mapContentProjectCode, parseContentErrorReportReceipt, parsePublicContentPage, parsePublicContentProjection } from './content-contract'
import { parseProjectProjection, parseProjectQuote, toOrderProjection, toQuoteSnapshot, type ProjectProjection } from './project-contract'
import { acceptNewerProjection } from '../domain/projection'
import { getOrCreateCommand } from '../domain/command-identity'
import type { CatalogItem, ContentErrorReportResult, DirectoryDetailResult, DirectorySummary, EligibilityResult, OrderProjection, OrderSummary, QuoteSnapshot, SupportCase } from '../domain/types'

const baseUrl = (import.meta.env.VITE_API_BASE_URL || '/api/v1').replace(/\/$/, '')
const contentBaseUrl = (import.meta.env.VITE_CONTENT_API_BASE_URL || '/project-api/v1').replace(/\/$/, '')
const useProjectMockApi = import.meta.env.VITE_USE_PROJECT_MOCK_API === 'true'
const projectSubjectRef = import.meta.env.VITE_MOCK_PROJECT_SUBJECT_REF || 'miniapp-local-mock-subject-v1'

export class ProjectApiError extends Error {
  constructor(public projectCode: string) { super(projectCode) }
}

const object = (value: unknown): value is Record<string, unknown> => typeof value === 'object' && value !== null

function requestData(path: string, method: 'GET' | 'POST', data?: unknown, root = baseUrl): Promise<unknown> {
  return new Promise((resolve, reject) => uni.request<unknown>({
    url: `${root}${path}`,
    method,
    data,
    header: { 'X-Project-Subject-Ref': projectSubjectRef },
    success: ({ data: body }) => {
      if (!object(body) || typeof body.status !== 'string' || typeof body.projectCode !== 'string') {
        reject(new ProjectApiError('INVALID_ENVELOPE'))
        return
      }
      if (body.status === 'ACCEPTED' && body.projectCode === 'OK' && body.data !== null && body.data !== undefined) {
        resolve(body.data)
        return
      }
      reject(new ProjectApiError(body.projectCode))
    },
    fail: () => reject(new ProjectApiError('NETWORK_ERROR')),
  }))
}

const projectProjection = async (path: string, method: 'GET' | 'POST', data?: unknown): Promise<ProjectProjection> => parseProjectProjection(await requestData(path, method, data))

export const api = {
  checkEligibility: (phone: string): Promise<EligibilityResult> => mockEligibility(phone),
  getCatalog: (): Promise<CatalogItem[]> => mockCatalog(),
  async createQuote(maskedPhone: string, productRef: string): Promise<QuoteSnapshot> {
    if (!useProjectMockApi) return mockQuote(maskedPhone)
    const quote = parseProjectQuote(await requestData('/quotes', 'POST', { phone:maskedPhone, operatorCode:'MOCK_OPERATOR', productCode:productRef, mnpState:'CONFIRMED' }))
    return toQuoteSnapshot(quote)
  },
  async createOrder(quoteRef: string, commandId: string, idempotencyKey: string): Promise<ProjectProjection> {
    return projectProjection('/orders', 'POST', { quoteRef, commandId, idempotencyKey })
  },
  async confirmMockPayment(orderRef: string, commandId: string, idempotencyKey: string, expectedProjectionVersion: number, expectedAggregateVersion: number): Promise<ProjectProjection> {
    return projectProjection(`/orders/${encodeURIComponent(orderRef)}/mock-payment`, 'POST', { commandId, idempotencyKey, expectedProjectionVersion, expectedAggregateVersion })
  },
  async completeMockTopup(orderRef: string, commandId: string, idempotencyKey: string, expectedProjectionVersion: number, expectedAggregateVersion: number, mnpState: 'CONFIRMED' | 'UNKNOWN'): Promise<ProjectProjection> {
    return projectProjection(`/orders/${encodeURIComponent(orderRef)}/mock-topup`, 'POST', { commandId, idempotencyKey, expectedProjectionVersion, expectedAggregateVersion, mnpState })
  },
  async getCoreProjection(orderRef: string): Promise<ProjectProjection> {
    if (!useProjectMockApi) throw new ProjectApiError('PROJECT_MOCK_API_DISABLED')
    return projectProjection(`/orders/${encodeURIComponent(orderRef)}/projection`, 'GET')
  },
  async getProjection(orderRef: string): Promise<OrderProjection> {
    if (!orderRef) throw new Error('ORDER_REF_REQUIRED')
    const projection = useProjectMockApi ? toOrderProjection(await projectProjection(`/orders/${encodeURIComponent(orderRef)}/projection`, 'GET')) : await mockProjection(orderRef)
    return acceptNewerProjection(orderRef, projection)
  },
  getOrders: (): Promise<OrderSummary[]> => mockOrders(),
  recoverOrder: (_input: string): Promise<{outcome:'GENERIC_ACCEPTED';supportRef:string}> => mockRecovery(),
  getSupportCase: (): Promise<SupportCase> => mockSupport(),
  async getDirectory(): Promise<DirectorySummary[]> {
    if (!useProjectMockApi) return mockDirectory()
    return parsePublicContentPage(await requestData('/content/items', 'GET', undefined, contentBaseUrl))
  },
  async getDirectoryDetail(contentRef: string, contentVersion: number): Promise<DirectoryDetailResult> {
    if (!useProjectMockApi) return mockDirectoryDetail(contentRef, contentVersion)
    try {
      const item = parsePublicContentProjection(await requestData(`/content/items/${encodeURIComponent(contentRef)}?contentVersion=${contentVersion}`, 'GET', undefined, contentBaseUrl))
      if (item.contentVersion !== contentVersion) throw new Error('CONTENT_VERSION_RESPONSE_MISMATCH')
      return { outcome:'READY', item }
    } catch (error) {
      if (error instanceof ProjectApiError) {
        const mapped = mapContentProjectCode(error.projectCode)
        if (mapped) return mapped
      }
      throw error
    }
  },
  async reportDirectoryError(contentRef: string, contentVersion: number, reason: string): Promise<ContentErrorReportResult> {
    const identity = getOrCreateCommand(uni, `content-report:${contentRef}:${contentVersion}`)
    const command = buildContentErrorReport(identity, contentVersion, reason)
    if (!useProjectMockApi) return mockReportDirectoryError(contentRef, command)
    return parseContentErrorReportReceipt(await requestData(`/content/items/${encodeURIComponent(contentRef)}/reports`, 'POST', command, contentBaseUrl))
  },
}

export const apiRuntime = Object.freeze({ mode:useProjectMockApi ? 'PROJECT_MOCK_API' : 'BUILTIN_MOCK', baseUrl, contentBaseUrl, subjectRefSemantics:'LOCAL_MOCK_ROUTING_ONLY_NOT_TRUSTED_IDENTITY' })
