'use strict';
const crypto = require('node:crypto');
const GRADES = ['DOUBLE','1','2','3','4','YAGLIK'];
const LABELS = ['Double','1 No','2 No','3 No','4 No','Yağlık'];
const QUALITY = [100,90,75,55,30,10]; // Parity-tested against unchanged ZeytinLeagueScore.java.
const finite = n => typeof n === 'number' && Number.isFinite(n);
const clamp = n => Math.max(0, Math.min(100, n));
const hash = s => crypto.createHash('sha256').update(s).digest('hex');
const compare = (a,b) => a < b ? -1 : a > b ? 1 : 0;
function canonical(value) {
  if (Array.isArray(value)) return '[' + value.map(canonical).join(',') + ']';
  if (value && typeof value === 'object') return '{' + Object.keys(value).sort().map(k => JSON.stringify(k)+':'+canonical(value[k])).join(',') + '}';
  return JSON.stringify(value);
}
function region(district, province) {
  const clean = s => String(s || '').trim().replace(/\s+/gu,' ').normalize('NFC');
  const d=clean(district), p=clean(province);
  if (!d || !p || d.length>70 || p.length>70) throw new Error('Region missing');
  // Fold Turkish diacritics AND ASCII spellings into one key; display stays separate.
  const fold = s => clean(s).toLocaleLowerCase('tr-TR').replace(/ı/g,'i').normalize('NFD').replace(/\p{M}/gu,'').replace(/[^a-z0-9]+/g,'_').replace(/^_|_$/g,'');
  const key = fold(p)+'_'+fold(d);
  if (!fold(p) || !fold(d)) throw new Error('Invalid region');
  const title = s => clean(s).toLocaleLowerCase('tr-TR').replace(/(^|\s)(\S)/gu, (_,space,c) => space+c.toLocaleUpperCase('tr-TR'));
  return {key, name:title(d)+' / '+title(p)};
}
function season(record) {
  const n=record.season_year;
  if (Number.isInteger(n) && n>=2000 && n<=2100) return n;
  if (n != null && n!==0) throw new Error('Invalid season');
  const date=String(record.record_date || '').trim();
  const match=date.match(/^(\d{4})[-/.]/) || date.match(/[-/.](\d{4})$/);
  const y=match ? +match[1] : 0;
  if(y<2000 || y>2100) throw new Error('Missing season'); return y;
}
function totals(record) {
  if(!Array.isArray(record.grades) || record.grades.length>100 || !record.grades.length) throw new Error('Invalid grades');
  const grades=Array(6).fill(0), values=Array(6).fill(0);
  for(const row of record.grades) {
    const i=GRADES.indexOf(row.grade), kg=row.kg, price=row.price_per_kg, amount=row.line_total;
    if(i<0 || !finite(kg) || !finite(price) || !finite(amount) || kg<0 || price<0 || amount<0
      || kg>1e9 || price>1e7 || Math.abs(kg*price-amount)>Math.max(.01,Math.abs(amount)*1e-6)) throw new Error('Invalid raw value');
    grades[i]+=kg; values[i]+=amount;
  }
  const kg=grades.reduce((a,b)=>a+b,0), amount=values.reduce((a,b)=>a+b,0);
  if(!finite(kg) || !finite(amount) || kg<=0 || amount<=0) throw new Error('Unpriced record');
  return {grades,values,kg,amount};
}
// Digests whitelist the actual evidence fields. Client league_score/rank are ignored.
function recordDigest(r) { return hash(canonical({season:season(r),date:r.record_date || '',...totals(r)})); }
function saleDigest(r) { return hash(canonical({season:r.season_year,date:r.sale_date || '',kind:r.sale_kind,consent:r.market_consent,...totals(r)})); }
function regionDigest(g) { return hash(canonical(region(g.district,g.province))); }
function proofId(uid,gardenId,recordId) { return hash(canonical([uid,gardenId,recordId])); }
function validateProof(proof, id, raw, garden, sale, saleGarden, now=Date.now()) {
  try {
    const uid=proof.uid;
    if(proof.schema_version!==1 || proof.status!=='verified' || !uid || !proof.garden_id || !proof.record_id
      || !proof.sale_garden_id || !proof.sale_id || typeof proof.evidence_id!=='string' || proof.evidence_id.length<16
      || proof.evidence_id.length>256 || typeof proof.verifier!=='string' || !proof.verifier.trim()
      || !finite(proof.verified_at_ms) || proof.verified_at_ms<=0 || proof.verified_at_ms>now
      || !finite(proof.expires_at_ms) || proof.expires_at_ms<=now || proof.expires_at_ms>proof.verified_at_ms+366*86400000
      || id!==proofId(uid,proof.garden_id,proof.record_id)) return null;
    if(!raw || !garden || !sale || !saleGarden || garden.owner_uid!==uid || saleGarden.owner_uid!==uid
      || raw.owner_uid!==uid || sale.owner_uid!==uid || raw.cloud_id!==proof.record_id
      || sale.cloud_id!==proof.sale_id || raw.garden_cloud_id!==proof.garden_id
      || sale.garden_cloud_id!==proof.sale_garden_id || sale.sale_kind!=='SALE' || sale.market_consent!==1) return null;
    const year=season(raw), actual=totals(raw), sold=totals(sale), r=region(garden.district,garden.province);
    if(proof.season!==year || sale.season_year!==year || recordDigest(raw)!==proof.record_digest
      || saleDigest(sale)!==proof.sale_digest || regionDigest(garden)!==proof.region_digest) return null;
    // No implicit linking by date/price. V1 requires an explicit, evidenced one-to-one match.
    for(let i=0;i<6;i++) if(Math.abs(actual.grades[i]-sold.grades[i])>.01 || Math.abs(actual.values[i]-sold.values[i])>.01) return null;
    return {uid,season:year,region_id:r.key,region_name:r.name,...actual,records:1,
      evidence_key:hash(proof.evidence_id), sale_key:hash(canonical([uid,proof.sale_garden_id,proof.sale_id]))};
  } catch { return null; }
}
function aggregate(rows,uid,year) {
  const p={uid,season:year,kg:0,amount:0,grades:Array(6).fill(0),values:Array(6).fill(0),records:0};
  const regions=new Map(), seen=new Set();
  for(const row of rows) {
    if(row.uid!==uid || row.season!==year) throw new Error('Owner/season mismatch');
    if(row.invalid || seen.has(row.evidence_key) || seen.has(row.sale_key)) return null;
    seen.add(row.evidence_key);seen.add(row.sale_key);
    p.kg+=row.kg;p.amount+=row.amount;p.records++;
    for(let i=0;i<6;i++){p.grades[i]+=row.grades[i];p.values[i]+=row.values[i];}
    const group=regions.get(row.region_id)||{kg:0,name:row.region_name};group.kg+=row.kg;
    if(compare(row.region_name,group.name)<0)group.name=row.region_name;
    regions.set(row.region_id,group);
  }
  if(!p.records) return null;
  const best=[...regions.entries()].sort((a,b)=>b[1].kg-a[1].kg||compare(a[0],b[0]))[0];
  p.region_id=best[0];p.region_name=best[1].name; return p;
}
function score(p,medians,counts) {
  let quality=0,expected=0,sum=0,ready=!!medians && !!counts && medians.length===6 && counts.length===6;
  let valid=finite(p.kg)&&finite(p.amount)&&p.kg>0&&p.amount>0;
  for(let i=0;i<6;i++) {
    const kg=p.grades[i];valid=valid&&finite(kg)&&kg>=0;sum+=kg;quality+=kg*QUALITY[i];
    if(kg>0&&ready){if(!finite(medians[i])||medians[i]<=0||counts[i]<3)ready=false;else expected+=kg*medians[i];}
  }
  valid=valid&&Math.abs(sum-p.kg)<=Math.max(.01,p.kg*.000001);
  if(!valid)return {...p,quality:0,price:50,volume:0,score:0,eligible:false,benchmarkReady:false,outlier:false};
  quality=clamp(quality/p.kg);const benchmarkReady=ready&&expected>0;
  const ratio=benchmarkReady?p.amount/expected:1, price=benchmarkReady?clamp(100*(ratio-.5)):50;
  const volume=clamp(100*Math.log1p(p.kg)/Math.log1p(10000)), outlier=benchmarkReady&&(ratio<.25||ratio>3);
  return {...p,quality,price,volume,score:clamp(.5*quality+.35*price+.15*volume),benchmarkReady,outlier,
    eligible:p.kg>=100&&p.records>0&&!outlier};
}
// Sorted observation arrays give exact leave-one-producer-out IQR/median in O(log N).
function observationIndex(producers,grade) {
  const values=producers.filter(p=>p.grades[grade]>0&&p.values[grade]>0)
    .map(p=>({uid:p.uid,price:p.values[grade]/p.grades[grade]})).sort((a,b)=>a.price-b.price||compare(a.uid,b.uid));
  return {values,positions:new Map(values.map((v,i)=>[v.uid,i]))};
}
function peerMedian(index,uid) {
  const excluded=index.positions.get(uid), values=index.values;
  const n=values.length-(excluded===undefined?0:1);
  if(n<3)return {median:0,count:0};
  const at=i=>values[i+(excluded!==undefined&&i>=excluded?1:0)].price;
  const q=(start,len,p)=>{const x=(len-1)*p,lo=Math.floor(x),hi=Math.ceil(x);return at(start+lo)+(at(start+hi)-at(start+lo))*(x-lo);};
  const q1=q(0,n,.25),q3=q(0,n,.75),lo=q1-1.5*(q3-q1),hi=q3+1.5*(q3-q1);
  function bound(value,upper){let l=0,r=n;while(l<r){const m=(l+r)>>>1;if(at(m)<value||(upper&&at(m)===value))l=m+1;else r=m;}return l;}
  const start=bound(lo,false),end=bound(hi,true),count=end-start;
  return {median:count>=3?q(start,count,.5):0,count};
}
function rankRegion(producers) {
  const ids=new Set(), regionId=producers[0]?.region_id, year=producers[0]?.season;
  for(const p of producers) {
    if(ids.has(p.uid)||p.region_id!==regionId||p.season!==year)throw new Error('Duplicate or mixed cohort');ids.add(p.uid);
  }
  let cohort=producers.filter(p=>score(p).eligible);
  // Monotone elimination, bounded rounds. Fail closed rather than publish unsettled results.
  for(let round=0;round<12;round++) {
    if(cohort.length<4)return [];
    const indices=GRADES.map((_,i)=>observationIndex(cohort,i));
    const scored=cohort.map(p=>{
      const peers=indices.map(index=>peerMedian(index,p.uid));return score(p,peers.map(v=>v.median),peers.map(v=>v.count));
    });
    const next=scored.filter(p=>p.eligible&&p.benchmarkReady);
    if(next.length===cohort.length) return next.sort((a,b)=>b.score-a.score||b.quality-a.quality||compare(a.producer_id,b.producer_id))
      .map((p,i)=>({...p,rank:i+1}));
    cohort=next;
  }
  return [];
}
function publicRow(p) { return {producer_id:p.producer_id,region_id:p.region_id,rank:p.rank,kg:p.kg,amount:p.amount,score:p.score,grades:p.grades}; }
function summary(ranked,regionId,name) {
  const kg=ranked.reduce((s,p)=>s+p.kg,0),amount=ranked.reduce((s,p)=>s+p.amount,0);
  const grades=GRADES.map((_,i)=>ranked.reduce((s,p)=>s+p.grades[i],0));
  let dominant=0;for(let i=1;i<6;i++)if(grades[i]>grades[dominant])dominant=i;
  return {name,region_id:regionId,producer_count:ranked.length,kg,amount,weighted_average:kg?amount/kg:0,
    dominant:kg?LABELS[dominant]:'Belirsiz',confidence:ranked.length>=4?'verified':'insufficient',top:ranked.slice(0,20).map(publicRow)};
}
function publicId(uid,secret){if(typeof secret!=='string'||secret.length<32)throw new Error('Missing league identity secret');return crypto.createHmac('sha256',secret).update(uid).digest('hex');}
module.exports={GRADES,QUALITY,hash,canonical,region,season,totals,recordDigest,saleDigest,regionDigest,proofId,validateProof,aggregate,score,peerMedian,observationIndex,rankRegion,publicRow,summary,publicId};
