import {randomUUID,createHash} from 'node:crypto';
import semver from 'semver';
export const types = new Set(['general','feature','important_update','maintenance']);
export const validId = id => typeof id === 'string' && /^[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}$/i.test(id);
export function httpsUrl(value) {
 if (value == null || value === '') return null;
 const u = new URL(value);
 if (u.protocol !== 'https:' || u.username || u.password || u.port || u.hostname === 'localhost' || u.hostname.endsWith('.local') || /^[\d.:\[\]]+$/.test(u.hostname)) throw new Error('Use a public HTTPS URL without credentials');
 return u.href;
}
function text(value, maximum, required = false) {
 if (typeof value !== 'string' || value.length > maximum || (required && !value.trim())) throw new Error('Invalid text field');
 return value.trim();
}
function date(value) { const n = Date.parse(value); if (!Number.isFinite(n)) throw new Error('Invalid timestamp'); return new Date(n).toISOString(); }
function code(value) { if (value == null || value === '') return null; if (!Number.isSafeInteger(Number(value)) || Number(value) < 1) throw new Error('Invalid version code'); return Number(value); }
function version(value) { if (!value) return null; if (typeof value !== 'string' || value.length > 80 || !semver.valid(value)) throw new Error('Invalid version name'); return semver.valid(value); }

export function normalize(input, now = Date.now()) {
 const result = {
  id: input.id || randomUUID(), title:text(input.title,120,true), body:text(input.body,4000,true),
  type:input.type, imageUrl:httpsUrl(input.imageUrl), actionLabel:text(input.actionLabel || '',60), actionUrl:httpsUrl(input.actionUrl),
  publishedAt:date(input.publishedAt || new Date(now).toISOString()), expiresAt:date(input.expiresAt),
  minVersionCode:code(input.minVersionCode), maxVersionCode:code(input.maxVersionCode), minVersionName:version(input.minVersionName), maxVersionName:version(input.maxVersionName),
  active:input.active !== false, audience:input.audience || 'test',
 };
 if (!validId(result.id) || !types.has(result.type) || !['test','all'].includes(result.audience)) throw new Error('Invalid ID, type or audience');
 if (Date.parse(result.expiresAt) <= Date.parse(result.publishedAt)) throw new Error('Expiry must follow publication');
 if (result.active && Date.parse(result.expiresAt) <= now) throw new Error('Active announcement is already expired');
 if (result.minVersionName && result.maxVersionName && semver.gt(result.minVersionName,result.maxVersionName)) throw new Error('Invalid version range');
 if (result.minVersionCode && result.maxVersionCode && result.minVersionCode > result.maxVersionCode) throw new Error('Invalid version range');
 if (Boolean(result.actionLabel) !== Boolean(result.actionUrl)) throw new Error('Action needs both label and HTTPS destination');
 if (result.audience === 'test') result.testToken = text(input.testToken || '',4096,true);
 return result;
}
export function revision(item) { return createHash('sha256').update(JSON.stringify(item)).digest('hex'); }
export function pushMessage(item, invalidate = false, now = Date.now()) {
 return {...(item.audience === 'test' ? {token:item.testToken} : {topic:'erhu-announcements'}),
  data:{announcement_id:item.id,event:invalidate?'invalidate':'published'},
  android:{priority:item.type==='important_update'?'high':'normal',ttl:Math.max(0,Math.min(86400000,Date.parse(item.expiresAt)-now))}};
}
export const visible = (a, now) => a.active && Date.parse(a.publishedAt) <= now && Date.parse(a.expiresAt) > now;
export function publicItem(a) {
 const {testToken,audience,notificationSent,leaseUntil,updatedBy,updatedAt,revision,deliveryError,...publicFields} = a;
 return publicFields;
}
