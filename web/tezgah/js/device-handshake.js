// Kalıcı kimlik yalnız imzalar; her bağlantıda ayrı, geçici P-256 ECDH.
import * as C from './crypto.js';
import * as P from './protocol.js';
import * as D from './device-keys.js';

export class DeviceHandshake {
  constructor(identity) {
    this.identity = identity;
    this.generation = 0;
  }

  async hello(ready) {
    const generation = ++this.generation;
    const { room, id, phoneKey } = this.identity.device;
    P.token(ready.nonce, 32);
    await D.verify(phoneKey, D.transcript('zd-web/device-ready/v1', [room, id, ready.nonce]), ready.signature);
    this.ephemeral = await C.keyPair();
    this.B = C.base64(await crypto.subtle.exportKey('spki', this.ephemeral.publicKey));
    this.nb = C.base64(C.random(32));
    this.fields = [room, id, ready.nonce, this.nb, this.B];
    const signature = await D.sign(this.identity.key.privateKey, D.transcript('zd-web/device-hello/v1', this.fields));
    P.require(this.generation === generation, 'ESKI_BAGLANTI');
    return { v: 1, type: 'device_hello', nb: this.nb, browserPublicKey: this.B, signature };
  }

  async answer(answer) {
    P.require(this.ephemeral && !this.keys, 'GECERSIZ_CIHAZ_AKISI');
    const generation = this.generation;
    P.token(answer.np, 32);
    await C.publicKey(answer.phonePublicKey);
    const transcript = D.transcript('zd-web/device-session/v1', [...this.fields, answer.np, answer.phonePublicKey]);
    await D.verify(this.identity.device.phoneKey, transcript, answer.signature);
    const shared = await C.ecdh(this.ephemeral.privateKey, answer.phonePublicKey);
    let keys;
    try {
      keys = await C.initial(shared, await C.sha256(transcript), transcript);
      await C.verify(keys.confirmation(), 'device-phone', keys.transcriptHash, C.unbase64(answer.proof));
      P.require(this.generation === generation, 'ESKI_BAGLANTI');
      const proof = C.base64(await C.proof(keys.confirmation(), 'device-browser', keys.transcriptHash));
      this.keys = keys;
      this.session = C.base64(keys.transcriptHash.slice(0,16));
      this.epoch = C.base64(keys.transcriptHash.slice(16,32));
      this.ephemeral = null;
      return { v: 1, type: 'device_finished', proof };
    } catch (error) {
      keys?.close();
      throw error;
    } finally {
      shared.fill(0);
    }
  }

  close() {
    this.generation++;
    this.ephemeral = null;
    this.keys?.close();
    this.keys = null;
    this.fields = null;
  }
}
