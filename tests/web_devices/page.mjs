import { spawn } from 'node:child_process';
import { createInterface } from 'node:readline';
import { mkdtempSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import assert from 'node:assert/strict';
import { TezgahPage } from '../../web/tezgah/app.js';
import * as D from '../../web/tezgah/js/device-keys.js';
import * as C from '../../web/tezgah/js/crypto.js';
const dir = mkdtempSync(join(tmpdir(),'zd-saved-page-'));
const child = spawn('java',['-cp',process.argv[2],'com.zeytindefteri.app.DeviceBridge',join(dir,'db'),'tests/buyers/sql_helper.py']);
child.stderr.pipe(process.stderr);
const pending = [];
createInterface({input:child.stdout}).on('line',line => pending.shift()(JSON.parse(line)));
const call = command => new Promise((resolve,reject) => {
  const timer = setTimeout(() => reject(new Error('JAVA_TIMEOUT')),10000);
  pending.push(value => { clearTimeout(timer); resolve(value); });
  child.stdin.write(JSON.stringify(command)+'\n');
});
let n = 0;
function check(value,label) { assert.ok(value,label); n++; }
try {
  const browser = await D.generate();
  const room = C.base64(C.random(16));
  const init = await call({cmd:'init',room,id:browser.id,browserKey:browser.publicKey});
  const identity = {key:browser.key,device:{id:browser.id,room,phoneKey:init.phoneKey}};
  let clock = 0;
  let socket;
  let qrCount = 0;
  const app = new TezgahPage({relay:'https://relay.example',mono:()=>clock,createRoom:async()=>{qrCount++;throw Error('QR olmamalı');},connect:()=>{
    socket={readyState:1,bufferedAmount:0,frames:[],send(raw){this.frames.push(JSON.parse(raw));},close(){this.readyState=3;}};
    return socket;
  }});
  app.identity = identity;
  async function receive(frame) {
    socket.onmessage({data:JSON.stringify(frame)});
    await app.transport.tail;
  }
  const epochs = new Set();
  for(let cycle=0;cycle<5;cycle++) {
    await app.start();
    const ready = await call({cmd:'ready'});
    await receive(ready);
    const answer = await call({cmd:'hello',frame:socket.frames.shift()});
    await receive(answer);
    const approved = await call({cmd:'page_finished',frame:socket.frames.shift()});
    await receive(approved);
    check(app.approved,'gerçek telefon onayı');
    const data = await call({cmd:'request',frame:socket.frames.shift()});
    await receive(data);
    check(app.data.items[0].uretici==='Kalıcı üretici','gerçek SQLite → kalıcı sayfa');
    check(!epochs.has(app.epoch),'yeni bağlantıda yeni epoch');
    epochs.add(app.epoch);
    if(cycle===0) {
      clock+=1800001;
      await app.tick();
      check(app.locked && app.data===null && app.approved,'30 dakika veri gizli, bağlantı açık');
      app.activity();
      await new Promise(resolve=>setTimeout(resolve,5));
      check(!app.locked,'dokununca yeniden istek');
    }
    app.stop();
    check(app.identity===identity && app.data===null,'sayfa kapama cihaz kimliğini korur');
  }
  check(qrCount===0,'beş açılışta QR yok');
  app.stop();
  console.log(`PASS ${n} gerçek Java telefon ↔ kalıcı JS sayfa / 5 açılış / idle kilidi`);
} finally {
  child.kill();
  rmSync(dir,{recursive:true,force:true});
}
