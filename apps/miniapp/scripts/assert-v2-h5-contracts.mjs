import assert from 'node:assert/strict'
import {readFileSync} from 'node:fs'
import {resolve} from 'node:path'

const root=resolve(import.meta.dirname,'..')
const read=path=>readFileSync(resolve(root,path),'utf8')
const pages=JSON.parse(read('src/pages.json')).pages.map(page=>page.path)
const expected=['pages/index/index','pages/legal/user-agreement','pages/legal/privacy-policy','pages/legal/cooperation','pages/directory/list','pages/directory/detail','pages/life-content/list','pages/life-content/detail']
assert.deepEqual(pages,expected,'V2 routes must be the fixed public H5 route set')

const nav=read('src/components/BottomNav.vue')
for(const label of ['首页','资讯','黄页'])assert.match(nav,new RegExp(`>${label}<`))
for(const forbidden of ['我的','/pages/profile','/pages/recharge','/pages/order','/pages/payment','/pages/refund'])assert.equal(nav.includes(forbidden),false,`BottomNav contains ${forbidden}`)

const home=read('src/pages/index/index.vue')
const winla='https://www.95cxmd.com/h5/#/home/view?id=25&source=pcxvaj'
assert.equal(home.split(winla).length-1,1,'WINLA URL must be one exact constant')
assert.match(home,/充值、订单、退款及售后服务由小啦全球充提供。/)
assert.match(home,/@click="openWinla"/)
assert.match(home,/第三方服务暂时无法打开，请稍后再试/)
assert.equal(home.includes('AppHeader'),false,'Home must not duplicate the browser title bar')
for(const removedHomePreview of ['孟加拉黄页','生活资讯','class="entries"'])assert.equal(home.includes(removedHomePreview),false,`Home contains removed preview: ${removedHomePreview}`)
for(const forbidden of ['<iframe','/pages/recharge/','api/client'])assert.equal(home.includes(forbidden),false,`Home contains ${forbidden}`)
assert.match(home,/setInterval\(tickClocks,60000\)/)
assert.match(home,/onHide\(stopClock\)/)

const routedSources=expected.map(path=>read(`src/${path}.vue`)).join('\n')+read('src/api/v2-public-client.ts')
for(const forbidden of ['Authorization','Bearer ','buyer-auth','anonymous-sessions','/quotes','/orders','/payments','/refunds','session_key','openid'])assert.equal(routedSources.includes(forbidden),false,`V2 closure contains ${forbidden}`)
for(const file of ['src/pages/index/index.vue','src/pages/directory/list.vue','src/pages/directory/detail.vue','src/pages/life-content/list.vue','src/pages/life-content/detail.vue'])assert.equal(read(file).includes("api/client"),false,`${file} imports V1 client`)

const privacy=read('src/pages/legal/privacy-policy.vue')
assert.match(privacy,/问题类型和选填说明/)
assert.match(privacy,/请勿在说明中填写个人敏感信息/)
assert.match(privacy,/必要访问日志/)
assert.match(privacy,/到期删除或匿名化/)
const cooperation=read('src/pages/legal/cooperation.vue')
assert.match(cooperation,/华人在孟不在本站内销售充值商品，也不接收充值款项/)
assert.match(cooperation,/不从WINLA充值产生佣金/)
assert.match(cooperation,/不会自动跳转或嵌入第三方充值页面/)

console.log('V2_H5_CONTRACTS PASS 29/29')
