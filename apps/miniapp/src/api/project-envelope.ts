export class ProjectApiError extends Error{
  projectCode:string
  constructor(projectCode:string){super(projectCode);this.projectCode=projectCode}
}

const object=(value:unknown):value is Record<string,unknown>=>typeof value==='object'&&value!==null
const exactKeys=(value:Record<string,unknown>,allowed:string[])=>Object.keys(value).length===allowed.length&&Object.keys(value).every((key)=>allowed.includes(key))

export function parseAcceptedProjectEnvelope(body:unknown):unknown{
  if(!object(body)||!exactKeys(body,['status','projectCode','data'])||typeof body.status!=='string'||typeof body.projectCode!=='string')throw new ProjectApiError('INVALID_ENVELOPE')
  if(body.status==='ACCEPTED'&&body.projectCode==='OK'&&body.data!==null&&body.data!==undefined)return body.data
  if(body.status==='REJECTED'&&body.projectCode!=='OK'&&body.data===null)throw new ProjectApiError(body.projectCode)
  throw new ProjectApiError('INVALID_ENVELOPE')
}
