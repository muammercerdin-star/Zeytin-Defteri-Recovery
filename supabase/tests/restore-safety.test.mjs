// Local PostgreSQL engine only; fixtures are NOT live RPC definitions.
import assert from 'node:assert/strict';
import fs from 'node:fs';
import {pathToFileURL} from 'node:url';
const {PGlite}=await import(pathToFileURL(process.env.PGLITE_MODULE).href);
const notices=[];const db=new PGlite();
const exec=db.exec.bind(db);db.exec=q=>exec(q,{onNotice:n=>notices.push(n.message)});
const sql=n=>fs.readFileSync(new URL('../'+n,import.meta.url),'utf8');
const restore=sql('RESTORE-MIN-PRODUCERS-5.sql'),dry=sql('DRY-RUN-RESTORE-MIN-PRODUCERS-5.sql');
const definitions=async()=>JSON.stringify((await db.query("select n.nspname,p.proname,pg_get_functiondef(p.oid) d from pg_proc p join pg_namespace n on n.oid=p.pronamespace where n.nspname in ('public','private') and p.prokind='f' order by n.nspname,p.proname,p.oid")).rows);
const get=async n=>(await db.query(`select pg_get_functiondef('public.${n}(text[])'::regprocedure) d`)).rows[0].d;
const patch=d=>d.replace('count(distinct producer) >= 5','count(distinct producer) >= 2');
async function reset(){await db.exec('set client_min_messages=notice;drop schema if exists private cascade;drop schema public cascade;create schema public;create schema private;create table private.market_threshold_patch_backup(batch_id text,schema_name text,function_name text,identity_args text,function_definition text,from_threshold int,to_threshold int,changed_at timestamptz default now());');notices.length=0;}
async function fixture(name,{backup=true,patched=true}={}){
 await db.exec(`create function public.${name}(ids text[]) returns boolean language sql as $$select count(distinct producer) >= 5 from unnest(ids) producer$$;`);
 const d=await get(name);
 if(backup)await db.query('insert into private.market_threshold_patch_backup values($1,$2,$3,$4,$5,5,2,now())',['batch','public',name,'ids text[]',d]);
 if(patched)await db.exec(patch(d));return d;
}
async function rejected(){const before=await definitions();await assert.rejects(db.exec(restore));await db.exec('rollback');assert.equal(await definitions(),before);}
let count=0;async function test(name,fn){await reset();await fn();count++;console.log('PASS '+name);}
try{
await test('Market and League backed-up patch restore + distinct producer boundary',async()=>{
 await fixture('market_snapshot');await fixture('market_league_regions');
 await db.exec(restore);
 for(const name of ['market_snapshot','market_league_regions']){
  for(let n=1;n<=5;n++)assert.equal((await db.query(`select public.${name}($1::text[]) p`,[Array.from({length:n},(_,i)=>String(i))])).rows[0].p,n===5);
  assert.equal((await db.query(`select public.${name}($1::text[]) p`,[Array(10).fill('same')])).rows[0].p,false);
  assert(!(await get(name)).includes('>= 2'));
 }
 const before=await definitions();await db.exec(restore);assert.equal(await definitions(),before);
});
await test('Unpatched League without backup untouched',async()=>{await fixture('market_snapshot');await fixture('market_league_regions',{backup:false,patched:false});const before=await get('market_league_regions');await db.exec(restore);assert.equal(await get('market_league_regions'),before);});
await test('Newer body aborts whole batch; no partial restore',async()=>{await fixture('market_chart');await fixture('market_zzz');await db.exec((await get('market_zzz')).replace('>= 2','>= 3'));await rejected();});
await test('Missing backup never authorizes name-based restore',async()=>{await fixture('market_snapshot');await fixture('market_league_regions',{backup:false});const before=await get('market_league_regions');await db.exec(restore);assert.equal(await get('market_league_regions'),before);assert(notices.some(n=>n.includes('SKIPPED_NO_BACKUP')));});
await test('Changed signature aborts and leaves other candidate patched',async()=>{await fixture('market_chart');await fixture('market_snapshot');await db.exec('drop function public.market_snapshot(text[]);create function public.market_snapshot(integer) returns int language sql as $$select 2$$');await rejected();});
await test('Dry-run is read-only and reports eligible/mismatch/no-backup',async()=>{await fixture('market_chart');await fixture('market_snapshot');await fixture('market_league_regions',{backup:false});await db.exec((await get('market_snapshot')).replace('>= 2','>= 3'));const before=await definitions();await db.exec(dry);assert.equal(await definitions(),before);for(const s of ['RESTORE_ELIGIBLE','MISMATCH_NEWER_OR_UNKNOWN_BODY','SKIPPED_NO_BACKUP'])assert(notices.some(n=>n.includes(s)));assert.match(dry,/begin transaction read only/i);});
await test('Missing backup table fails closed; dry-run reports',async()=>{await db.exec('drop table private.market_threshold_patch_backup');await db.exec(dry);await rejected();});
await test('Duplicate backup identity fails closed',async()=>{await fixture('market_snapshot');await db.exec('insert into private.market_threshold_patch_backup select * from private.market_threshold_patch_backup');await rejected();});
await test('Multiple batches never silently choose latest',async()=>{await fixture('market_snapshot');await db.exec("insert into private.market_threshold_patch_backup select 'other',schema_name,function_name,identity_args,function_definition,from_threshold,to_threshold,now() from private.market_threshold_patch_backup");await rejected();await db.exec(dry);assert(notices.some(n=>n.includes('choose a reviewed batch_id')));});
await test('Mismatched backup CREATE identity cannot redirect restore',async()=>{await fixture('market_snapshot');await db.exec("update private.market_threshold_patch_backup set function_definition=replace(function_definition,'public.market_snapshot','public.market_league_other')");await rejected();});
await test('Unrecognized original is unsafe',async()=>{await fixture('market_snapshot');await db.exec("update private.market_threshold_patch_backup set function_definition=replace(function_definition,'>= 5','>= 4')");await rejected();});
console.log(`${count} restore safety scenarios passed`);
}finally{await db.close();}
