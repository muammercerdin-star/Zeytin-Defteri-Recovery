import assert from 'node:assert/strict';
import { Operations, decimal } from '../../web/tezgah/js/operations.js';
import { TezgahPage } from '../../web/tezgah/app.js';
let checks = 0;
function check(value, message) {
  assert.ok(value, message);
  checks++;
}
function equal(actual, expected, message) {
  assert.deepEqual(actual, expected, message);
  checks++;
}
export class Element {
  constructor(tag) {
    this.tag = tag;
    this.children = [];
    this.events = {};
    this.value = '';
    this.hidden = false;
    this.disabled = false;
    this.textContent = '';
    this.classList = { toggle() {} };
  }
  append(child) {
    child.parent = this;
    this.children.push(child);
    if (this.tag === 'select' && this.children.length === 1) this.value = child.value;
  }
  insertBefore(child, before) {
    child.parent = this;
    this.children.splice(this.children.indexOf(before), 0, child);
  }
  replaceChildren() {
    this.children = [];
  }
  remove() {
    this.parent.children = this.parent.children.filter(child => child !== this);
  }
  setAttribute(name, value) {
    this[name] = value;
  }
  addEventListener(name, fn) {
    this.events[name] = fn;
  }
  focus() {
    this.focused = true;
  }
  set innerHTML(value) {
    throw new Error('HTML injection: ' + value);
  }
}
function walk(node) {
  return [node, ...node.children.flatMap(walk)];
}
const pause = () => new Promise(resolve => setImmediate(resolve));
equal(decimal('1.140,5', 1), '1140.5', 'Turkish decimal');
equal(decimal('1.234.567,89'), '1234567.89', 'Turkish grouping');
equal(decimal('0'), '0', 'zero');
for (const invalid of ['1.14', '-1', 'NaN', 'Infinity', '1e6', '<img>', '1,234', '100000001', '1..140']) {
  assert.throws(() => decimal(invalid));
  checks++;
}
const root = new Element('section');
const document = { getElementById: () => root, createElement: tag => new Element(tag) };
const sent = [];
const reads = [];
const malicious = '<img src=x onerror=alert(1)>';
const app = {
  full: true,
  commands: new Map(),
  canWrite() { return this.full; },
  async command(tur, alanlar) {
    const id = String(sent.length + 1);
    sent.push({ id, tur, alanlar });
    this.commands.set(id, {});
    return id;
  },
  async operation(op, args) {
    reads.push({ op, args });
    if (op === 'web_meta') return { tarih: '2026-11-03' };
    if (op === 'ureticiler') return { items: [{ ad: malicious }] };
    if (op === 'alim_detay') return { id: '9', surum: 'fingerprint', uretici: malicious, tarih: '2020-01-01', odenenTL: '20.00', model: { dara: '2.0', fiyatlar: ['10', '0', '0', '0', '0', '0'], tartimlar: [{ elek: 0, kasa: 1, kg: '22.0' }] } };
    if (op === 'aksam_hesabi') return { teslim: ['1', '0', '0', '0', '0', '0'], komisyon: '1', avans: '0', surum: 'closing', tuccar: '100' };
    if (op === 'cari') return { items: [{ id: '1', kisi: malicious, yon: 2, tutar: '100', tarih: '2026-11-03', not: malicious, surum: 'version' }], complete: true, nextCursor: null, alacak: '100', borc: '0' };
    return { items: [], complete: true, nextCursor: null, count: 0 };
  }
};
const ui = new Operations(document, app);
ui.state({ approved: true, writable: true, stale: false, data: {} });
await pause();
check(!ui.writeFields.hidden, 'full permission reveals forms');
equal(ui.date.value, '2026-11-03', 'phone calendar day');
const sections = () => walk(root).filter(node => node.tag === 'details');
const section = title => sections().find(node => node.children[0].textContent === title);
const inputs = node => walk(node).filter(item => item.tag === 'input');
async function submit(node) {
  const form = walk(node).find(item => item.tag === 'form');
  let prevented = false;
  form.events.submit({ preventDefault() { prevented = true; } });
  await pause();
  check(prevented, 'native form submit / Enter is intercepted');
}
function complete() {
  const last = sent.at(-1);
  app.commands.delete(last.id);
  app.onCommand({ id: last.id, durum: 'tamam', ozet: { mesaj: malicious } });
  equal(ui.message.textContent, malicious, 'phone result remains literal text');
}
const producer = section('Yeni üretici');
inputs(producer)[0].value = malicious;
inputs(producer)[1].value = '555';
await submit(producer);
equal(sent.at(-1).tur, 'uretici_ekle', 'producer form sends command');
complete();
ui.seller.value = malicious;
await ui.searchProducers();
equal(ui.producers.children[0].textContent, malicious, 'producer suggestion no HTML');
ui.prices[0].value = '12,50';
ui.weighings[0].kg.value = '1.140,5';
await submit(ui.purchaseSection);
equal(sent.at(-1).alanlar.model.tartimlar[0].kg, '1140.5', 'purchase number normalized');
equal(sent.at(-1).alanlar.model.fiyatlar[0], '12.50', 'price sent as text');
check(!('tutar' in sent.at(-1).alanlar), 'browser never sends trusted total');
complete();
await ui.rowAction('edit', { id: '9' });
check(ui.paid.disabled, 'edit preserves paid amount');
await submit(ui.purchaseSection);
equal(sent.at(-1).tur, 'alim_duzenle', 'row edit');
equal(sent.at(-1).alanlar.surum, 'fingerprint', 'edit optimistic version');
app.onCommand({ id: sent.at(-1).id, durum: 'onay_bekleniyor', ozet: {} });
check(ui.message.textContent.includes('Telefonda onay bekleniyor'), 'approval status');
complete();
await ui.rowAction('delete', { id: '9' });
equal(sent.at(-1).tur, 'alim_sil', 'delete is phone command');
complete();
await ui.rowAction('pay', { id: '9', kalanTL: '120.50' });
await submit(ui.paymentSection);
equal(sent.at(-1).alanlar, { alimId: '9', tutar: '120.50' }, 'payment form');
complete();
for (const title of ['Toplu ödeme · telefonda onay', 'Para kasası / cari tahsilat ve ödeme', 'Cari satış', 'Fiziksel kasa giriş / çıkış', 'Akşam hesabı · teslim ve komisyon', 'Kapanmış Z’yi cari hesaba işle']) {
  check(!!section(title), title + ' form exists');
}
// Diğer günlük formların gerçek submit alanları.
const bulk = section('Toplu ödeme · telefonda onay');
inputs(bulk)[0].value = 'Ahmet';
inputs(bulk)[1].value = '1.234,50';
await submit(bulk);
equal(sent.at(-1).alanlar, { uretici: 'Ahmet', tutar: '1234.50' }, 'bulk payment fields');
complete();
const cash = section('Para kasası / cari tahsilat ve ödeme');
inputs(cash)[0].value = 'Tüccar';
inputs(cash)[1].value = '5,25';
inputs(cash)[2].value = malicious;
for (const type of ['cari_tahsilat', 'cari_odeme', 'kasa_giris', 'kasa_cikis']) {
  walk(cash).find(node => node.tag === 'select').value = type;
  await submit(cash);
  equal(sent.at(-1).tur, type, 'cash direction');
  equal(sent.at(-1).alanlar.tutar, '5.25', 'cash number');
  complete();
}
const sale = section('Cari satış');
inputs(sale)[0].value = 'Alıcı';
inputs(sale)[1].value = '100,5';
inputs(sale)[2].value = '10';
await submit(sale);
equal(sent.at(-1).tur, 'cari_satis', 'sale command');
equal(sent.at(-1).alanlar.kg, '100.5', 'sale kg');
check(!('tutar' in sent.at(-1).alanlar), 'sale total comes from phone');
complete();
const crates = section('Fiziksel kasa giriş / çıkış');
inputs(crates)[0].value = 'Aracı';
inputs(crates)[1].value = '12';
await submit(crates);
equal(sent.at(-1).alanlar.adet, 12, 'physical crates count integer');
complete();
const closing = section('Akşam hesabı · teslim ve komisyon');
walk(closing).find(node => node.textContent === 'Telefondaki hesabı getir').events.click();
await pause();
await submit(closing);
equal(sent.at(-1).tur, 'aksam_hesabi', 'closing form');
equal(sent.at(-1).alanlar.surum, 'closing', 'closing version from phone');
complete();
const z = section('Kapanmış Z’yi cari hesaba işle');
inputs(z)[0].value = '1';
inputs(z)[1].value = 'Tüccar';
await submit(z);
equal(sent.at(-1).tur, 'z_cariye_isle', 'Z ledger form');
complete();
ui.reportType.value = 'cari';
await ui.loadReport();
check(walk(ui.reportTable).some(node => node.tag === 'td' && node.textContent === malicious), 'ledger XSS literal');
check(walk(ui.reportTable).some(node => node.textContent === 'Tahsilat'), 'ledger enum DTO mapped');
ui.state({ approved: false, writable: false, stale: true, data: {} });
check(ui.writeFields.hidden && ui.writeFields.disabled, 'offline forms hidden and disabled');
app.full = false;
ui.state({ approved: true, writable: false, stale: false, data: {} });
check(ui.writeFields.hidden, 'resume cannot restore write permission');
check(!walk(root).some(node => node.textContent === malicious || node.value === malicious), 'downgrade clears old forms and actions');
const before = sent.length;
await submit(section('Yeni üretici'));
equal(sent.length, before, 'readonly form cannot dispatch even forced submit');
app.onWipe();
check(root.hidden, 'idle/stop clears entire operations panel');
check(!walk(root).some(node => node.value === malicious), 'idle removes sensitive input');
// Gerçek sayfa motorunun komut kuyruğu: belirsiz sonuç aynı id ile tekrar edilir.
const page = new TezgahPage();
page.approved = true;
page.permissions = ['tezgah.read', 'tezgah.write'];
page.channel = { close() {} };
const frames = [];
page.encrypted = async payload => { frames.push(payload); };
const id = await page.command('odeme', { alimId: '1', tutar: '5' });
check(/^[\w-]{22}$/.test(id), 'random 128-bit command id');
page.operation = async () => ({ bulundu: false });
page.commands.get(id).checkAt = 0;
page.reconcile();
await pause();
equal(frames.length, 2, 'unknown result retransmitted');
equal(frames[0].id, frames[1].id, 'retransmission retains original id');
page.commandResult({ id, durum: 'tamam', ozet: {} });
equal(page.commands.size, 0, 'completed request removed from RAM');
page.permissions = ['tezgah.read'];
await assert.rejects(page.command('odeme', {}));
checks++;
page.stop();
console.log(`PASS ${checks} web operations / forms / permissions / XSS checks`);
