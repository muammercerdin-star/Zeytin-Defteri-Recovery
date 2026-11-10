// Uçtan uca bağlantı testi: GERÇEK relay worker.js (Node DO taklidi; Workers çalışma zamanı değil),
// GERÇEK web app.js, GERÇEK Java TezgahWebPhone. Telefon saati kayması: relay+tarayıcı saati kaydırılarak
// taklit edilir. 8 Kasım 2026 canlı hatası: telefon saati relay'den birkaç saniye kayınca ve QR 60 sn'den
// yaşlıyken eşleşme reddediliyordu ("20 denemede bir bağlanıyor").
// Kullanım: node e2e.mjs <classpath> <telefonSaatiKaymaSn,...> <qrYasiSn,...> <tarayiciSaatiKaymaSn,...> <connect|reject>
import { spawn } from 'node:child_process';
import { createInterface } from 'node:readline';
import { readFileSync, writeFileSync, mkdtempSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { pathToFileURL } from 'node:url';

const [cp, skewArg = '0', ageArg = '1', browserArg = '0', expect = 'connect'] = process.argv.slice(2);
const realNow = Date.now.bind(Date);
let shift = 0;                       // ms: relay/tarayıcı saati = gerçek + shift
Date.now = () => realNow() + shift;

const tmp = mkdtempSync(join(tmpdir(), 'zd-e2e-'));
writeFileSync(join(tmp, 'worker.mjs'), readFileSync('cloud/tezgah-relay/worker.js', 'utf8'));
const { default: worker, TezgahRelay } = await import(pathToFileURL(join(tmp, 'worker.mjs')));
const { TezgahPage } = await import(pathToFileURL(join(process.cwd(), 'web/tezgah/app.js')));

const OriginalResponse = globalThis.Response;
globalThis.Response = class extends OriginalResponse {
  constructor(body, init = {}) {
    if (init.status === 101) { super(null, { status: 200 }); Object.defineProperty(this, 'status', { value: 101 }); this.webSocket = init.webSocket; }
    else super(body, init);
  }
};
class Socket {
  readyState = 1; attachment = null; listener = null;
  send(message) { if (this.readyState !== 1) throw new Error('CLOSED'); const p = this.peer; queueMicrotask(() => p.listener?.('message', message)); }
  close(code, reason) {
    if (this.readyState === 3) return;
    this.readyState = 3; this.peer.readyState = 3;
    const p = this.peer, self = this;
    queueMicrotask(() => { p.listener?.('close', { code, reason }); self.listener?.('close', { code, reason }); });
  }
  serializeAttachment(a) { this.attachment = structuredClone(a); }
  deserializeAttachment() { return structuredClone(this.attachment); }
}
globalThis.WebSocketPair = class { constructor() { this[0] = new Socket(); this[1] = new Socket(); this[0].peer = this[1]; this[1].peer = this[0]; } };
class Context {
  sockets = []; data = new Map(); alarmAt = null; tail = Promise.resolve();
  constructor() { this.storage = { get: async k => structuredClone(this.data.get(k)), put: async (k, v) => { this.data.set(k, structuredClone(v)); },
    deleteAll: async () => this.data.clear(), getAlarm: async () => this.alarmAt, setAlarm: async at => { this.alarmAt = at; }, deleteAlarm: async () => { this.alarmAt = null; } }; }
  blockConcurrencyWhile(fn) { const p = this.tail.then(fn); this.tail = p.catch(() => {}); return p; }
  acceptWebSocket(ws) { this.sockets.push(ws); }
  getWebSockets() { return this.sockets.filter(s => s.readyState === 1); }
}
class Namespace {
  objects = new Map();
  constructor(env) { this.env = env; }
  idFromName(name) { return name; }
  get(name) { if (!this.objects.has(name)) { const ctx = new Context(), obj = new TezgahRelay(ctx, this.env); this.objects.set(name, { ctx, obj }); } return { fetch: r => this.objects.get(name).obj.fetch(r) }; }
}
const webOrigin = 'https://web.example.test', base = 'https://relay.example.test';
const env = { RELAY_URL: base, ALLOWED_ORIGINS: JSON.stringify([webOrigin]), CREATE_PER_MINUTE: '100' };
env.ROOMS = new Namespace(env);

// Relay soketi: istemci ucu + DO'ya teslim edilen sunucu ucu.
async function relaySocket(room, origin) {
  const headers = { Upgrade: 'websocket' };
  if (origin) headers.Origin = origin;
  const res = await worker.fetch(new Request(base + '/v1/socket?room=' + room, { headers }), env);
  if (res.status !== 101) return { status: res.status };
  const client = res.webSocket, server = client.peer, entry = env.ROOMS.objects.get(room);
  server.listener = (kind, value) => {
    if (kind === 'message') entry.obj.webSocketMessage(server, value);
    else entry.obj.webSocketClose(server);
  };
  return { status: 101, client, server, entry };
}

// Supabase köprüsü üzerinden telefon soketi (E2E_BRIDGE=1): telefon ↔ köprü (gerçek bridge()) ↔ relay.
const bridgeMod = process.env.E2E_BRIDGE ? await import(pathToFileURL(join(process.cwd(), 'bridge.ts'))) : null;
async function phoneSocket(room) {
  if (!bridgeMod) return relaySocket(room, null);
  const upstream = { readyState: 0, onopen: null, onmessage: null, onclose: null, onerror: null, inner: null,
    send(t) { this.inner.send(t); }, close(c, r) { if (this.readyState === 3) return; this.readyState = 3; this.inner?.close(c, r); } };
  relaySocket(room, null).then(s => {          // relay bağlantısı geç açılır: köprü aradaki çerçeveleri bekletmeli
    if (s.status !== 101) { upstream.readyState = 3; upstream.onerror?.({}); return; }
    upstream.inner = s.client; upstream.readyState = 1;
    s.client.listener = (k, v) => { if (k === 'message') upstream.onmessage?.({ data: v }); else { upstream.readyState = 3; upstream.onclose?.(v); } };
    setTimeout(() => upstream.onopen?.({}), 3);
  });
  const phone = { listener: null, send(t) { queueMicrotask(() => server.onmessage?.({ data: t })); } };
  const server = { readyState: 1, onopen: null, onmessage: null, onclose: null, onerror: null,
    send(t) { queueMicrotask(() => phone.listener?.('message', t)); },
    close(c, r) { if (this.readyState === 3) return; this.readyState = 3; queueMicrotask(() => phone.listener?.('close', { code: c, reason: r })); } };
  bridgeMod.bridge(server, upstream);
  return { status: 101, client: phone };
}

// --- Java telefon ---
const child = spawn('java', ['-cp', cp, 'com.zeytindefteri.app.E2EPhone', join(tmp, 'p.sqlite'), 'tests/buyers/sql_helper.py'], { stdio: ['pipe', 'pipe', 'inherit'] });
const waiting = [];
createInterface({ input: child.stdout }).on('line', l => waiting.shift()?.(JSON.parse(l)));
const java = c => new Promise((res, rej) => { const t = setTimeout(() => rej(new Error('JAVA_TIMEOUT')), 15000); waiting.push(r => { clearTimeout(t); res(r); }); child.stdin.write(JSON.stringify(c) + '\n'); });

async function trial(skewSec, ageSec, browserSec = 0) {
  // Oda ageSec saniye önce oluşturuldu; telefon saati relay'den skewSec ileride (negatifse geride).
  shift = -(skewSec + ageSec) * 1000;
  let qr = null, pageSocket = null;
  const page = new TezgahPage({
    relay: base,
    now: () => Date.now() + browserSec * 1000,
    qr: text => { if (text) qr = text; },
    createRoom: async (origin, input) => {
      const r = await worker.fetch(new Request(base + '/v2/rooms', { method: 'POST', headers: { Origin: webOrigin, 'Content-Type': 'application/json' }, body: JSON.stringify(input) }), env);
      if (r.status !== 201) throw new Error('RELAY_' + r.status);
      return r.json();
    },
    connect: url => {
      const room = new URL(url).searchParams.get('room');
      const ws = { readyState: 0, bufferedAmount: 0, inner: null,
        send(t) { this.inner.send(t); }, close(code, reason) { this.readyState = 3; this.inner?.close(code ?? 1000, reason ?? ''); } };
      relaySocket(room, webOrigin).then(s => {
        if (s.status !== 101) { ws.readyState = 3; ws.onclose?.({ code: 1006, reason: 'HTTP_' + s.status }); return; }
        ws.inner = s.client; ws.readyState = 1;
        s.client.listener = (kind, v) => { if (kind === 'message') ws.onmessage?.({ data: v }); else { ws.readyState = 3; ws.onclose?.(v); } };
        ws.onopen?.();
      });
      pageSocket = ws;
      return ws;
    }
  });
  await page.start();
  await new Promise(r => setTimeout(r, 20));
  if (!qr) return { ok: false, why: 'QR yok: ' + page.state + ' ' + page.message };
  // QR ekranda ageSec saniyedir duruyor: relay ve tarayıcı saati şimdiye gelir.
  shift = -skewSec * 1000;
  const room = Buffer.from(qr.slice(4), 'base64url').subarray(0, 16).toString('base64url');
  let r = await java({ cmd: 'qr', text: qr });
  if (r.error) return { ok: false, why: 'tarama: ' + r.error };
  const ph = await phoneSocket(room);
  if (ph.status !== 101) return { ok: false, why: 'telefon soketi HTTP ' + ph.status };
  let closed = null;
  const inbox = [];
  ph.client.listener = (kind, v) => { if (kind === 'message') inbox.push(v); else closed = v; };
  const pump = async frames => { for (const f of frames) ph.client.send(JSON.stringify(f)); await new Promise(x => setTimeout(x, 5)); };
  r = await java({ cmd: 'opened' }); await pump(r.frames);
  for (let i = 0; i < 40 && !r.ready; i++) {
    await new Promise(x => setTimeout(x, 10));
    await page.tail;
    while (inbox.length) {
      r = await java({ cmd: 'receive', frame: inbox.shift() });
      if (r.error) return { ok: false, why: 'telefon: ' + r.error };
      await pump(r.frames);
    }
    if (closed) return { ok: false, why: 'telefon soketi kapandı: ' + closed.reason };
  }
  await new Promise(x => setTimeout(x, 30)); await page.tail;
  const ok = r.ready && page.approved && page.state === 'bagli';
  const why = ok ? '' : `telefon hazır=${r.ready} sayfa=${page.state} ${page.message}`;
  page.wipe(false);
  return { ok, why };
}

let pass = 0, fail = 0;
const rows = [];
for (const browser of browserArg.split(',').map(Number)) {
for (const skew of skewArg.split(',').map(Number)) {
  for (const age of ageArg.split(',').map(Number)) {
    const res = await trial(skew, age, browser);
    res.ok ? pass++ : fail++;
    rows.push(`telefon saati ${String(skew).padStart(4)} sn | tarayıcı saati ${String(browser).padStart(4)} sn | QR yaşı ${String(age).padStart(3)} sn | ${res.ok ? 'BAĞLANDI' : 'BAŞARISIZ → ' + res.why}`);
  }
}
}
await java({ cmd: 'close' }).catch(() => {});
child.kill();
const unexpected = expect === 'connect' ? fail : pass;
if (unexpected) {
  console.log(rows.join('\n'));
  console.log(`FAIL: beklenen ${expect}, ${unexpected} deneme farklı sonuçlandı`);
  process.exit(1);
}
console.log(`PASS: ${pass + fail} uçtan uca deneme${bridgeMod ? ' (Supabase köprüsü üzerinden)' : ''} (${expect === 'connect' ? 'hepsi bağlandı' : 'süresi dolan QR hepsi reddedildi'})`);
process.exit(0);
