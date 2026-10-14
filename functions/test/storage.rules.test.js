'use strict';
// Firebase Storage kuralları. İstemci erişimi tamamen kapalıdır (uygulama Storage kullanmaz).
// Yalnızca yerel Storage emülatöründe çalışır:
//   firebase emulators:exec --only storage "npm --prefix functions test"
// Emülatör yoksa test "skipped" olarak raporlanır; geçti sayılmaz.
const test=require('node:test'),fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict');
const host=process.env.FIREBASE_STORAGE_EMULATOR_HOST;

test('Storage: istemci yükleme/okuma/silme kapalı; sunucu (kurallar dışı) temizliği çalışır',{skip:!host},async()=>{
 if(!/^(localhost|127\.0\.0\.1):\d+$/.test(host))throw Error('Local emulator only');
 const req=require('node:module').createRequire(path.join(__dirname,'rules/package.json'));
 const {initializeTestEnvironment,assertSucceeds,assertFails}=req('@firebase/rules-unit-testing');
 const {ref,uploadBytes,getBytes,deleteObject,getMetadata}=req('firebase/storage');
 const [hostname,port]=host.split(':');
 const env=await initializeTestEnvironment({projectId:'demo-zeytin-storage',storage:{host:hostname,port:+port,
   rules:fs.readFileSync(path.resolve(__dirname,'../../storage.rules'),'utf8')}});
 try{
  await env.clearStorage();
  const png=new Uint8Array([0x89,0x50,0x4e,0x47,0x0d,0x0a,0x1a,0x0a]);
  // Eski sürümden kalmış olabilecek bir dosya (sunucu tarafından, kurallar dışı)
  await env.withSecurityRulesDisabled(async ctx=>{
   await uploadBytes(ref(ctx.storage(),'social_posts/A/p1/eski.png'),png,{contentType:'image/png'});
  });
  const a=env.authenticatedContext('A').storage(),b=env.authenticatedContext('B').storage(),guest=env.unauthenticatedContext().storage();
  // B1 yetkisiz (misafir) yükleme: RED
  await assertFails(uploadBytes(ref(guest,'social_posts/A/p2/x.png'),png,{contentType:'image/png'}));
  // Kendi yoluna bile istemci yüklemesi: RED (Senaryo A: istemci Storage kullanmaz)
  await assertFails(uploadBytes(ref(a,'social_posts/A/p2/x.png'),png,{contentType:'image/png'}));
  // B2 izin verilmeyen dosya türü: RED
  await assertFails(uploadBytes(ref(a,'social_posts/A/p2/x.exe'),new Uint8Array([1,2,3]),{contentType:'application/x-msdownload'}));
  // B3 boyut sınırı aşımı: RED
  await assertFails(uploadBytes(ref(a,'social_posts/A/p2/big.png'),new Uint8Array(11*1024*1024),{contentType:'image/png'}));
  // B4 başka kullanıcının dosyasını değiştirme/silme: RED
  await assertFails(uploadBytes(ref(b,'social_posts/A/p1/eski.png'),png,{contentType:'image/png'}));
  await assertFails(deleteObject(ref(b,'social_posts/A/p1/eski.png')));
  await assertFails(deleteObject(ref(a,'social_posts/A/p1/eski.png')));
  // Okuma da kapalı (istemci SDK'sı kullanılmıyor)
  await assertFails(getBytes(ref(a,'social_posts/A/p1/eski.png')));
  await assertFails(getMetadata(ref(guest,'social_posts/A/p1/eski.png')));
  // B5 hesap silme sunucu işlemi korunur: Admin SDK kurallara tabi değildir; kurallar dışı bağlamda silme çalışır.
  await env.withSecurityRulesDisabled(async ctx=>{
   await assertSucceeds(deleteObject(ref(ctx.storage(),'social_posts/A/p1/eski.png')));
  });
  assert.ok(true);
 }finally{await env.cleanup();}
});
