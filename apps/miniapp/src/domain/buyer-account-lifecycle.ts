const object=(value:unknown):value is Record<string,unknown>=>typeof value==='object'&&value!==null&&!Array.isArray(value)
const exactKeys=(value:Record<string,unknown>,keys:string[])=>Object.keys(value).length===keys.length&&Object.keys(value).every(key=>keys.includes(key))

export type BuyerClosureView=Readonly<{schemaVersion:'BUYER_CLOSURE_V1';closureRef:string;state:'REQUESTED';blockerCount:number;version:1;requestRef:string;replayed:boolean}>

export function parseBuyerClosureView(value:unknown,requestRef:string):BuyerClosureView{
  const keys=['schemaVersion','closureRef','state','blockerCount','version','requestRef','replayed']
  if(!object(value)||!exactKeys(value,keys)||value.schemaVersion!=='BUYER_CLOSURE_V1'||value.state!=='REQUESTED'||value.requestRef!==requestRef||typeof value.closureRef!=='string'||!value.closureRef.trim()||!Number.isInteger(value.blockerCount)||(value.blockerCount as number)<0||value.version!==1||typeof value.replayed!=='boolean')throw new Error('BUYER_CLOSURE_RESPONSE_INVALID')
  return value as unknown as BuyerClosureView
}
