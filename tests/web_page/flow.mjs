import { spawn } from 'node:child_process';
import { createInterface } from 'node:readline';
import { mkdtempSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import assert from 'node:assert/strict';
import { TezgahPage } from '../../web/tezgah/app.js';
import * as C from '../../web/tezgah/js/crypto.js';

const temp = mkdtempSync(join(tmpdir(), 'zd-real-page-'));
const child = spawn('java', ['-cp', process.argv[2], 'com.zeytindefteri.app.PhoneBridge',
  join(temp, 'phone.sqlite'), 'tests/buyers/sql_helper.py'], { stdio: ['pipe', 'pipe', 'inherit'] });
const waiting = [];
const lines = createInterface({ input: child.stdout });
lines.on('line', line => waiting.shift()?.(JSON.parse(line)));
let checks = 0;
function check(condition, message) {
  assert.ok(condition, message);
  checks++;
}
function call(command) {
  return new Promise((resolve, reject) => {
    const timer = setTimeout(() => reject(new Error('JAVA_TIMEOUT')), 15000);
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
const app = new TezgahPage({
  relay: 'https://relay.example',
  mono: () => clock,
  qr: text => { qr = text; },
  createRoom: async () => ({ v: 1, room: C.base64(C.random(16)),
    browserCapability: C.base64(C.random(32)), phoneCapability: C.base64(C.random(32)),
    expiresAt: Math.floor(Date.now() / 1000) + 85 }),
  connect: () => {
    socket = { readyState: 1, bufferedAmount: 0, frames: [],
      send(text) { this.frames.push(JSON.parse(text)); },
      close() { this.readyState = 3; } };
    return socket;
  }
});
async function browserReceive(frame) {
  socket.onmessage({ data: JSON.stringify(frame) });
  await app.tail;
}
async function phoneReceive(frame) {
  return call({ cmd: 'receive', frame: JSON.stringify(frame) });
}
try {
  await app.start();
  const parsed = JSON.parse(qr);
  let result = await call({ cmd: 'qr', text: qr });
  check(!result.error && result.frames[0].type === 'auth', 'real phone accepts page QR');
  socket.onopen();
  check(socket.frames.shift().role === 'browser', 'browser auth');
  await browserReceive({ v: 1, type: 'auth_ok', stage: 'pending' });
  result = await phoneReceive({ v: 1, type: 'auth_ok', stage: 'pending' });
  await browserReceive(result.frames[0]);
  check(app.state === 'onay' && !app.approved, 'waiting approval');
  result = await phoneReceive(socket.frames.shift());
  check(!result.error && result.frames.length === 0, 'browser proof alone cannot consume');
  check(!app.pending && socket.frames.length === 0, 'page never requests before phone approval');
  result = await call({ cmd: 'approve' });
  const consume = result.frames[0];
  check(consume.type === 'consume', 'local approval enables consume');
  await browserReceive({ v: 1, type: 'consumed', session: consume.session,
    capability: C.base64(C.random(32)) });
  check(!app.approved, 'consumed alone still locked');
  result = await phoneReceive({ v: 1, type: 'consumed', session: consume.session,
    capability: C.base64(C.random(32)) });
  await browserReceive(result.frames[0]);
  check(app.approved && app.state === 'bagli', 'Java encrypted onaylandi opens actual page');
  const oldRequest = socket.frames.shift();
  result = await phoneReceive(oldRequest);
  await browserReceive(result.frames[0]);
  check(app.data.items[0].uretici === 'Java Uretici', 'actual page gets real SQLite producer');
  check(app.data.totals.kalanTL === '75.00', 'actual page gets remaining balance');
  check(app.data.complete && app.data.nextCursor === null, 'real completion contract');
  const previousSnapshot = app.data.snapshot;
  result = await call({ cmd: 'change' });
  await browserReceive(result.frames[0]);
  check(socket.frames.length === 0, 'invalidation debounce');
  clock += 401;
  await app.tick();
  check(socket.frames.length === 1, 'actual page refreshes after invalidation');
  result = await phoneReceive(socket.frames.shift());
  await browserReceive(result.frames[0]);
  check(app.data.snapshot !== previousSnapshot && !app.pending, 'fresh SQLite snapshot after event');
  await call({ cmd: 'lost' });
  await browserReceive({ v: 1, type: 'peer_left', role: 'phone' });
  check(app.state === 'koptu' && app.data.items.length === 1, 'retains stale rows on loss');
  const oldEpoch = app.epoch;
  await browserReceive({ v: 1, type: 'peer_joined', role: 'phone' });
  result = await phoneReceive(socket.frames.shift());
  check(!result.error && result.frames[0].type === 'resume_answer', 'Java accepts real page resume offer');
  await browserReceive(result.frames[0]);
  result = await phoneReceive(socket.frames.shift());
  check(!result.error && result.frames[0].type === 'resume_complete', 'mutual resume proof');
  await browserReceive(result.frames[1]);
  check(app.state === 'yeniden' && app.epoch !== oldEpoch, 'actual page new epoch / reconnected');
  result = await phoneReceive(socket.frames.shift());
  await browserReceive(result.frames[0]);
  check(app.data.items[0].uretici === 'Java Uretici', 'fresh data after resume');
  result = await phoneReceive(oldRequest);
  check(result.error === 'ESKI_EPOCH_VEYA_YON', 'old page request rejected after resume');
  // Aynı gerçek sayfa QR'ının süresi ve anahtar doğrulaması: telefon tarafı reddeder.
  result = await call({ cmd: 'qr', text: JSON.stringify({ ...parsed, expiresAt: 1 }) });
  check(!!result.error, 'expired QR rejected by Java');
  result = await call({ cmd: 'qr', text: JSON.stringify({ ...parsed, v: 0 }) });
  check(!!result.error, 'old QR protocol rejected by Java');
  await app.start();
  result = await call({ cmd: 'qr', text: qr });
  check(!result.error, 'fresh actual page QR accepted');
  result = await phoneReceive({ v: 1, type: 'web_finished', room: JSON.parse(qr).room,
    proof: C.base64(new Uint8Array(32)) });
  check(result.error === 'KANIT_RED', 'wrong browser key proof rejected by Java');
  child.stdin.write(JSON.stringify({ cmd: 'close' }) + '\n');
  child.stdin.end();
  await new Promise(resolve => child.on('exit', resolve));
  console.log(`PASS ${checks}/${checks} Java phone ↔ actual JS page (real SQLite, host simulation)`);
} finally {
  app.stop();
  child.kill();
  lines.close();
  rmSync(temp, { recursive: true, force: true });
}
