import { spawn } from 'node:child_process';
import { createInterface } from 'node:readline';
import { mkdtempSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import assert from 'node:assert/strict';
import { TezgahPage } from '../../web/tezgah/app.js';
import * as C from '../../web/tezgah/js/crypto.js';
let checks = 0;
function check(condition, message) {
  assert.ok(condition, message);
  checks++;
}
const temporary = mkdtempSync(join(tmpdir(), 'zd-command-flow-'));
const child = spawn('java', ['-cp', process.argv[2], 'com.zeytindefteri.app.CommandPhoneBridge', join(temporary, 'phone.sqlite'), 'tests/web_commands/sql_bridge.py'], { stdio: ['pipe', 'pipe', 'inherit'] });
const watchdog = setTimeout(() => {
  console.error('Uçtan uca komut testi 30 saniyede tamamlanamadı.');
  child.kill();
  process.exit(1);
}, 30000);
const waiting = [];
const lines = createInterface({ input: child.stdout });
lines.on('line', line => waiting.shift()?.(JSON.parse(line)));
function call(command) {
  return new Promise((resolve, reject) => {
    const timer = setTimeout(() => reject(new Error('Java yanıt vermedi.')), 15000);
    waiting.push(result => {
      clearTimeout(timer);
      resolve(result);
    });
    child.stdin.write(JSON.stringify(command) + '\n');
  });
}
let qr;
let socket;
let clock = 0;
const results = [];
const page = new TezgahPage({
  relay: 'https://relay.example',
  mono: () => clock,
  qr: value => { qr = value; },
  createRoom: async () => ({ v: 1, room: C.base64(C.random(16)), browserCapability: C.base64(C.random(32)), phoneCapability: C.base64(C.random(32)), expiresAt: Math.floor(Date.now() / 1000) + 85 }),
  connect: () => {
    socket = { readyState: 1, bufferedAmount: 0, frames: [], send(value) { this.frames.push(JSON.parse(value)); }, close() { this.readyState = 3; } };
    return socket;
  }
});
page.onCommand = result => results.push(result);
async function deliver(result) {
  check(!result.error, 'phone protocol: ' + result.error);
  for (const frame of result.frames) {
    // Relay bu kontrolü tüketir; şifreli onaylandi karşı uca aktarılır.
    if (frame.type === 'resume_complete') continue;
    socket.onmessage({ data: JSON.stringify(frame) });
    await page.tail;
  }
}
async function pump() {
  let last;
  let idle = 0;
  for (let count = 0; count < 100; count++) {
    await new Promise(resolve => setTimeout(resolve, 5));
    if (!socket.frames.length) {
      if (++idle >= 6) break;
      continue;
    }
    idle = 0;
    last = await call({ cmd: 'receive', frame: JSON.stringify(socket.frames.shift()) });
    await deliver(last);
  }
  return last;
}
async function pair(full) {
  await page.start();
  let result = await call({ cmd: 'qr', text: qr, full });
  check(!result.error, 'QR accepted');
  socket.onopen();
  socket.frames.shift();
  await page.receive({ v: 1, type: 'auth_ok', stage: 'pending' });
  await deliver(await call({ cmd: 'receive', frame: JSON.stringify({ v: 1, type: 'auth_ok', stage: 'pending' }) }));
  result = await call({ cmd: 'receive', frame: JSON.stringify(socket.frames.shift()) });
  check(result.frames.length === 0 && !page.approved, 'no command/data before phone consent');
  await assert.rejects(page.command('uretici_ekle', { ad: 'Yasak' }));
  checks++;
  result = await call({ cmd: 'approve' });
  const session = result.frames[0].session;
  await page.receive({ v: 1, type: 'consumed', session, capability: C.base64(C.random(32)) });
  await deliver(await call({ cmd: 'receive', frame: JSON.stringify({ v: 1, type: 'consumed', session, capability: C.base64(C.random(32)) }) }));
  await pump();
  check(page.canWrite() === full, 'phone controls permission');
}
try {
  await pair(true);
  await page.command('uretici_ekle', { ad: '<img onerror>', telefon: '' });
  await pump();
  check(results.at(-1).durum === 'tamam', 'producer command completes');
  const meta = page.operation('web_meta');
  await pump();
  const today = (await meta).tarih;
  const fields = { uretici: '<img onerror>', tarih: today, odenen: '20', model: { dara: '2', fiyatlar: ['10', '0', '0', '0', '0', '0'], tartimlar: [{ elek: 0, kasa: 1, kg: '22' }] } };
  const id = await page.command('alim_ekle', fields);
  const original = page.commands.get(id).request;
  let response = await call({ cmd: 'receive', frame: JSON.stringify(socket.frames.shift()) });
  check(response.count === 1 && response.traces.length === 2, 'phone saved and left trace');
  // Cevabı kaybet: ağ kopması/resume sonrası ilk sonuç tekrar sorulur.
  await call({ cmd: 'lost' });
  await page.receive({ v: 1, type: 'peer_left', role: 'phone' });
  await page.receive({ v: 1, type: 'peer_joined', role: 'phone' });
  await pump();
  check(page.approved && page.canWrite(), 'resume retains permission: ' + page.state + ' / ' + page.message);
  await pump();
  check(results.some(result => result.id === id && result.durum === 'tamam'), 'lost response recovered from journal');
  await page.encrypted(original);
  response = await pump();
  check(response.count === 1, 'resend same id across epoch does not duplicate');
  await deliver(await call({ cmd: 'change' }));
  clock += 401;
  await page.tick();
  await pump();
  check(page.data.items[0].uretici === '<img onerror>' && page.data.totals.tutarTL === '200.00', 'new screen comes from phone calculation');
  check(page.data.totals.kalanTL === '180.00', 'phone remaining amount');
  const purchase = page.data.items[0];
  const detailPromise = page.operation('alim_detay', { alimId: purchase.id });
  await pump();
  const detail = await detailPromise;
  const deletion = await page.command('alim_sil', { alimId: purchase.id, surum: detail.surum });
  response = await pump();
  check(response.count === 1 && results.at(-1).durum === 'onay_bekleniyor', 'delete waits on actual phone core');
  await deliver(await call({ cmd: 'decide', id: deletion, approve: false }));
  check(results.at(-1).durum === 'iptal', 'phone rejection delivered');
  const timed = await page.command('alim_sil', { alimId: purchase.id, surum: detail.surum });
  await pump();
  await deliver(await call({ cmd: 'expire' }));
  check(results.at(-1).id === timed && results.at(-1).durum === 'iptal', '60 second expiry delivered');
  const approved = await page.command('alim_sil', { alimId: purchase.id, surum: detail.surum });
  await pump();
  await deliver(await call({ cmd: 'decide', id: approved, approve: true }));
  check(results.some(result => result.id === approved && result.durum === 'onaylandi') && results.at(-1).durum === 'tamam', 'phone approval followed by commit');
  await deliver(await call({ cmd: 'downgrade' }));
  check(!page.canWrite(), 'phone downgrade reaches page');
  // Kötü niyetli web, gizli formu atlayıp geçerli şifreli komut yollar: telefon reddeder.
  const forbiddenId = C.base64(C.random(16));
  page.commands.set(forbiddenId, { checkAt: Infinity });
  await page.encrypted({ v: 1, type: 'komut', id: forbiddenId, tur: 'uretici_ekle', alanlar: { ad: 'Yasak' } });
  await pump();
  check(results.at(-1).durum === 'reddedildi', 'phone enforces readonly against encrypted forged command');
  await call({ cmd: 'lost' });
  await page.receive({ v: 1, type: 'peer_left', role: 'phone' });
  await page.receive({ v: 1, type: 'peer_joined', role: 'phone' });
  await pump();
  check(page.approved && !page.canWrite(), 'resume cannot undo downgrade');
  await pair(false);
  check(!page.canWrite(), 'new readonly pairing default');
  page.stop();
  child.stdin.write(JSON.stringify({ cmd: 'close' }) + '\n');
  console.log(`PASS ${checks} Java phone ↔ JS page encrypted command / resume checks`);
} finally {
  clearTimeout(watchdog);
  lines.close();
  child.kill();
  rmSync(temporary, { recursive: true, force: true });
}
