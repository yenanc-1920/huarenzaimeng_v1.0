import type { ContentErrorReportCommand, ContentErrorReportResult, DirectoryDetailResult, DirectoryItem, DirectorySummary } from '../domain/types'
import type { CommandIdentity } from '../domain/command-identity'

const object = (value: unknown): value is Record<string, unknown> => typeof value === 'object' && value !== null
const text = (value: unknown): value is string => typeof value === 'string' && value.length > 0
const integer = (value: unknown): value is number => typeof value === 'number' && Number.isSafeInteger(value) && value > 0
const instant = (value: unknown): value is string => text(value) && Number.isFinite(Date.parse(value))

export function parsePublicContentPage(value: unknown): DirectorySummary[] {
  if (!object(value) || !Array.isArray(value.items) || !Number.isSafeInteger(value.count) || value.count < 0 || value.count !== value.items.length) throw new Error('INVALID_CONTENT_PAGE_DTO')
  return value.items.map((entry) => {
    if (!object(entry) || !text(entry.contentRef) || !text(entry.title) || !text(entry.summary) || !text(entry.category)
      || !integer(entry.contentVersion) || !instant(entry.validUntil) || !instant(entry.updatedAt) || entry.qualification !== 'ELIGIBLE') {
      throw new Error('INVALID_CONTENT_SUMMARY_DTO')
    }
    return { contentRef:entry.contentRef, contentVersion:entry.contentVersion, title:entry.title, summary:entry.summary,
      category:entry.category, validUntil:entry.validUntil, updatedAt:entry.updatedAt, publicationState:'ELIGIBLE' }
  })
}

export function parsePublicContentProjection(value: unknown): DirectoryItem & { publicationState: 'ELIGIBLE' } {
  if (!object(value) || !text(value.contentRef) || !text(value.title) || !text(value.summary) || !text(value.category)
    || value.sourceCategory !== 'SELF_RESEARCH' || value.verificationScope !== 'NAME_AND_PUBLIC_CONTACT_CHANNELS'
    || !instant(value.verifiedAt) || !instant(value.validUntil) || !integer(value.version) || !instant(value.updatedAt)
    || !text(value.disclaimer) || !value.disclaimer.includes('不构成')) {
    throw new Error('INVALID_CONTENT_DETAIL_DTO')
  }
  return { contentRef:value.contentRef, contentVersion:value.version, title:value.title, summary:value.summary,
    category:value.category, sourceCategory:'SELF_RESEARCH', sourceType:'自营调研来源', verificationScope:'NAME_AND_PUBLIC_CONTACT_CHANNELS',
    verifiedAt:value.verifiedAt, validUntil:value.validUntil, updatedAt:value.updatedAt,
    disclaimer:value.disclaimer, publicationState:'ELIGIBLE' }
}

export function mapContentProjectCode(projectCode: string): Exclude<DirectoryDetailResult, { outcome: 'READY' }> | null {
  if (projectCode === 'RESOURCE_NOT_CONFIRMABLE') return { outcome:'EMPTY' }
  if (projectCode === 'CONTENT_UNDER_REVIEW') return { outcome:'REVIEW' }
  if (projectCode === 'CONTENT_REMOVED') return { outcome:'REMOVED' }
  if (projectCode === 'CONTENT_QUALIFICATION_UNKNOWN' || projectCode === 'CONTENT_VERSION_STALE') return { outcome:'UNKNOWN' }
  return null
}

export function buildContentErrorReport(identity: CommandIdentity, contentVersion: number, reason: string): ContentErrorReportCommand {
  if (!text(identity.commandId) || !text(identity.idempotencyKey) || !integer(contentVersion) || !text(reason)) throw new Error('INVALID_CONTENT_REPORT_COMMAND')
  return { commandId:identity.commandId, idempotencyKey:identity.idempotencyKey, contentVersion,
    expectedAggregateVersion:contentVersion, reason }
}

export function parseContentErrorReportReceipt(value: unknown): ContentErrorReportResult {
  if (!object(value) || !text(value.supportRef) || value.status !== 'CONTENT_ERROR_REPORTED' || value.reviewTarget !== 'A120') throw new Error('INVALID_CONTENT_REPORT_RECEIPT')
  return { outcome:'CONTENT_ERROR_REPORTED', supportRef:value.supportRef, reviewTarget:'A120' }
}
