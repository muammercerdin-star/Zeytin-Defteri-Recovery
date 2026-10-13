export const GRADES = ['DOUBLE','1','2','3','4','YAGLIK'];
export const PERIODS = [
  {key:'7d', days:7},
  {key:'30d', days:30},
  {key:'90d', days:90},
  {key:'season', days:0}
];
export const MIN_PRODUCERS = 5;
export const MIN_DAILY_PRODUCERS = 5;
export const MIN_IQR_ROWS = 6;
export const DAY_MS = 86400000;

const finite = n => typeof n === 'number' && Number.isFinite(n);
const clean = s => String(s == null ? '' : s).trim().replace(/\s+/gu,' ').normalize('NFC');

export function fold(value) {
  return clean(value).toLocaleLowerCase('tr-TR').replace(/ı/g,'i').normalize('NFD')
    .replace(/\p{M}/gu,'').replace(/[^a-z0-9]+/g,'_').replace(/^_|_$/g,'');
}

export function title(value) {
  return clean(value).toLocaleLowerCase('tr-TR')
    .replace(/(^|\s)(\S)/gu, (_,space,c) => space + c.toLocaleUpperCase('tr-TR'));
}

export function region(district, province) {
  const d = clean(district), p = clean(province);
  if (!d || !p || d.length > 70 || p.length > 70) throw new Error('Region missing');
  const dk = fold(d), pk = fold(p);
  if (!dk || !pk) throw new Error('Invalid region');
  return {id:pk+'_'+dk, name:title(d)+' / '+title(p), district:title(d), province:title(p)};
}

export function normalizeGrade(value) {
  const g = clean(value).toUpperCase().replace('YAĞLIK','YAGLIK');
  if (g === '1 NO') return '1';
  if (g === '2 NO') return '2';
  if (g === '3 NO') return '3';
  if (g === '4 NO') return '4';
  return g;
}

export function normalizeContribution(raw) {
  if (!raw || typeof raw !== 'object') return null;
  try {
    const sourceKey = clean(raw.source_key);
    const recordDate = clean(raw.record_date);
    const grade = normalizeGrade(raw.grade);
    const kg = Number(raw.kg);
    const price = Number(raw.price_per_kg);
    const r = region(raw.district, raw.province);
    if (!sourceKey || sourceKey.length > 180) return null;
    if (!/^\d{4}-\d{2}-\d{2}$/.test(recordDate) || !Number.isFinite(Date.parse(recordDate+'T00:00:00.000Z'))) return null;
    if (!GRADES.includes(grade)) return null;
    if (!finite(kg) || !finite(price) || kg <= 0 || price <= 0 || kg > 1e9 || price > 1e7) return null;
    return {
      source_key: sourceKey,
      record_date: recordDate,
      record_day_ms: Date.parse(recordDate+'T00:00:00.000Z'),
      region_id: r.id,
      region_name: r.name,
      district: r.district,
      province: r.province,
      grade,
      kg,
      price_per_kg: price
    };
  } catch {
    return null;
  }
}

export function percentile(sorted, p) {
  if (!sorted.length) return 0;
  if (sorted.length === 1) return sorted[0];
  const x = (sorted.length - 1) * p;
  const lo = Math.floor(x), hi = Math.ceil(x);
  return sorted[lo] + (sorted[hi] - sorted[lo]) * (x - lo);
}

export function median(sorted) { return percentile(sorted, .5); }

export function filterOutliers(rows) {
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
  const label=score>=90?'Yüksek Güven':score>=75?'Güvenilir Veri':score>=50?'Orta Güven':score>=30?'Sınırlı Veri':'Çok Az Veri';
  return {score,label};
}

function observations(sources, grade, startMs, endMs) {
  const rows=[];
  for(const s of sources){
    if(!s || !finite(s.record_day_ms))continue;
    if(startMs!=null && s.record_day_ms<startMs)continue;
    if(endMs!=null && s.record_day_ms>=endMs)continue;
    if(s.grade !== grade || !finite(s.kg) || !finite(s.price_per_kg) || s.kg<=0 || s.price_per_kg<=0) continue;
    rows.push({uid:s.producer_hash,record_id:s.source_key,day:s.record_date,day_ms:s.record_day_ms,kg:s.kg,price:s.price_per_kg});
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

export function aggregateRows(rows, nowMs=Date.now(), includeChart=true) {
  const producerRows=collapseByProducer(rows);
  if(producerRows.length<MIN_PRODUCERS)return {available:false};
  const filtered=filterOutliers(producerRows), acceptedProducers=filtered.accepted;
  if(acceptedProducers.length<MIN_PRODUCERS)return {available:false};
  const acceptedUids=new Set(acceptedProducers.map(r=>r.uid));
  const acceptedRaw=rows.filter(r=>acceptedUids.has(r.uid));
  const prices=acceptedProducers.map(r=>r.price).sort((a,b)=>a-b);
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

export function periodBounds(periodKey, nowMs=Date.now()) {
  const period=PERIODS.find(p=>p.key===periodKey)||PERIODS[1];
  if(period.days<=0)return {start:null,end:nowMs+DAY_MS,previousStart:null,previousEnd:null,days:0};
  const end=nowMs+DAY_MS, start=nowMs-period.days*DAY_MS;
  return {start,end,previousStart:start-period.days*DAY_MS,previousEnd:start,days:period.days};
}

export function buildSelectedMarket(sources, grade, periodKey, nowMs=Date.now()) {
  const b=periodBounds(periodKey,nowMs);
  const current=aggregateRows(observations(sources,grade,b.start,b.end),nowMs,true);
  if(!current.available)return {available:false};
  let previous={available:false};
  if(b.days>0)previous=aggregateRows(observations(sources,grade,b.previousStart,b.previousEnd),nowMs,false);
  const previousAvailable=!!previous.available;
  const previousMedian=previousAvailable?previous.median:0;
  const changeAbs=previousAvailable?roundHalf(current.median-previousMedian):0;
  const changePct=previousAvailable&&previousMedian>0?((current.median-previousMedian)/previousMedian)*100:0;
  return {
    ...current,
    previous_available:previousAvailable,
    previous_median:previousMedian,
    previous_record_count:previousAvailable?previous.record_count:0,
    change_abs:changeAbs,
    change_pct:Math.round(changePct*100)/100,
    updated_at_ms:nowMs
  };
}
