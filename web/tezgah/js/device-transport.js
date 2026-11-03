import * as D from './device-keys.js';
import * as P from './protocol.js';
import * as C from './crypto.js';
import { DeviceHandshake } from './device-handshake.js';

// Her soket yeni challenge, kimlik imzası ve ayrı ECDH anahtarları kullanır.
export class DeviceTransport {
  constructor({ identity, relay, connect = url => new WebSocket(url), state, accepted, frame, revoked }) {
    this.identity = identity;
    const url = new URL(relay);
    P.require(url.protocol === 'https:' && !url.username && !url.password && url.pathname === '/' && !url.search && !url.hash, 'GECERSIZ_RELAY');
    this.url = url.origin.replace('https:', 'wss:') + '/v1/socket?room=' + encodeURIComponent(identity.device.room);
    this.connect = connect;
    this.state = state;
    this.accepted = accepted;
    this.frame = frame;
    this.revoked = revoked;
    this.generation = 0;
    this.stopped = false;
    this.tail = Promise.resolve();
    this.attempt = 0;
  }

  open() {
    if (this.stopped) return;
    clearTimeout(this.retry);
    const generation = ++this.generation;
    this.handshake?.close();
    this.handshake = new DeviceHandshake(this.identity);
    const socket = this.connect(this.url);
    this.socket = socket;
    const current = () => !this.stopped && generation === this.generation;
    this.state('Telefonunda Zeytin Defteri\'ni aç');
    socket.onmessage = event => {
      this.tail = this.tail.then(async () => {
        if (!current()) return;
        P.require(typeof event.data === 'string' && P.utf8(event.data).length <= P.MAX_FRAME_BYTES, 'BOYUT_SINIRI');
        const value = JSON.parse(event.data);
        P.require(value.v === 1, 'GECERSIZ_SURUM');
        if (value.type === 'device_challenge') {
          P.token(value.nonce,32);
          const transcript = D.transcript('zd-web/device-relay/v1', [this.identity.device.room,'browser',value.nonce]);
          const signature = await D.sign(this.identity.key.privateKey,transcript);
          const publicKey = C.base64(await crypto.subtle.exportKey('spki',this.identity.key.publicKey));
          if (!current()) return;
          this.send({v:1,type:'device_auth',room:this.identity.device.room,role:'browser',
            publicKey,signature});
        } else if (value.type === 'auth_ok') {
          P.require(value.stage === 'device','GECERSIZ_CIHAZ_AKISI');
          this.attempt = 0;
        } else if (value.type === 'device_ready') {
          this.handshake.close();
          this.handshake = new DeviceHandshake(this.identity);
          const hello = await this.handshake.hello(value);
          if (current()) this.send(hello);
        } else if (value.type === 'device_answer') {
          const finished = await this.handshake.answer(value);
          if (!current()) return;
          await this.accepted(this.handshake.keys,this.handshake.session,this.handshake.epoch,socket);
          this.send(finished);
        } else if (value.type === 'encrypted') {
          await this.frame(value);
        } else if (value.type === 'peer_left') {
          this.handshake.close();
          this.state('Telefonunda Zeytin Defteri\'ni aç');
        }
      }).catch(() => {
        if (current()) socket.close(1000,'DOGRULAMA_RED');
      });
    };
    socket.onclose = event => {
      if (!current()) return;
      this.handshake.close();
      if (['CIHAZ_IPTAL','AUTH_RED','ROL_RED'].includes(event.reason)) {
        this.stopped = true;
        void this.revoked();
        return;
      }
      this.state('Telefonunda Zeytin Defteri\'ni aç');
      const delay = Math.min(30000,1000 * 2 ** Math.min(this.attempt++,5));
      this.retry = setTimeout(() => this.open(),delay / 2 + Math.random() * delay / 2);
    };
    socket.onerror = () => {
      if (current()) socket.close();
    };
  }

  send(value) {
    P.require(this.socket?.readyState === 1 && this.socket.bufferedAmount <= 65536,'BAGLANTI_KOPTU');
    this.socket.send(JSON.stringify(value));
  }

  close() {
    this.stopped = true;
    this.generation++;
    clearTimeout(this.retry);
    this.socket?.close();
    this.handshake?.close();
  }
}
