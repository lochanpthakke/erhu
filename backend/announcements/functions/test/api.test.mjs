import test from 'node:test';
import assert from 'node:assert/strict';
import {createServer} from 'node:http';
import {createApi} from '../api.mjs';
import {normalize,visible,httpsUrl,publicItem,pushMessage} from '../domain.mjs';
const clock=Date.parse('2026-11-04T12:00:00Z');
const fixture=(extra={})=>({id:'11111111-1111-1111-1111-111111111111',title:'Test notice',body:'A test for one device',type:'feature',audience:'test',testToken:'one-isolated-test-device',expiresAt:new Date(clock+86400000).toISOString(),...extra});
async function withApi(work) {
 const items=new Map(),sent=[];
 const handler=createApi({store:{async list(){return [...items.values()];},async get(id){return items.get(id);},async put(a){items.set(a.id,a);}},now:()=>clock,
 verifyAdmin:async token=>{if(token==='admin')return{uid:'owner',admin:true};if(token==='normal')return{uid:'user',admin:false};throw new Error('Invalid token');},dispatch:async(a,options)=>sent.push({item:a,options})});
 const server=createServer(async(req,res)=>{let raw='';for await(const chunk of req)raw+=chunk;req.body=raw?JSON.parse(raw):{};req.path=new URL(req.url,'http://localhost').pathname;req.get=name=>req.headers[name.toLowerCase()];res.set=(k,v)=>res.setHeader(k,v);res.status=code=>{res.statusCode=code;return res;};res.json=data=>{res.setHeader('Content-Type','application/json');res.end(JSON.stringify(data));};res.send=()=>res.end();await handler(req,res);});
 await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));const base='http://127.0.0.1:'+server.address().port;
 const request=(path,method='GET',body=null,token='admin')=>fetch(base+path,{method,headers:{...(token?{Authorization:'Bearer '+token}:{}),'Content-Type':'application/json'},...(body?{body:JSON.stringify(body)}:{})});
 try{await work({request,items,sent});}finally{await new Promise(resolve=>server.close(resolve));}
}
test('authenticated publisher sends a test notice only to its configured device; test feed stays private',()=>withApi(async({request,sent})=>{
 assert.equal((await request('/admin/announcements','POST',fixture())).status,201);assert.equal(sent.length,1);assert.equal(sent[0].item.audience,'test');assert.equal(sent[0].item.testToken,'one-isolated-test-device');
 assert.equal((await(await request('/feed')).json()).announcements.length,0);
 const item=await(await request('/announcements/'+fixture().id)).json();assert.equal(item.title,'Test notice');assert.equal(item.testToken,undefined);assert.equal(item.audience,undefined);
}));
test('missing, expired and ordinary-user credentials cannot publish',()=>withApi(async({request,items})=>{
 for(const [token,status]of[[null,401],['expired',401],['normal',403]])assert.equal((await request('/admin/announcements','POST',fixture(),token)).status,status);
 assert.equal(items.size,0);
}));
test('scheduled notices do not dispatch early and multiple notices remain one feed, not stacked notifications',()=>withApi(async({request,sent})=>{
 await request('/admin/announcements','POST',fixture({audience:'all',publishedAt:new Date(clock+60000).toISOString()}));assert.equal(sent.length,0);assert.equal((await(await request('/feed')).json()).announcements.length,0);
 await request('/admin/announcements','POST',fixture({audience:'all'}));assert.equal((await(await request('/feed')).json()).announcements.length,1);
}));
test('deactivation invalidates content and makes direct notification links unavailable',()=>withApi(async({request,sent})=>{
 await request('/admin/announcements','POST',fixture());assert.equal((await request('/admin/announcements/'+fixture().id,'DELETE')).status,204);assert.equal((await request('/announcements/'+fixture().id)).status,404);assert.equal(sent.at(-1).options.invalidate,true);
}));
test('invalid payloads, unsafe actions and reversed version ranges are rejected',()=>withApi(async({request,items})=>{
 for(const extra of [{title:''},{imageUrl:'http://example.com/image.jpg'},{actionLabel:'Open'},{minVersionCode:20,maxVersionCode:10},{type:'unknown'},{expiresAt:new Date(clock-1).toISOString()},{id:'../../bad'}])assert.equal((await request('/admin/announcements','POST',fixture(extra))).status,400);
 assert.equal(items.size,0);
}));
test('expiry boundaries and target version data survive normalized publication',()=>{
 const a=normalize(fixture({minVersionCode:4,maxVersionCode:8,minVersionName:'1.0.3',maxVersionName:'2.0.0'}),clock);assert.equal(visible(a,clock),true);assert.equal(visible(a,Date.parse(a.expiresAt)),false);assert.equal(publicItem(a).minVersionCode,4);assert.throws(()=>httpsUrl('https://127.0.0.1/private'));
});

test('data-only push messages carry IDs only and never broadcast a test notice',()=>{
 const item=normalize(fixture(),clock);const message=pushMessage(item,false,clock);
 assert.equal(message.token,'one-isolated-test-device');assert.equal(message.topic,undefined);assert.equal(message.notification,undefined);
 assert.deepEqual(message.data,{announcement_id:item.id,event:'published'});
 assert.equal(pushMessage({...item,audience:'all'}).topic,'erhu-announcements');
 assert.equal(pushMessage(item,true).data.event,'invalidate');
});
test('version names use semantic ordering and cannot reverse the range',()=>{
 assert.throws(()=>normalize(fixture({minVersionName:'1.10.0',maxVersionName:'1.9.9'}),clock));
 assert.throws(()=>normalize(fixture({minVersionName:'01.0.0'}),clock));
 assert.equal(normalize(fixture({minVersionName:'1.0.3-beta.1',maxVersionName:'1.0.3'}),clock).minVersionName,'1.0.3-beta.1');
});
