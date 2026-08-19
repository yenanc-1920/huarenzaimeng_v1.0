const KEY='buyerGuestRef'
const valid=(value:unknown):value is string=>typeof value==='string'&&/^[A-Za-z0-9._:-]{8,128}$/.test(value)
export type GuestRefStorage={getStorageSync(key:string):unknown;setStorageSync(key:string,value:unknown):void}

export function readOrCreateBuyerGuestRef(storage:GuestRefStorage,now=Date.now(),random=Math.random()):string{
  const stored=storage.getStorageSync(KEY)
  if(valid(stored))return stored
  const entropy=Math.floor(Math.max(0,Math.min(.999999999,random))*0x100000000).toString(36).padStart(7,'0')
  const created=`GUEST-${now.toString(36)}-${entropy}`
  storage.setStorageSync(KEY,created)
  return created
}
