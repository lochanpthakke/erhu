import {normalize,validId,visible,publicItem,revision} from './domain.mjs';
/** Dependencies are Firebase adapters in production and isolated fakes in HTTP tests. */
export function createApi({store,verifyAdmin,dispatch,now = Date.now}) {
 return async (req,res) => {
  res.set('Cache-Control','no-store'); res.set('X-Content-Type-Options','nosniff');
  const path = req.path.replace(/^\/api(?=\/|$)/,'');
  const id = path.split('/').at(-1);
  try {
   if (req.method === 'GET' && path === '/feed') {
    const items=(await store.list(true,now())).filter(a => a.audience === 'all' && visible(a,now())).sort((a,b)=>b.publishedAt.localeCompare(a.publishedAt)).slice(0,100);
    return res.json({announcements:items.map(publicItem),serverTime:new Date(now()).toISOString()});
   }
   if (req.method === 'GET' && path.startsWith('/announcements/') && validId(id)) {
    const item=await store.get(id);
    return item && visible(item,now()) ? res.json(publicItem(item)) : res.status(404).json({error:'Announcement unavailable'});
   }
   if (!path.startsWith('/admin/')) return res.status(404).json({error:'Not found'});
   const authorization=req.get('Authorization') || '';
   if (!authorization.startsWith('Bearer ')) return res.status(401).json({error:'Admin sign-in required'});
   let identity;
   try { identity=await verifyAdmin(authorization.slice(7)); } catch { return res.status(401).json({error:'Invalid or expired sign-in'}); }
   if (identity.admin !== true) return res.status(403).json({error:'Administrator access required'});
   if (req.method === 'GET' && path === '/admin/announcements') return res.json({announcements:(await store.list()).map(({testToken,...item})=>item)});
   if (req.method === 'POST' && path === '/admin/announcements') {
    if (Buffer.byteLength(JSON.stringify(req.body || {})) > 16000) return res.status(413).json({error:'Announcement too large'});
    const item=normalize(req.body,now());
    const previous=await store.get(item.id);
    const hash=revision(item);
    const saved=previous?.revision===hash ? previous : {...item,revision:hash,notificationSent:false,leaseUntil:0,updatedBy:identity.uid,updatedAt:now()};
    if (saved!==previous) await store.put(saved);
    if (previous && !item.active) await dispatch(previous,{invalidate:true});
    else if (visible(saved,now())) await dispatch(saved);
    return res.status(201).json(publicItem(saved));
   }
   if (req.method === 'DELETE' && path.startsWith('/admin/announcements/') && validId(id)) {
    const previous=await store.get(id); if (!previous) return res.status(404).json({error:'Not found'});
    await store.put({...previous,active:false,updatedBy:identity.uid,updatedAt:now()});
    await dispatch(previous,{invalidate:true});
    return res.status(204).send();
   }
   return res.status(404).json({error:'Not found'});
  } catch (error) {
   // Validation messages contain field constraints, never credentials or request bodies.
   if (error instanceof TypeError || error?.message?.match(/Invalid|Use a public|Expiry|already expired|Action needs/)) return res.status(400).json({error:error.message});
   return res.status(503).json({error:'Announcement service unavailable; retry safely using the same ID'});
  }
 };
}
