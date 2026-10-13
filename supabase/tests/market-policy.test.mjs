import test from 'node:test';
import assert from 'node:assert/strict';
import {
  buildSelectedMarket,
  normalizeContribution,
  region
} from '../functions/market-api/policy.mjs';

function row(uid, price, kg=20, day='2026-09-10', district='Alaşehir', province='Manisa', grade='2') {
  const r = region(district, province);
  return {
    producer_hash: uid,
    source_key: `${uid}-${price}`,
    record_date: day,
    record_day_ms: Date.parse(day+'T00:00:00.000Z'),
    region_id: r.id,
    region_name: r.name,
    district: r.district,
    province: r.province,
    grade,
    kg,
    price_per_kg: price
  };
}

test('region normalization is stable for Turkish spelling', () => {
  assert.equal(region('Alaşehir', 'Manisa').id, 'manisa_alasehir');
  assert.equal(region('ALAŞEHİR', 'MANİSA').id, 'manisa_alasehir');
});

test('invalid contribution is rejected', () => {
  assert.equal(normalizeContribution({grade:'2',kg:0,price_per_kg:60}), null);
});

test('fewer than five producers never publishes', () => {
  const rows=[row('a',60),row('b',61),row('c',62),row('d',63)];
  assert.equal(buildSelectedMarket(rows,'2','30d',Date.parse('2026-09-11T12:00:00Z')).available,false);
});

test('five producers publishes producer-balanced median', () => {
  const rows=[
    row('a',55),row('b',60),row('c',60),row('d',65),row('e',70),
    row('a',1000),row('a',1000)
  ];
  const out=buildSelectedMarket(rows,'2','30d',Date.parse('2026-09-11T12:00:00Z'));
  assert.equal(out.available,true);
  // A üreticisinin kendi medyanı 1000 olsa bile yalnız tek üretici oyu taşır.
  assert.equal(out.producer_count,5);
  assert.ok(out.median >= 60 && out.median <= 70);
});

test('previous period comparison is calculated from the previous window', () => {
  const now=Date.parse('2026-09-11T12:00:00Z');
  const currentDay='2026-09-10';
  const prevDay='2026-09-02';
  const rows=[];
  for (let i=0;i<5;i++) {
    rows.push(row('u'+i,60,20,currentDay));
    rows.push(row('u'+i,70,20,prevDay));
  }
  const out=buildSelectedMarket(rows,'2','7d',now);
  assert.equal(out.available,true);
  assert.equal(out.previous_available,true);
  assert.equal(out.median,60);
  assert.equal(out.previous_median,70);
  assert.equal(out.change_abs,-10);
});
