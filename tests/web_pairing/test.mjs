import { spawn } from 'node:child_process';
import { createInterface } from 'node:readline';
import { writeFileSync, mkdtempSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import assert from 'node:assert/strict';
import { TezgahPage, drawCode } from '../../web/tezgah/app.js';
import * as C from '../../web/tezgah/js/crypto.js';
import * as Compact from '../../web/tezgah/js/compact.js';
const temp = mkdtempSync(join(tmpdir(), 'zd-compact-'));
const java = spawn('java', ['-cp', process.argv[2], 'com.zeytindefteri.app.PhoneBridge', join(temp, 'db'), 'tests/buyers/sql_helper.py']);
java.stderr.pipe(process.stderr);
const waiting = [];
createInterface({ input: java.stdout }).on('line', line => waiting.shift()(JSON.parse(line)));
function call(command) {
  return new Promise((resolve, reject) => {
    const timeout = setTimeout(() => reject(new Error('JAVA_TIMEOUT')), 15000);
    waiting.push(result => {
      clearTimeout(timeout);
      resolve(result);
    });
    java.stdin.write(JSON.stringify(command) + '\n');
  });
}
let n = 0;
function check(ok, label) {
  assert.ok(ok, label);
  n++;
}
let qr;
let socket;
let request;
let clock = 0;
const app = new TezgahPage({ relay: 'https://relay.example', mono: () => clock, qr: value => { qr = value; },
  createRoom: async (origin, input) => {
    request = input;
    return { v: 2, room: input.room, browserCapability: C.base64(C.random(32)), expiresAt: Math.floor(Date.now()/1000) + 85 };
  },
  connect: () => {
    socket = { readyState: 1, bufferedAmount: 0, frames: [], send(text) { this.frames.push(JSON.parse(text)); }, close() { this.readyState = 3; } };
    return socket;
  }
});
async function receive(frame) {
  socket.onmessage({ data: JSON.stringify(frame) });
  await app.tail;
}
const phone = frame => call({ cmd: 'receive', frame: JSON.stringify(frame) });
const vectors = [];
try {
  for (let cycle = 0; cycle < 5; cycle++) {
    await app.start();
    check(qr.length === 68 && qr.startsWith('ZD1.'), '68 karakter QR');
    vectors.push(qr);
    let reply = await call({ cmd: 'qr', text: qr });
    check(!reply.error && reply.frames[0].type === 'auth', 'Java kısa QR');
    const cap = reply.frames[0].capability;
    check(C.base64(await C.sha256(new TextEncoder().encode(cap))) === request.phoneCapabilityHash, 'Java/JS HKDF capability aynı');
    socket.onopen();
    socket.frames.shift();
    await receive({ v: 1, type: 'auth_ok', stage: 'pending' });
    reply = await phone({ v: 1, type: 'auth_ok', stage: 'pending' });
    check(reply.frames[0].type === 'bootstrap_request', 'B doğrulanmadan ECDH yok');
    await receive(reply.frames[0]);
    const bootstrap = socket.frames.shift();
    if (cycle === 0) {
      const forged = { ...bootstrap, mac: C.base64(C.random(32)) };
      check((await phone(forged)).error === 'QR_DOGRULANAMADI', 'HMAC değiştirme reddi');
      const newBrowser = await C.keyPair();
      const B = C.base64(await crypto.subtle.exportKey('spki', newBrowser.publicKey));
      const secret = C.unbase64(qr.slice(4)).slice(16);
      const altered = await Compact.bootstrap(bootstrap.room, secret, B, bootstrap.nb, bootstrap.expiresAt);
      check((await phone(altered)).error === 'SABIT_B_UYUSMAZ', 'S çalınsa bile B değiştirme reddi');
      const stale = await Compact.bootstrap(bootstrap.room, secret, bootstrap.browserPublicKey, bootstrap.nb, Math.floor(Date.now()/1000) - 1);
      check((await phone(stale)).error === 'QR_SURESI_DOLDU', 'eski QR reddi');
      const near = await Compact.bootstrap(bootstrap.room, secret, bootstrap.browserPublicKey, bootstrap.nb, Math.floor(Date.now()/1000) + 20);
      check((await phone(near)).error === 'QR_YENILENMEK_UZERE', '30 sn altı QR reddi');
    }
    reply = await phone(bootstrap);
    check(!reply.error && reply.frames[0].type === 'phone_hello', 'doğrulanan bootstrap');
    await receive(reply.frames[0]);
    reply = await phone(socket.frames.shift());
    check(!reply.error && reply.frames.length === 0 && !app.approved, 'onaysız veri yok');
    reply = await call({ cmd: 'approve' });
    const consume = reply.frames[0];
    await receive({ v: 1, type: 'consumed', session: consume.session, capability: C.base64(C.random(32)) });
    reply = await phone({ v: 1, type: 'consumed', session: consume.session, capability: C.base64(C.random(32)) });
    await receive(reply.frames[0]);
    check(app.approved, 'sayfa açıldı');
    reply = await phone(socket.frames.shift());
    await receive(reply.frames[0]);
    check(app.data.items[0].uretici === 'Java Uretici', 'gerçek SQLite veri');
    if (cycle === 0) {
      const oldSocket = socket;
      await app.start('Bağlantıyı kestiniz.');
      check(oldSocket.frames.at(-1).type === 'close', 'tarayıcı kesince relay kapatma mesajı');
    } else if (cycle === 1) {
      const generation = app.generation;
      socket.onclose({code:1008,reason:'TELEFON_KAPATTI'});
      for (let wait = 0; wait < 200 && !app.room; wait++) await new Promise(resolve => setTimeout(resolve,1));
      check(app.generation > generation && !app.approved && app.message.includes('Telefon bağlantıyı kapattı'), 'telefon kapattı nedeni ve yeni QR');
    } else if (cycle === 2) {
      app.stop();
      check(!app.keys && !app.data, 'sayfa kapanınca anahtar/veri temizliği');
    } else if (cycle === 3) {
      await call({cmd:'lost'});
      await receive({v:1,type:'peer_left',role:'phone'});
      const epoch = app.epoch;
      await app.visible();
      reply = await phone(socket.frames.shift());
      check(reply.frames[0].type === 'resume_answer', 'sekme görünür olunca anında resume');
      await receive(reply.frames[0]);
      reply = await phone(socket.frames.shift());
      await receive(reply.frames[1]);
      check(app.approved && app.epoch !== epoch, 'arka plan dönüşünde yeni epoch');
      reply = await phone(socket.frames.shift());
      await receive(reply.frames[0]);
      app.stop();
    } else {
      clock += 300001;
      await app.tick();
      check(!app.keys && !app.data && !app.approved, 'geçici oturumda beş dakika kilidi');
      const generation = app.generation;
      app.qrDeadline = clock - 1;
      await app.tick();
      check(app.generation > generation, 'süresi dolan QR yenilenir');
    }
  }
  vectors.push('Z', 'ZD1.' + 'A'.repeat(64), 'ZD1.' + '_'.repeat(64), 'A'.repeat(512));
  const raster = vectors.map(text => {
    let pixels;
    const canvas = { width: 0, height: 0, getContext: () => ctx };
    const ctx = { fillStyle: '#ffffff', clearRect() {}, fillRect(x,y,w,h) {
      if (!pixels || pixels.length !== canvas.width * canvas.height) pixels = Array(canvas.width * canvas.height).fill(0xffffff);
      const color = parseInt(this.fillStyle.slice(1), 16);
      for (let row = Math.ceil(y); row < Math.min(canvas.height,y+h); row++) {
        for (let col = Math.ceil(x); col < Math.min(canvas.width,x+w); col++) pixels[row*canvas.width+col] = color;
      }
    } };
    drawCode(canvas,text);
    check(canvas.width >= 320, 'en az 320 piksel');
    return { text, size: canvas.width, pixels };
  });
  writeFileSync(process.argv[3], JSON.stringify(raster));
  console.log(`PASS ${n} kısa QR / 5 Java-sayfa eşleşme döngüsü / kurcalama kontrolleri`);
} finally {
  java.kill();
  rmSync(temp, { recursive: true, force: true });
}
