// Workers çalışma zamanı DEĞİL: Node altında açık mock storage/WebSocketPair/DO olay kuyruğu.
// Yayın, edge hibernation ve gerçek TLS/operatör testi yerine geçmez.
import {readFileSync,writeFileSync,mkdtempSync,rmSync} from 'node:fs';
import {tmpdir} from 'node:os';import {join} from 'node:path';import {pathToFileURL} from 'node:url';
import assert from 'node:assert/strict';
const tmp=mkdtempSync(join(tmpdir(),'zd-relay-')),src=readFileSync('cloud/tezgah-relay/worker.js','utf8');
writeFileSync(join(tmp,'worker.mjs'),src);
const {default:worker,TezgahRelay}=await import(pathToFileURL(join(tmp,'worker.mjs')));
const OriginalResponse=globalThis.Response,originalNow=Date.now,originalConsole={};let now=1800000000000,n=0;const logs=[];
Date.now=()=>now;
for(const k of ['log','info','warn','error','debug','trace']){originalConsole[k]=console[k];console[k]=(...a)=>logs.push([k,...a]);}
globalThis.Response=class extends OriginalResponse {
 constructor(body,init={}) { if(init.status===101){super(null,{status:200});Object.defineProperty(this,'status',{value:101});this.webSocket=init.webSocket;}else super(body,init); }
};
class Socket {
 readyState=1;received=[];attachment=null;
 send(message){if(this.readyState!==1)throw new Error('CLOSED');this.peer.received.push(message);}
 close(code,reason){this.readyState=3;this.peer.readyState=3;this.code=code;this.reason=reason;}
 serializeAttachment(a){this.attachment=structuredClone(a);}
 deserializeAttachment(){return structuredClone(this.attachment);}
}
globalThis.WebSocketPair=class {constructor(){this[0]=new Socket();this[1]=new Socket();this[0].peer=this[1];this[1].peer=this[0];}};
class Context {
 sockets=[];data=new Map();writes=[];alarmAt=null;tail=Promise.resolve();
 constructor(){this.storage={get:async k=>structuredClone(this.data.get(k)),put:async(k,v)=>{this.writes.push(structuredClone(v));this.data.set(k,structuredClone(v));},deleteAll:async()=>this.data.clear(),getAlarm:async()=>this.alarmAt,setAlarm:async at=>{this.alarmAt=at;},deleteAlarm:async()=>{this.alarmAt=null;}};}
 blockConcurrencyWhile(fn){const p=this.tail.then(fn);this.tail=p.catch(()=>{});return p;}
 acceptWebSocket(ws){this.sockets.push(ws);}
 getWebSockets(){return this.sockets.filter(s=>s.readyState===1);}
}
class Namespace {
 objects=new Map();
 constructor(env){this.env=env;}
 idFromName(name){return name;}
 get(name){if(!this.objects.has(name)){const ctx=new Context(),obj=new TezgahRelay(ctx,this.env);this.objects.set(name,{ctx,obj});}return{fetch:r=>this.objects.get(name).obj.fetch(r)};}
 hibernate(name){const e=this.objects.get(name);e.obj=new TezgahRelay(e.ctx,this.env);return e;}
}
const origin='https://web.example.test',base='https://relay.example.test';
function environment(extra={}){const e={RELAY_URL:base,ALLOWED_ORIGINS:JSON.stringify([origin]),CREATE_PER_MINUTE:'100',...extra};e.ROOMS=new Namespace(e);return e;}
const ok=(value,msg)=>{assert.ok(value,msg);n++;},eq=(a,b,msg)=>{assert.deepEqual(a,b,msg);n++;};
const b64=(n,v=1)=>Buffer.alloc(n,v).toString('base64url');
async function create(e,options={}){return worker.fetch(new Request(base+'/v1/rooms',{method:'POST',headers:{Origin:origin,'Content-Type':'application/json',...options.headers},body:options.body??'{}'}),e);}
async function room(e){const r=await create(e);eq(r.status,201,'room created');return r.json();}
async function socket(e,r,role='phone',cap=null,authenticate=true){
 const headers={Upgrade:'websocket'};if(role==='browser')headers.Origin=origin;
 const res=await worker.fetch(new Request(base+'/v1/socket?room='+r.room,{headers}),e);
 if(res.status!==101)return {res};
 const client=res.webSocket,ws=client.peer;const entry=e.ROOMS.objects.get(r.room);
 if(authenticate)await entry.obj.webSocketMessage(ws,JSON.stringify({v:1,type:'auth',room:r.room,role,capability:cap??r[role+'Capability']}));
 return {res,client,ws,entry};
}
const message=(s,f)=>s.entry.obj.webSocketMessage(s.ws,typeof f==='string'?f:JSON.stringify(f));
async function pair(e){const r=await room(e),web=await socket(e,r,'browser'),phone=await socket(e,r);return{r,web,phone};}
async function consume(p){await message(p.phone,{v:1,type:'consume',session:b64(16,2)});return{phone:JSON.parse(p.phone.client.received.at(-1)).capability,browser:JSON.parse(p.web.client.received.at(-1)).capability};}
const encrypted=(seq='1',dir='p2w')=>({v:1,type:'encrypted',session:b64(16,2),epoch:b64(16,3),dir,seq,ct:b64(32,4)});
async function alarm(entry){entry.ctx.alarmAt=null;await entry.obj.alarm();}
try {
 ok((src.match(/^export /gm)||[]).length===2,'yalnız default ve DO export');
 let e=environment(),p=await pair(e);
 eq(p.phone.res.status,101,'phone upgrade');eq(p.web.res.status,101,'browser upgrade');
 ok(p.phone.ws.attachment.role==='phone'&&p.web.ws.attachment.role==='browser','role auth');
 const timeoutRoom=await room(e),timeout=await socket(e,timeoutRoom,'phone',null,false);now+=5000;await alarm(timeout.entry);eq(timeout.ws.reason,'ZAMAN_ASIMI','5 sec auth timeout');
 const lateRoom=await room(e),late=await socket(e,lateRoom,'phone',null,false);now+=5000;await message(late,{v:1,type:'auth',room:lateRoom.room,role:'phone',capability:lateRoom.phoneCapability});eq(late.ws.readyState,3,'late auth refused even delayed alarm');
 e=environment();p=await pair(e);const bad=await socket(e,p.r,'phone',p.r.browserCapability);eq(bad.ws.readyState,3,'wrong role capability');eq(p.phone.ws.readyState,1,'wrong capability cannot evict original');
 const duplicate=await socket(e,p.r,'phone');eq(duplicate.ws.readyState,1,'new valid phone replaces');eq(p.phone.ws.reason,'BAGLANTI_YENILENDI','old phone closed');p.phone=duplicate;
 const noOrigin=await socket(e,p.r,'phone',p.r.browserCapability,false);await message(noOrigin,{v:1,type:'auth',room:p.r.room,role:'browser',capability:p.r.browserCapability});eq(noOrigin.ws.reason,'ROL_RED','browser requires origin');
 const noRoom=e.ROOMS.objects.size;const forbidden=await worker.fetch(new Request(base+'/v1/socket?room='+p.r.room,{headers:{Upgrade:'websocket',Origin:'https://evil.test'}}),e);eq(forbidden.status,403,'evil origin');eq(e.ROOMS.objects.size,noRoom,'no DO access on evil origin');
 eq((await worker.fetch(new Request('https://other.test/v1/rooms',{method:'POST'}),e)).status,503,'environment relay host');
 eq((await create(e,{body:JSON.stringify({secret:'NEVER_SEND_S'})})).status,400,'room input accepts no S');
 eq((await create(e,{body:'x'.repeat(513)})).status,400,'room body bounded');
 // Pending E2EE handshake yalnız doğru karşı uca gider.
 const hello={v:1,type:'phone_hello',room:p.r.room,phonePublicKey:b64(91),np:b64(32,2),proof:b64(32,3)},before=p.phone.client.received.length,other=p.web.client.received.length;
 await message(p.phone,hello);eq(p.phone.client.received.length,before,'no sender echo');eq(p.web.client.received.length,other+1,'only peer receives');
 const caps=await consume(p);eq(p.phone.entry.ctx.data.get('meta').stage,'active','consume active');
 ok(caps.phone&&caps.browser,'rotated capabilities delivered');
 now+=1100;const data=encrypted(),pb=p.phone.client.received.length,wb=p.web.client.received.length;
 await message(p.phone,data);eq(JSON.parse(p.web.client.received.at(-1)),data,'opaque encrypted forward');eq(p.phone.client.received.length,pb,'no echo');eq(p.web.client.received.length,wb+1,'one peer');
 // Hibernation sonrası role attachment ve consume/hash metadata kalır.
 e.ROOMS.hibernate(p.r.room);p.phone.entry=e.ROOMS.objects.get(p.r.room);p.web.entry=p.phone.entry;now+=1100;
 await message(p.phone,encrypted('2'));eq(JSON.parse(p.web.client.received.at(-1)).seq,'2','hibernate forward');
 const persisted=JSON.stringify([...e.ROOMS.objects.values()].flatMap(x=>x.ctx.writes));
 ok(!persisted.includes(caps.phone)&&!persisted.includes(caps.browser)&&!persisted.includes(p.r.phoneCapability)&&!persisted.includes(p.r.browserCapability),'only capability hashes persisted');
 ok(!persisted.includes(data.ct)&&!persisted.includes('phonePublicKey')&&!persisted.includes('NEVER_SEND_S'),'no ciphertext or handshake payload persisted');
 const attach=JSON.stringify(p.phone.ws.attachment);ok(!attach.includes('ct')&&!attach.includes('capability')&&!attach.includes('np'),'attachments metadata only');
 const webCap=caps.browser;closeClient(p.web);await p.web.entry.obj.webSocketClose(p.web.ws);const dropBefore=p.phone.client.received.length;now+=1100;await message(p.phone,encrypted('3'));eq(p.phone.client.received.length,dropBefore,'absent peer frame dropped');
 const writesBefore=JSON.stringify(p.phone.entry.ctx.writes);ok(!writesBefore.includes(data.ct),'dropped payload never saved');
 const old=await socket(e,p.r,'browser');eq(old.ws.readyState,3,'consumed QR cap rejected');
 const connected=await socket(e,p.r,'browser',webCap);eq(connected.ws.attachment.role,'browser','active resume cap accepted');
 const recon=p.phone.entry.ctx.data.get('meta').reconnectUntil;ok(recon===now-1100+300000,'resume window fixed');
 now=recon;await alarm(p.phone.entry);ok(!p.phone.entry.ctx.data.has('meta'),'resume expiry erases metadata');eq(connected.ws.readyState,3,'resume expiry closes sockets');
 // İki consume paralel: yalnız bir kez tüketilir, eski QR hash'leri geri gelmez.
 e=environment();p=await pair(e);await Promise.all([message(p.phone,{v:1,type:'consume',session:b64(16,2)}),message(p.phone,{v:1,type:'consume',session:b64(16,5)})]);
 eq(p.phone.client.received.filter(x=>JSON.parse(x).type==='consumed').length,1,'atomic double consume race');eq(p.phone.entry.ctx.data.get('meta').session,b64(16,2),'first consume wins');
 e=environment();const raceRoom=await room(e),raceWeb=await socket(e,raceRoom,'browser'),raceA=await socket(e,raceRoom,'phone',null,false),raceB=await socket(e,raceRoom,'phone',null,false);
 const auth={v:1,type:'auth',room:raceRoom.room,role:'phone',capability:raceRoom.phoneCapability};await Promise.all([message(raceA,auth),message(raceB,auth)]);
 eq([raceA,raceB].filter(x=>x.ws.attachment.role==='phone'&&x.ws.readyState===1).length,1,'concurrent phone auth race');
 e=environment();p=await pair(e);await message(p.web,{v:1,type:'consume',session:b64(16,2)});eq(p.web.ws.reason,'TUKETIM_RED','browser cannot approve phone');
 e=environment();p=await pair(e);await message(p.phone,encrypted());eq(p.phone.ws.reason,'ONAY_GEREKLI','pending data forbidden');
 e=environment();let r=await room(e);now+=90000;const expired=await socket(e,r);eq(expired.res.status,410,'expired QR upgrade refused');ok(!e.ROOMS.objects.get(r.room).ctx.data.has('meta'),'expired room deleted');
 e=environment();p=await pair(e);await consume(p);now+=1100;await message(p.phone,'a'.repeat(65537));eq(p.phone.ws.reason,'BOYUT_SINIRI','64KiB cap');
 e=environment();p=await pair(e);await consume(p);now+=1100;await p.phone.entry.obj.webSocketMessage(p.phone.ws,new ArrayBuffer(20));eq(p.phone.ws.reason,'GECERSIZ_ZARF','binary rejected');
 e=environment();p=await pair(e);const activeCaps=await consume(p);now+=1100;
 for(let i=0;i<50;i++)await message(p.phone,{v:1,type:'ping'});eq(p.phone.ws.readyState,1,'short burst survives');
 for(let i=0;i<20;i++)await message(p.phone,{v:1,type:'ping'});
 ok(p.phone.client.received.some(x=>JSON.parse(x).type==='hiz_siniri'),'drop sends rate notice');eq(p.phone.ws.readyState,1,'first rate excess not closed');
 // Sürekli 20/sn üzeri, araya kabul edilen frame girse de aşım serisi 10 sn sürer.
 for(let t=0;t<110 && p.phone.ws.readyState===1;t++){now+=100;for(let i=0;i<10;i++)await message(p.phone,{v:1,type:'ping'});}
 eq(p.phone.ws.reason,'HIZ_SINIRI','sustained excess closes');
 e=environment({BYTES_PER_MINUTE:'65536'});p=await pair(e);await consume(p);now=Math.ceil(now/60000)*60000+1000;
 const large={...encrypted(),ct:b64(32784,8)};await message(p.phone,large);await message(p.phone,large);
 eq(p.phone.ws.readyState,1,'byte budget drops without immediate close');ok(p.phone.client.received.some(x=>JSON.parse(x).type==='hiz_siniri'),'byte rate notice');
 e=environment();p=await pair(e);const rotated=await consume(p),oldWeb=p.web;const replacement=await socket(e,p.r,'browser',rotated.browser);
 eq(oldWeb.ws.reason,'BAGLANTI_YENILENDI','active old browser closed');now+=1100;await message(p.phone,encrypted());eq(JSON.parse(replacement.client.received.at(-1)).type,'encrypted','frames go to new socket');
 await oldWeb.entry.obj.webSocketClose(oldWeb.ws);eq(oldWeb.entry.ctx.data.get('meta').reconnectUntil,0,'stale close does not break replacement');
 e=environment();p=await pair(e);await consume(p);now+=1100;await message(p.phone,encrypted('1','w2p'));eq(p.phone.ws.reason,'GECERSIZ_ZARF','wrong sender direction');
 e=environment({CREATE_PER_MINUTE:'1'});await room(e);eq((await create(e)).status,429,'global creation budget');
 e=environment();r=await room(e);const pending=[];for(let i=0;i<4;i++)pending.push(await socket(e,r,'phone',null,false));eq((await socket(e,r,'phone',null,false)).res.status,429,'pending socket bound');
 now+=90000;await alarm(pending[0].entry);ok(pending.every(x=>x.ws.readyState===3),'expiry closes all');
 e=environment();
 const compactRoom=b64(16,91),compactCap=b64(32,92);
 const compactHash=Buffer.from(await crypto.subtle.digest('SHA-256',new TextEncoder().encode(compactCap))).toString('base64url');
 const compactResponse=await worker.fetch(new Request(base+'/v2/rooms',{method:'POST',headers:{Origin:origin,'Content-Type':'application/json'},body:JSON.stringify({room:compactRoom,phoneCapabilityHash:compactHash})}),e);
 eq(compactResponse.status,201,'compact room route');
 const compactData=await compactResponse.json();
 eq(compactData.v,2,'compact version');
 ok(!('phoneCapability' in compactData),'relay never gets compact phone capability during creation');
 const compactWeb=await socket(e,compactData,'browser');
 const compactPhone=await socket(e,compactData,'phone',compactCap);
 await message(compactPhone,{v:1,type:'bootstrap_request',room:compactRoom});
 eq(JSON.parse(compactWeb.client.received.at(-1)).type,'bootstrap_request','metadata requested live');
 const metadata={v:1,type:'bootstrap',room:compactRoom,browserPublicKey:b64(91),nb:b64(32),expiresAt:Math.floor(now/1000)+90,mac:b64(32,5)};
 await message(compactWeb,metadata);
 eq(JSON.parse(compactPhone.client.received.at(-1)),metadata,'metadata goes only to authenticated phone');
 ok(!JSON.stringify(compactPhone.entry.ctx.writes).includes('browserPublicKey'),'metadata not persisted');
 await message(compactPhone,metadata);
 eq(compactPhone.ws.reason,'ONAY_GEREKLI','phone cannot inject browser metadata');
 // Kalıcı oda: etkin telefon capability'si olmadan kayıt yok; yalnız public-key hash saklanır.
 e=environment();
 p=await pair(e);
 const enrolledCaps=await consume(p);
 const deviceRoom=b64(16,70);
 const browserIdentity=await crypto.subtle.generateKey({name:'ECDSA',namedCurve:'P-256'},false,['sign','verify']);
 const phoneIdentity=await crypto.subtle.generateKey({name:'ECDSA',namedCurve:'P-256'},false,['sign','verify']);
 const browserSpki=Buffer.from(await crypto.subtle.exportKey('spki',browserIdentity.publicKey)).toString('base64url');
 const phoneSpki=Buffer.from(await crypto.subtle.exportKey('spki',phoneIdentity.publicKey)).toString('base64url');
 const keyHash=async value=>Buffer.from(await crypto.subtle.digest('SHA-256',new TextEncoder().encode(value))).toString('base64url');
 const registration={pairRoom:p.r.room,room:deviceRoom,browserHash:await keyHash(browserSpki),phoneHash:await keyHash(phoneSpki)};
 const register=cap=>worker.fetch(new Request(base+'/v2/devices',{method:'POST',headers:{Authorization:'Bearer '+cap},body:JSON.stringify(registration)}),e);
 eq((await register(b64(32,80))).status,403,'registration requires paired phone');
 eq((await register(enrolledCaps.phone)).status,201,'paired phone enrolls hashes');
 const deviceEntry=e.ROOMS.objects.get(deviceRoom);
 ok(!JSON.stringify(deviceEntry.ctx.data.get('meta')).includes(deviceRoom),'persistent room identifier stored only as hash');
 async function deviceSocket(role,identity,publicKey) {
   const sock=await socket(e,{room:deviceRoom},role,null,false);
   const challenge=JSON.parse(sock.client.received.at(-1));
   eq(challenge.type,'device_challenge','fresh relay challenge');
   const fields=[deviceRoom,role,challenge.nonce];
   const parts=[Buffer.from('zd-web/device-relay/v1\n')];
   for(const field of fields) {
     const bytes=Buffer.from(field),length=Buffer.alloc(4);
     length.writeUInt32BE(bytes.length);
     parts.push(length,bytes);
   }
   const signature=Buffer.from(await crypto.subtle.sign({name:'ECDSA',hash:'SHA-256'},identity.privateKey,Buffer.concat(parts))).toString('base64url');
   const auth={v:1,type:'device_auth',room:deviceRoom,role,publicKey,signature};
   await message(sock,auth);
   return {...sock,auth};
 }
 let persistentPhone=await deviceSocket('phone',phoneIdentity,phoneSpki);
 let persistentWeb=await deviceSocket('browser',browserIdentity,browserSpki);
 eq(persistentPhone.ws.attachment.role,'phone','signed phone auth');
 eq(persistentWeb.ws.attachment.role,'browser','signed browser auth');
 const wrongDevice=await deviceSocket('browser',phoneIdentity,phoneSpki);
 eq(wrongDevice.ws.reason,'AUTH_RED','other role identity cannot connect');
 const replay=await socket(e,{room:deviceRoom},'browser',null,false);
 await message(replay,persistentWeb.auth);
 eq(replay.ws.reason,'AUTH_RED','old challenge signature cannot reconnect');
 await message(persistentPhone,{v:1,type:'device_ready',nonce:b64(32,71),signature:b64(64,72)});
 eq(JSON.parse(persistentWeb.client.received.at(-1)).type,'device_ready','signed endpoint handshake relayed');
 ok(!JSON.stringify(deviceEntry.ctx.writes).includes('device_ready'),'handshake not stored');
 const firstExpiry=deviceEntry.ctx.data.get('meta').expiresAt;
 now+=86400000;
 await message(persistentPhone,{v:1,type:'ping'});
 eq(deviceEntry.ctx.data.get('meta').expiresAt,firstExpiry,'ready heartbeat does not renew thirty days');
 await message(persistentPhone,{v:1,type:'device_active'});
 eq(deviceEntry.ctx.data.get('meta').expiresAt,now+30*86400000,'verified phone marks device use');
 await message(persistentWeb,{v:1,type:'close'});
 eq(JSON.parse(persistentPhone.client.received.at(-1)).reason,'CIKIS','browser closes active connection immediately');
 persistentWeb=await deviceSocket('browser',browserIdentity,browserSpki);
 eq(persistentWeb.ws.attachment.role,'browser','closed page reconnects using identity');
 now=deviceEntry.ctx.data.get('meta').expiresAt;
 await alarm(deviceEntry);
 eq(deviceEntry.ctx.data.size,0,'thirty unused days deletes device metadata');
 eq(logs,[],'no console call on every tested path');
 // Statik ek denetim: DO log API'si yok, IP/cookie başlığı okunmuyor.
 ok(!/console\s*[.[]|CF-Connecting-IP|X-Forwarded-For|headers\.get\(['"]Cookie/.test(src),'no log/IP/cookie reads');
 for(const k of Object.keys(originalConsole))console[k]=originalConsole[k];
 console.log(`PASS ${n}/${n} relay logic (Node mock; gerçek Workers dağıtımı değil)`);
} finally {
 Date.now=originalNow;globalThis.Response=OriginalResponse;
 for(const k of Object.keys(originalConsole))console[k]=originalConsole[k];rmSync(tmp,{recursive:true,force:true});
}
function closeClient(s){s.client.close(1000,'bye');}
