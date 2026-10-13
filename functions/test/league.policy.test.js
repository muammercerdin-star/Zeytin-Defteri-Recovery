'use strict';
const test=require('node:test'),assert=require('node:assert/strict'),P=require('../league/policy');
function producer(i,options={}) {
  const kg=options.kg??1000,grade=options.grade??0,price=options.price??100;
  const grades=Array(6).fill(0),values=Array(6).fill(0);grades[grade]=kg;values[grade]=kg*price;
  return {uid:'uid-'+i,producer_id:P.publicId('uid-'+i,'a'.repeat(32)),season:2026,region_id:'manisa_alasehir',region_name:'Alaşehir / Manisa',kg,amount:kg*price,grades,values,records:1};
}
function evidence(overrides={}) {
  const now=Date.now(),uid='owner',garden={owner_uid:uid,district:'Alaşehir',province:'Manisa'};
  const raw={owner_uid:uid,cloud_id:'e1',garden_cloud_id:'g1',season_year:2026,record_date:'2026-09-01',
    grades:[{grade:'1',kg:522,price_per_kg:31276/522,line_total:31276}]};
  const sale={...raw,cloud_id:'s1',sale_kind:'SALE',sale_date:'2026-09-01',market_consent:1};
  const proof={schema_version:1,status:'verified',uid,garden_id:'g1',record_id:'e1',sale_garden_id:'g1',sale_id:'s1',season:2026,
    evidence_id:'external-evidence-0001',verifier:'trusted-provider',verified_at_ms:now-1000,expires_at_ms:now+30*86400000,
    record_digest:P.recordDigest(raw),sale_digest:P.saleDigest(sale),region_digest:P.regionDigest(garden),...overrides};
  return {now,raw,sale,garden,proof,id:P.proofId(uid,'g1','e1')};
}
test('1 and 2 producers remain insufficient; never manufacture regional positions',()=>{
 assert.deepEqual(P.rankRegion([producer(1)]),[]);assert.deepEqual(P.rankRegion([producer(1),producer(2)]),[]);
});
test('same owner with three gardens is one producer, weighted totals preserved',()=>{
 const rows=[1,2,3].map(i=>({...producer('a',{kg:i*100,price:i*10}),uid:'a',evidence_key:'e'+i,sale_key:'s'+i}));
 const a=P.aggregate(rows,'a',2026);assert.equal(a.records,3);assert.equal(a.kg,600);assert.equal(a.amount,14000);assert.equal(a.amount/a.kg,14000/600);
});
test('owner, season and regional cohort cannot mix',()=>{
 assert.throws(()=>P.aggregate([producer(1)],'uid-2',2026));assert.throws(()=>P.aggregate([producer(1)],'uid-1',2025));
 assert.throws(()=>P.rankRegion([producer(1),{...producer(2),season:2025}]));
 assert.throws(()=>P.rankRegion([producer(1),{...producer(2),region_id:'manisa_akhisar'}]));
});
test('99kg rejected; 100kg accepted with other eligibility conditions',()=>{
 assert.equal(P.score(producer(1,{kg:99})).eligible,false);assert.equal(P.score(producer(1,{kg:100})).eligible,true);
 assert.equal(P.rankRegion([1,2,3,4].map(i=>producer(i,{kg:100}))).length,4);
});
test('extreme and tiny priced submissions do not win',()=>{
 const normal=[1,2,3,4,5,6].map(i=>producer(i));
 const ranked=P.rankRegion([...normal,producer(7,{price:5000}),producer(8,{kg:1,price:5000})]);
 assert.equal(ranked.length,6);assert.ok(!ranked.some(p=>['uid-7','uid-8'].includes(p.uid)));
});
test('quality advantage dominates size-only advantage; volume is capped',()=>{
 const a=P.score(producer('high',{kg:500})),b=P.score(producer('oil',{kg:1000000,grade:5,price:10}));
 assert.ok(a.quality>b.quality);assert.ok(a.score>b.score);assert.equal(b.volume,100);
});
test('stable tie break, exact Top20 and private #21',()=>{
 const input=Array.from({length:25},(_,i)=>producer(i)),a=P.rankRegion(input),b=P.rankRegion([...input].reverse());
 assert.deepEqual(a.map(p=>p.producer_id),b.map(p=>p.producer_id));assert.equal(a[20].rank,21);
 const summary=P.summary(a,'manisa_alasehir','Alaşehir / Manisa');assert.equal(summary.top.length,20);assert.equal(summary.producer_count,25);assert.equal(summary.kg,25000);
 assert.ok(!JSON.stringify(summary).includes('uid-'));assert.deepEqual(Object.keys(P.publicRow(a[0])).sort(),['amount','grades','kg','producer_id','rank','region_id','score']);
});
test('Turkish case, diacritics and whitespace normalize to one region',()=>{
 const inputs=[['Alaşehir','Manisa'],[' ALAŞEHİR ',' MANİSA '],['alasehir','manisa'],['ALAŞEHIR','MANISA']];
 for(const [d,p] of inputs)assert.equal(P.region(d,p).key,'manisa_alasehir');
 assert.throws(()=>P.region('','Manisa'));
});
test('proof binding, raw arithmetic, consent, revocation and client forged ranks',()=>{
 const f=evidence(),check=()=>P.validateProof(f.proof,f.id,f.raw,f.garden,f.sale,f.garden,f.now);
 assert.equal(check().kg,522);assert.equal(check().amount,31276);
 f.raw.rank=1;f.raw.league_score=100;assert.equal(check().kg,522);
 f.proof.status='revoked';assert.equal(check(),null);f.proof.status='verified';
 f.sale.market_consent=0;assert.equal(check(),null);f.sale.market_consent=1;
 f.raw.grades[0].kg=523;assert.equal(check(),null);
});
test('client backup alone / stale evidence / mismatched owner is never verification',()=>{
 const f=evidence();assert.equal(P.validateProof({},f.id,f.raw,f.garden,f.sale,f.garden),null);
 assert.equal(P.validateProof({...f.proof,expires_at_ms:1},f.id,f.raw,f.garden,f.sale,f.garden),null);
 assert.equal(P.validateProof(f.proof,f.id,{...f.raw,owner_uid:'other'},f.garden,f.sale,f.garden),null);
});
test('leave-one-out median agrees with a slow independent IQR reference',()=>{
 function median(xs){if(xs.length<3)return 0;xs.sort((a,b)=>a-b);const q=(a,p)=>{const n=(a.length-1)*p,l=Math.floor(n),h=Math.ceil(n);return a[l]+(a[h]-a[l])*(n-l);};
 const q1=q(xs,.25),q3=q(xs,.75),clean=xs.filter(x=>x>=q1-1.5*(q3-q1)&&x<=q3+1.5*(q3-q1));return clean.length>=3?q(clean,.5):0;}
 for(let n=4;n<55;n++){
  const ps=Array.from({length:n},(_,i)=>producer(i,{price:i%9===0?5000:50+(i*17)%60})),index=P.observationIndex(ps,0);
  for(const p of ps)assert.equal(P.peerMedian(index,p.uid).median,median(ps.filter(x=>x.uid!==p.uid).map(x=>x.amount/x.kg)));
 }
});
module.exports={producer,evidence};
