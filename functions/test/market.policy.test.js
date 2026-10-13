'use strict';
const test=require('node:test');
const assert=require('node:assert/strict');
const P=require('../market/policy');

function source(uid,price,day='2026-09-10',kg=20,grade='2'){
  return {uid,record_id:uid+'_'+day+'_'+price,season:2026,region_id:'manisa_alasehir',region_name:'Alaşehir / Manisa',
    district:'Alaşehir',province:'Manisa',record_date:day,record_day_ms:Date.parse(day+'T00:00:00Z'),grades:[{grade,kg,price}]};
}

test('region normalization is stable for Turkish spelling',()=>{
  assert.equal(P.region('Alaşehir','Manisa').id,'manisa_alasehir');
  assert.equal(P.region('ALAŞEHİR','MANİSA').id,'manisa_alasehir');
});

test('normalizer rejects identity mismatch and keeps only priced grade rows',()=>{
  const raw={owner_uid:'u1',cloud_id:'r1',garden_cloud_id:'g1',season_year:2026,record_date:'2026-09-10',grades:[
    {grade:'2',kg:20,price_per_kg:60,line_total:1200},{grade:'1',kg:0,price_per_kg:80,line_total:0}]};
  const garden={owner_uid:'u1',district:'Alaşehir',province:'Manisa'};
  const ok=P.normalizeSource('u1','g1','r1',raw,garden);
  assert.equal(ok.grades.length,1);assert.equal(ok.grades[0].grade,'2');
  assert.equal(P.normalizeSource('u2','g1','r1',raw,garden),null);
});

test('public market requires at least five distinct producers',()=>{
  const now=Date.parse('2026-09-11T12:00:00Z');
  let stats=P.buildRegionStats([source('u1',60),source('u2',61),source('u3',62),source('u4',63)],
    {region_id:'manisa_alasehir',region_name:'Alaşehir / Manisa',district:'Alaşehir',province:'Manisa'},2026,now);
  assert.equal(stats.length,0);
  stats=P.buildRegionStats([source('u1',60),source('u2',61),source('u3',62),source('u4',63),source('u5',64)],
    {region_id:'manisa_alasehir',region_name:'Alaşehir / Manisa',district:'Alaşehir',province:'Manisa'},2026,now);
  assert.ok(stats.some(s=>s.grade==='2'&&s.period==='7d'));
});

test('outlier is removed and aggregate never publishes user identity',()=>{
  const now=Date.parse('2026-09-11T12:00:00Z');
  const rows=[source('u1',80),source('u2',81),source('u3',82),source('u4',83),source('u5',84),source('u6',850)];
  const stat=P.buildRegionStats(rows,{region_id:'manisa_alasehir',region_name:'Alaşehir / Manisa',district:'Alaşehir',province:'Manisa'},2026,now)
    .find(s=>s.grade==='2'&&s.period==='7d');
  assert.ok(stat);assert.equal(stat.outlier_count,1);assert.ok(stat.median<100);assert.ok(stat.range_high<100);
  assert.equal(Object.prototype.hasOwnProperty.call(stat,'uid'),false);
  assert.equal(JSON.stringify(stat).includes('u1'),false);
});

test('daily chart requires five producers for that day',()=>{
  const now=Date.parse('2026-09-11T12:00:00Z');
  const rows=[source('u1',60),source('u2',61),source('u3',62),source('u4',63),source('u5',64),
    source('u1',65,'2026-09-09'),source('u2',66,'2026-09-09')];
  const stat=P.buildRegionStats(rows,{region_id:'manisa_alasehir',region_name:'Alaşehir / Manisa',district:'Alaşehir',province:'Manisa'},2026,now)
    .find(s=>s.grade==='2'&&s.period==='7d');
  assert.equal(stat.chart.length,1);assert.equal(stat.chart[0].day,'2026-09-10');
});

test('previous period comparison is generated only from anonymous cohort',()=>{
  const now=Date.parse('2026-09-11T12:00:00Z');
  const rows=[];
  for(let i=1;i<=5;i++){rows.push(source('u'+i,60+i,'2026-09-10'));rows.push(source('u'+i,50+i,'2026-09-02'));}
  const stat=P.buildRegionStats(rows,{region_id:'manisa_alasehir',region_name:'Alaşehir / Manisa',district:'Alaşehir',province:'Manisa'},2026,now)
    .find(s=>s.grade==='2'&&s.period==='7d');
  assert.equal(stat.previous_available,true);assert.ok(stat.change_abs>0);assert.ok(stat.change_pct>0);
});

test('one producer cannot dominate the public median by submitting many records',()=>{
  const now=Date.parse('2026-09-11T12:00:00Z');
  const rows=[source('u1',60),source('u2',61),source('u3',62),source('u4',63),source('u5',64)];
  for(let i=0;i<100;i++)rows.push(source('u1',500,'2026-09-10',1));
  const stat=P.buildRegionStats(rows,{region_id:'manisa_alasehir',region_name:'Alaşehir / Manisa',district:'Alaşehir',province:'Manisa'},2026,now)
    .find(s=>s.grade==='2'&&s.period==='7d');
  assert.ok(stat);assert.ok(stat.median<100,'producer-balanced median must resist spam');
  assert.equal(stat.producer_count,5);
});
