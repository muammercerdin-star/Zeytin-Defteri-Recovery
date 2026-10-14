'use strict';
// Meydan gönderisi güvenlik kuralları (social_posts). Yalnızca yerel Firestore emülatöründe çalışır:
//   firebase emulators:exec --only firestore "npm --prefix functions test"
// Emülatör yoksa test "skipped" olarak raporlanır; geçti sayılmaz.
const test=require('node:test'),fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict');
const host=process.env.FIRESTORE_EMULATOR_HOST;

test('Meydan gönderileri: kimlik, ad, tarih, metin sınırı, alan beyaz listesi, sahiplik, eski belgeler, yorumlar',
 {skip:!host},async()=>{
 if(!/^(localhost|127\.0\.0\.1):\d+$/.test(host))throw Error('Local emulator only');
 const req=require('node:module').createRequire(path.join(__dirname,'rules/package.json'));
 const {initializeTestEnvironment,assertSucceeds,assertFails}=req('@firebase/rules-unit-testing');
 const {doc,getDoc,setDoc,updateDoc,deleteDoc}=req('firebase/firestore');
 const [hostname,port]=host.split(':');
 const env=await initializeTestEnvironment({projectId:'demo-zeytin-posts',firestore:{host:hostname,port:+port,
   rules:fs.readFileSync(path.resolve(__dirname,'../../firestore.rules'),'utf8')}});
 try{
  await env.clearFirestore();
  const now=Date.now();
  // Eski şema: cloud_id/created_at yok, medya alanları ve bilinmeyen eski alanlar var.
  await env.withSecurityRulesDisabled(async ctx=>{
   const db=ctx.firestore();
   await setDoc(doc(db,'social_posts/legacy'),{user_id:'A',user_name:'Eski Ad',content:'Eski gönderi',post_type:'GENERAL',
     photo_uri:'content://eski',media_urls:['https://example.invalid/a.jpg'],is_active:1,legacy_flag:true,updated_at:now-1000});
   await setDoc(doc(db,'social_posts/b-post'),{cloud_id:'b-post',user_id:'B',user_name:'Üretici',post_type:'GENERAL',
     content:'B gönderisi',created_at:now-5000,updated_at:now-5000});
  });
  // Oturum belgesi: A'nın Firebase adı "Ali Çiftçi"; C'nin adı yok.
  const a=env.authenticatedContext('A',{name:'Ali Çiftçi'}).firestore();
  const b=env.authenticatedContext('B').firestore();
  const c=env.authenticatedContext('C').firestore();
  const guest=env.unauthenticatedContext().firestore();
  // SocialCloudRepository.publishLocalPost'un gerçekten gönderdiği alanlar.
  const post=(id,over={})=>({cloud_id:id,user_id:'A',user_name:'Ali Çiftçi',province:'Balıkesir',district:'Edremit',
    neighborhood:'',garden_name:'',variety:'',post_type:'PEST',content:'Tuzaklarda sinek hareketi var.',
    is_real_sale:0,sale_grade:'',sale_kg:0.0,sale_price:0.0,created_at:now,updated_at:now,...over});
  const write=(db,id,data)=>setDoc(doc(db,'social_posts/'+id),data,{merge:true});

  // A1 giriş yapmadan gönderi: RED
  await assertFails(write(guest,'g1',post('g1')));
  // A2 kendi hesabıyla geçerli gönderi: İZİN (istemcinin merge yazımı)
  await assertSucceeds(write(a,'p1',post('p1')));
  // Adı olmayan oturum varsayılan adı yazmalı
  await assertSucceeds(write(c,'c1',post('c1',{user_id:'C',user_name:'Üretici'})));
  await assertFails(write(c,'c2',post('c2',{user_id:'C',user_name:'Ali Çiftçi'})));
  // A3 başkasının UID'siyle gönderi: RED
  await assertFails(write(a,'p2',post('p2',{user_id:'B'})));
  await assertFails(write(b,'p3',post('p3')));
  // Sahte kullanıcı adı: RED
  await assertFails(write(a,'p4',post('p4',{user_name:'Zeytin Defteri Yönetimi'})));
  // A4 gelecek tarihli sahte gönderi: RED (10 dakika saat toleransı içi kabul)
  await assertFails(write(a,'p5',post('p5',{created_at:now+24*3600*1000})));
  await assertFails(write(a,'p6',post('p6',{updated_at:now+24*3600*1000})));
  await assertSucceeds(write(a,'p7',post('p7',{created_at:now+60*1000,updated_at:now+60*1000})));
  // A5 aşırı uzun metin: RED; sınırda: İZİN; boş/boşluk: RED
  await assertFails(write(a,'p8',post('p8',{content:'z'.repeat(5001)})));
  await assertSucceeds(write(a,'p9',post('p9',{content:'z'.repeat(5000)})));
  await assertFails(write(a,'p10',post('p10',{content:''})));
  await assertFails(write(a,'p11',post('p11',{content:'   \n\t '})));
  await assertFails(write(a,'p12',post('p12',{district:'x'.repeat(101)})));
  // A6 geçersiz alan türleri: RED
  await assertFails(write(a,'p13',post('p13',{content:12345})));
  await assertFails(write(a,'p14',post('p14',{created_at:String(now)})));
  await assertFails(write(a,'p15',post('p15',{sale_kg:'10'})));
  await assertFails(write(a,'p16',post('p16',{post_type:'ADMIN'})));
  await assertFails(write(a,'p17',post('p17',{is_active:2})));
  await assertFails(write(a,'p18',post('p18',{cloud_id:'baska-id'})));
  // A7 yetkisiz yönetim/sistem alanları: RED
  await assertFails(write(a,'p19',post('p19',{is_real_sale:1})));
  await assertFails(write(a,'p20',post('p20',{verified:true})));
  await assertFails(write(a,'p21',post('p21',{moderation_status:'APPROVED'})));
  await assertFails(write(a,'p22',post('p22',{photo_uri:'https://example.invalid/x.jpg'})));
  // Zorunlu alan eksik: RED
  const missing=post('p23');delete missing.created_at;
  await assertFails(write(a,'p23',missing));

  // A8 gönderi sahibini değiştirme: RED
  await assertFails(updateDoc(doc(a,'social_posts/p1'),{user_id:'B'}));
  // A9 kendi gönderisini düzenleme: kurala uygun İZİN
  await assertSucceeds(write(a,'p1',post('p1',{content:'Güncel durum: azaldı.',updated_at:now+1})));
  await assertFails(write(a,'p1',post('p1',{created_at:now-100000})));      // oluşturma zamanı değişmez
  await assertFails(updateDoc(doc(a,'social_posts/p1'),{cloud_id:'x'}));      // kimlik değişmez
  await assertFails(updateDoc(doc(a,'social_posts/p1'),{content:'z'.repeat(5001)}));
  await assertFails(updateDoc(doc(a,'social_posts/p1'),{is_real_sale:1}));
  await assertFails(updateDoc(doc(a,'social_posts/p1'),{user_name:'Başka Biri'}));
  await assertFails(updateDoc(doc(a,'social_posts/p1'),{updated_at:now+24*3600*1000}));
  // A10 başkasının gönderisini düzenleme/silme: RED
  await assertFails(write(a,'b-post',{...post('b-post'),user_id:'B'}));
  await assertFails(updateDoc(doc(a,'social_posts/b-post'),{content:'ele geçirildi'}));
  await assertFails(deleteDoc(doc(a,'social_posts/b-post')));
  // Okuma: giriş yapan herkes okur, misafir okuyamaz (değişmedi)
  await assertSucceeds(getDoc(doc(b,'social_posts/p1')));
  await assertFails(getDoc(doc(guest,'social_posts/p1')));

  // Eski belge: okunur; sahibi istemcinin yazımıyla güncelleyebilir (cloud_id/created_at bir kez eklenir),
  // dokunulmayan eski alanlar korunur.
  await assertSucceeds(getDoc(doc(b,'social_posts/legacy')));
  await assertSucceeds(write(a,'legacy',post('legacy',{content:'Eski gönderi',post_type:'GENERAL',updated_at:now+2})));
  const legacy=(await getDoc(doc(a,'social_posts/legacy'))).data();
  assert.equal(legacy.photo_uri,'content://eski');assert.equal(legacy.legacy_flag,true);assert.equal(legacy.cloud_id,'legacy');
  await assertFails(updateDoc(doc(a,'social_posts/legacy'),{legacy_flag:false}));  // beyaz liste dışı alan değişmez
  await assertFails(updateDoc(doc(b,'social_posts/legacy'),{content:'B yazdı'}));

  // Yorumlar: geçerli yorum oluşturma İZİN (yeni ve eski gönderide), yorum kuralları değişmedi
  const comment=(id,postId)=>({cloud_id:id,post_cloud_id:postId,user_id:'B',user_name:'B',content:'Bizde de var',is_active:1,
    created_at:now,updated_at:now,parent_comment_id:'',root_comment_id:id,reply_to_uid:'',reply_to_name:''});
  await assertSucceeds(setDoc(doc(b,'social_posts/p1/comments/k1'),comment('k1','p1')));
  await assertSucceeds(setDoc(doc(b,'social_posts/legacy/comments/k2'),comment('k2','legacy')));
  await assertFails(setDoc(doc(guest,'social_posts/p1/comments/k3'),comment('k3','p1')));

  // Kendi gönderisini silme: İZİN (değişmedi)
  await assertSucceeds(deleteDoc(doc(a,'social_posts/p9')));

  // Moderatörün kaldırdığı gönderi ve yanıt: sahibi yeniden açamaz, düzenleyemez (sunucu yazdı, kural dışı).
  await env.withSecurityRulesDisabled(async ctx=>{
   const db=ctx.firestore();
   await setDoc(doc(db,'social_posts/mod-post'),{...post('mod-post'),is_active:0,moderated_by:'mod-uid',moderated_at:now});
   await setDoc(doc(db,'social_posts/p1/comments/kmod'),{...comment('kmod','p1'),is_active:0,moderated_by:'mod-uid'});
  });
  await assertFails(updateDoc(doc(a,'social_posts/mod-post'),{is_active:1,updated_at:now+5}));
  await assertFails(updateDoc(doc(a,'social_posts/mod-post'),{content:'geri açtım',updated_at:now+5}));
  await assertFails(setDoc(doc(b,'social_posts/p1/comments/kmod'),{...comment('kmod','p1'),is_active:1,updated_at:now+5}));
  await assertSucceeds(getDoc(doc(b,'social_posts/mod-post')));
 }finally{await env.cleanup();}
});
