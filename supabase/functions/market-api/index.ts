import { createClient } from 'npm:@supabase/supabase-js@2';
import { createRemoteJWKSet, jwtVerify } from 'npm:jose@5';
import {
  GRADES,
  MIN_PRODUCERS,
  buildSelectedMarket,
  normalizeContribution,
  normalizeGrade,
  region
} from './policy.mjs';

const SUPABASE_URL = Deno.env.get('SUPABASE_URL') ?? '';

// 2026 Supabase key model: hosted Edge Functions receive the new
// SUPABASE_SECRET_KEYS JSON dictionary. Keep a legacy fallback only so an
// older project does not break during migration. Secret keys never leave
// the server and are never shipped in the Android APK.
function resolveAdminKey(): string {
  const secretKeysJson = (Deno.env.get('SUPABASE_SECRET_KEYS') ?? '').trim();
  if (secretKeysJson) {
    try {
      const parsed = JSON.parse(secretKeysJson);
      const current = String(parsed?.default ?? '').trim();
      if (current) return current;
    } catch (_) {
      // Fall through to the legacy compatibility key below.
    }
  }

  return (Deno.env.get('SUPABASE_SERVICE_ROLE_KEY') ?? '').trim();
}

const ADMIN_KEY = resolveAdminKey();
const FIREBASE_PROJECT_ID = Deno.env.get('FIREBASE_PROJECT_ID') ?? 'zeytin-defteri';
const MARKET_HASH_SALT = Deno.env.get('MARKET_HASH_SALT') ?? '';
const POLICY_VERSION = 1;
const MAX_SYNC_ROWS = 2000;
const PAGE_SIZE = 1000;
const MAX_QUERY_ROWS = 100000;

const admin = createClient(SUPABASE_URL, ADMIN_KEY, {
  auth: { persistSession: false, autoRefreshToken: false }
});

const firebaseJwks = createRemoteJWKSet(
  new URL('https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com')
);

const headers = {
  'content-type': 'application/json; charset=utf-8',
  'access-control-allow-origin': '*',
  'access-control-allow-headers': 'content-type, apikey, x-firebase-token',
  'access-control-allow-methods': 'POST, OPTIONS'
};

function json(status: number, body: unknown) {
  return new Response(JSON.stringify(body), { status, headers });
}

async function firebaseUid(req: Request): Promise<string> {
  const token = (req.headers.get('x-firebase-token') ?? '').trim();
  if (!token) throw new Error('Firebase token missing');

  const issuer = `https://securetoken.google.com/${FIREBASE_PROJECT_ID}`;
  const { payload } = await jwtVerify(token, firebaseJwks, {
    issuer,
    audience: FIREBASE_PROJECT_ID
  });

  const uid = String(payload.sub ?? '').trim();
  if (!uid) throw new Error('Firebase UID missing');
  return uid;
}

async function producerHash(uid: string): Promise<string> {
  if (MARKET_HASH_SALT.length < 24) {
    throw new Error('MARKET_HASH_SALT is not configured');
  }
  const enc = new TextEncoder();
  const key = await crypto.subtle.importKey(
    'raw',
    enc.encode(MARKET_HASH_SALT),
    { name: 'HMAC', hash: 'SHA-256' },
    false,
    ['sign']
  );
  const signed = await crypto.subtle.sign('HMAC', key, enc.encode(uid));
  return [...new Uint8Array(signed)].map(b => b.toString(16).padStart(2, '0')).join('');
}

function safeYear(value: unknown): number {
  const year = Number(value);
  if (!Number.isInteger(year) || year < 2000 || year > 2100) {
    throw new Error('Invalid season_year');
  }
  return year;
}

function safePeriod(value: unknown): string {
  const period = String(value ?? '30d').trim();
  return ['7d','30d','90d','season'].includes(period) ? period : '30d';
}

function toNumber(value: unknown): number {
  const n = Number(value);
  return Number.isFinite(n) ? n : 0;
}

function mapContribution(row: Record<string, unknown>) {
  return {
    producer_hash: String(row.producer_hash ?? ''),
    source_key: String(row.source_key ?? ''),
    record_date: String(row.record_date ?? ''),
    record_day_ms: Date.parse(String(row.record_date ?? '') + 'T00:00:00.000Z'),
    region_id: String(row.region_id ?? ''),
    region_name: String(row.region_name ?? ''),
    district: String(row.district ?? ''),
    province: String(row.province ?? ''),
    grade: String(row.grade ?? ''),
    kg: toNumber(row.kg),
    price_per_kg: toNumber(row.price_per_kg)
  };
}

async function fetchContributionRows(
  seasonYear: number,
  filters: { regionId?: string; province?: string }
) {
  const out: ReturnType<typeof mapContribution>[] = [];
  let from = 0;

  for (;;) {
    let query = admin
      .from('zd_market_contributions')
      .select('producer_hash,source_key,record_date,region_id,region_name,district,province,grade,kg,price_per_kg')
      .eq('season_year', seasonYear)
      .order('id', { ascending: true })
      .range(from, from + PAGE_SIZE - 1);

    if (filters.regionId) query = query.eq('region_id', filters.regionId);
    if (filters.province) query = query.eq('province', filters.province);

    const { data, error } = await query;
    if (error) throw error;

    const page = data ?? [];
    for (const row of page) out.push(mapContribution(row));

    if (page.length < PAGE_SIZE) break;
    if (out.length >= MAX_QUERY_ROWS) throw new Error('Market cohort capacity exceeded');
    from += PAGE_SIZE;
  }

  return out;
}

async function getConsent(hash: string) {
  const { data, error } = await admin
    .from('zd_market_consents')
    .select('enabled,policy_version,updated_at')
    .eq('producer_hash', hash)
    .maybeSingle();
  if (error) throw error;
  return {
    enabled: !!data?.enabled && Number(data?.policy_version) === POLICY_VERSION,
    policy_version: Number(data?.policy_version ?? POLICY_VERSION)
  };
}

async function setConsent(hash: string, enabled: boolean) {
  const { error } = await admin.from('zd_market_consents').upsert({
    producer_hash: hash,
    enabled,
    policy_version: POLICY_VERSION,
    updated_at: new Date().toISOString()
  }, { onConflict: 'producer_hash' });
  if (error) throw error;

  if (!enabled) {
    const purge = await admin.from('zd_market_contributions').delete().eq('producer_hash', hash);
    if (purge.error) throw purge.error;
  }
  return { enabled, policy_version: POLICY_VERSION };
}

async function syncRows(hash: string, seasonYear: number, rawRows: unknown[]) {
  const consent = await getConsent(hash);
  if (!consent.enabled) throw new Error('Anonymous market consent is disabled');
  if (!Array.isArray(rawRows) || rawRows.length > MAX_SYNC_ROWS) throw new Error('Invalid sync payload');

  const normalized = rawRows.map(normalizeContribution).filter(Boolean);

  const remove = await admin
    .from('zd_market_contributions')
    .delete()
    .eq('producer_hash', hash)
    .eq('season_year', seasonYear);
  if (remove.error) throw remove.error;

  if (normalized.length) {
    const insertRows = normalized.map((row: any) => ({
      producer_hash: hash,
      season_year: seasonYear,
      source_key: row.source_key,
      record_date: row.record_date,
      region_id: row.region_id,
      region_name: row.region_name,
      district: row.district,
      province: row.province,
      grade: row.grade,
      kg: row.kg,
      price_per_kg: row.price_per_kg,
      updated_at: new Date().toISOString()
    }));

    const insert = await admin.from('zd_market_contributions').insert(insertRows);
    if (insert.error) throw insert.error;
  }

  return { accepted_rows: normalized.length };
}

async function publicRegions(seasonYear: number) {
  const { data, error } = await admin.rpc('zd_market_public_regions', {
    p_season_year: seasonYear
  });
  if (error) throw error;
  return (data ?? []).map((r: any) => ({
    id: String(r.id ?? ''),
    name: String(r.name ?? ''),
    district: String(r.district ?? ''),
    province: String(r.province ?? ''),
    producer_count: Number(r.producer_count ?? 0)
  }));
}

async function marketSnapshot(body: any) {
  const seasonYear = safeYear(body.season_year);
  const grade = normalizeGrade(body.grade);
  if (!GRADES.includes(grade)) throw new Error('Invalid grade');
  const period = safePeriod(body.period);
  const selected = region(body.district, body.province);
  const nowMs = Date.now();

  const selectedRows = await fetchContributionRows(seasonYear, { regionId: selected.id });
  const current = buildSelectedMarket(selectedRows, grade, period, nowMs);

  if (!current.available) {
    return { available: false, updated_at_ms: nowMs };
  }

  // Bölgeler kartı: aynı ildeki yalnız 5+ üreticili anonim bölgeler.
  const provinceRows = await fetchContributionRows(seasonYear, { province: selected.province });
  const byRegion = new Map<string, typeof provinceRows>();
  for (const row of provinceRows) {
    let bucket = byRegion.get(row.region_id);
    if (!bucket) { bucket = []; byRegion.set(row.region_id, bucket); }
    bucket.push(row);
  }

  const regions: any[] = [];
  for (const [regionId, rows] of byRegion.entries()) {
    const stat = buildSelectedMarket(rows, grade, period, nowMs);
    if (!stat.available || stat.producer_count < MIN_PRODUCERS) continue;
    const first = rows[0];
    regions.push({
      id: regionId,
      name: first?.district ?? first?.region_name ?? regionId,
      median: stat.median,
      total_kg: stat.total_kg,
      producer_count: stat.producer_count
    });
  }
  regions.sort((a,b) => b.total_kg - a.total_kg || a.name.localeCompare(b.name, 'tr'));

  return { ...current, regions };
}

Deno.serve(async (req: Request) => {
  if (req.method === 'OPTIONS') return new Response('ok', { headers });
  if (req.method !== 'POST') return json(405, { ok:false, error:'POST required' });

  try {
    if (!SUPABASE_URL || !ADMIN_KEY) throw new Error('Supabase server configuration missing');

    const uid = await firebaseUid(req);
    const hash = await producerHash(uid);
    const body = await req.json();
    const action = String(body?.action ?? '').trim();

    if (action === 'consent_get') {
      return json(200, { ok:true, consent: await getConsent(hash) });
    }

    if (action === 'consent_set') {
      return json(200, { ok:true, consent: await setConsent(hash, body.enabled === true) });
    }

    if (action === 'sync') {
      const seasonYear = safeYear(body.season_year);
      if (Number(body.policy_version) !== POLICY_VERSION) throw new Error('Policy version mismatch');
      const result = await syncRows(hash, seasonYear, body.rows ?? []);
      return json(200, { ok:true, sync: result });
    }

    if (action === 'regions') {
      const seasonYear = safeYear(body.season_year);
      return json(200, { ok:true, regions: await publicRegions(seasonYear) });
    }

    if (action === 'market') {
      return json(200, { ok:true, market: await marketSnapshot(body) });
    }

    return json(400, { ok:false, error:'Unknown action' });
  } catch (error) {
    const message = error instanceof Error ? error.message : 'Unknown error';
    return json(400, { ok:false, error:message });
  }
});
