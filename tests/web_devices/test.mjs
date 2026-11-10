import { spawn } from 'node:child_process';
import { createInterface } from 'node:readline';
import assert from 'node:assert/strict';
import * as D from '../../web/tezgah/js/device-keys.js';
import * as C from '../../web/tezgah/js/crypto.js';
import { DeviceHandshake } from '../../web/tezgah/js/device-handshake.js';
let checks = 0;
function check(ok,label) {
  assert.ok(ok,label);
  checks++;
}
const child = spawn('java', ['-cp',process.argv[2],'com.zeytindefteri.app.DeviceBridge']);
child.stderr.pipe(process.stderr);
const waiting = [];
createInterface({input:child.stdout}).on('line',line => waiting.shift()(JSON.parse(line)));
function call(command) {
  return new Promise((resolve,reject) => {
    const timeout = setTimeout(() => reject(new Error('JAVA_TIMEOUT')),10000);
    waiting.push(value => {
      clearTimeout(timeout);
      resolve(value);
    });
    child.stdin.write(JSON.stringify(command)+'\n');
  });
}
try {
  const browser = await D.generate();
  check(!browser.key.privateKey.extractable,'cihaz özel anahtarı dışarı aktarılamaz');
  await assert.rejects(crypto.subtle.exportKey('pkcs8',browser.key.privateKey));
  checks++;
  const room = C.base64(C.random(16));
  const init = await call({cmd:'init',room,id:browser.id,browserKey:browser.publicKey});
  const identity = {key:browser.key,device:{id:browser.id,room,phoneKey:init.phoneKey}};
  const epochs = new Set();
  for (let cycle=0;cycle<5;cycle++) {
    const handshake = new DeviceHandshake(identity);
    const ready = await call({cmd:'ready'});
    if (cycle===0) {
      await assert.rejects(handshake.hello({...ready,signature:C.base64(C.random(64))}));
      checks++;
    }
    const hello = await handshake.hello(ready);
    if (cycle===0) {
      check(!!(await call({cmd:'hello',frame:{...hello,nb:C.base64(C.random(32))}})).error,'browser transcript kurcalama reddi');
    }
    const answer = await call({cmd:'hello',frame:hello});
    assert.equal(answer.error, undefined, 'Java answer: ' + JSON.stringify(answer));
    const finished = await handshake.answer(answer);
    if (cycle===0) {
      check(!!(await call({cmd:'finished',frame:{...finished,proof:C.base64(C.random(32))}})).error,'yanlış anahtar kanıtı reddi');
    }
    const encrypted = await call({cmd:'finished',frame:finished});
    const channel = new C.Channel(handshake.keys,handshake.session,handshake.epoch,'w2p');
    check(new TextDecoder().decode(await channel.open(encrypted))==='cihaz doğrulandı','Java ↔ JS imza, ECDH, HKDF, GCM');
    check(!epochs.has(handshake.epoch),'her yeniden bağlantıda yeni anahtar/epoch');
    epochs.add(handshake.epoch);
    await assert.rejects(channel.open(encrypted));
    checks++;
    const oldHello = hello;
    await call({cmd:'ready'});
    check(!!(await call({cmd:'hello',frame:oldHello})).error,'eski nonce ile tekrar bağlanma reddi');
    channel.close();
    handshake.close();
  }
  for (const delta of [0,30*86400000-1,30*86400000,31*86400000,-1]) {
    const last=1000000000;
    const result=await call({cmd:'expired',last,now:last+delta});
    check(result.expired===D.expired(last,last+delta),'Java/JS 30 gün sınırı aynı');
  }
  // IndexedDB API taklidi; CryptoKey'nin gerçek structuredClone'u kullanılır.
  const data = new Map();
  const fakeDB = {open() {
    const request = {};
    queueMicrotask(() => {
      request.result = {close() {},transaction() {
        const tx = {objectStore() {return {
          put(value,key) {return operation(() => data.set(key,structuredClone(value)));},
          get(key) {return operation(() => structuredClone(data.get(key)));},
          delete(key) {return operation(() => data.delete(key));}
        };}};
        function operation(fn) {
          const op = {};
          queueMicrotask(() => {
            op.result=fn();
            op.onsuccess();
            tx.oncomplete();
          });
          return op;
        }
        return tx;
      }};
      request.onsuccess();
    });
    return request;
  }};
  const store = new D.DeviceStore(fakeDB);
  await store.save(identity.key,identity.device);
  const loaded = await new D.DeviceStore(fakeDB).load();
  check(Object.keys(data.get('current')).sort().join(',')==='device,key','yalnız anahtar ve kimlik kaydı');
  check(!loaded.key.privateKey.extractable,'yeniden açılan anahtar dışarı aktarılamaz');
  await assert.rejects(store.save(identity.key,{...identity.device,uretici:'Gizli isim'}));
  checks++;
  await store.forget();
  check(await store.load()===null,'çıkış anahtarı siler');
  console.log(`PASS ${checks} kalıcı cihaz kripto / 5 yeni ECDH / depolama şeması (IndexedDB mock)`);
} finally {
  child.kill();
}
