import {initializeApp} from 'firebase-admin/app';
import {getAuth} from 'firebase-admin/auth';
import {getFirestore} from 'firebase-admin/firestore';
import {getMessaging} from 'firebase-admin/messaging';
import {onRequest} from 'firebase-functions/v2/https';
import {onSchedule} from 'firebase-functions/v2/scheduler';
import {setGlobalOptions} from 'firebase-functions/v2';
import {createApi} from './api.mjs';
import {visible,pushMessage} from './domain.mjs';
initializeApp();
setGlobalOptions({region:process.env.ANNOUNCEMENT_REGION || 'us-central1',maxInstances:3});
const collection=getFirestore().collection('erhuAnnouncements');
const store={
 async list(publicOnly=false,now=Date.now()) {
  const query=publicOnly ? collection.where('active','==',true).where('publishedAt','<=',new Date(now).toISOString()).where('expiresAt','>',new Date(now).toISOString()) : collection;
  return (await query.orderBy('publishedAt','desc').limit(publicOnly?100:200).get()).docs.map(d=>d.data());
 },
 async get(id) { return (await collection.doc(id).get()).data() || null; },
 async put(item) { await collection.doc(item.id).set(item); },
};
async function dispatch(item,{invalidate=false}={}) {
 const ref=collection.doc(item.id);
 if (!invalidate) {
  const claimed=await getFirestore().runTransaction(async transaction=>{
   const current=(await transaction.get(ref)).data();
   if (!current || !visible(current,Date.now()) || current.notificationSent || current.leaseUntil>Date.now()) return false;
   transaction.update(ref,{leaseUntil:Date.now()+300000});return current;
  });
  if (!claimed) return;
  item=claimed;
 }
 try {
  await getMessaging().send(pushMessage(item,invalidate));
  if (!invalidate) await getFirestore().runTransaction(async transaction=>{
   const latest=(await transaction.get(ref)).data();
   if (latest?.revision===item.revision) transaction.update(ref,{notificationSent:true,leaseUntil:0});
  });
 } catch (error) {
  if (!invalidate) await ref.update({leaseUntil:0});
  throw error;
 }
}
export const announcementApi=onRequest({cors:false,maxInstances:3},createApi({store,verifyAdmin:token=>getAuth().verifyIdToken(token,true),dispatch}));
export const scheduledAnnouncements=onSchedule('every 5 minutes',async()=>{
 const now=new Date().toISOString();
 const rows=await collection.where('active','==',true).where('notificationSent','==',false).where('publishedAt','<=',now).where('expiresAt','>',now).orderBy('publishedAt').limit(100).get();
 for (const row of rows.docs) { const item=row.data();if(visible(item,Date.now())) { try { await dispatch(item); } catch { /* Retry this ID later without blocking other announcements or logging tokens. */ } } }
});
