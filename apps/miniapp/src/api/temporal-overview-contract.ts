export type ClockAuthorityState = 'BOTH_AVAILABLE' | 'DHAKA_UNAVAILABLE' | 'BEIJING_UNAVAILABLE' | 'BOTH_UNAVAILABLE' | 'STALE'
export type ClockViewState = 'LOADING' | ClockAuthorityState | 'RECOVERED'
export type HolidayState = 'NO_HOLIDAY_CONFIRMED' | 'CONFIRMED_HOLIDAY' | 'PENDING_CONFIRMATION' | 'READ_ERROR' | 'STALE_OR_EXPIRED' | 'UNPUBLISHED'

export interface TemporalClock {
  cityCode: 'DHAKA' | 'BEIJING'
  displayName: string
  zoneId: 'Asia/Dhaka' | 'Asia/Shanghai'
  localDate: string | null
  localTime: string | null
  availabilityState: 'AVAILABLE' | 'UNAVAILABLE'
}

export interface TemporalHoliday {
  countryCode: 'CN' | 'BD'
  localDate: string
  state: HolidayState
  holidayId: string | null
  name: string | null
  note: string | null
  sourceType: string | null
  sourceCoverageDate: string | null
  effectiveFrom: string | null
  effectiveTo: string | null
  version: string | null
}

export interface TemporalOverview {
  requestRef: string
  projectCode: string
  schemaVersion: 'TEMPORAL_OVERVIEW_V1'
  referenceInstant: string
  generatedAt: string
  timeZoneRuleVersion: string
  clockStaleAfterSeconds: number
  clockState: ClockAuthorityState
  clocks: { dhaka: TemporalClock; beijing: TemporalClock }
  holidayRuleVersion: string
  holidays: { china: TemporalHoliday; bangladesh: TemporalHoliday }
  retryClass: 'NONE' | 'USER_INITIATED_READ_ONLY'
}

export interface TemporalOverviewReadResponse {
  body: unknown
  statusCode: number
  cacheControl: string | null
}
