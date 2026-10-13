// Real local PostgreSQL (PGlite). Never opens a remote database connection.
// PGLITE_MODULE must point to an independently installed @electric-sql/pglite entrypoint.
import assert from 'node:assert/strict';import fs from 'node:fs';import {pathToFileURL} from 'node:url';
const {PGlite}=await import(pathToFileURL(process.env.PGLITE_MODULE).href);const db=new PGlite();
const sql=name=>fs.readFileSync(new URL('../'+name,import.meta.url),'utf8');
try {
 await db.exec('create role anon; create role authenticated; create role service_role;');
 await db.exec(sql('market-schema.sql'));
 for(let n=1;n<=5;n++){
  await db.exec('truncate public.zd_market_contributions, public.zd_market_consents restart identity cascade;');
  for(let i=0;i<n;i++){
   const hash=String(i).padStart(64,'0');
   await db.query('insert into public.zd_market_consents(producer_hash,enabled) values($1,true)',[hash]);
   await db.query("insert into public.zd_market_contributions(producer_hash,season_year,source_key,record_date,region_id,region_name,district,province,grade,kg,price_per_kg) values($1,2026,$2,'2026-09-10','manisa_alasehir','Alaşehir / Manisa','Alaşehir','Manisa','2',20,60)",[hash,'sale'+i]);
  }
  const out=await db.query('select * from public.zd_market_public_regions(2026)');assert.equal(out.rows.length,n===5?1:0);if(n===5)assert.equal(Number(out.rows[0].producer_count),5);
  console.log(`PASS actual market-schema.sql: ${n} distinct producers -> ${n===5?'PUBLIC':'HIDDEN'}`);
 }
 await db.exec("delete from public.zd_market_contributions where producer_hash <> repeat('0',64); delete from public.zd_market_consents where producer_hash <> repeat('0',64);");
 for(let i=1;i<10;i++)await db.query("insert into public.zd_market_contributions(producer_hash,season_year,source_key,record_date,region_id,region_name,district,province,grade,kg,price_per_kg) values(repeat('0',64),2026,$1,'2026-09-10','manisa_alasehir','Alaşehir / Manisa','Alaşehir','Manisa','2',20,60)",['extra'+i]);
 assert.equal((await db.query('select * from public.zd_market_public_regions(2026)')).rows.length,0);console.log('PASS actual SQL: one producer / ten sales -> HIDDEN');
 await assert.rejects(db.exec(sql('RESTORE-MIN-PRODUCERS-5.sql')),/Missing original/);await db.exec('rollback;');
 // These three functions are PATCH TEST FIXTURES, NOT reconstructed/live V7 RPC definitions.
 await db.exec('create schema private; create table private.market_threshold_patch_backup(batch_id text,schema_name text,function_name text,identity_args text,function_definition text,from_threshold integer,to_threshold integer,changed_at timestamptz default now());');
 for(const name of ['market_snapshot','market_regions','market_chart']){
  await db.exec(`create function public.${name}(v_producers integer) returns boolean language sql as $$select v_producers >= 5$$;`);
  const def=(await db.query(`select pg_get_functiondef('public.${name}(integer)'::regprocedure) as def`)).rows[0].def;
  await db.query('insert into private.market_threshold_patch_backup(batch_id,schema_name,function_name,identity_args,function_definition,from_threshold,to_threshold) values($1,$2,$3,$4,$5,5,2)',['batch','public',name,'v_producers integer',def]);
  await db.exec(def.replace('v_producers >= 5','v_producers >= 2'));
 }
 await db.exec('create function public.market_league_fixture() returns int language sql as $$select 2$$;');
 const leagueBefore=(await db.query("select pg_get_functiondef('public.market_league_fixture()'::regprocedure) as d")).rows[0].d;
 await db.exec(sql('RESTORE-MIN-PRODUCERS-5.sql'));await db.exec(sql('RESTORE-MIN-PRODUCERS-5.sql'));
 for(const name of ['market_snapshot','market_regions','market_chart'])for(let n=1;n<=5;n++)assert.equal((await db.query(`select public.${name}($1) as published`,[n])).rows[0].published,n===5);
 assert.equal((await db.query("select pg_get_functiondef('public.market_league_fixture()'::regprocedure) as d")).rows[0].d,leagueBefore);
 for(const row of (await db.query('select function_definition from private.market_threshold_patch_backup')).rows)
  await db.exec(row.function_definition.replace('v_producers >= 5','v_producers >= 2'));
 // Last in sort order: earlier changes must roll back when newer code is encountered.
 await db.exec('create or replace function public.market_snapshot(v_producers integer) returns boolean language sql as $$select v_producers >= 2 /* newer code */$$;');
 await assert.rejects(db.exec(sql('RESTORE-MIN-PRODUCERS-5.sql')),/refusing to overwrite/);await db.exec('rollback;');
 assert.equal((await db.query('select public.market_chart(2) as published')).rows[0].published,true);
 await db.exec("delete from private.market_threshold_patch_backup where function_name='market_chart'");
 await assert.rejects(db.exec(sql('RESTORE-MIN-PRODUCERS-5.sql')),/refusing to overwrite/);await db.exec('rollback;');
 await db.exec('truncate private.market_threshold_patch_backup;');await assert.rejects(db.exec(sql('RESTORE-MIN-PRODUCERS-5.sql')),/No original/);await db.exec('rollback;');
 await assert.rejects(db.exec(sql('TEST-ONLY-MIN-PRODUCERS-2.sql')),/disabled/);
 console.log('PASS restore fixtures: 2->5, idempotence, newer-code refusal/atomic rollback, missing-backup refusal / newer-body refusal with partial history, League untouched, lowering disabled');
}finally{await db.close();}
