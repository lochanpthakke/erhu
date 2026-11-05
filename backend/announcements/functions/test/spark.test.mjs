import {test} from 'node:test';
import assert from 'node:assert/strict';
import {snapshot,deliver} from '../spark.mjs';
import {normalize} from '../domain.mjs';
const now=Date.parse('2026-11-05T16:00:00Z');
const make = extra => normalize({title:'hello',body:'hello',type:'general',audience:'test',testToken:'private-token',expiresAt:'2026-11-06T16:00:00Z',...extra},now);
test('Spark feed excludes tests, inactive, expired and future notices; strips private fields',()=>{
 const published=make({audience:'all'});
 const feed=snapshot([published,make({}),make({audience:'all',active:false}),make({audience:'all',publishedAt:'2026-11-06T12:00:00Z'})],now);
 assert.equal(feed.announcements.length,1);
 assert.equal(feed.announcements[0].id,published.id);
 assert.ok(!JSON.stringify(feed).includes('private-token'));
 assert.ok(!('audience' in feed.announcements[0]));
 assert.equal(snapshot([published],now+172800000).announcements.length,0);
});
test('Spark test push uses exactly one token, HTTP v1 TTL and ID-only payload',async()=>{
 const item=make({});
 const receipt=await deliver(item,'erhu-app-25b513','oauth-secret',async(url,options)=>{
   assert.equal(url,'https://fcm.googleapis.com/v1/projects/erhu-app-25b513/messages:send');
   const {message}=JSON.parse(options.body);
   assert.equal(message.token,'private-token');assert.equal(message.topic,undefined);
   assert.deepEqual(message.data,{announcement_id:item.id,event:'published'});
   assert.equal(message.android.ttl,'86400s');
   assert.equal(options.headers.Authorization,'Bearer oauth-secret');
   return {ok:true,json:async()=>({name:'projects/test/messages/1'})};
 },now);
 assert.equal(receipt,'projects/test/messages/1');
});
test('Spark handles denied, rate-limited and malformed delivery without leaking tokens',async()=>{
 for (const status of [401,403,429,503]) await assert.rejects(deliver(make({}),'erhu-app-25b513','secret',async()=>({ok:false,status}),now),new RegExp(`HTTP ${status}`));
 await assert.rejects(deliver(make({}),'erhu-app-25b513','secret',async()=>({ok:true,json:async()=>({})}),now),/no message receipt/);
});
