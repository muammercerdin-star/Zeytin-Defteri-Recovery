import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import {createRequire} from 'node:module';
import {MIN_PRODUCERS,buildSelectedMarket,region} from '../functions/market-api/policy.mjs';
const require=createRequire(import.meta.url),legacy=require('../../functions/market/policy.js');
const now=Date.parse('2026-09-11T12:00:00Z'),r=region('Alaşehir','Manisa');
const row=(uid,id)=>({producer_hash:uid,source_key:id,record_date:'2026-09-10',record_day_ms:Date.parse('2026-09-10T00:00:00Z'),region_id:r.id,region_name:r.name,district:r.district,province:r.province,grade:'2',kg:20,price_per_kg:60});
const oldRow=(uid,id)=>({uid,record_id:id,season:2026,region_id:r.id,region_name:r.name,district:r.district,province:r.province,record_date:'2026-09-10',record_day_ms:Date.parse('2026-09-10T00:00:00Z'),grades:[{grade:'2',kg:20,price:60}]});
for(let n=1;n<=5;n++)test(`${n} distinct producers: ${n===5?'PUBLIC':'HIDDEN'} in both retained server policies`,()=>{
 const rows=Array.from({length:n},(_,i)=>row('u'+i,'sale'+i));
 for(const period of ['7d','30d','90d'])assert.equal(buildSelectedMarket(rows,'2',period,now).available,n===5);
 const old=legacy.buildRegionStats(Array.from({length:n},(_,i)=>oldRow('u'+i,'sale'+i)),{region_id:r.id,region_name:r.name,district:r.district,province:r.province},2026,now);
 assert.equal(old.some(s=>s.grade==='2'&&s.period==='7d'),n===5);
});
test('one producer / ten different sales stays HIDDEN',()=>{
 assert.equal(buildSelectedMarket(Array.from({length:10},(_,i)=>row('one','sale'+i)),'2','30d',now).available,false);
 assert.equal(legacy.buildRegionStats(Array.from({length:10},(_,i)=>oldRow('one','sale'+i)),{region_id:r.id,region_name:r.name,district:r.district,province:r.province},2026,now).length,0);
});
test('Android and retained production server thresholds agree; old lowering entrypoint is disabled',()=>{
 const config=fs.readFileSync(new URL('../../app/src/main/java/com/zeytindefteri/app/MarketBackendConfig.java',import.meta.url),'utf8');
 assert.match(config,/PUBLIC_MARKET_MIN_PRODUCERS\s*=\s*5\s*;/);assert.equal(MIN_PRODUCERS,5);assert.equal(legacy.MIN_PRODUCERS,5);
 const schema=fs.readFileSync(new URL('../market-schema.sql',import.meta.url),'utf8');assert.match(schema,/having count\(distinct c.producer_hash\) >= 5/);
 const disabled=fs.readFileSync(new URL('../TEST-ONLY-MIN-PRODUCERS-2.sql',import.meta.url),'utf8');assert.match(disabled,/RAISE EXCEPTION/);assert.ok(!/EXECUTE|create or replace|regexp_replace/i.test(disabled));
});
