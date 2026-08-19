export const USER_AGREEMENT_POLICY_VERSION='2026-08-28'
export const PRIVACY_POLICY_VERSION='2026-08-28'

export type LoginPrivacyState=
  | 'UNSEEN'
  | 'AGREEMENTS_ACCEPTED'
  | 'OFFICIAL_PRIVACY_PENDING'
  | 'OFFICIAL_PRIVACY_GRANTED'
  | 'LOGIN_CODE_PENDING'
  | 'SESSION_ACTIVE'

export type LoginAgreementSelection={
  userAgreementAccepted:boolean
  privacyPolicyAccepted:boolean
}

export type ConsentAcceptance=
  | {policyVersion:typeof USER_AGREEMENT_POLICY_VERSION;accepted:true;policyType:'USER_AGREEMENT'}
  | {policyVersion:typeof PRIVACY_POLICY_VERSION;accepted:true;policyType:'PRIVACY_POLICY'}

export type SessionConsentCommand=Readonly<{
  userAgreementVersion:typeof USER_AGREEMENT_POLICY_VERSION
  privacyPolicyVersion:typeof PRIVACY_POLICY_VERSION
  userAgreementAccepted:true
  privacyPolicyAccepted:true
}>

export function agreementsAccepted(selection:LoginAgreementSelection):boolean{
  return selection.userAgreementAccepted===true&&selection.privacyPolicyAccepted===true
}

export function consentAcceptances(selection:LoginAgreementSelection):ConsentAcceptance[]{
  if(!agreementsAccepted(selection))return[]
  return [
    {policyVersion:USER_AGREEMENT_POLICY_VERSION,accepted:true,policyType:'USER_AGREEMENT'},
    {policyVersion:PRIVACY_POLICY_VERSION,accepted:true,policyType:'PRIVACY_POLICY'}
  ]
}

export function sessionConsentCommand(selection:LoginAgreementSelection):SessionConsentCommand|null{
  if(!agreementsAccepted(selection))return null
  return {userAgreementVersion:USER_AGREEMENT_POLICY_VERSION,privacyPolicyVersion:PRIVACY_POLICY_VERSION,userAgreementAccepted:true,privacyPolicyAccepted:true}
}

export function officialPrivacyGranted(event:unknown):boolean{
  if(!event||typeof event!=='object'||!('detail' in event))return false
  const detail=(event as {detail?:unknown}).detail
  if(!detail||typeof detail!=='object')return false
  return (detail as {errMsg?:unknown}).errMsg==='agreePrivacyAuthorization:ok'
}
