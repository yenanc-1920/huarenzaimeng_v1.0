import { isCompleteSnapshot, samePriceSnapshot, type PriceSnapshotIdentity } from './price-snapshot.ts'

export interface SnapshotStorage { getStorageSync(key:string):unknown; setStorageSync(key:string,value:unknown):void }
const keyFor=(orderRef:string)=>`frozenPriceSnapshot:${orderRef}`

export function freezeOrderSnapshot(storage:SnapshotStorage,orderRef:string,snapshot:PriceSnapshotIdentity):boolean{
  if(!orderRef||!isCompleteSnapshot(snapshot))return false
  storage.setStorageSync(keyFor(orderRef),snapshot)
  return true
}

export function validateOrderSnapshot(storage:SnapshotStorage,orderRef:string,candidate:PriceSnapshotIdentity|null):boolean{
  if(!orderRef||!isCompleteSnapshot(candidate))return false
  const frozen=storage.getStorageSync(keyFor(orderRef)) as PriceSnapshotIdentity|null
  return isCompleteSnapshot(frozen)&&samePriceSnapshot(frozen,candidate)
}
