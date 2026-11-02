import * as C from './js/crypto.js';
import * as P from './js/protocol.js';
import { RELAY_URL } from './config.js';
import qrCode from './js/vendor/qrcode.js';

const IDLE_MS = 300000;
const MAX_AGE_MS = 28800000;
const decoder = new TextDecoder('utf-8', { fatal: true });
const statuses = {
  yapilandirilmadi: 'Kurulum gerekiyor',
  baglaniyor: 'Bağlanıyor',
  qr: 'Telefonunuzla QR kodunu okutun',
  onay: 'Telefonda onay bekleniyor',
  bagli: 'Bağlı',
  koptu: 'Telefon bağlantısı koptu',
  yeniden: 'Yeniden bağlandı',
  kapandi: 'Oturum kapandı · Yeni QR hazırlanıyor',
  hata: 'Bağlantı kurulamadı'
};

function errorLabel(error) {
  const labels = {
    ORIGIN_403: 'Web adresine izin verilmedi. Worker ALLOWED_ORIGINS ayarını kontrol edin.',
    YAPILANDIRILMADI_503: 'Aktarıcı yapılandırılmadı. Worker adresini ve ayarlarını kontrol edin.',
    GECERSIZ_RELAY: 'Relay adresi yalnız HTTPS origin olmalı.',
    QR_SURESI_DOLDU: 'QR süresi doldu. Yeni QR oluşturun.'
  };
  const code = /^[A-Z][A-Z0-9_]+$/.test(error.message) ? error.message : '';
  const label = labels[code] || 'Bağlantı tamamlanamadı. Adres, ağ ve CSP ayarlarını kontrol edin.';
  return code ? label + ' (' + code + ')' : label;
}

export function relayOrigin(value) {
  const u = new URL(value);
  P.require(u.protocol === 'https:' && !u.username && !u.password &&
    u.pathname === '/' && !u.search && !u.hash, 'GECERSIZ_RELAY');
  return u.origin;
}

export function money(value) {
  return new Intl.NumberFormat('tr-TR', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2
  }).format(Number(value)) + ' ₺';
}

export function kg(value) {
  return new Intl.NumberFormat('tr-TR', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2
  }).format(Number(value));
}

export function dateLabel(value) {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(value)) {
    return '—';
  }
  return value.split('-').reverse().join('.');
}

export function updateLabel(asOf, timezone) {
  try {
    return new Intl.DateTimeFormat('tr-TR', {
      hour: '2-digit', minute: '2-digit', timeZone: timezone
    }).format(new Date(asOf));
  } catch {
    // Android bazen IANA yerine GMT+03:00 bildirir; ISO offset'indeki saati koru.
    return /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:/.test(asOf) ? asOf.slice(11, 16) : '—';
  }
}

// DOM ve ağ dışarıdan verilebilir: host testi de üretimdeki aynı durum makinesini kullanır.
export class TezgahPage {
  constructor({ relay = RELAY_URL, createRoom, connect, now = () => Date.now(),
    mono = () => Math.floor(performance.now()), view = () => {}, qr = () => {} } = {}) {
    this.relay = relay;
    this.now = now;
    this.mono = mono;
    this.view = view;
    this.drawQr = qr;
    this.createRoom = createRoom ?? (async origin => {
      const response = await fetch(origin + '/v1/rooms', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: '{}',
        credentials: 'omit',
        cache: 'no-store',
        referrerPolicy: 'no-referrer'
      });
      if (!response.ok) {
        throw new Error(response.status === 403 ? 'ORIGIN_403' :
          response.status === 503 ? 'YAPILANDIRILMADI_503' : 'RELAY_' + response.status);
      }
      return response.json();
    });
    this.connect = connect ?? (url => new WebSocket(url));
    this.state = 'baglaniyor';
    this.message = '';
    this.data = null;
    this.filter = { kapsam: 'acik_donem' };
    this.page = 0;
    this.cursors = [null];
    this.generation = 0;
    this.lastActivity = this.mono();
    this.lastActivityWall = this.now();
    this.tail = Promise.resolve();
    this.retryAt = Infinity;
    this.counter = 0;
    this.retryCount = 0;
  }

  publish(state = this.state, message = this.message) {
    this.state = state;
    this.message = message;
    this.view({ state, message, data: this.data, page: this.page,
      loading: !!this.pending, stale: !['bagli', 'yeniden'].includes(state),
      seconds: this.room ? Math.max(0, Math.ceil((this.qrDeadline - this.mono()) / 1000)) : 0 });
  }

  wipe(notify = true) {
    this.socketGeneration = (this.socketGeneration ?? 0) + 1;
    if (this.socket) {
      if (notify && this.socket.readyState === 1) {
        this.socket.send(JSON.stringify({ v: 1, type: 'close' }));
      }
      this.socket.close(1000, 'Oturum kapandı');
    }
    this.socket = null;
    this.channel?.close();
    this.keys?.close();
    this.pairing?.close();
    this.guard?.close();
    this.channel = this.keys = this.pairing = this.guard = null;
    this.browser = this.room = this.resumeOffer = null;
    this.session = this.epoch = this.phoneKey = null;
    this.data = this.pending = null;
    this.refreshAt = null;
    this.approvalDeadline = null;
    this.resumed = false;
    this.consumed = this.approved = false;
    this.retryAt = Infinity;
    this.lostAt = null;
    this.cursors = [null];
    this.page = 0;
    this.drawQr(null);
  }

  async start(reason = '') {
    const generation = ++this.generation;
    this.wipe();
    this.lastActivity = this.mono();
    this.lastActivityWall = this.now();
    this.publish(reason ? 'kapandi' : 'baglaniyor', reason);
    let secret;
    try {
      this.origin = relayOrigin(this.relay);
      const browser = await C.keyPair();
      const B = C.base64(await crypto.subtle.exportKey('spki', browser.publicKey));
      secret = C.random(32);
      const nb = C.base64(C.random(32));
      const room = await this.createRoom(this.origin);
      if (generation !== this.generation) {
        // Oda yalnız 90 sn yaşayabilir; eski async sonuç ekrana/anahtarlara dönmez.
        return;
      }
      P.require(room.v === 1, 'GECERSIZ_ODA');
      P.token(room.room, 16);
      P.token(room.browserCapability, 32);
      P.token(room.phoneCapability, 32);
      const remaining = room.expiresAt * 1000 - this.now();
      P.require(Number.isSafeInteger(room.expiresAt) && remaining > 0 &&
        remaining <= 90000, 'QR_SURESI_DOLDU');
      // Sunucunun relayUrl alanı hedef seçemez; tek hedef yerel config.js.
      this.room = { room: room.room, expiresAt: room.expiresAt,
        browserCapability: room.browserCapability };
      this.browser = browser;
      this.qrDeadline = this.mono() + remaining;
      this.pairing = new C.Pairing(room.room, room.expiresAt, secret, B, nb);
      this.drawQr(JSON.stringify({ kind: 'ZD_WEB_PAIR', v: 1, room: room.room,
        expiresAt: room.expiresAt, phoneCapability: room.phoneCapability,
        secret: C.base64(secret), browserPublicKey: B, nb }));
      this.openSocket(generation);
    } catch (error) {
      if (generation === this.generation) {
        this.wipe();
        this.publish(this.relay ? 'hata' : 'yapilandirilmadi', this.relay ? errorLabel(error) :
          'config.js ve _headers dosyalarını kurulum rehberine göre düzenleyin.');
      }
    } finally {
      secret?.fill(0);
    }
  }

  openSocket(generation = this.generation) {
    if (!this.room || generation !== this.generation) {
      return;
    }
    const socketGeneration = (this.socketGeneration ?? 0) + 1;
    this.socketGeneration = socketGeneration;
    const socket = this.connect(this.origin.replace('https:', 'wss:') +
      '/v1/socket?room=' + encodeURIComponent(this.room.room));
    this.socket = socket;
    const current = () => generation === this.generation &&
      socketGeneration === this.socketGeneration;
    socket.onopen = () => {
      if (!current()) {
        socket.close();
        return;
      }
      this.send({ v: 1, type: 'auth', room: this.room.room, role: 'browser',
        capability: this.room.browserCapability });
      this.lastPing = this.mono();
      this.lastPong = this.mono();
      this.authenticated = false;
      this.authDeadline = this.mono() + 5000;
    };
    socket.onmessage = event => {
      this.tail = this.tail.then(async () => {
        if (!current()) {
          return;
        }
        P.require(typeof event.data === 'string' &&
          P.utf8(event.data).length <= P.MAX_FRAME_BYTES, 'BOYUT_SINIRI');
        const frame = JSON.parse(event.data);
        P.require(frame.v === 1, 'GECERSIZ_SURUM');
        await this.receive(frame, current);
      }).catch(error => {
        if (current()) {
          // Kimlik/tag/protokol hatası: mevcut anahtarlarla devam etmeyiz.
          void this.start('Güvenli oturum sonlandırıldı. ' + errorLabel(error));
        }
      });
    };
    socket.onclose = event => {
      if (!current()) {
        return;
      }
      this.socket = null;
      if (event?.code === 1008 && event.reason !== 'ZAMAN_ASIMI') {
        void this.start('Oturum telefondan veya aktarıcıdan kapatıldı.');
        return;
      }
      this.disconnected();
      const delay = Math.min(30000, 1000 * 2 ** Math.min(this.retryCount++, 5));
      this.retryAt = this.mono() + Math.floor(delay / 2 + Math.random() * delay / 2);
    };
    socket.onerror = () => {
      if (current()) {
        socket.close();
      }
    };
  }

  send(frame) {
    P.require(this.socket?.readyState === 1, 'BAGLANTI_KOPTU');
    P.require(this.socket.bufferedAmount <= 65536, 'GONDERIM_SINIRI');
    this.socket.send(JSON.stringify(frame));
  }

  disconnected() {
    if (!this.consumed) {
      this.publish('koptu');
      return;
    }
    if (this.lostAt === null) {
      this.lostAt = this.mono();
      this.guard?.disconnected(this.lostAt);
    }
    this.channel?.close();
    this.channel = null;
    this.approved = false;
    this.approvalDeadline = null;
    this.pending = null;
    this.resumeOffer = null;
    this.publish('koptu');
  }

  async receive(frame, current = () => true) {
    switch (frame.type) {
      case 'auth_ok':
        this.authenticated = true;
        if (this.consumed) {
          P.require(frame.stage === 'active', 'ESKI_ODA');
          await this.beginResume();
        } else {
          P.require(frame.stage === 'pending', 'ESKI_ODA');
          this.publish('qr');
        }
        break;
      case 'peer_joined':
        if (frame.role === 'phone' && this.consumed) {
          this.disconnected();
          await this.beginResume();
        }
        break;
      case 'peer_left':
        if (frame.role === 'phone') {
          this.disconnected();
        }
        break;
      case 'phone_hello': {
        P.require(!this.consumed && frame.room === this.room.room &&
          this.mono() < this.qrDeadline, 'QR_SURESI_DOLDU');
        const keys = await this.pairing.browserKeys(this.browser,
          frame.phonePublicKey, frame.np, Math.floor(this.now() / 1000));
        try {
          await C.verify(keys.confirmation(), 'phone-hello', keys.transcriptHash,
            C.unbase64(frame.proof));
          P.require(!this.phoneKey || this.phoneKey === frame.phonePublicKey + frame.np,
            'ESLESME_DEGISTI');
          if (!current()) {
            return;
          }
          this.keys?.close();
          this.keys = keys;
          this.phoneKey = frame.phonePublicKey + frame.np;
          const hash = keys.transcriptHash;
          this.session = C.base64(hash.slice(0, 16));
          this.epoch = C.base64(hash.slice(16));
          this.send({ v: 1, type: 'web_finished', room: this.room.room,
            proof: C.base64(await C.proof(keys.confirmation(), 'web-finished', hash)) });
          this.publish('onay');
        } finally {
          if (keys !== this.keys) {
            keys.close();
          }
        }
        break;
      }
      case 'consumed':
        P.require(!this.consumed && this.keys && frame.session === this.session,
          'GECERSIZ_ONAY');
        P.token(frame.capability, 32);
        this.room.browserCapability = frame.capability;
        this.channel = new C.Channel(this.keys, this.session, this.epoch, 'w2p');
        this.guard = new C.ResumeGuard(this.session, this.epoch, this.keys.resumeRoot());
        this.consumed = true;
        this.approvalDeadline = this.mono() + 15000;
        this.startedAt = this.mono();
        this.startedWall = this.now();
        this.pairing.close();
        this.pairing = this.browser = null;
        this.drawQr(null);
        // consumed yalnız relay kontrolüdür; veri için şifreli onaylandi beklenir.
        break;
      case 'resume_answer':
        await this.finishResume(frame, current);
        break;
      case 'encrypted': {
        P.require(this.consumed && this.channel, 'ONAY_GEREKLI');
        const plain = JSON.parse(decoder.decode(await this.channel.open(frame)));
        if (current()) {
          await this.payload(plain);
        }
        break;
      }
      case 'pong':
        this.lastPong = this.mono();
        break;
      case 'hiz_siniri':
        this.message = 'Aktarım yavaşlatıldı; istek yeniden denenecek.';
        this.publish();
        break;
      default:
        throw new Error('BILINMEYEN_MESAJ');
    }
  }

  async beginResume() {
    if (this.resumeOffer) {
      return;
    }
    this.disconnected();
    this.resumeOffer = { v: 1, type: 'resume_offer', session: this.session,
      oldEpoch: this.epoch, epoch: C.base64(C.random(16)), nb: C.base64(C.random(32)) };
    this.resumeDeadline = this.mono() + 10000;
    this.send(this.resumeOffer);
  }

  async finishResume(frame, current) {
    P.require(this.resumeOffer && this.keys && this.lostAt !== null, 'RESUME_BEKLENMIYOR');
    const offer = this.resumeOffer;
    const root = this.keys.resumeRoot();
    let keys;
    try {
      const transcript = P.resumeTranscript(this.session, offer.oldEpoch,
        offer.epoch, offer.nb, frame.np);
      const hash = await C.sha256(transcript);
      const phoneProof = C.unbase64(frame.proof);
      await C.verify(root, 'resume-phone', hash, phoneProof);
      const webProof = await C.proof(root, 'resume-browser', hash);
      keys = await this.guard.accept(this.mono(), offer.oldEpoch, offer.epoch,
        offer.nb, frame.np, phoneProof, webProof);
      if (!current()) {
        return;
      }
      this.send({ v: 1, type: 'resume_finished', proof: C.base64(webProof) });
      this.channel?.close();
      this.keys.close();
      this.keys = keys;
      this.epoch = offer.epoch;
      this.channel = new C.Channel(keys, this.session, this.epoch, 'w2p');
      this.resumeOffer = null;
      this.lostAt = null;
      this.resumed = true;
      this.approvalDeadline = this.mono() + 15000;
    } finally {
      root.fill(0);
      if (keys && keys !== this.keys) {
        keys.close();
      }
    }
  }

  async payload(payload) {
    P.require(payload.v === 1, 'GECERSIZ_SURUM');
    if (payload.type === 'onaylandi') {
      P.require(payload.session === this.session &&
        Array.isArray(payload.permissions) && payload.permissions.includes('tezgah.read'),
      'GECERSIZ_ONAY');
      this.approved = true;
      this.approvalDeadline = null;
      this.retryCount = 0;
      this.publish(this.resumed ? 'yeniden' : 'bagli', '');
      await this.refresh();
      return;
    }
    P.require(this.approved, 'ONAY_GEREKLI');
    if (payload.type === 'olay' && payload.event === 'veri_degisti') {
      this.refreshAt = this.mono() + 400;
      return;
    }
    const pending = this.pending;
    if (!pending || payload.id !== pending.id) {
      return;
    }
    this.pending = null;
    if (payload.type === 'hata') {
      if (payload.code === 'SNAPSHOT_GECERSIZ') {
        if ((this.snapshotRetries ?? 0) >= 2) {
          this.page = 0;
          this.data = null;
          await this.refresh();
        } else {
          this.snapshotRetries = (this.snapshotRetries ?? 0) + 1;
          await this.refresh(true);
        }
      } else {
        this.publish(this.state, payload.message || 'Telefon isteği tamamlayamadı.');
      }
      return;
    }
    P.require(payload.type === 'cevap' && payload.ok === true &&
      payload.op === 'bugunun_alimlari', 'GECERSIZ_CEVAP');
    const data = payload.data;
    P.require(data && Array.isArray(data.items) && data.items.length <= 200 &&
      typeof data.complete === 'boolean' && data.totals &&
      (data.complete ? data.nextCursor === null : typeof data.nextCursor === 'string'),
    'GECERSIZ_VERI');
    if (pending.view !== this.viewId) {
      return;
    }
    this.cursors[pending.index + 1] = data.nextCursor;
    if (pending.index < this.targetPage && !data.complete) {
      await this.request(data.nextCursor, pending.index + 1);
      return;
    }
    this.page = pending.index;
    this.data = data;
    this.updatedAt = this.now();
    this.publish(this.state, '');
  }

  async request(cursor, index, attempts = 0) {
    if (!this.approved || !this.channel) {
      return;
    }
    const id = 'w' + (++this.counter);
    this.pending = { id, index, cursor, attempts, view: this.viewId,
      deadline: this.mono() + 15000 };
    const channel = this.channel;
    const generation = this.generation;
    const frame = await channel.seal(P.utf8(JSON.stringify({ v: 1, type: 'istek',
      id, op: 'bugunun_alimlari', args: { ...this.filter, limit: 100, cursor } })));
    if (generation === this.generation && channel === this.channel && this.approved) {
      this.send(frame);
      this.publish();
    }
  }

  async refresh(restarting = false) {
    if (!restarting) {
      this.snapshotRetries = 0;
    }
    this.viewId = (this.viewId ?? 0) + 1;
    this.targetPage = this.page;
    this.cursors = [null];
    await this.request(null, 0);
  }

  async setFilter(day) {
    this.filter = day ? { kapsam: 'takvim', tarih: day } : { kapsam: 'acik_donem' };
    this.page = 0;
    this.data = null;
    this.publish();
    await this.refresh();
  }

  async navigate(direction) {
    if (this.pending || !this.data) {
      return;
    }
    const index = this.page + direction;
    if (index < 0 || (direction > 0 && this.data.complete)) {
      return;
    }
    this.targetPage = index;
    await this.request(this.cursors[index], index);
  }

  activity() {
    this.lastActivity = this.mono();
    this.lastActivityWall = this.now();
  }

  async tick() {
    const now = this.mono();
    if (now - this.lastActivity >= IDLE_MS ||
      this.now() - this.lastActivityWall >= IDLE_MS) {
      await this.start('5 dakika işlem yapılmadığı için ekran kilitlendi.');
      return;
    }
    if (this.consumed && (now - this.startedAt >= MAX_AGE_MS ||
      this.now() - this.startedWall >= MAX_AGE_MS)) {
      await this.start('Oturumun 8 saatlik süresi doldu.');
      return;
    }
    if (this.room && !this.consumed && (now >= this.qrDeadline ||
      this.now() >= this.room.expiresAt * 1000)) {
      await this.start('QR süresi doldu; yeni QR oluşturuluyor.');
      return;
    }
    if (this.lostAt !== null && now - this.lostAt >= IDLE_MS) {
      await this.start('Telefonla yeniden bağlantı süresi doldu.');
      return;
    }
    if (this.socket?.readyState === 1) {
      if (!this.authenticated && now >= this.authDeadline) {
        this.socket.close();
      } else if (now - this.lastPong >= 60000) {
        this.socket.close();
      } else if (this.authenticated && now - this.lastPing >= 30000) {
        this.send({ v: 1, type: 'ping' });
        this.lastPing = now;
      }
    }
    if (!this.socket && this.room && now >= this.retryAt) {
      this.retryAt = Infinity;
      this.openSocket();
    }
    if (this.approvalDeadline !== null && now >= this.approvalDeadline) {
      await this.start('Telefonun güvenli onayı zamanında ulaşmadı.');
      return;
    }
    if (this.resumeOffer && now >= this.resumeDeadline) {
      // İki uçtan biri son proof'u kaçırmış olabilir; eski epoch'a dönmeyiz.
      await this.start('Güvenli yeniden bağlantı tamamlanamadı.');
      return;
    }
    if (this.approved && this.refreshAt && now >= this.refreshAt) {
      this.refreshAt = null;
      await this.refresh();
    }
    if (this.pending && now >= this.pending.deadline) {
      const pending = this.pending;
      this.pending = null;
      if (pending.attempts < 2) {
        await this.request(pending.cursor, pending.index, pending.attempts + 1);
      } else {
        this.publish(this.state, 'Telefon yanıt vermedi. Yenile düğmesini kullanın.');
      }
    }
    this.publish();
  }

  stop() {
    ++this.generation;
    this.wipe();
    this.publish('kapandi', 'Oturum kapandı.');
  }
}

export function renderRows(document, container, items) {
  container.replaceChildren();
  for (const item of items) {
    const row = document.createElement('tr');
    const values = [dateLabel(item.tarih), item.uretici, kg(item.netKg),
      String(item.kasa), money(item.tutarTL), money(item.odenenTL), money(item.kalanTL)];
    for (const value of values) {
      const cell = document.createElement('td');
      cell.textContent = value;
      row.append(cell);
    }
    container.append(row);
  }
}

export function drawCode(canvas, text) {
  const ctx = canvas.getContext('2d');
  ctx.clearRect(0, 0, canvas.width, canvas.height);
  if (!text) {
    return;
  }
  const code = qrCode(0, 'M');
  // QR JSON'u ASCII'dir; UTF-8/ECI belirsizliği yoktur.
  code.addData(text, 'Byte');
  code.make();
  const count = code.getModuleCount();
  const scale = 4;
  canvas.width = canvas.height = (count + 8) * scale;
  ctx.fillStyle = '#ffffff';
  ctx.fillRect(0, 0, canvas.width, canvas.height);
  ctx.fillStyle = '#000000';
  for (let y = 0; y < count; y++) {
    for (let x = 0; x < count; x++) {
      if (code.isDark(y, x)) {
        ctx.fillRect((x + 4) * scale, (y + 4) * scale, scale, scale);
      }
    }
  }
}

export function boot(document) {
  const byId = id => document.getElementById(id);
  let previousData;
  let updated;
  const app = new TezgahPage({
    qr: text => {
      drawCode(byId('qr'), text);
      byId('qr').hidden = !text;
    },
    view: state => {
      byId('status').textContent = statuses[state.state];
      byId('status').dataset.state = state.state;
      byId('message').textContent = state.message;
      byId('pairing').hidden = !!state.data;
      byId('ledger').hidden = !state.data;
      byId('ledger').classList.toggle('stale', state.stale);
      byId('stale').hidden = !state.data || !state.stale;
      byId('seconds').textContent = state.seconds ? state.seconds + ' saniye geçerli' : '';
      byId('previous').disabled = state.loading || state.stale || state.page === 0;
      byId('next').disabled = state.loading || state.stale || !state.data || state.data.complete;
      byId('refresh').disabled = state.loading || state.stale;
      byId('day').disabled = state.stale;
      byId('open-period').disabled = state.stale;
      byId('page').textContent = 'Sayfa ' + (state.page + 1);
      byId('loading').hidden = !state.loading;
      if (state.data !== previousData) {
        previousData = state.data;
        const data = state.data;
        renderRows(document, byId('rows'), data?.items ?? []);
        for (const [id, value] of Object.entries({
          count: data ? String(data.totals.count) : '—',
          weight: data ? kg(data.totals.netKg) + ' kg' : '—',
          amount: data ? money(data.totals.tutarTL) : '—',
          paid: data ? money(data.totals.odenenTL) : '—',
          remaining: data ? money(data.totals.kalanTL) : '—'
        })) {
          byId(id).textContent = value;
        }
        byId('empty').hidden = !data || data.items.length !== 0;
        updated = data ? updateLabel(data.asOf, data.timezone) : '';
      }
      byId('updated').textContent = updated ? 'Son güncelleme ' + updated : '';
    }
  });
  const safe = promise => promise.catch(() => {
    void app.start('Bağlantı yeniden hazırlanıyor.');
  });
  byId('disconnect').addEventListener('click', () => safe(app.start('Bağlantıyı siz kestiniz.')));
  byId('retry').addEventListener('click', () => safe(app.start()));
  byId('refresh').addEventListener('click', () => safe(app.refresh()));
  byId('previous').addEventListener('click', () => safe(app.navigate(-1)));
  byId('next').addEventListener('click', () => safe(app.navigate(1)));
  byId('day').addEventListener('change', event => {
    byId('period').textContent = event.target.value ? 'Takvim günü' : 'Açık dönem';
    safe(app.setFilter(event.target.value));
  });
  byId('open-period').addEventListener('click', () => {
    byId('day').value = '';
    byId('period').textContent = 'Açık dönem';
    safe(app.setFilter(null));
  });
  for (const name of ['pointerdown', 'keydown', 'wheel']) {
    document.addEventListener(name, () => app.activity(), { passive: true });
  }
  document.addEventListener('visibilitychange', () => safe(app.tick()));
  let ticking = false;
  const timer = setInterval(async () => {
    if (ticking) {
      return;
    }
    ticking = true;
    try {
      await safe(app.tick());
    } finally {
      ticking = false;
    }
  }, 1000);
  globalThis.addEventListener('pagehide', () => {
    clearInterval(timer);
    app.stop();
  });
  globalThis.addEventListener('pageshow', event => {
    if (event.persisted) {
      globalThis.location.reload();
    }
  });
  void app.start();
  return app;
}

if (typeof document !== 'undefined') {
  boot(document);
}
