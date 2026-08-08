import type { OrderProjection } from './types'
export const NON_BUSINESS_NAVIGATION=Object.freeze(['BACK','HOME','HELP','SUPPORT']as const)
export function isAllowedProjectionAction(p:OrderProjection|null,code:string):boolean{return!!p?.allowedActions.some(a=>a.actionCode===code&&a.enabled)}
export function isWhitelistedNavigation(code:string):boolean{return(NON_BUSINESS_NAVIGATION as readonly string[]).includes(code)}
