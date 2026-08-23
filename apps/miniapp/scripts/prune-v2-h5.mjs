import {rmSync} from 'node:fs'
import {resolve} from 'node:path'

const staticRoot=resolve(import.meta.dirname,'../dist/build/h5/static')
for(const relative of ['default-avatar.png','operators'])rmSync(resolve(staticRoot,relative),{recursive:true,force:true})
