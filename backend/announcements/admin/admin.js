let idToken = null, config;
const status = text => { document.getElementById('status').textContent = text; };
const form = document.getElementById('announcement');
const field = name => form.elements.namedItem(name);
function dateInput(date) { const d=new Date(date);return new Date(d.getTime()-d.getTimezoneOffset()*60000).toISOString().slice(0,16); }
function reset() { form.reset();field('id').value=crypto.randomUUID();field('publishedAt').value=dateInput(Date.now());field('expiresAt').value=dateInput(Date.now()+7*86400000);audience(); }
function audience() {const test=field('audience').value==='test';document.getElementById('token-field').hidden=!test;field('testToken').required=test;document.getElementById('save').textContent=test?'Publish to test device':'Publish to all subscribers';}
async function request(path,options={}) {
 const response=await fetch('/api'+path,{...options,headers:{'Content-Type':'application/json','Authorization':'Bearer '+idToken,...options.headers}});
 if(response.status===401) {idToken=null;document.getElementById('editor').hidden=true;document.getElementById('login').hidden=false;}
 if(response.status===204) return null;
 const data=await response.json();if(!response.ok) throw new Error(data.error || 'Request failed');return data;
}
async function list() {
 const data=await request('/admin/announcements');const container=document.getElementById('items');container.replaceChildren();
 for(const item of data.announcements) {
  const div=document.createElement('article');div.className='item';const title=document.createElement('strong');title.textContent=item.title;
  const summary=document.createElement('p');summary.textContent=`${item.type} | ${item.active?'Active':'Inactive'} | ${item.audience} | ${item.publishedAt} to ${item.expiresAt}`;
  const edit=document.createElement('button');edit.type='button';edit.textContent='Edit';edit.onclick=()=>{for(const [key,value] of Object.entries(item)){const f=field(key);if(!f)continue;if(key==='active')f.checked=value;else if(key==='publishedAt'||key==='expiresAt')f.value=dateInput(value);else f.value=value??'';}field('testToken').value='';audience();window.scrollTo({top:0,behavior:'smooth'});};
  const remove=document.createElement('button');remove.type='button';remove.textContent='Deactivate';remove.onclick=async()=>{try{await request('/admin/announcements/'+item.id,{method:'DELETE'});await list();status('Announcement deactivated.');}catch(e){status(e.message);}};
  div.append(title,summary,edit,remove);container.append(div);
 }
}
document.getElementById('sign-in').onsubmit=async event=>{
 event.preventDefault();const data=new FormData(event.target);
 try {
  config ||= await (await fetch('/__/firebase/init.json')).json();
  if(!config.apiKey) throw new Error('Firebase Hosting configuration is not available. Deploy this admin site to the configured project first.');
  const response=await fetch('https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key='+encodeURIComponent(config.apiKey),{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({email:data.get('email'),password:data.get('password'),returnSecureToken:true})});
  const auth=await response.json();event.target.elements.password.value='';if(!response.ok)throw new Error('Sign-in failed. Check your administrator account.');
  idToken=auth.idToken;await list();document.getElementById('login').hidden=true;document.getElementById('editor').hidden=false;reset();status('Signed in. Test-device delivery is selected by default.');
 }catch(e){idToken=null;status(e.message);}
};
form.onsubmit=async event=>{
 event.preventDefault();if(field('audience').value==='all'&&!confirm('Publish this announcement to all subscribed erhu devices?'))return;
 const body=Object.fromEntries(new FormData(form));body.active=field('active').checked;body.publishedAt=new Date(body.publishedAt).toISOString();body.expiresAt=new Date(body.expiresAt).toISOString();
 for(const key of ['minVersionCode','maxVersionCode'])body[key]=body[key]?Number(body[key]):null;
 document.getElementById('save').disabled=true;
 try{await request('/admin/announcements',{method:'POST',body:JSON.stringify(body)});status('Saved. Due notices are dispatched now; future notices are scheduled.');await list();}catch(e){status(e.message);}finally{document.getElementById('save').disabled=false;}
};
field('audience').onchange=audience;document.getElementById('new').onclick=reset;document.getElementById('refresh').onclick=()=>list().catch(e=>status(e.message));document.getElementById('sign-out').onclick=()=>{idToken=null;document.getElementById('editor').hidden=true;document.getElementById('login').hidden=false;status('Signed out.');};
