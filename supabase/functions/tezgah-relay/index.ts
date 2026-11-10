// Zeytin Defteri – Tezgâh bilgisayar bağlantısı köprüsü (Supabase Edge Function).
//
// Neden: bazı Türk ağlarında uygulamanın *.workers.dev adresine kurduğu bağlantı yolda tutulup bırakılmıyor
// (Chrome başka yoldan geçtiği için aynı adres tarayıcıda açılıyor). Telefon bağlanamazsa buraya (supabase.co)
// bağlanır; bu fonksiyon bağlantıyı olduğu gibi Cloudflare'deki tezgah-relay'e iletir.
//
// Güvenlik: içerik telefonda şifrelenir, bilgisayarda çözülür (uçtan uca). Bu köprü yalnız şifreli metin
// çerçevelerini iki yöne taşır; okumaz, saklamaz, günlüğe yazmaz. Anahtar/şifre yok. Kimlik doğrulama
// ve oda kuralları değişmeden relay'de kalır. Yalnız TELEFON yolu: Origin başlığı olan (tarayıcı) istek reddedilir.
//
// Supabase panelinde bu fonksiyon için "Verify JWT" KAPALI olmalı (config.toml: verify_jwt = false).
// Not: Supabase ücretsiz planda bağlantı en çok ~150 sn açık kalır; uygulama kopunca kendisi yeniden bağlanır.

export const RELAY_HTTP = "https://tezgah-relay.muammercerdin.workers.dev";
export const RELAY_WSS = "wss://tezgah-relay.muammercerdin.workers.dev";
const H = { "content-type": "application/json; charset=utf-8", "cache-control": "no-store" };
const MAX_PENDING = 64;          // upstream açılana kadar bekletilen en fazla çerçeve
const MAX_FRAME = 256 * 1024;    // tek çerçeve üst sınırı (relay'inkinden büyük değil)
const MAX_BODY = 8192;

const json = (o: unknown, status = 200) => new Response(JSON.stringify(o), { status, headers: H });

/** İstek yolundan fonksiyon adını atar: "/tezgah-relay/v1/socket" ya da "/functions/v1/tezgah-relay/v1/socket" → "/v1/socket". */
export function subPath(pathname: string): string {
  const i = pathname.indexOf("/tezgah-relay");
  const rest = i >= 0 ? pathname.slice(i + "/tezgah-relay".length) : pathname;
  return rest === "" ? "/" : rest;
}

/** Oda adı: relay'in token biçimi (base64url, 16–64 karakter). */
export function validRoom(room: string | null): boolean {
  return typeof room === "string" && /^[A-Za-z0-9_-]{16,64}$/.test(room);
}

/** Kapanış kodunu sunucunun gönderebileceği aralığa çevirir (1005/1006/1015 gönderilemez). */
export function closeCode(code: number | undefined): number {
  if (code === 1000 || code === 1001 || code === 1008 || code === 1011) return code;
  if (typeof code === "number" && code >= 3000 && code <= 4999) return code;
  return 1011;
}

type Sock = {
  readyState: number;
  send(data: string): void;
  close(code?: number, reason?: string): void;
  onopen: ((e: unknown) => void) | null;
  onmessage: ((e: { data: unknown }) => void) | null;
  onclose: ((e: { code?: number; reason?: string }) => void) | null;
  onerror: ((e: unknown) => void) | null;
};

/** İki soketi birbirine bağlar: istemci (telefon) ↔ upstream (relay). Yalnız metin çerçeveleri taşınır. */
export function bridge(client: Sock, upstream: Sock): void {
  const pending: string[] = [];       // telefon → relay (relay açılana kadar)
  const toClient: string[] = [];      // relay → telefon (telefon soketi açılana kadar)
  let closed = false;
  const shut = (target: Sock, code: number | undefined, reason: string | undefined) => {
    try { if (target.readyState === 0 || target.readyState === 1) target.close(closeCode(code), (reason ?? "").slice(0, 120)); }
    catch { try { target.close(1011, "KOPRU"); } catch { /* zaten kapalı */ } }
  };
  const end = (code: number | undefined, reason: string | undefined) => {
    if (closed) return;
    closed = true;
    pending.length = 0;
    toClient.length = 0;
    shut(client, code, reason);
    shut(upstream, code, reason);
  };
  client.onmessage = (e) => {
    if (closed) return;
    if (typeof e.data !== "string" || e.data.length > MAX_FRAME) { end(1008, "GECERSIZ_CERCEVE"); return; }
    if (upstream.readyState === 1) upstream.send(e.data);
    else if (pending.length < MAX_PENDING) pending.push(e.data);
    else end(1011, "KOPRU_DOLU");
  };
  upstream.onopen = () => {
    if (closed) return;
    while (pending.length && upstream.readyState === 1) upstream.send(pending.shift() as string);
  };
  upstream.onmessage = (e) => {
    if (closed) return;
    if (typeof e.data !== "string" || e.data.length > MAX_FRAME) { end(1011, "GECERSIZ_CERCEVE"); return; }
    if (client.readyState === 1) client.send(e.data);
    else if (client.readyState === 0 && toClient.length < MAX_PENDING) toClient.push(e.data);
    else if (client.readyState === 0) end(1011, "KOPRU_DOLU");
  };
  client.onopen = () => {
    if (closed) return;
    while (toClient.length && client.readyState === 1) client.send(toClient.shift() as string);
  };
  upstream.onclose = (e) => end(e?.code, e?.reason);
  client.onclose = (e) => end(e?.code ?? 1000, e?.reason ?? "TELEFON_KAPATTI");
  upstream.onerror = () => end(1011, "AKTARICI_ULASILAMADI");
  client.onerror = () => end(1011, "TELEFON_HATASI");
}

export type Deps = {
  upgrade: (req: Request) => { socket: Sock; response: Response };
  connect: (url: string) => Sock;
  fetch: (url: string, init: RequestInit) => Promise<Response>;
};

export async function handle(req: Request, deps: Deps): Promise<Response> {
  const url = new URL(req.url);
  const path = subPath(url.pathname);
  // Yalnız telefon: tarayıcılar relay'e doğrudan bağlanır (Origin denetimi orada).
  if (req.headers.get("origin")) return json({ error: "YETKI_YOK" }, 403);
  if (req.method === "GET" && path === "/") return json({ ok: true, service: "tezgah-relay-kopru" });
  if (req.method === "GET" && path === "/v1/socket") {
    const keys = [...url.searchParams.keys()];
    const room = url.searchParams.get("room");
    if (keys.length !== 1 || keys[0] !== "room" || !validRoom(room)) return json({ error: "GECERSIZ_ISTEK" }, 400);
    if (req.headers.get("upgrade")?.toLowerCase() !== "websocket") return json({ error: "WEBSOCKET_GEREKLI" }, 426);
    const { socket, response } = deps.upgrade(req);
    let upstream: Sock;
    try { upstream = deps.connect(RELAY_WSS + "/v1/socket?room=" + encodeURIComponent(room as string)); }
    catch { return json({ error: "AKTARICI_ULASILAMADI" }, 502); }
    bridge(socket, upstream);
    return response;
  }
  if (req.method === "POST" && path === "/v2/devices" && url.search === "") {
    const body = await req.text();
    if (body.length > MAX_BODY) return json({ error: "GECERSIZ_ISTEK" }, 413);
    try {
      const r = await deps.fetch(RELAY_HTTP + "/v2/devices", {
        method: "POST",
        headers: { "content-type": "application/json; charset=utf-8", "authorization": req.headers.get("authorization") ?? "" },
        body,
        signal: AbortSignal.timeout(15000),
      });
      return new Response(await r.text(), { status: r.status, headers: H });
    } catch {
      return json({ error: "AKTARICI_ULASILAMADI" }, 502);
    }
  }
  return json({ error: "BULUNAMADI" }, 404);
}

// Supabase (Deno) çalışma zamanı.
declare const Deno: { serve: (h: (r: Request) => Response | Promise<Response>) => void; upgradeWebSocket: (r: Request) => { socket: Sock; response: Response } } | undefined;
if (typeof Deno !== "undefined") {
  Deno.serve((req) => handle(req, {
    upgrade: (r) => Deno!.upgradeWebSocket(r),
    connect: (u) => new WebSocket(u) as unknown as Sock,
    fetch: (u, i) => fetch(u, i),
  }));
}
