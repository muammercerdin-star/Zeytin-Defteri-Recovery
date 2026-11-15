// Bütün kayıtlar telefona gider. Bu modülde kalıcı saklama veya tutar otoritesi yoktur.
const grades = ['Double', '1 numara', '2 numara', '3 numara', '4 numara', 'Yağlık'];
const labels = {
  alim_defteri: 'Alım defteri / ekstre',
  cari: 'Cari hesap',
  kasalar: 'Kasa adetleri',
  aksam_hesabi: 'Akşam hesabı',
  z_listesi: 'Z listesi'
};

export function decimal(value, scale = 2) {
  const text = String(value).trim();
  if (!/^(?:\d+|\d{1,3}(?:\.\d{3})+)(?:,\d+)?$/.test(text)) {
    throw new Error('Sayıyı Türkçe yazın. Örnek: 1.140,5');
  }
  const normalized = text.replaceAll('.', '').replace(',', '.');
  const parts = normalized.split('.');
  if ((parts[1]?.length ?? 0) > scale || Number(normalized) > 100000000) {
    throw new Error('Sayı sınırını veya ondalık basamaklarını kontrol edin.');
  }
  return normalized;
}

// Boş bırakılan isteğe bağlı sayı kutusu 0 sayılır; tezgâhçı önce "0" silmek zorunda kalmaz.
function numberOrZero(input, scale = 2) {
  return decimal(input.value.trim() || '0', scale);
}

function guess(input, scale = 2) {
  try {
    return Number(numberOrZero(input, scale));
  } catch {
    return NaN;
  }
}

const round1 = value => Math.round(value * 10) / 10;

/** Telefondaki TezgahModel ile aynı formül; yalnız önizleme, tutar otoritesi telefondur. */
export function estimate(tare, prices, rows) {
  const net = prices.map(() => 0);
  for (const row of rows) {
    net[row.elek] += Math.max(0, row.kg - Math.max(0, row.kasa) * Math.max(0, tare));
  }
  const gradeNet = net.map(round1);
  const total = gradeNet.reduce((sum, value, index) => sum + value * prices[index], 0);
  return { net: round1(gradeNet.reduce((a, b) => a + b, 0)), total: Math.round(total * 100) / 100, gradeNet };
}

function localized(value) {
  return String(value ?? '').replace('.', ',');
}

function amount(value, unit = '') {
  return new Intl.NumberFormat('tr-TR', { maximumFractionDigits: 2 }).format(Number(value)) + unit;
}

export class Operations {
  constructor(document, app) {
    this.document = document;
    this.app = app;
    this.root = document.getElementById('operations');
    this.epoch = 0;
    this.full = false;
    this.online = false;
    this.build();
    app.onCommand = result => this.result(result);
    app.onWipe = () => this.clear();
    app.onChange = () => {
      this.reportDirty = true;
    };
  }

  element(tag, text = '', className = '') {
    const node = this.document.createElement(tag);
    node.textContent = text;
    node.className = className;
    return node;
  }

  button(parent, text, action, className = 'quiet') {
    const button = this.element('button', text, className);
    button.type = 'button';
    button.addEventListener('click', () => this.safe(action));
    parent.append(button);
    return button;
  }

  input(parent, label, value = '', numeric = false, hint = '') {
    const wrapper = this.element('label', label);
    const input = this.element('input');
    input.value = value;
    if (hint) input.placeholder = hint;
    input.autocomplete = 'off';
    input.maxLength = numeric ? 24 : 100;
    if (numeric) input.inputMode = 'decimal';
    wrapper.append(input);
    parent.append(wrapper);
    return input;
  }

  select(parent, label, choices) {
    const wrapper = this.element('label', label);
    const select = this.element('select');
    for (const [value, text] of choices) {
      const option = this.element('option', text);
      option.value = value;
      select.append(option);
    }
    wrapper.append(select);
    parent.append(wrapper);
    return select;
  }

  form(title, action, host = this.writeFields) {
    const details = this.element('details', '', 'card operation');
    const heading = this.element('summary', title);
    details.append(heading);
    const form = this.element('form', '', 'operation-form');
    const fields = this.element('div', '', 'form-grid');
    form.append(fields);
    const submit = this.element('button', 'Telefona gönder', 'primary');
    submit.type = 'submit';
    form.append(submit);
    form.addEventListener('submit', event => {
      event.preventDefault();
      this.safe(async () => {
        if (!this.app.canWrite() || this.app.commands.size) throw new Error('Yetkiyi ve bekleyen işlemi kontrol edin.');
        await action();
      });
    });
    details.append(form);
    host.append(details);
    return { fields, details, form, submit };
  }

  say(message) {
    this.message.textContent = message;
  }

  async safe(action) {
    const epoch = this.epoch;
    try {
      await action();
    } catch (error) {
      if (epoch === this.epoch) this.say(error.message || 'İşlem tamamlanamadı.');
    }
  }

  async send(type, fields) {
    this.say('Telefon işlemi doğruluyor…');
    const id = await this.app.command(type, fields);
    this.lock(true);
    return id;
  }

  result(result) {
    const waiting = result.durum === 'onay_bekleniyor';
    const message = waiting ? 'Telefonda onay bekleniyor · 60 saniye içinde onaylayın.' :
      result.hata || result.ozet?.mesaj || result.durum;
    this.say(message);
    this.lock(!this.online || !!this.app.commands.size);
    if (result.durum === 'tamam') {
      this.reportDirty = true;
      if (result.id === this.purchaseCommand) {
        this.resetPurchase();
        this.seller.focus();
      }
    }
  }

  lock(disabled) {
    this.writeFields.disabled = disabled;
    if (this.quickFields) this.quickFields.disabled = disabled;
  }

  build() {
    this.root.replaceChildren();
    this.root.hidden = true;
    // Alım girişi, ayrı bir yer varsa tablonun yanında hep açık durur.
    const quick = this.document.getElementById('quick-entry');
    this.quick = quick && quick !== this.root ? quick : null;
    this.workspace = this.quick ? this.document.getElementById('workspace') : null;
    if (this.quick) {
      this.quick.replaceChildren();
      this.quick.hidden = true;
      this.workspace?.classList.add('solo');
      this.quickFields = this.element('fieldset', '', 'write-fields');
      this.quickFields.hidden = true;
      this.quickFields.disabled = true;
      this.quick.append(this.quickFields);
    } else {
      this.quickFields = null;
    }
    this.root.append(this.element('h2', 'Tezgâh işlemleri'));
    this.permission = this.element('p', 'Yalnız görüntüleme', 'permission-label');
    this.root.append(this.permission);
    this.message = this.element('p', '', 'message');
    this.message.setAttribute('role', 'status');
    this.message.setAttribute('aria-live', 'polite');
    this.root.append(this.message);
    this.writeFields = this.element('fieldset', '', 'write-fields');
    this.writeFields.hidden = true;
    this.writeFields.disabled = true;
    this.root.append(this.writeFields);
    this.buildProducer();
    this.buildPurchase();
    this.buildPayment();
    this.buildCash();
    this.buildSale();
    this.buildCrates();
    this.buildClosing();
    const maintenance = this.element('div', '', 'maintenance');
    this.writeFields.append(maintenance);
    this.button(maintenance, 'Z / dönem kapat · telefonda onay', () => this.send('z_kapat', {}));
    this.button(maintenance, 'Telefonda bulut yedeği al', () => this.send('yedek_al', {}));
    maintenance.append(this.element('p', 'Geri yükleme yalnız telefondan yapılır. Para hareketleri cari hesaba, fiziksel kasa adetleri kasa defterine yazılır.', 'footnote'));
    this.buildReport();
  }

  buildProducer() {
    const section = this.form('Yeni üretici', () => this.send('uretici_ekle', { ad: name.value.trim(), telefon: phone.value.trim() }));
    const name = this.input(section.fields, 'Üretici adı');
    name.required = true;
    const phone = this.input(section.fields, 'Telefon (isteğe bağlı)');
    phone.maxLength = 32;
  }

  buildPurchase() {
    const host = this.quickFields || this.writeFields;
    const section = this.form('Yeni alım', async () => {
      const model = {
        dara: decimal(this.tare.value, 1),
        fiyatlar: this.prices.map(input => numberOrZero(input)),
        tartimlar: this.weighings.map(row => ({ elek: Number(row.grade.value), kasa: Number(numberOrZero(row.crates, 0)), kg: decimal(row.kg.value, 1) }))
      };
      const fields = { uretici: this.seller.value.trim(), tarih: this.date.value, model };
      if (this.editing) {
        fields.alimId = this.editing.id;
        fields.surum = this.editing.surum;
      } else {
        fields.odenen = numberOrZero(this.paid);
      }
      this.purchaseCommand = await this.send(this.editing ? 'alim_duzenle' : 'alim_ekle', fields);
    }, host);
    this.purchaseSection = section.details;
    this.purchaseSection.open = true;
    this.purchaseTitle = section.details.children[0];
    section.details.className = 'card operation quick';

    // Günün fiyatları: sabah bir kez girilir, her alımda korunur (bu oturumun belleğinde).
    this.priceBox = this.element('details', '', 'day-prices');
    this.priceSummary = this.element('summary', 'Günün fiyatları');
    this.priceBox.append(this.priceSummary);
    const priceGrid = this.element('div', '', 'price-grid');
    this.priceBox.append(priceGrid);
    this.prices = grades.map(grade => this.input(priceGrid, grade + ' ₺/kg', '', true, '0'));
    this.tare = this.input(priceGrid, 'Kasa darası kg', '2', true);
    section.form.insertBefore(this.priceBox, section.fields);

    this.seller = this.input(section.fields, 'Üretici', '', false, 'Adını yazın, listeden seçin');
    this.seller.required = true;
    this.seller.setAttribute('list', 'web-producers');
    this.producers = this.element('datalist');
    this.producers.id = 'web-producers';
    section.fields.append(this.producers);
    this.seller.addEventListener('input', () => {
      clearTimeout(this.searchTimer);
      this.searchTimer = setTimeout(() => this.safe(() => this.searchProducers()), 350);
    });
    this.seller.addEventListener('focus', () => this.safe(() => this.searchProducers()));

    this.weighingBox = this.element('div', '', 'weighings');
    section.form.insertBefore(this.weighingBox, section.submit);
    this.weighings = [];
    this.addWeighing();
    const rowTools = this.element('div', '', 'row-tools');
    section.form.insertBefore(rowTools, section.submit);
    this.button(rowTools, '+ Tartım satırı', () => {
      const row = this.addWeighing();
      row.grade.focus();
    });

    this.preview = this.element('div', '', 'preview');
    this.previewMain = this.element('strong', '');
    this.previewNote = this.element('small', '');
    this.preview.append(this.previewMain, this.previewNote);
    this.preview.setAttribute('aria-live', 'polite');
    section.form.insertBefore(this.preview, section.submit);

    const extra = this.element('div', '', 'form-grid compact');
    section.form.insertBefore(extra, section.submit);
    this.paid = this.input(extra, 'Şimdi ödenen ₺', '', true, '0');
    this.date = this.input(extra, 'Alım günü');
    this.date.type = 'date';
    this.date.required = true;

    section.submit.className = 'primary send';
    const after = this.element('div', '', 'after-send');
    section.form.append(after);
    this.cancelEdit = this.button(after, 'Düzenlemeyi bırak', () => this.resetPurchase());
    this.cancelEdit.hidden = true;
    after.append(this.element('p', 'Enter ile sonraki kutuya geçin. Kesin tutarı telefon hesaplar ve kaydeder.', 'hint'));

    this.purchaseForm = section.form;
    for (const input of [...this.prices, this.tare]) {
      input.addEventListener('input', () => this.updatePreview());
    }
    this.paid.addEventListener('keydown', event => this.enter(event, null));
    this.seller.addEventListener('keydown', event => this.enter(event, () => this.weighings[0].grade));
    this.updatePreview();
  }

  /** Enter: bir sonraki kutu. Son kutuda (Şimdi ödenen) form gönderilir. */
  enter(event, next) {
    if (event.key !== 'Enter' || event.isComposing) return;
    event.preventDefault();
    const target = next?.();
    if (target) {
      target.focus();
      target.select?.();
    } else {
      this.purchaseForm.requestSubmit?.();
    }
  }

  updatePreview() {
    if (!this.previewMain) return;
    const filled = this.prices.filter(input => guess(input) > 0).length;
    const parts = this.prices.map((input, index) => {
      const value = guess(input);
      return value > 0 ? grades[index].replace(' numara', ' no') + ' ' + amount(value, ' ₺') : null;
    }).filter(Boolean);
    this.priceSummary.textContent = filled ? 'Günün fiyatları · ' + parts.join(' · ') : 'Günün fiyatları · önce fiyatları girin';
    if (!filled) this.priceBox.open = true;
    const tare = guess(this.tare, 1);
    const prices = this.prices.map(input => guess(input));
    const rows = this.weighings.map(row => ({ elek: Number(row.grade.value), kasa: guess(row.crates, 0), kg: row.kg.value.trim() ? guess(row.kg, 1) : 0 }));
    if ([tare, ...prices].some(Number.isNaN) || rows.some(row => Number.isNaN(row.kasa) || Number.isNaN(row.kg))) {
      this.previewMain.textContent = 'Sayıyı kontrol edin';
      this.previewNote.textContent = 'Türkçe yazın. Örnek: 1.140,5';
      return;
    }
    const result = estimate(tare, prices, rows);
    this.previewMain.textContent = 'Net ' + amount(result.net, ' kg') + ' · ' + amount(result.total, ' ₺');
    const missing = rows.filter(row => row.kg > 0 && !(prices[row.elek] > 0)).map(row => grades[row.elek]);
    const heavy = rows.some(row => row.kg > 0 && row.kasa * tare >= row.kg);
    this.previewNote.textContent = missing.length ? [...new Set(missing)].join(', ') + ' fiyatı girilmedi' :
      heavy ? 'Kasa darası brüt kilodan fazla, kontrol edin' : 'Tahmini · kesin hesabı telefon yapar';
    this.preview.classList.toggle('warn', missing.length > 0 || heavy);
  }

  async searchProducers() {
    if (!this.app.canWrite()) return;
    const epoch = this.epoch;
    const q = this.seller.value.trim();
    const result = await this.app.operation('ureticiler', { q });
    if (epoch !== this.epoch || q !== this.seller.value.trim()) return;
    this.producers.replaceChildren();
    for (const producer of result.items) {
      const option = this.element('option', producer.ad);
      option.value = producer.ad;
      this.producers.append(option);
    }
  }

  addWeighing(value = { elek: 0, kasa: 0, kg: '' }) {
    if (this.weighings.length >= 100) throw new Error('En çok 100 tartım eklenebilir.');
    const box = this.element('div', '', 'weighing');
    const grade = this.select(box, 'Elek', grades.map((name, index) => [String(index), name]));
    grade.value = String(value.elek);
    const crates = this.input(box, 'Kasa', value.kasa ? String(value.kasa) : '', true, '0');
    const kg = this.input(box, 'Brüt kg', localized(value.kg), true);
    kg.required = true;
    const row = { grade, crates, kg, box };
    this.weighings.push(row);
    const remove = this.button(box, '×', () => {
      if (this.weighings.length === 1) throw new Error('En az bir tartım gerekli.');
      this.weighings = this.weighings.filter(item => item !== row);
      box.remove();
      this.updatePreview();
    }, 'quiet remove');
    remove.setAttribute('aria-label', 'Tartım satırını çıkar');
    remove.title = 'Satırı çıkar';
    const following = () => {
      const index = this.weighings.indexOf(row);
      return this.weighings[index + 1]?.grade || this.paid;
    };
    grade.addEventListener('keydown', event => this.enter(event, () => crates));
    crates.addEventListener('keydown', event => this.enter(event, () => kg));
    kg.addEventListener('keydown', event => this.enter(event, following));
    for (const input of [crates, kg]) input.addEventListener('input', () => this.updatePreview());
    grade.addEventListener('change', () => this.updatePreview());
    this.weighingBox.append(box);
    this.updatePreview();
    return row;
  }

  resetPurchase() {
    this.editing = null;
    this.purchaseCommand = null;
    this.seller.value = '';
    this.paid.value = '';
    this.paid.disabled = false;
    this.date.value = this.today || '';
    this.purchaseTitle.textContent = 'Yeni alım';
    this.cancelEdit.hidden = true;
    this.weighingBox.replaceChildren();
    this.weighings = [];
    this.addWeighing();
  }

  async rowAction(type, item) {
    return this.safe(async () => {
      if (!this.app.canWrite()) throw new Error('Tam kullanım yetkisi gerekli.');
      if (type === 'pay') {
        this.payId.value = item.id;
        this.payAmount.value = localized(item.kalanTL);
        this.paymentSection.open = true;
        this.payAmount.focus();
        return;
      }
      const epoch = this.epoch;
      const detail = await this.app.operation('alim_detay', { alimId: item.id });
      if (epoch !== this.epoch || !this.app.canWrite()) return;
      if (type === 'delete') {
        await this.send('alim_sil', { alimId: item.id, surum: detail.surum });
        return;
      }
      this.editing = detail;
      this.seller.value = detail.uretici;
      this.date.value = detail.tarih;
      this.tare.value = localized(detail.model.dara);
      this.paid.value = localized(detail.odenenTL);
      this.paid.disabled = true;
      detail.model.fiyatlar.forEach((value, index) => { this.prices[index].value = localized(value); });
      this.weighingBox.replaceChildren();
      this.weighings = [];
      for (const row of detail.model.tartimlar) this.addWeighing(row);
      this.purchaseSection.open = true;
      this.purchaseTitle.textContent = 'Alım #' + item.id + ' düzenleniyor';
      this.cancelEdit.hidden = false;
      this.updatePreview();
      this.seller.focus();
      this.say('Alım #' + item.id + ' düzenleniyor. Mevcut ödemeler korunur; geçmiş tarih için telefon onayı gerekir.');
    });
  }

  buildPayment() {
    const section = this.form('Üretici ödemesi', () => this.send('odeme', { alimId: this.payId.value.trim(), tutar: decimal(this.payAmount.value) }));
    this.paymentSection = section.details;
    this.payId = this.input(section.fields, 'Alım numarası (satırdaki Öde düğmesi doldurur)');
    this.payAmount = this.input(section.fields, 'Ödeme ₺', '', true);
    const bulk = this.form('Toplu ödeme · telefonda onay', () => this.send('toplu_odeme', { uretici: seller.value.trim(), tutar: decimal(total.value) }));
    const seller = this.input(bulk.fields, 'Üretici adı');
    const total = this.input(bulk.fields, 'Toplam ödeme ₺', '', true);
  }

  buildCash() {
    const section = this.form('Para kasası / cari tahsilat ve ödeme', () => this.send(type.value, { kisi: party.value.trim(), tutar: decimal(total.value), not: note.value.trim() }));
    const type = this.select(section.fields, 'İşlem', [['cari_tahsilat', 'Cari tahsilat'], ['cari_odeme', 'Cari ödeme'], ['kasa_giris', 'Para kasası giriş'], ['kasa_cikis', 'Para kasası çıkış']]);
    const party = this.input(section.fields, 'Kişi / tüccar');
    const total = this.input(section.fields, 'Tutar ₺', '', true);
    const note = this.input(section.fields, 'Not (isteğe bağlı)');
    note.maxLength = 500;
  }

  buildSale() {
    const section = this.form('Cari satış', () => {
      const fields = { kisi: party.value.trim(), yontem: Number(method.value), alinan: decimal(received.value), not: note.value.trim() };
      if (fields.yontem === 1) {
        fields.kilolar = weights.map(input => decimal(input.value, 1));
        fields.fiyatlar = prices.map(input => decimal(input.value));
      } else {
        fields.kg = decimal(weight.value, 1);
        fields.fiyat = decimal(price.value);
      }
      return this.send('cari_satis', fields);
    });
    const party = this.input(section.fields, 'Alıcı');
    const method = this.select(section.fields, 'Hesaplama', [['2', 'Toplam kilo × fiyat'], ['1', 'Elek elek satış'], ['3', 'Komisyon: kilo × ücret']]);
    const weight = this.input(section.fields, 'Toplam kg (toplam / komisyon)', '0', true);
    const price = this.input(section.fields, 'Birim fiyat / komisyon ₺', '0', true);
    const weights = grades.map(grade => this.input(section.fields, grade + ' kg (elek yöntemi)', '0', true));
    const prices = grades.map(grade => this.input(section.fields, grade + ' fiyat ₺ (elek yöntemi)', '0', true));
    const received = this.input(section.fields, 'Şimdi tahsil edilen ₺', '0', true);
    const note = this.input(section.fields, 'Not');
    note.maxLength = 500;
  }

  buildCrates() {
    const section = this.form('Fiziksel kasa giriş / çıkış', () => this.send(type.value, { kisi: party.value.trim(), adet: Number(decimal(count.value, 0)), not: note.value.trim() }));
    const type = this.select(section.fields, 'Yön', [['kasa_adet_giris', 'Gelen kasa'], ['kasa_adet_cikis', 'Giden kasa']]);
    const party = this.input(section.fields, 'Aracı');
    const count = this.input(section.fields, 'Kasa adedi', '', true);
    const note = this.input(section.fields, 'Not');
    note.maxLength = 500;
  }

  buildClosing() {
    const section = this.form('Akşam hesabı · teslim ve komisyon', () => {
      if (!this.closingVersion) throw new Error('Önce telefondaki hesabı getir düğmesine basın.');
      return this.send('aksam_hesabi', { teslim: delivered.map(input => decimal(input.value, 1)),
        komisyon: decimal(rate.value), avans: decimal(advance.value), surum: this.closingVersion });
    });
    const delivered = grades.map(grade => this.input(section.fields, grade + ' teslim kg', '0', true));
    const rate = this.input(section.fields, 'Kilo başı komisyon ₺', '0', true);
    const advance = this.input(section.fields, 'Alınan avans ₺', '0', true);
    this.button(section.form, 'Telefondaki hesabı getir', async () => {
      const epoch = this.epoch;
      const data = await this.app.operation('aksam_hesabi');
      if (epoch !== this.epoch) return;
      this.closingVersion = data.surum;
      data.teslim.forEach((value, index) => { delivered[index].value = localized(value); });
      rate.value = localized(data.komisyon);
      advance.value = localized(data.avans);
      this.say('Akşam hesabı getirildi. Kaydettiğinizde telefon yeniden kontrol eder.');
    });
    const transfer = this.form('Kapanmış Z’yi cari hesaba işle', () => this.send('z_cariye_isle', { zId: id.value.trim(), kisi: party.value.trim(), alinan: decimal(received.value) }));
    const id = this.input(transfer.fields, 'Z kayıt numarası (Z listesinden)');
    const party = this.input(transfer.fields, 'Tüccar');
    const received = this.input(transfer.fields, 'Tahsil edilen / iade edilen ₺', '0', true);
  }

  buildReport() {
    const section = this.element('section', '', 'card operation');
    section.append(this.element('h3', 'Defterler ve hesaplar'));
    const toolbar = this.element('div', '', 'report-toolbar');
    this.reportType = this.select(toolbar, 'Görünüm', Object.entries(labels));
    this.reportSeller = this.input(toolbar, 'Üretici filtresi (alım defteri)');
    this.button(toolbar, 'Getir / yenile', () => {
      this.reportCursor = 0;
      return this.loadReport();
    });
    this.reportNext = this.button(toolbar, 'Sonraki sayfa →', () => {
      this.reportCursor = this.nextCursor;
      return this.loadReport();
    });
    this.reportNext.disabled = true;
    this.reportType.addEventListener('change', () => {
      this.reportCursor = 0;
      this.reportNext.disabled = true;
    });
    section.append(toolbar);
    this.reportInfo = this.element('p', '', 'footnote');
    section.append(this.reportInfo);
    this.reportTable = this.element('div', '', 'table-scroll');
    section.append(this.reportTable);
    this.root.append(section);
  }

  async loadReport() {
    const epoch = this.epoch;
    const operation = this.reportType.value;
    const args = operation === 'aksam_hesabi' ? {} : { cursor: this.reportCursor || 0, limit: 100 };
    if (operation === 'alim_defteri') args.uretici = this.reportSeller.value.trim();
    const data = await this.app.operation(operation, args);
    if (epoch !== this.epoch) return;
    this.activeReport = operation;
    this.reportDirty = false;
    this.nextCursor = data.nextCursor;
    this.reportNext.disabled = data.complete !== false;
    this.reportTable.replaceChildren();
    const table = this.element('table');
    const body = this.element('tbody');
    table.append(body);
    const columns = {
      alim_defteri: [['id', 'No'], ['tarih', 'Tarih'], ['uretici', 'Üretici'], ['netKg', 'Net kg'], ['tutarTL', 'Tutar ₺'], ['odenenTL', 'Ödenen ₺'], ['kalanTL', 'Kalan ₺']],
      cari: [['id', 'No'], ['tarih', 'Tarih'], ['kisi', 'Kişi'], ['yon', 'Tür'], ['tutar', 'Tutar ₺'], ['not', 'Not']],
      kasalar: [['id', 'No'], ['tarih', 'Tarih'], ['kisi', 'Aracı'], ['yon', 'Tür'], ['adet', 'Kasa'], ['not', 'Not']],
      z_listesi: [['id', 'Kayıt no'], ['etiket', 'Z'], ['alimSayisi', 'Alım'], ['kg', 'Net kg'], ['tutar', 'Tutar ₺'], ['cariyeIslendi', 'Cariye işlendi']],
      aksam_hesabi: [['netKg', 'Alınan kg'], ['teslimKg', 'Teslim kg'], ['tutar', 'Üretici toplam ₺'], ['komisyon', 'Komisyon ₺/kg'], ['avans', 'Avans ₺'], ['tuccar', 'Tüccar ödeyecek ₺']]
    }[operation];
    const head = this.element('tr');
    for (const [, label] of columns) head.append(this.element('th', label));
    if (this.app.canWrite() && ['alim_defteri', 'cari', 'kasalar'].includes(operation)) head.append(this.element('th', 'İşlem'));
    body.append(head);
    for (const item of data.items || [data]) {
      const row = this.element('tr');
      for (const [key] of columns) {
        let value = item[key] ?? '';
        if (['tutarTL', 'odenenTL', 'kalanTL', 'netKg', 'kg', 'tutar', 'odenen', 'teslimKg', 'komisyon', 'avans', 'tuccar'].includes(key)) value = amount(value);
        if (typeof value === 'boolean') value = value ? 'Evet' : 'Hayır';
        if (key === 'yon') value = operation === 'cari' ? ({ 1: 'Satış', 2: 'Tahsilat', 3: 'Ödeme' }[value] || value) : ({ 1: 'Giden', 2: 'Gelen' }[value] || value);
        row.append(this.element('td', String(value)));
      }
      if (this.app.canWrite() && ['alim_defteri', 'cari', 'kasalar'].includes(operation)) {
        const actions = this.element('td');
        if (operation === 'alim_defteri') {
          this.button(actions, 'Öde', () => this.rowAction('pay', item));
          if (!item.zId) {
            this.button(actions, 'Düzenle', () => this.rowAction('edit', item));
            this.button(actions, 'Sil', () => this.rowAction('delete', item));
          }
        } else {
          this.button(actions, 'Sil · telefon onayı', () => this.send(operation === 'cari' ? 'cari_sil' : 'kasa_adet_sil', { kayitId: item.id, surum: item.surum }));
        }
        row.append(actions);
      }
      body.append(row);
    }
    this.reportTable.append(table);
    this.reportInfo.textContent = operation === 'cari' ? 'Toplam alacak: ' + amount(data.alacak, ' ₺') + ' · Toplam borç: ' + amount(data.borc, ' ₺') :
      operation === 'kasalar' ? 'Dışarıdaki kasa: ' + data.disarida + ' · Bendeki kasa: ' + data.bende :
        (data.count == null ? '' : data.count + ' kayıt · ') + 'Telefonun güncel verisi';
  }

  state(state) {
    if (!state.approved && !state.data) {
      this.root.hidden = true;
      return;
    }
    if (this.full && !state.writable && !state.stale) this.clear();
    if (!state.stale) this.full = state.writable;
    this.root.classList.toggle('stale', state.stale);
    this.online = state.approved && !state.stale;
    this.root.hidden = false;
    this.writeFields.hidden = !state.writable;
    if (this.quickFields) {
      this.quickFields.hidden = !state.writable;
      this.quick.hidden = !state.writable;
      this.workspace?.classList.toggle('solo', !state.writable);
    }
    this.lock(!this.online || !!this.app.commands.size);
    this.permission.textContent = state.writable ? 'Tam kullanım · Kayıtları telefon doğrular ve saklar' : 'Yalnız görüntüleme · Yetki telefondan verilir';
    if (this.online && !this.today && !this.metaLoading) {
      this.metaLoading = true;
      const epoch = this.epoch;
      void this.app.operation('web_meta').then(data => {
        if (epoch !== this.epoch) return;
        this.today = data.tarih;
        this.date.value = this.today;
      }).catch(() => {}).finally(() => {
        if (epoch === this.epoch) this.metaLoading = false;
      });
    }
    if (this.reportDirty && this.activeReport && this.online && !this.reportLoading) {
      this.reportLoading = true;
      this.safe(() => this.loadReport()).finally(() => { this.reportLoading = false; });
    }
  }

  clear() {
    this.epoch++;
    clearTimeout(this.searchTimer);
    this.today = '';
    this.editing = null;
    this.closingVersion = null;
    this.purchaseCommand = null;
    this.activeReport = null;
    this.reportDirty = false;
    this.reportLoading = false;
    this.metaLoading = false;
    this.full = false;
    this.build();
  }
}
