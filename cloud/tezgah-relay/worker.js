// İçerik/şifreli içerik/IP hiçbir kalıcı depoya veya loga yazılmaz.
// Sadece vars üzerinden dağıtım adresi ve izinli origin; E2EE anahtarı bu Worker'a gelmez.
const MAX_FRAME = 65536, AUTH_MS = 5000, PENDING_MS = 90000, RESUME_MS = 300000, LIFE_MS = 28800000;
const encoder = new TextEncoder();
function json(body, status = 200, origin = null) {
  const headers = { 'Content-Type': 'application/json; charset=utf-8', 'Cache-Control': 'no-store' };
  if (origin) { headers['Access-Control-Allow-Origin'] = origin; headers.Vary = 'Origin'; }
  return new Response(JSON.stringify(body), { status, headers });
}
function origins(env) {
  try {
    const list = JSON.parse(env.ALLOWED_ORIGINS || '[]');
    return Array.isArray(list) ? list.filter(o => typeof o === 'string' && new URL(o).origin === o && new URL(o).protocol === 'https:') : [];
  } catch { return []; }
}
function configured(env, url) {
  try { const base = new URL(env.RELAY_URL); return base.protocol === 'https:' && base.origin === url.origin && base.pathname === '/' && !base.username && !base.password && !base.search && !base.hash; }
  catch { return false; }
}
function b64(a) { let s=''; for(const b of a)s+=String.fromCharCode(b);return btoa(s).replaceAll('+','-').replaceAll('/','_').replace(/=+$/,''); }
function rand(n) { return b64(crypto.getRandomValues(new Uint8Array(n))); }
function token(s,n) {
  if(typeof s!=='string'||!(/^[A-Za-z0-9_-]+$/).test(s)||s.length!==Math.ceil(n*8/6))return false;
  try { const raw=atob(s.replaceAll('-','+').replaceAll('_','/'));return raw.length===n && b64(Uint8Array.from(raw,c=>c.charCodeAt(0)))===s; } catch { return false; }
}
async function hash(s) { return b64(new Uint8Array(await crypto.subtle.digest('SHA-256',encoder.encode(s)))); }
function exact(o, keys) { return o && typeof o==='object' && !Array.isArray(o) && Object.keys(o).sort().join(',')===keys.split(',').sort().join(','); }
function send(ws, value) { try { ws.send(JSON.stringify(value));return true; } catch { return false; } }
function close(ws, reason='OTURUM_IPTAL') { try { ws.close(1008,reason); } catch { /* Veri/log yok. */ } }
async function smallBody(request) {
  if(!request.body)return {};
  const reader=request.body.getReader();let size=0;const chunks=[];
  try { for(;;) { const {done,value}=await reader.read();if(done)break;size+=value.length;if(size>512)throw new Error('BOYUT');chunks.push(value); } }
  catch { await reader.cancel().catch(()=>{});throw new Error('GECERSIZ_ISTEK'); }
  const bytes=new Uint8Array(size);let at=0;for(const c of chunks){bytes.set(c,at);at+=c.length;}
  return size?JSON.parse(new TextDecoder('utf-8',{fatal:true}).decode(bytes)):{};
}

export default {
  async fetch(request, env) {
    const url=new URL(request.url), origin=request.headers.get('Origin');
    if(!configured(env,url))return json({error:'YAPILANDIRILMADI'},503);
    const allowed=origins(env).includes(origin);
    // Tarayıcıdan gelen bütün origin'ler doğrulanır. Origin'siz soket yalnız phone auth kabul eder.
    if(origin && !allowed)return json({error:'YETKI_YOK'},403);
    if(request.method==='OPTIONS' && ['/v1/rooms','/v2/rooms'].includes(url.pathname)) {
      if(!allowed)return json({error:'YETKI_YOK'},403);
      return new Response(null,{status:204,headers:{'Access-Control-Allow-Origin':origin,'Access-Control-Allow-Methods':'POST','Access-Control-Allow-Headers':'Content-Type','Cache-Control':'no-store',Vary:'Origin'}});
    }
    if(request.method==='POST' && ['/v1/rooms','/v2/rooms'].includes(url.pathname) && !url.search) {
      if(!allowed)return json({error:'YETKI_YOK'},403);
      if(!request.headers.get('Content-Type')?.toLowerCase().startsWith('application/json'))return json({error:'GECERSIZ_ISTEK'},400,origin);
      try {
        const input = await smallBody(request);
        const compact = url.pathname === '/v2/rooms';
        if (compact ? !exact(input, 'room,phoneCapabilityHash') || !token(input.room, 16) || !token(input.phoneCapabilityHash, 32) : !exact(input, '')) {
          return json({error:'GECERSIZ_ISTEK'},400,origin);
        }
        const gate=env.ROOMS.get(env.ROOMS.idFromName('creation-budget'));
        const budget=await gate.fetch(new Request('https://internal/gate',{method:'POST'}));
        if(budget.status!==200)return json({error:'HIZ_VEYA_KOTA_SINIRI'},429,origin);
        const room = compact ? input.room : rand(16);
        const cb = rand(32), cp = compact ? null : rand(32), now = Date.now();
        const stub=env.ROOMS.get(env.ROOMS.idFromName(room));
        const created=await stub.fetch(new Request('https://internal/init',{method:'POST',body:JSON.stringify({room,browserHash:await hash(cb),phoneHash:compact ? input.phoneCapabilityHash : await hash(cp),expiresAt:now+PENDING_MS})}));
        if(created.status!==201)return json({error:'OLUSTURULAMADI'},503,origin);
        return json({v:compact ? 2 : 1,room,browserCapability:cb,...(compact ? {} : {phoneCapability:cp}),expiresAt:Math.floor((now+PENDING_MS)/1000),relayUrl:env.RELAY_URL},201,origin);
      } catch { return json({error:'GECERSIZ_ISTEK'},400,origin); }
    }
    if(request.method==='GET' && url.pathname==='/v1/socket') {
      const params=[...url.searchParams.keys()],room=url.searchParams.get('room');
      if(params.length!==1||params[0]!=='room'||!token(room,16))return json({error:'GECERSIZ_ISTEK'},400);
      if(request.headers.get('Upgrade')?.toLowerCase()!=='websocket')return json({error:'WEBSOCKET_GEREKLI'},426);
      const stub=env.ROOMS.get(env.ROOMS.idFromName(room));
      // Başlıkların geri kalanını (cookie, IP dahil) DO'ya taşımayız.
      return stub.fetch(new Request('https://internal/socket',{headers:{Upgrade:'websocket','X-Web-Origin':origin||'','X-Room':room}}));
    }
    return json({error:'BULUNAMADI'},404);
  }
};

// SQLite-backed DO; KV storage metotları SQLite arka ucunda yalnız metadata tutar.
export class TezgahRelay {
  constructor(ctx,env) {
    this.ctx=ctx;this.env=env;this.meta=null;
    ctx.blockConcurrencyWhile(async()=>{this.meta=await ctx.storage.get('meta')||null;});
  }
  sockets() { return this.ctx.getWebSockets().filter(ws=>ws.readyState===1); }
  attachment(ws) { return ws.deserializeAttachment()||{}; }
  role(role) { return this.sockets().find(ws=>this.attachment(ws).role===role); }
  async save() { await this.ctx.storage.put('meta',this.meta); }
  async schedule() {
    if(!this.meta || this.meta.kind==='gate')return;
    let at=this.meta.expiresAt;
    if(this.meta.reconnectUntil)at=Math.min(at,this.meta.reconnectUntil);
    for(const ws of this.sockets()){const a=this.attachment(ws);at=Math.min(at,a.role?a.lastSeen+60000:a.authDeadline);}
    const old=await this.ctx.storage.getAlarm();
    if(old===null||old>at)await this.ctx.storage.setAlarm(at);
  }
  async end(reason = 'OTURUM_IPTAL') {
    for(const ws of this.sockets())close(ws, reason);
    this.meta=null;await this.ctx.storage.deleteAll();await this.ctx.storage.deleteAlarm();
  }
  async fetch(request) {
    return this.ctx.blockConcurrencyWhile(async()=>{
      const path=new URL(request.url).pathname,now=Date.now();
      if(path==='/gate' && request.method==='POST') {
        let b=this.meta;
        if(b && b.kind!=='gate')return json({},409);
        if(!b)b={kind:'gate',day:0,dayCount:0,minute:0,minuteCount:0};
        const day=Math.floor(now/86400000),minute=Math.floor(now/60000);
        if(b.day!==day){b.day=day;b.dayCount=0;}if(b.minute!==minute){b.minute=minute;b.minuteCount=0;}
        // IP okunmaz/saklanmaz. Paylaşılan pilot bütçesi kota saldırısını sınırlar, kullanılabilirlik garantisi değildir.
        const perMinute=limit(this.env.CREATE_PER_MINUTE,10,1,100),perDay=limit(this.env.CREATE_PER_DAY,1000,1,5000);
        if(b.dayCount>=perDay||b.minuteCount>=perMinute)return json({},429);
        b.dayCount++;b.minuteCount++;this.meta=b;await this.save();return json({});
      }
      if(path==='/init' && request.method==='POST') {
        if(this.meta)return json({},409);
        const m=await request.json();
        if(!token(m.room,16)||!token(m.browserHash,32)||!token(m.phoneHash,32)||m.expiresAt<=now||m.expiresAt>now+PENDING_MS)return json({},400);
        this.meta={kind:'room',room:m.room,browserHash:m.browserHash,phoneHash:m.phoneHash,expiresAt:m.expiresAt,stage:'pending',reconnectUntil:0,upgradeMinute:0,upgrades:0};
        await this.save();await this.schedule();return json({},201);
      }
      if(path!=='/socket'||!this.meta||this.meta.kind!=='room')return json({},404);
      if(now>=this.meta.expiresAt||(this.meta.reconnectUntil && now>=this.meta.reconnectUntil)){await this.end();return json({},410);}
      if(request.headers.get('X-Room')!==this.meta.room)return json({},403);
      const minute=Math.floor(now/60000);if(this.meta.upgradeMinute!==minute){this.meta.upgradeMinute=minute;this.meta.upgrades=0;}
      if(this.meta.upgrades>=20||this.sockets().length>=4)return json({},429);
      this.meta.upgrades++;await this.save();
      const pair=new WebSocketPair(),client=pair[0],server=pair[1];
      this.ctx.acceptWebSocket(server);
      server.serializeAttachment({authDeadline:now+AUTH_MS,origin:request.headers.get('X-Web-Origin')||'',second:0,count:0,minute:0,bytes:0,lastSeen:now});
      await this.schedule();return new Response(null,{status:101,webSocket:client});
    });
  }
  async webSocketMessage(ws,message) {
    return this.ctx.blockConcurrencyWhile(async()=>{
      try { await this.message(ws,message); } catch { close(ws,'GECERSIZ_ISTEK'); }
    });
  }
  async message(ws,message) {
    const now=Date.now(),m=this.meta,a=this.attachment(ws);
    if(!m||m.kind!=='room'||now>=m.expiresAt||(m.reconnectUntil && now>=m.reconnectUntil)){await this.end();return;}
    if(ws.readyState!==1)return;
    if(typeof message!=='string'){close(ws,'GECERSIZ_ZARF');return;}
    const size=encoder.encode(message).length;
    if(size>MAX_FRAME){close(ws,'BOYUT_SINIRI');return;}
    const rate=limit(this.env.FRAME_RATE,20,1,1000),burst=limit(this.env.FRAME_BURST,60,1,3000);
    const bytesPerMinute=limit(this.env.BYTES_PER_MINUTE,4194304,65536,16777216);
    const minute=Math.floor(now/60000);
    a.tokens=Math.min(burst,(a.tokens??burst)+Math.max(0,now-(a.tokenAt??now))*rate/1000);a.tokenAt=now;
    if(a.minute!==minute){a.minute=minute;a.bytes=0;}
    if(a.tokens<1||a.bytes+size>bytesPerMinute){
      if(a.excessSince==null)a.excessSince=now;
      if(a.excessLast!=null&&now-a.excessLast>1000)a.excessSince=now;
      a.excessLast=now;a.lastSeen=now;
      if(a.warnAt==null||now-a.warnAt>=1000){send(ws,{v:1,type:'hiz_siniri'});a.warnAt=now;}
      ws.serializeAttachment(a);
      if(now-a.excessSince>=10000)close(ws,'HIZ_SINIRI');
      await this.schedule();return;
    }
    a.tokens-=1;a.bytes+=size;if(a.excessLast!=null&&now-a.excessLast>1000){a.excessSince=null;a.excessLast=null;}
    a.lastSeen=now;ws.serializeAttachment(a);
    const f=JSON.parse(message);
    if(!a.role) {
      if(now>=a.authDeadline||!exact(f,'v,type,room,role,capability')||f.v!==1||f.type!=='auth'||f.room!==m.room||!['browser','phone'].includes(f.role)||!token(f.capability,32)) {close(ws,'AUTH_RED');return;}
      if((f.role==='browser'&&!origins(this.env).includes(a.origin))||(f.role==='phone'&&a.origin)){close(ws,'ROL_RED');return;}
      if(await hash(f.capability)!==m[f.role+'Hash']){close(ws,'AUTH_RED');return;}
      const old=this.role(f.role);if(old){const oldA=this.attachment(old);oldA.replaced=true;old.serializeAttachment(oldA);close(old,'BAGLANTI_YENILENDI');}
      a.role=f.role;delete a.origin;delete a.authDeadline;ws.serializeAttachment(a);
      send(ws,{v:1,type:'auth_ok',stage:m.stage});const other=this.role(f.role==='phone'?'browser':'phone');if(other)send(other,{v:1,type:'peer_joined',role:f.role});await this.schedule();return;
    }
    const peer=this.role(a.role==='phone'?'browser':'phone');
    if(exact(f,'v,type')&&f.v===1&&f.type==='ping'){send(ws,{v:1,type:'pong'});await this.schedule();return;}
    if(exact(f,'v,type')&&f.v===1&&f.type==='close'){await this.end(a.role === 'phone' ? 'TELEFON_KAPATTI' : 'TARAYICI_KAPATTI');return;}
    if(f.v!==1){close(ws,'SURUM_DESTEKLENMIYOR');return;}
    if(f.type==='consume') {
      if(!exact(f,'v,type,session')||a.role!=='phone'||m.stage!=='pending'||!peer||!token(f.session,16)){close(ws,'TUKETIM_RED');return;}
      // Sonuç beklenmeden ikinci olayın araya girmemesi blockConcurrencyWhile ile sağlanır.
      const phone=rand(32),browser=rand(32);
      m.stage='active';m.session=f.session;m.expiresAt=now+LIFE_MS;m.phoneHash=await hash(phone);m.browserHash=await hash(browser);
      await this.save();
      send(ws,{v:1,type:'consumed',session:f.session,capability:phone});
      send(peer,{v:1,type:'consumed',session:f.session,capability:browser});
      // İlk pending alarmı erken kalabilir; alarm yeni süreyi tekrar planlar.
      await this.schedule();return;
    }
    if(f.type==='resume_complete') {
      if(!exact(f,'v,type')||a.role!=='phone'||m.stage!=='active'||!peer){close(ws,'RESUME_RED');return;}
      m.reconnectUntil=0;await this.save();await this.schedule();return;
    }
    if(m.stage==='pending') {
      if(size>4096||(!validPairFrame(f,a.role)||f.room!==m.room)){close(ws,'ONAY_GEREKLI');return;}
    } else if(f.type==='encrypted') {
      if(!exact(f,'v,type,session,epoch,dir,seq,ct')||f.session!==m.session||!token(f.epoch,16)||f.dir!==(a.role==='phone'?'p2w':'w2p')||!validSeq(f.seq)||!validCt(f.ct)) {close(ws,'GECERSIZ_ZARF');return;}
    } else if(size>4096||!validResumeFrame(f,a.role)) {close(ws,'GECERSIZ_ZARF');return;}
    // Geçici karşı uç send tamponu dışında payload hiçbir yerde tutulmaz; alıcı yoksa düşer.
    if(peer){try{peer.send(message);}catch{close(peer);}}
    await this.schedule();
  }
  async disconnected(ws) {
    return this.ctx.blockConcurrencyWhile(async()=>{
      const a=this.attachment(ws);close(ws);
      if(this.meta?.stage==='active'&&a.role&&!a.replaced&&!this.role(a.role)&&!this.meta.reconnectUntil){this.meta.reconnectUntil=Date.now()+RESUME_MS;await this.save();}
      if(a.role&&!a.replaced&&!this.role(a.role)){const other=this.role(a.role==='phone'?'browser':'phone');if(other)send(other,{v:1,type:'peer_left',role:a.role});}
      await this.schedule();
    });
  }
  async webSocketClose(ws) { await this.disconnected(ws); }
  async webSocketError(ws) { await this.disconnected(ws); }
  async alarm() {
    return this.ctx.blockConcurrencyWhile(async()=>{
      const now=Date.now(),m=this.meta;if(!m||m.kind==='gate')return;
      if(now>=m.expiresAt||(m.reconnectUntil&&now>=m.reconnectUntil)){await this.end();return;}
      for(const ws of this.sockets()) {
        const a=this.attachment(ws);
        if((!a.role&&now>=a.authDeadline)||(a.role&&now>=a.lastSeen+60000)) {
          close(ws,'ZAMAN_ASIMI');if(a.role&&m.stage==='active'&&!m.reconnectUntil)m.reconnectUntil=now+RESUME_MS;
        }
      }
      await this.save();await this.schedule();
    });
  }
}
function limit(raw,fallback,min,max){const n=Number(raw);return Number.isInteger(n)&&n>=min&&n<=max?n:fallback;}
function validSeq(s){return typeof s==='string'&&/^[1-9][0-9]{0,18}$/.test(s)&&BigInt(s)<=9223372036854775807n;}
function validCt(s){if(typeof s!=='string'||s.length<22||s.length>43712)return false;try{const n=Math.floor(s.length*6/8);return n>=16&&n<=32784&&token(s,n);}catch{return false;}}
function validPairFrame(f,role) {
  if (role === 'phone' && f.type === 'bootstrap_request') {
    return exact(f, 'v,type,room') && token(f.room, 16);
  }
  if (role === 'browser' && f.type === 'bootstrap') {
    return exact(f, 'v,type,room,browserPublicKey,nb,expiresAt,mac') &&
      token(f.room, 16) && token(f.browserPublicKey, 91) && token(f.nb, 32) &&
      Number.isSafeInteger(f.expiresAt) && token(f.mac, 32);
  }
  return role==='phone'?exact(f,'v,type,room,phonePublicKey,np,proof')&&f.type==='phone_hello'&&token(f.room,16)&&token(f.phonePublicKey,91)&&token(f.np,32)&&token(f.proof,32)
    :exact(f,'v,type,room,proof')&&f.type==='web_finished'&&token(f.room,16)&&token(f.proof,32);
}
function validResumeFrame(f,role) {
  if(role==='browser'&&f.type==='resume_offer')return exact(f,'v,type,session,oldEpoch,epoch,nb')&&token(f.session,16)&&token(f.oldEpoch,16)&&token(f.epoch,16)&&f.oldEpoch!==f.epoch&&token(f.nb,32);
  if(role==='phone'&&f.type==='resume_answer')return exact(f,'v,type,np,proof')&&token(f.np,32)&&token(f.proof,32);
  return role==='browser'&&f.type==='resume_finished'&&exact(f,'v,type,proof')&&token(f.proof,32);
}
