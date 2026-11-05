// Run only in a trusted admin environment using Application Default Credentials.
import {initializeApp,applicationDefault} from 'firebase-admin/app';
import {getAuth} from 'firebase-admin/auth';
const uid=process.argv[2];if(!uid) throw new Error('Usage: node grant-admin.mjs <existing Firebase Auth UID>');
if (!process.env.GCLOUD_PROJECT) throw new Error('Set GCLOUD_PROJECT to your actual Firebase project ID');
initializeApp({credential:applicationDefault(),projectId:process.env.GCLOUD_PROJECT});
const user=await getAuth().getUser(uid);
await getAuth().setCustomUserClaims(uid,{...user.customClaims,admin:true});
console.log('Admin claim assigned; sign in again to refresh the ID token.');
