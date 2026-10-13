'use strict';

const crypto = require('node:crypto');

const GRADES = ['DOUBLE','1','2','3','4','YAGLIK'];
const PERIODS = [
  {key:'7d', days:7},
  {key:'30d', days:30},
  {key:'90d', days:90},
  {key:'season', days:0}
];
const MIN_PRODUCERS = 5;
const MIN_DAILY_PRODUCERS = 5;
const MIN_IQR_ROWS = 6;
const DAY_MS = 86400000;

const finite = n => typeof n === 'number' && Number.isFinite(n);
const hash = s => crypto.createHash('sha256').update(String(s)).digest('hex');
const clean = s => String(s == null ? '' : s).trim().replace(/\s+/gu,' ').normalize('NFC');

function canonical(value) {
  if (Array.isArray(value)) return '[' + value.map(canonical).join(',') + ']';
  if (value && typeof value === 'object') {
    return '{' + Object.keys(value).sort().map(k => JSON.stringify(k)+':'+canonical(value[k])).join(',') + '}';
  }
  return JSON.stringify(value);
}

function fold(value) {
  return clean(value).toLocaleLowerCase('tr-TR').replace(/ı/g,'i').normalize('NFD')
    .replace(/\p{M}/gu,'').replace(/[^a-z0-9]+/g,'_').replace(/^_|_$/g,'');
}

function title(value) {
  return clean(value).toLocaleLowerCase('tr-TR')
    .replace(/(^|\s)(\S)/gu, (_,space,c) => space + c.toLocaleUpperCase('tr-TR'));
}

function region(district, province) {
  const d = clean(district), p = clean(province);
  if (!d || !p || d.length > 70 || p.length > 70) throw new Error('Region missing');
  const dk = fold(d), pk = fold(p);
  if (!dk || !pk) throw new Error('Invalid region');
  return {id:pk+'_'+dk, name:title(d)+' / '+title(p), district:title(d), province:title(p)};
}

function dayMillis(value) {
  const s = clean(value);
  if (!/^\d{4}-\d{2}-\d{2}$/.test(s)) throw new Error('Invalid record date');
  const ms = Date.parse(s+'T00:00:00.000Z');
  if (!Number.isFinite(ms)) throw new Error('Invalid record date');
  return ms;
}

function validateGradeRow(row) {
  if (!row || typeof row !== 'object') return null;
  const grade = clean(row.grade).toUpperCase().replace('YAĞLIK','YAGLIK');
  const kg = row.kg, price = row.price_per_kg;
  const line = row.line_total;
  if (!GRADES.includes(grade) || !finite(kg) || !finite(price) || kg <= 0 || price <= 0 || kg > 1e9 || price > 1e7) return null;
  if (line != null && finite(line) && line >= 0 && Math.abs(kg * price - line) > Math.max(.05, Math.abs(line) * 1e-5)) return null;
  return {grade, kg, price};
}

function sourceId(uid, gardenId, recordId) {
  return hash(canonical([uid,gardenId,recordId]));
}

function normalizeSource(uid, gardenId, recordId, raw, garden) {
  try {
    if (!raw || !garden || !uid || !gardenId || !recordId) return null;
    if (raw.owner_uid !== uid || garden.owner_uid !== uid || raw.cloud_id !== recordId || raw.garden_cloud_id !== gardenId) return null;
    const season = raw.season_year;
    if (!Number.isInteger(season) || season < 2000 || season > 2100) return null;
    const recordDate = clean(raw.record_date), recordDayMs = dayMillis(recordDate);
    const r = region(garden.district, garden.province);
    if (!Array.isArray(raw.grades) || raw.grades.length < 1 || raw.grades.length > 20) return null;
    const grades = raw.grades.map(validateGradeRow).filter(Boolean);
    if (!grades.length) return null;
    const normalized = {
      uid, garden_id:gardenId, record_id:recordId, season,
      region_id:r.id, region_name:r.name, district:r.district, province:r.province,
      record_date:recordDate, record_day_ms:recordDayMs,
      grades,
      policy_version:1
    };
    normalized.source_digest = hash(canonical({
      season, region_id:r.id, record_date:recordDate,
      grades:grades.map(g=>({grade:g.grade,kg:g.kg,price:g.price}))
    }));
    return normalized;
  } catch { return null; }
}

function percentile(sorted, p) {
  if (!sorted.length) return 0;
  if (sorted.length === 1) return sorted[0];
  const x = (sorted.length - 1) * p;
  const lo = Math.floor(x), hi = Math.ceil(x);
  return sorted[lo] + (sorted[hi] - sorted[lo]) * (x - lo);
}

function median(sorted) { return percentile(sorted, .5); }

function filterOutliers(rows) {
  if (rows.length < MIN_IQR_ROWS) return {accepted:rows.slice(), outliers:0};
  const prices = rows.map(r=>r.price).sort((a,b)=>a-b);
  const q1 = percentile(prices,.25), q3 = percentile(prices,.75), iqr = q3-q1;
  if (!(iqr > 0)) return {accepted:rows.slice(), outliers:0};
  const low=q1-1.5*iqr, high=q3+1.5*iqr;
  const accepted=rows.filter(r=>r.price>=low&&r.price<=high);
  if (accepted.length < Math.max(3,Math.ceil(rows.length*.5))) return {accepted:rows.slice(),outliers:0};
  return {accepted,outliers:rows.length-accepted.length};
}

const roundHalf = n => Math.round(n*2)/2;
const roundTen = n => Math.round(n/10)*10;

function confidence(rows, rawCount, outliers, nowMs) {
  if (!rows.length) return {score:0,label:'Veri Yok'};
  const producers = new Set(rows.map(r=>r.uid)).size;
  const newest = Math.max(...rows.map(r=>r.day_ms));
  const ageDays = Math.max(0,(nowMs-newest)/DAY_MS);
  const prices = rows.map(r=>r.price).sort((a,b)=>a-b), med=median(prices);
  const q1=percentile(prices,.25), q3=percentile(prices,.75);
  const spread = med>0 ? (q3-q1)/med : 1;
  const producerScore=Math.min(100,producers*11);
  const recordScore=Math.min(100,rows.length*5);
  const recencyScore=Math.max(0,100-ageDays*3);
  const spreadScore=Math.max(0,100-spread*260);
  const outlierScore=rawCount>0?Math.max(0,100-(outliers/rawCount)*180):0;
  let score=Math.round(.28*producerScore+.12*recordScore+.24*recencyScore+.24*spreadScore+.12*outlierScore);
  if(producers<5)score=Math.min(score,29);
  else if(producers<8)score=Math.min(score,59);
  else if(producers<15)score=Math.min(score,74);
  else if(producers<30)score=Math.min(score,89);
  let label=score>=90?'Yüksek Güven':score>=75?'Güvenilir Veri':score>=50?'Orta Güven':score>=30?'Sınırlı Veri':'Çok Az Veri';
  return {score,label};
}

function observations(sources, grade, startMs, endMs) {
  const rows=[];
  for(const s of sources){
    if(!s || !Array.isArray(s.grades) || !finite(s.record_day_ms))continue;
    if(startMs!=null && s.record_day_ms<startMs)continue;
    if(endMs!=null && s.record_day_ms>=endMs)continue;
    const g=s.grades.find(x=>x&&x.grade===grade);
    if(!g||!finite(g.kg)||!finite(g.price)||g.kg<=0||g.price<=0)continue;
    rows.push({uid:s.uid,record_id:s.record_id,day:s.record_date,day_ms:s.record_day_ms,kg:g.kg,price:g.price});
  }
  return rows;
}

function collapseByProducer(rows) {
  const groups=new Map();
  for(const r of rows){
    let g=groups.get(r.uid);
    if(!g){g={uid:r.uid,prices:[],kg:0,day_ms:0,rows:0};groups.set(r.uid,g);}
    g.prices.push(r.price);g.kg+=r.kg;g.day_ms=Math.max(g.day_ms,r.day_ms);g.rows++;
  }
  return [...groups.values()].map(g=>{
    g.prices.sort((a,b)=>a-b);
    return {uid:g.uid,price:median(g.prices),kg:g.kg,day_ms:g.day_ms,rows:g.rows};
  });
}

function buildDailyChart(rawRows, acceptedUids) {
  const days=new Map();
  for(const r of rawRows){
    if(!acceptedUids.has(r.uid))continue;
    let byUser=days.get(r.day);if(!byUser){byUser=new Map();days.set(r.day,byUser);}
    const prices=byUser.get(r.uid)||[];prices.push(r.price);byUser.set(r.uid,prices);
  }
  return [...days.entries()].sort((a,b)=>a[0].localeCompare(b[0])).map(([day,byUser])=>{
    if(byUser.size<MIN_DAILY_PRODUCERS)return null;
    const producerPrices=[];
    for(const prices of byUser.values()){prices.sort((a,b)=>a-b);producerPrices.push(median(prices));}
    producerPrices.sort((a,b)=>a-b);
    return {day,price:roundHalf(median(producerPrices)),producer_count:byUser.size};
  }).filter(Boolean).slice(-30);
}

function aggregateRows(rows, nowMs, includeChart) {
  const producerRows=collapseByProducer(rows);
  if(producerRows.length<MIN_PRODUCERS)return {available:false};

  // Her üretici piyasa fiyatında bir oy taşır. Bir hesabın çok sayıda kayıt
  // girerek medyanı tek başına sürüklemesi engellenir.
  const filtered=filterOutliers(producerRows), acceptedProducers=filtered.accepted;
  if(acceptedProducers.length<MIN_PRODUCERS)return {available:false};
  const acceptedUids=new Set(acceptedProducers.map(r=>r.uid));
  const acceptedRaw=rows.filter(r=>acceptedUids.has(r.uid));
  const prices=acceptedProducers.map(r=>r.price).sort((a,b)=>a-b);
  // Fiyat medyanı üretici-dengeli; hacimde de tek hesabın aşırı şişirmesini
  // sınırlamak için dönem başına üretici katkısı 250 tonla tavanlanır.
  const totalKg=acceptedProducers.reduce((sum,r)=>sum+Math.min(r.kg,250000),0);
  const outlierRecordCount=rows.length-acceptedRaw.length;

  const out={
    available:true,
    producer_count:acceptedProducers.length,
    record_count:acceptedRaw.length,
    outlier_count:outlierRecordCount,
    total_kg:roundTen(totalKg),
    median:roundHalf(median(prices)),
    range_low:roundHalf(percentile(prices,.25)),
    range_high:roundHalf(percentile(prices,.75))
  };
  const conf=confidence(acceptedProducers,producerRows.length,filtered.outliers,nowMs);
  out.confidence_score=conf.score;out.confidence_label=conf.label;
  out.last_day_ms=Math.max(...acceptedRaw.map(r=>r.day_ms));
  if(includeChart)out.chart=buildDailyChart(acceptedRaw,acceptedUids);
  return out;
}

function periodBounds(period, nowMs) {
  if(period.days<=0)return {start:null,end:nowMs+DAY_MS,previousStart:null,previousEnd:null};
  const end=nowMs+DAY_MS, start=nowMs-period.days*DAY_MS;
  return {start,end,previousStart:start-period.days*DAY_MS,previousEnd:start};
}

function statId(season, regionId, grade, periodKey) {
  return `${season}__${regionId}__${grade}__${periodKey}`;
}

function buildRegionStats(sources, meta, season, nowMs=Date.now()) {
  if(!Array.isArray(sources))throw new Error('Sources required');
  const stats=[];
  for(const period of PERIODS){
    const b=periodBounds(period,nowMs);
    for(const grade of GRADES){
      const current=aggregateRows(observations(sources,grade,b.start,b.end),nowMs,true);
      if(!current.available)continue;
      let previous={available:false};
      if(period.days>0)previous=aggregateRows(observations(sources,grade,b.previousStart,b.previousEnd),nowMs,false);
      const changeAbs=previous.available?roundHalf(current.median-previous.median):0;
      const changePct=previous.available&&previous.median>0?Math.round(((current.median-previous.median)/previous.median)*10000)/100:0;
      stats.push({
        schema_version:1,policy_version:1,season,region_id:meta.region_id,region_name:meta.region_name,
        district:meta.district,province:meta.province,grade,period:period.key,
        ...current,
        previous_available:!!previous.available,
        previous_median:previous.available?previous.median:0,
        previous_record_count:previous.available?previous.record_count:0,
        change_abs:changeAbs,change_pct:changePct,
        updated_at_ms:nowMs
      });
    }
  }
  return stats;
}

module.exports={
  GRADES,PERIODS,MIN_PRODUCERS,MIN_DAILY_PRODUCERS,DAY_MS,
  hash,canonical,region,sourceId,normalizeSource,filterOutliers,buildRegionStats,statId,median,percentile
};
