'use strict';

const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '..');
const readJson = file => JSON.parse(fs.readFileSync(path.join(root, file), 'utf8'));
const fail = message => { throw new Error(message); };
const ok = message => console.log('✓ ' + message);

const google = readJson('app/google-services.json');
const firebaserc = readJson('.firebaserc');
const firebase = readJson('firebase.json');
const pkg = readJson('functions/package.json');
const indexes = readJson('firestore.indexes.json');
const rules = fs.readFileSync(path.join(root, 'firestore.rules'), 'utf8');
const indexJs = fs.readFileSync(path.join(root, 'functions/index.js'), 'utf8');
const marketFunctions = fs.readFileSync(path.join(root, 'functions/market/functions.js'), 'utf8');

const appProject = google?.project_info?.project_id;
const deployProject = firebaserc?.projects?.default;
if (!appProject || appProject !== 'zeytin-defteri') fail('google-services.json beklenen Firebase projesi değil.');
if (deployProject !== appProject) fail(`Deploy projesi (${deployProject}) Android projesiyle (${appProject}) eşleşmiyor.`);
ok(`Firebase hedefi kilitli: ${appProject}`);

if (firebase?.firestore?.rules !== 'firestore.rules') fail('Firestore rules yolu beklenen değer değil.');
if (firebase?.firestore?.indexes !== 'firestore.indexes.json') fail('Firestore indexes yolu eksik/yanlış.');
if (firebase?.functions?.source !== 'functions') fail('Functions source yolu yanlış.');
if (firebase?.functions?.runtime !== 'nodejs22') fail('Functions runtime nodejs22 değil.');
ok('firebase.json rules + indexes + functions tanımları doğru.');

if (pkg?.engines?.node !== '22') fail('functions/package.json Node 22 hedeflemiyor.');
ok('Cloud Functions Node 22 hedefliyor.');

const requiredExports = [
  'zeytinMarketElekWritten',
  'zeytinMarketConsentWritten',
  'zeytinMarketGardenWritten',
  'zeytinMarketBackfill',
  'zeytinMarketPublish',
  'zeytinMarketRefreshWindows'
];
if (!indexJs.includes("require('./market/functions')")) fail('Market functions index.js içine bağlı değil.');
for (const name of requiredExports) {
  if (!marketFunctions.includes(`exports.${name}=`) && !marketFunctions.includes(`exports.${name} =`)) {
    fail(`Eksik market function export: ${name}`);
  }
}
ok('6 anonim Piyasa Cloud Function exportu hazır.');

for (const needle of [
  'match /zeytin_markets/{season}',
  'match /zeytin_market_stats/{statId}',
  'allow write: if false;'
]) {
  if (!rules.includes(needle)) fail(`Firestore rules içinde beklenen kural yok: ${needle}`);
}
ok('Anonim piyasa public çıktıları istemci için salt-okunur.');

const indexStrings = (indexes.indexes || []).map(x => JSON.stringify(x));
if (!indexStrings.some(x => x.includes('zeytin_market_stats') && x.includes('total_kg'))) {
  fail('zeytin_market_stats bölge karşılaştırma indeksi eksik.');
}
if (!indexStrings.some(x => x.includes('market_private_sources') && x.includes('region_id'))) {
  fail('market_private_sources season/region indeksi eksik.');
}
ok('Gerekli Piyasa Firestore indeksleri tanımlı.');

const predeploy = firebase?.functions?.predeploy || [];
if (!Array.isArray(predeploy) || !predeploy.some(x => x.includes('run check')) || !predeploy.some(x => x.includes('test'))) {
  fail('Functions predeploy release gate eksik.');
}
ok('Deploy öncesi syntax + test kapısı aktif.');

console.log('\nPIYASA V5.1 DEPLOY PREFLIGHT: PASS');
