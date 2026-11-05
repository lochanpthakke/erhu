// Trusted workstation publisher. No Functions, Firestore, server keys or billing required.
import {mkdir, readFile, writeFile, rm} from 'node:fs/promises';
import {resolve, dirname} from 'node:path';
import {fileURLToPath} from 'node:url';
import {spawnSync} from 'node:child_process';
import {applicationDefault} from 'firebase-admin/app';
import {normalize, publicItem, pushMessage, revision, visible} from './domain.mjs';

export function snapshot(items, now = Date.now()) {
  const ids = new Set();
  for (const item of items) {
    if (ids.has(item.id)) throw new Error('Duplicate announcement ID');
    ids.add(item.id);
  }
  return {
    announcements: items.filter(a => a.audience === 'all' && visible(a, now))
      .sort((a,b) => Date.parse(b.publishedAt)-Date.parse(a.publishedAt)).slice(0,100).map(publicItem),
  };
}

export async function render(items, directory, now = Date.now()) {
  // Only ever remove the explicitly named generated public directory.
  const root = resolve(directory);
  if (root !== resolve(dirname(fileURLToPath(import.meta.url)), '../spark/public')) throw new Error('Invalid output directory');
  await rm(root, {recursive:true, force:true});
  await mkdir(resolve(root,'api/announcements'), {recursive:true});
  await writeFile(resolve(root,'api/feed'), JSON.stringify(snapshot(items, now)));
  for (const item of items) {
    await writeFile(resolve(root,'api/announcements',item.id), JSON.stringify(publicItem(item)));
  }
}

export async function deliver(item, project, accessToken, request = fetch, now = Date.now(), invalidate = false) {
  if (!/^[a-z][a-z0-9-]{4,28}[a-z0-9]$/.test(project)) throw new Error('Provide the actual Firebase project ID');
  const message = pushMessage(item, invalidate, now);
  // HTTP v1 uses a protobuf duration, while Admin SDK uses milliseconds.
  message.android.ttl = `${Math.floor(message.android.ttl / 1000)}s`;
  const response = await request(`https://fcm.googleapis.com/v1/projects/${project}/messages:send`, {
    method:'POST', headers:{Authorization:`Bearer ${accessToken}`,'Content-Type':'application/json'},
    body:JSON.stringify({message}), signal:AbortSignal.timeout(30000),
  });
  if (!response.ok) throw new Error(`FCM delivery failed (HTTP ${response.status}); content remains deployed. Retry sync.`);
  const result = await response.json();
  if (typeof result.name !== 'string') throw new Error('FCM returned no message receipt');
  return result.name;
}

async function main() {
  const [command, project, inputFile, confirmation] = process.argv.slice(2);
  if (!['put','sync'].includes(command) || !project) throw new Error('Usage: node functions/spark.mjs put PROJECT notice.json [--all] | sync PROJECT [--all]');
  if (!/^[a-z][a-z0-9-]{4,28}[a-z0-9]$/.test(project)) throw new Error('Invalid Firebase project ID');
  const base = resolve(dirname(fileURLToPath(import.meta.url)), '..');
  const statePath = process.env.ERHU_ANNOUNCEMENTS_STATE;
  if (!statePath) throw new Error('Set ERHU_ANNOUNCEMENTS_STATE to a private state file outside Git');
  let state;
  try { state = JSON.parse(await readFile(statePath,'utf8')); }
  catch (error) { if (error.code !== 'ENOENT') throw error; state = {project, items:[], delivered:{}}; }
  if (state.project !== project) throw new Error('State belongs to a different Firebase project');
  const allowAll = (command === 'put' ? confirmation : inputFile) === '--all';
  if (command === 'put') {
    const input = JSON.parse(await readFile(inputFile,'utf8'));
    if (input.audience === 'all' && !allowAll) throw new Error('Broadcast requires explicit --all confirmation');
    const item = normalize({...input, testToken:process.env.ERHU_TEST_FCM_TOKEN});
    delete item.testToken; // Never persist registration tokens or put them in Hosting.
    state.items = [...state.items.filter(a => a.id !== item.id), item];
  }
  if (state.items.some(a => a.audience === 'all' && state.delivered[a.id] !== revision(a)) && !allowAll) {
    throw new Error('Pending broadcast requires explicit --all confirmation');
  }
  await mkdir(dirname(resolve(statePath)), {recursive:true});
  await writeFile(statePath, JSON.stringify(state,null,2), {mode:0o600});
  await render(state.items,resolve(base,'spark/public'));
  const executable = process.platform === 'win32' ? 'npx.cmd' : 'npx';
  const deployment = spawnSync(executable,['--yes','firebase-tools','deploy','--config','firebase.spark.json','--project',project,'--only','hosting','--non-interactive'],
    {cwd:base,stdio:'inherit',shell:process.platform === 'win32'});
  if (deployment.status !== 0) throw new Error('Hosting deployment failed; no messages sent');
  let accessToken = process.env.GOOGLE_OAUTH_ACCESS_TOKEN;
  const now = Date.now();
  for (const stored of state.items) {
    const rev = revision(stored);
    if (state.delivered[stored.id] === rev || Date.parse(stored.publishedAt) > now || Date.parse(stored.expiresAt) <= now) continue;
    if (stored.active && !visible(stored,now)) continue;
    const item = {...stored};
    if (item.audience === 'test') {
      if (!process.env.ERHU_TEST_FCM_TOKEN) throw new Error('Set ERHU_TEST_FCM_TOKEN for test delivery; nothing was broadcast');
      item.testToken = process.env.ERHU_TEST_FCM_TOKEN;
    }
    accessToken ||= (await applicationDefault().getAccessToken()).access_token;
    const receipt = await deliver(item,project,accessToken,fetch,now,!item.active);
    state.delivered[item.id] = rev;
    await writeFile(statePath,JSON.stringify(state,null,2),{mode:0o600});
    console.log(`Sent ${item.id}: ${receipt}`);
  }
  console.log('Hosting content synchronized. Scheduled notices require running sync when due.');
}
if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  main().catch(error => { console.error(error.message); process.exitCode = 1; });
}
