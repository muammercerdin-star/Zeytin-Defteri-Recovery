// Supabase köprüsü (supabase/functions/tezgah-relay): telefon ↔ relay çerçevelerini olduğu gibi taşır.
import assert from 'node:assert/strict';
const m = await import(new URL('../../supabase/functions/tezgah-relay/index.ts', import.meta.url));
let n = 0; const ok = (c, msg) => { assert.ok(c, msg); n++; };

class S { // sahte soket
  constructor(state = 1) { this.readyState = state; this.sent = []; this.closedWith = null; this.onopen = this.onmessage = this.onclose = this.onerror = null; }
  send(d) { if (this.readyState !== 1) throw new Error('CLOSED'); this.sent.push(d); }
  close(code, reason) { if (this.readyState === 3) return; this.readyState = 3; this.closedWith = [code, reason]; }
}

// Yol ve oda
ok(m.subPath('/tezgah-relay/v1/socket') === '/v1/socket', 'yol 1');
ok(m.subPath('/functions/v1/tezgah-relay/v1/socket') === '/v1/socket', 'yol 2');
ok(m.subPath('/tezgah-relay') === '/', 'kök');
ok(m.validRoom('AbCdEfGhIjKlMnOpQrStUv') && !m.validRoom('kısa') && !m.validRoom('a/b' + 'x'.repeat(20)) && !m.validRoom(null), 'oda biçimi');
ok(m.closeCode(1008) === 1008 && m.closeCode(4001) === 4001 && m.closeCode(1006) === 1011 && m.closeCode(undefined) === 1011, 'kapanış kodu');

// Upstream açılmadan gelen çerçeveler sırayla bekletilir, açılınca gönderilir
{
  const c = new S(1), u = new S(0); m.bridge(c, u);
  c.onmessage({ data: 'a' }); c.onmessage({ data: 'b' });
  ok(u.sent.length === 0, 'açılmadan gönderilmez');
  u.readyState = 1; u.onopen({});
  ok(u.sent.join() === 'a,b', 'açılınca sırayla');
  c.onmessage({ data: 'c' }); ok(u.sent.join() === 'a,b,c', 'sonra doğrudan');
  u.onmessage({ data: 'x' }); ok(c.sent.join() === 'x', 'relay → telefon');
  // relay kapatınca telefon aynı kod/sebeple kapanır (uygulama 1008/TARAYICI_KAPATTI'ya göre davranır)
  u.onclose({ code: 1008, reason: 'TARAYICI_KAPATTI' });
  ok(c.closedWith[0] === 1008 && c.closedWith[1] === 'TARAYICI_KAPATTI', 'kapanış aynen iletilir');
  u.onmessage({ data: 'y' }); ok(c.sent.length === 1, 'kapandıktan sonra taşınmaz');
}
// Telefon soketi henüz açılmadıysa relay çerçeveleri bekletilir
{ const c = new S(0), u = new S(1); m.bridge(c, u); u.onmessage({ data: 'h1' }); u.onmessage({ data: 'h2' }); ok(c.sent.length === 0, 'telefon açılmadan gönderilmez');
  c.readyState = 1; c.onopen({}); ok(c.sent.join() === 'h1,h2', 'telefon açılınca sırayla'); }
// Telefon kapanınca relay de kapanır
{ const c = new S(1), u = new S(1); m.bridge(c, u); c.onclose({ code: 1000, reason: 'CIKIS' }); ok(u.closedWith[0] === 1000 && u.closedWith[1] === 'CIKIS', 'telefon kapanışı iletilir'); }
// Relay'e ulaşılamazsa telefon 1011 ile kapanır (uygulama yeniden dener)
{ const c = new S(1), u = new S(0); m.bridge(c, u); u.onerror({}); ok(c.closedWith[0] === 1011, 'relay hatası 1011'); }
// İkili/çok büyük çerçeve reddedilir; tampon sınırlı
{ const c = new S(1), u = new S(1); m.bridge(c, u); c.onmessage({ data: new Uint8Array(3) }); ok(c.closedWith[0] === 1008 && u.closedWith[0] === 1008, 'ikili çerçeve yok'); }
{ const c = new S(1), u = new S(0); m.bridge(c, u); for (let i = 0; i < 64; i++) c.onmessage({ data: 'p' }); ok(c.closedWith === null, '64 bekletilir'); c.onmessage({ data: 'q' }); ok(c.closedWith[0] === 1011, '65. ile kapanır'); }
// Geçersiz kapanış kodu (1006) güvenli koda çevrilir
{ const c = new S(1), u = new S(1); m.bridge(c, u); u.onclose({ code: 1006, reason: '' }); ok(c.closedWith[0] === 1011, '1006 → 1011'); }

// handle: istek kuralları
const deps = (log = {}) => ({
  upgrade: () => { const s = new S(1); log.client = s; return { socket: s, response: new Response(null, { status: 200 }) }; },
  connect: (u) => { log.url = u; const s = new S(0); log.up = s; return s; },
  fetch: async (u, i) => { log.fetch = { u, i }; return new Response('{"ok":true}', { status: 201 }); },
});
const room = 'AbCdEfGhIjKlMnOpQrStUv';
const ws = (q, h = {}) => new Request('https://x.supabase.co/functions/v1/tezgah-relay/v1/socket' + q, { headers: { upgrade: 'websocket', ...h } });
{ const log = {}; const r = await m.handle(ws('?room=' + room), deps(log)); ok(r.status === 200 && log.url === m.RELAY_WSS + '/v1/socket?room=' + room, 'soket relay odasına'); }
ok((await m.handle(ws('?room=' + room, { origin: 'https://evil.example' }), deps())).status === 403, 'tarayıcı (Origin) reddedilir');
ok((await m.handle(ws('?room=kisa'), deps())).status === 400, 'geçersiz oda');
ok((await m.handle(ws('?room=' + room + '&x=1'), deps())).status === 400, 'fazla parametre');
ok((await m.handle(new Request('https://x.supabase.co/functions/v1/tezgah-relay/v1/socket?room=' + room), deps())).status === 426, 'websocket şart');
{ const log = {}; const r = await m.handle(new Request('https://x.supabase.co/functions/v1/tezgah-relay/v2/devices', { method: 'POST', headers: { authorization: 'Bearer abc', 'content-type': 'application/json' }, body: '{"a":1}' }), deps(log));
  ok(r.status === 201 && log.fetch.u === m.RELAY_HTTP + '/v2/devices' && log.fetch.i.headers.authorization === 'Bearer abc' && log.fetch.i.body === '{"a":1}', 'cihaz kaydı aynen iletilir'); }
ok((await m.handle(new Request('https://x.supabase.co/functions/v1/tezgah-relay/v1/rooms', { method: 'POST', body: '{}' }), deps())).status === 404, 'oda açma (tarayıcı işi) yok');
ok((await m.handle(new Request('https://x.supabase.co/functions/v1/tezgah-relay'), deps())).status === 200, 'sağlık');
console.log('PASS ' + n + ' köprü kontrolü');
