// Kalıcı kayıt yalnız CryptoKeyPair + cihaz kimliği içerir. İş verisi kabul edilmez.
import * as C from './crypto.js';
import * as P from './protocol.js';
const DATABASE = 'zd-web-device-v1';
const STORE = 'identity';
const DAYS_30 = 30 * 86400000;

function descriptor(device) {
  P.require(device && Object.keys(device).sort().join(',') === 'id,phoneKey,room', 'GECERSIZ_CIHAZ');
  P.token(device.id, 16);
  P.token(device.room, 16);
  P.token(device.phoneKey, 91);
  return { id: device.id, room: device.room, phoneKey: device.phoneKey };
}

export async function generate() {
  const key = await crypto.subtle.generateKey({ name: 'ECDSA', namedCurve: 'P-256' }, false, ['sign', 'verify']);
  return { key, id: C.base64(C.random(16)), publicKey: C.base64(await crypto.subtle.exportKey('spki', key.publicKey)) };
}

export async function sign(key, message) {
  P.require(key.type === 'private' && !key.extractable && key.algorithm.name === 'ECDSA', 'GECERSIZ_CIHAZ_ANAHTARI');
  return C.base64(await crypto.subtle.sign({ name: 'ECDSA', hash: 'SHA-256' }, key, message));
}

export async function verify(spki, message, signature) {
  await C.publicKey(spki);
  P.token(signature, 64);
  const key = await crypto.subtle.importKey('spki', C.unbase64(spki), { name: 'ECDSA', namedCurve: 'P-256' }, false, ['verify']);
  P.require(await crypto.subtle.verify({ name: 'ECDSA', hash: 'SHA-256' }, key, C.unbase64(signature), message), 'CIHAZ_KIMLIGI_RED');
}

// Uzunluk önekleri alan ekleme/birleştirme belirsizliğini engeller.
export function transcript(label, fields) {
  P.require(/^[a-z0-9/-]+$/.test(label) && Array.isArray(fields) && fields.length <= 16, 'GECERSIZ_CIHAZ_TRANSCRIPT');
  const parts = [P.utf8(label + '\n')];
  for (const field of fields) {
    P.require(typeof field === 'string', 'GECERSIZ_CIHAZ_TRANSCRIPT');
    const bytes = P.utf8(field);
    P.require(bytes.length <= 2048, 'GECERSIZ_CIHAZ_TRANSCRIPT');
    const size = new Uint8Array(4);
    new DataView(size.buffer).setUint32(0, bytes.length, false);
    parts.push(size, bytes);
  }
  return C.concat(...parts);
}

export function expired(lastUsed, now) {
  return !Number.isSafeInteger(lastUsed) || lastUsed < 0 || !Number.isSafeInteger(now) || now < lastUsed || now - lastUsed >= DAYS_30;
}

function open(indexedDB) {
  return new Promise((resolve, reject) => {
    const request = indexedDB.open(DATABASE, 1);
    request.onupgradeneeded = () => request.result.createObjectStore(STORE);
    request.onsuccess = () => resolve(request.result);
    request.onerror = () => reject(new Error('CIHAZ_KAYDI_ACILAMADI'));
    request.onblocked = () => reject(new Error('CIHAZ_KAYDI_MESGUL'));
  });
}

export class DeviceStore {
  constructor(indexedDB = globalThis.indexedDB) {
    this.indexedDB = indexedDB;
  }

  async transaction(mode, operation) {
    P.require(this.indexedDB, 'KALICI_OTURUM_DESTEKLENMIYOR');
    const db = await open(this.indexedDB);
    try {
      return await new Promise((resolve, reject) => {
        const tx = db.transaction(STORE, mode);
        let result;
        tx.oncomplete = () => resolve(result);
        tx.onabort = tx.onerror = () => reject(new Error('CIHAZ_KAYDI_BASARISIZ'));
        const request = operation(tx.objectStore(STORE));
        request.onsuccess = () => { result = request.result; };
      });
    } finally {
      db.close();
    }
  }

  async save(key, device) {
    P.require(key.privateKey?.type === 'private' && key.privateKey.extractable === false &&
      key.privateKey.algorithm.name === 'ECDSA' && key.privateKey.algorithm.namedCurve === 'P-256', 'GECERSIZ_CIHAZ_ANAHTARI');
    P.require(key.publicKey?.type === 'public' && key.publicKey.algorithm.name === 'ECDSA', 'GECERSIZ_CIHAZ_ANAHTARI');
    const record = { key: { privateKey: key.privateKey, publicKey: key.publicKey }, device: descriptor(device) };
    await C.publicKey(record.device.phoneKey);
    await this.transaction('readwrite', store => store.put(record, 'current'));
  }

  async load() {
    const record = await this.transaction('readonly', store => store.get('current'));
    if (!record) return null;
    P.require(Object.keys(record).sort().join(',') === 'device,key', 'GECERSIZ_CIHAZ_KAYDI');
    descriptor(record.device);
    P.require(record.key.privateKey?.extractable === false && record.key.privateKey.algorithm.name === 'ECDSA', 'GECERSIZ_CIHAZ_ANAHTARI');
    return record;
  }

  async forget() {
    await this.transaction('readwrite', store => store.delete('current'));
  }
}
