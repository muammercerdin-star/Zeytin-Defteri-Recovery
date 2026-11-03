import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { createHash } from 'node:crypto';
import { TezgahPage, money, kg, dateLabel, relayOrigin, renderRows, drawCode, updateLabel } from '../../web/tezgah/app.js';
import * as C from '../../web/tezgah/js/crypto.js';
import * as P from '../../web/tezgah/js/protocol.js';
import qrCode from '../../web/tezgah/js/vendor/qrcode.js';

let checks = 0;
function check(value, message) {
  assert.ok(value, message);
  checks++;
}
function equal(a, b, message) {
  assert.deepEqual(a, b, message);
  checks++;
}

class Element {
  constructor(tag) {
    this.tag = tag;
    this.children = [];
    this.textContent = '';
  }
  append(child) {
    this.children.push(child);
  }
  replaceChildren() {
    this.children = [];
  }
  set innerHTML(value) {
    throw new Error('Unsafe DOM write: ' + value);
  }
}
const document = { createElement: name => new Element(name) };
const table = new Element('tbody');
const xss = '<img src=x onerror=alert(1)>';
renderRows(document, table, [{ tarih: '2026-11-02', uretici: xss, netKg: '1234.5',
  kasa: 3, tutarTL: '1234.50', odenenTL: '100.00', kalanTL: '1134.50' }]);
equal(table.children[0].children[1].textContent, xss, 'producer is literal text');
equal(table.children[0].children[1].children.length, 0, 'no img node');
equal(table.children[0].children.length, 7, 'all columns');
equal(money('1234.50'), '1.234,50 ₺', 'Turkish money');
equal(money('-12.03'), '-12,03 ₺', 'negative money');
equal(money('0'), '0,00 ₺', 'zero money');
equal(kg('1234.5'), '1.234,50', 'Turkish kg');
equal(dateLabel('2026-11-02'), '02.11.2026', 'date');
equal(dateLabel('<img>'), '—', 'invalid date');
equal(updateLabel('2026-11-02T23:41:00+03:00', 'Europe/Istanbul'), '23:41', 'phone timezone');
equal(updateLabel('2026-11-02T23:41:00+03:00', 'GMT+03:00'), '23:41', 'Android fixed offset fallback');
equal(relayOrigin('https://relay.example/'), 'https://relay.example', 'origin');
for (const url of ['http://relay.example', 'https://a:b@relay.example',
  'https://relay.example/path', 'https://relay.example/#x', 'https://relay.example/?x']) {
  assert.throws(() => relayOrigin(url));
  checks++;
}

let clock = 0;
let wall = Date.now();
let created = 0;
let code;
const sockets = [];
const states = [];
const page = new TezgahPage({
  relay: 'https://relay.example',
  now: () => wall,
  mono: () => clock,
  view: state => states.push(state),
  qr: text => { code = text; },
  createRoom: async () => {
    created++;
    return { v: 1, room: C.base64(C.random(16)), expiresAt: Math.floor(wall / 1000) + 90,
      browserCapability: C.base64(C.random(32)), phoneCapability: C.base64(C.random(32)),
      relayUrl: 'https://attacker.example' };
  },
  connect: url => {
    const socket = { readyState: 1, bufferedAmount: 0, sent: [], url,
      send(text) { this.sent.push(JSON.parse(text)); },
      close() { this.readyState = 3; } };
    sockets.push(socket);
    return socket;
  }
});
await page.start();
let socket = sockets.at(-1);
socket.onopen();
equal(socket.sent[0].type, 'auth', 'first frame auth');
equal(socket.sent[0].role, 'browser', 'browser role');
check(socket.url.startsWith('wss://relay.example/'), 'server cannot select relay');
const firstQr = JSON.parse(code);
check(!('relay' in firstQr) && !('browserCapability' in firstQr), 'QR excludes browser capability and relay');
equal(firstQr.kind, 'ZD_WEB_PAIR', 'QR type');
socket.onmessage({ data: JSON.stringify({ v: 1, type: 'auth_ok', stage: 'pending' }) });
await page.tail;
equal(page.state, 'qr', 'QR ready');
await page.request(null, 0);
equal(socket.sent.length, 1, 'no requests before approval');

const phone = await C.keyPair();
const np = C.base64(C.random(32));
const phoneSpki = C.base64(await crypto.subtle.exportKey('spki', phone.publicKey));
const t = await P.transcript(firstQr.room, firstQr.expiresAt, firstQr.nb, np,
  firstQr.browserPublicKey, phoneSpki);
const phoneKeys = await C.initial(await C.ecdh(phone.privateKey, firstQr.browserPublicKey),
  C.unbase64(firstQr.secret), t);
const hello = { v: 1, type: 'phone_hello', room: firstQr.room, phonePublicKey: phoneSpki,
  np, proof: C.base64(await C.proof(phoneKeys.confirmation(), 'phone-hello', phoneKeys.transcriptHash)) };
await page.receive(hello);
equal(page.state, 'onay', 'wait for explicit phone approval');
equal(page.approved, false, 'hello not approval');
const finished = socket.sent.at(-1);
await C.verify(phoneKeys.confirmation(), 'web-finished', phoneKeys.transcriptHash,
  C.unbase64(finished.proof));
check(true, 'browser key proof');
await page.request(null, 0);
equal(socket.sent.at(-1).type, 'web_finished', 'no data before consumed');
await page.receive({ v: 1, type: 'consumed', session: page.session,
  capability: C.base64(C.random(32)) });
equal(page.approved, false, 'relay consumed is not approval');
equal(code, null, 'QR removed after consume');
const phoneChannel = new C.Channel(phoneKeys, page.session, page.epoch, 'p2w');
async function deliver(payload) {
  await page.receive(await phoneChannel.seal(P.utf8(JSON.stringify(payload))));
}
await deliver({ v: 1, type: 'onaylandi', session: page.session, permissions: ['tezgah.read'] });
equal(page.state, 'bagli', 'encrypted approval opens UI');
const ask = JSON.parse(new TextDecoder().decode(await phoneChannel.open(socket.sent.at(-1))));
equal(ask.op, 'bugunun_alimlari', 'only view command');
equal(ask.args.kapsam, 'acik_donem', 'open period default');
equal(ask.args.limit, 100, 'bounded page');
const data = { items: [], totals: { count: 0, netKg: '0', tutarTL: '0', odenenTL: '0', kalanTL: '0' },
  complete: false, nextCursor: 'cursor1', asOf: new Date().toISOString(), timezone: 'Europe/Istanbul' };
await deliver({ v: 1, type: 'cevap', id: ask.id, op: ask.op, ok: true, data });
equal(page.data.items.length, 0, 'empty list successful');
equal(page.pending, null, 'response ends request');
await page.navigate(1);
let next = JSON.parse(new TextDecoder().decode(await phoneChannel.open(socket.sent.at(-1))));
equal(next.args.cursor, 'cursor1', 'next cursor');
await deliver({ v: 1, type: 'cevap', id: next.id, op: next.op, ok: true,
  data: { ...data, complete: true, nextCursor: null } });
equal(page.page, 1, 'second page');
await deliver({ v: 1, type: 'olay', event: 'veri_degisti', revision: '2' });
clock += 401;
await page.tick();
next = JSON.parse(new TextDecoder().decode(await phoneChannel.open(socket.sent.at(-1))));
equal(next.args.cursor, null, 'invalidation creates fresh snapshot');
await deliver({ v: 1, type: 'cevap', id: next.id, op: next.op, ok: true, data });
next = JSON.parse(new TextDecoder().decode(await phoneChannel.open(socket.sent.at(-1))));
equal(next.args.cursor, 'cursor1', 'invalidation walks back to visible page');
await deliver({ v: 1, type: 'cevap', id: next.id, op: next.op, ok: true,
  data: { ...data, complete: true, nextCursor: null } });
equal(page.page, 1, 'visible page preserved');
await page.refresh();
for (let retry = 0; retry < 3; retry++) {
  const expired = JSON.parse(new TextDecoder().decode(await phoneChannel.open(socket.sent.at(-1))));
  await deliver({ v: 1, type: 'hata', id: expired.id, code: 'SNAPSHOT_GECERSIZ' });
  check(page.pending.id !== expired.id, 'expired snapshot gets a new request');
}
equal(page.page, 0, 'repeated snapshot expiry falls back to first page');
await page.setFilter('2026-11-02');
next = JSON.parse(new TextDecoder().decode(await phoneChannel.open(socket.sent.at(-1))));
equal(next.args.tarih, '2026-11-02', 'calendar filter');
equal(next.args.kapsam, 'takvim', 'calendar distinct from open period');
page.disconnected();
equal(page.state, 'koptu', 'disconnect state');
check(states.at(-1).stale, 'stale rows on disconnect');
await page.setFilter('2026-11-01');
equal(states.at(-1).data, null, 'offline filter cannot leave old rows under a new date');
equal(page.filter.tarih, '2026-11-01', 'offline selection retained for next connection');
page.data = data;
const oldKeys = page.keys;
clock += 100;
page.activity();
clock += 299999;
// QR yeniden açılmadan önce tam hareketsizlik sınırı; eski veri/anahtar kalmaz.
clock++;
await page.tick();
equal(created, 2, 'idle creates new QR');
equal(page.data, null, 'idle removes all data');
equal(page.keys, null, 'idle removes keys');
assert.throws(() => oldKeys.resumeRoot(), /OTURUM_IPTAL/);
checks++;
check(JSON.parse(code).room !== firstQr.room, 'idle uses new room');
equal(sockets[0].sent.at(-1).type, 'close', 'old room explicitly closed');
clock += 90001;
wall += 90001;
await page.tick();
equal(created, 3, 'expired QR renewed');
equal(sockets[1].sent.at(-1).type, 'close', 'expired room closed');
// Gecikmiş eski socket callback'i yeni oturumun durumunu değiştiremez.
socket.onmessage({ data: JSON.stringify({ v: 1, type: 'auth_ok', stage: 'active' }) });
await page.tail;
equal(created, 3, 'old callback ignored');
page.stop();
equal(page.data, null, 'stop clears data');
equal(states.at(-1).data, null, 'stop also clears rendered data');
equal(states.at(-1).state, 'kapandi', 'pagehide closes displayed session');

// Bazı işletim sistemlerinde performance.now uyku sırasında ilerlemez.
// Duvar saati de kontrol edilir; geriye alınırsa monoton saat koruması sürer.
let sleeperWall = Date.now();
let sleeperRooms = 0;
const sleeper = new TezgahPage({
  relay: 'https://relay.example',
  mono: () => 0,
  now: () => sleeperWall,
  createRoom: async () => {
    sleeperRooms++;
    return { v: 1, room: C.base64(C.random(16)),
      expiresAt: Math.floor(sleeperWall / 1000) + 90,
      browserCapability: C.base64(C.random(32)), phoneCapability: C.base64(C.random(32)) };
  },
  connect: () => ({ readyState: 1, bufferedAmount: 0, send() {}, close() {} })
});
await sleeper.start();
sleeper.consumed = true;
sleeper.startedAt = 0;
sleeper.startedWall = sleeperWall;
sleeper.data = data;
sleeperWall += 300001;
await sleeper.tick();
equal(sleeperRooms, 2, 'sleep time still triggers idle lock');
equal(sleeper.data, null, 'sleep wake removes stale data');
sleeper.stop();

let configureCalls = 0;
const disabled = new TezgahPage({ relay: '', createRoom: () => { configureCalls++; } });
await disabled.start();
equal(disabled.state, 'yapilandirilmadi', 'empty config disabled');
equal(configureCalls, 0, 'empty config no network');
for (const [status, label] of [[403, 'ORIGIN_403'], [503, 'YAPILANDIRILMADI_503']]) {
  const original = globalThis.fetch;
  globalThis.fetch = async () => ({ ok: false, status });
  const failed = new TezgahPage({ relay: 'https://relay.example' });
  await failed.start();
  equal(failed.state, 'hata', 'HTTP failure state');
  check(failed.message.includes(label), 'Turkish HTTP diagnostic with code');
  globalThis.fetch = original;
}

const fixture = JSON.parse(readFileSync('tests/web_page/qr-vectors.json', 'utf8'));
for (const vector of fixture.vectors) {
  const code = qrCode(0, 'M');
  code.addData(vector.text, 'Byte');
  code.make();
  equal(code.getModuleCount(), vector.size, 'independent QR size');
  let bits = '';
  for (let y = 0; y < vector.size; y++) {
    for (let x = 0; x < vector.size; x++) {
      bits += code.isDark(y, x) ? '1' : '0';
    }
  }
  const hash = createHash('sha256').update(bits).digest('hex');
  check(vector.matrixSha256ByMask.includes(hash), 'Nayuki independent QR matrix vector');
}
let rectangles = 0;
const canvas = { width: 420, height: 420, getContext: () => ({
  clearRect() {}, fillRect() { rectangles++; } }) };
drawCode(canvas, JSON.stringify(firstQr));
check(canvas.width > 300 && rectangles > 100, 'actual pairing QR rendered locally');
const source = readFileSync('web/tezgah/app.js', 'utf8');
check(!/innerHTML|localStorage|indexedDB|document\.cookie|sessionStorage/.test(source), 'no unsafe DOM or persistent storage');
const headers = readFileSync('web/tezgah/_headers', 'utf8');
for (const rule of ["default-src 'self'", "frame-ancestors 'none'", 'Referrer-Policy: no-referrer',
  'Cache-Control: no-store']) {
  check(headers.includes(rule), 'security header ' + rule);
}
// connect-src, config.js'teki relay adresiyle aynı olmalı (boşsa yer tutucu kalır).
const relayConfig = readFileSync('web/tezgah/config.js', 'utf8').match(/RELAY_URL = '([^']*)'/)[1];
const relayHost = relayConfig ? new URL(relayConfig).host : 'relay.example.invalid';
check(!relayConfig || relayConfig === 'https://' + relayHost, 'relay config is a bare https origin');
check(headers.includes('connect-src https://' + relayHost + ' wss://' + relayHost + ';'),
  'CSP connect-src matches config relay ' + relayHost);
check(!/connect-src[^;]*\*/.test(headers), 'no wildcard connections');
check(!/https?:\/\//.test(readFileSync('web/tezgah/index.html', 'utf8')), 'no CDN or remote assets');
const gradle = readFileSync('app/build.gradle', 'utf8');
check(gradle.includes("com.squareup.okhttp3:okhttp:4.12.1"), 'JVM JAR version fixed');
check(!gradle.includes('okhttp-android') && !gradle.includes('okhttp:5.'), 'no SDK37 OkHttp AAR');
check(/compileSdk\s+36/.test(gradle) && /minSdk\s+24/.test(gradle), 'SDK unchanged');
const home = readFileSync('app/src/main/java/com/zeytindefteri/app/TezgahHomeActivity.java', 'utf8');
check(/\/\*\* "Sor" penceresi:[^\n]+\*\/\s+private void askQuestion\(\)/.test(home), 'Javadoc belongs to askQuestion');
console.log(`PASS ${checks}/${checks} web page unit / QR / DOM / security`);
