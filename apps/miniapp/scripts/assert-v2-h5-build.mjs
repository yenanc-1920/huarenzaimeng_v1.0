import assert from 'node:assert/strict'
import {readdirSync,readFileSync} from 'node:fs'
import {join,resolve} from 'node:path'

const dist=resolve(import.meta.dirname,'../dist/build/h5')
const files=[]
const walk=dir=>readdirSync(dir,{withFileTypes:true}).forEach(entry=>entry.isDirectory()?walk(join(dir,entry.name)):files.push(join(dir,entry.name)))
walk(dist)
const output=files.filter(file=>/\.(?:js|html|json)$/.test(file)).map(file=>readFileSync(file,'utf8')).join('\n')
const exactUrl='https://www.95cxmd.com/h5/#/home/view?id=25&source=pcxvaj'
assert.equal(output.split(exactUrl).length-1,1,'built H5 must contain one exact WINLA URL')
for(const forbidden of ['/buyer-auth/','/buyer-api/','/anonymous-sessions','/quotes','/orders','/payments','/refunds','Authorization','Bearer '])assert.equal(output.includes(forbidden),false,`built H5 contains forbidden network capability ${forbidden}`)
for(const forbidden of ['pages/recharge/','pages/order/','pages/payment/','pages/refund/','pages/profile/','pages/auth/'])assert.equal(output.includes(forbidden),false,`built H5 contains forbidden route ${forbidden}`)
assert.equal(/<iframe/i.test(output),false,'built H5 must not embed an iframe')
for(const forbidden of ['default-avatar.png','static/operators/'])assert.equal(files.some(file=>file.replaceAll('\\','/').endsWith(forbidden)||file.replaceAll('\\','/').includes(forbidden)),false,`built H5 contains unused V1 asset ${forbidden}`)
console.log('V2_H5_BUILD_GATE PASS 18/18')
