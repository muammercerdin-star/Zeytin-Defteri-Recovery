'use strict';
const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path'),os=require('node:os'),cp=require('node:child_process');
const P=require('../league/policy');
const root=path.resolve(__dirname,'../..');
// Compile the actual pure parsing/identity methods taken verbatim from Android.
// Firebase callbacks still require the Android device smoke test.
test('backend rows parse through actual Android methods; region keys and own #37 agree',t=>{
 if(cp.spawnSync('java',['-version']).status!==0){t.skip('JDK required');return;}
 const source=fs.readFileSync(path.join(root,'app/src/main/java/com/zeytindefteri/app/ZeytinLeagueRepository.java'),'utf8');
 const methods=source.slice(source.indexOf('    private static String path('),source.indexOf('    private static double value('))+
   source.slice(source.indexOf('    public static String regionKey('),source.lastIndexOf('\n}'));
 const p={producer_id:'safe-anonymous-id',region_id:P.region('Alaşehir','Manisa').key,rank:37,kg:522,amount:31276,score:57.45,grades:[0,0,0,522,0,0]};
 const row=P.publicRow(p),summary=P.summary([p],p.region_id,'Alaşehir / Manisa');
 for(const key of ['name','producer_count','kg','amount','dominant','top'])assert.ok(key in summary);
 assert.equal(summary.weighted_average,summary.amount/summary.kg);
 assert.deepEqual(Object.keys(row).sort(),['amount','grades','kg','producer_id','rank','region_id','score']);
 const literal=v=>typeof v==='string'?JSON.stringify(v):Array.isArray(v)?`Arrays.asList(${v.map(literal).join(',')})`:String(v)+'d';
 const pairs=Object.entries(row).map(([k,v])=>`m.put(${JSON.stringify(k)},${literal(v)});`).join('\n');
 const regions=[['Alaşehir','Manisa'],[' ALASEHIR ',' MANISA '],['İzmit','Kocaeli'],['Sarıgöl','Manisa'],['Akhisar','Manisa']];
 const checks=regions.map(([d,p])=>`if(!regionKey(${JSON.stringify(d+' / '+p)}).equals(${JSON.stringify(P.region(d,p).key)}))throw new AssertionError("Region mismatch");`).join('\n');
 const dir=fs.mkdtempSync(path.join(os.tmpdir(),'league-contract-'));
 try{
  const app=path.join(dir,'LeagueContract.java'),launcher=path.join(dir,'RunContract.java');
  fs.writeFileSync(app,`package com.zeytindefteri.app;import java.util.*;import java.text.Normalizer;
public class LeagueContract {${methods}
 public static void main(String[] args){Map<String,Object> m=new HashMap<>();${pairs}
 ZeytinLeagueScore.Producer p=producer(m);
 if(p.rank!=37||p.kg!=522||Math.abs(p.average()-31276d/522)>1e-10||!p.dominant().equals("3 No")||!p.eligible)throw new AssertionError("Row parse");
 if(!path(2026,"g1").equals("zeytin_leagues/2026/snapshots/g1"))throw new AssertionError("Path");
 ${checks}
 long now=1700000000000L;
 if(!fresh(now,now)||!fresh(now-86400000L,now)||fresh(now-86400001L,now)||fresh(0,now)||fresh(now+86400001L,now))throw new AssertionError("Stale snapshot accepted");
 m.put("rank",0);try{producer(m);throw new AssertionError("Invalid row accepted");}catch(IllegalArgumentException expected){}
 System.out.println("contract OK");}}
`);
  fs.writeFileSync(launcher,`import java.io.*;import java.net.*;class RunContract{public static void main(String[] a)throws Exception{
 if(javax.tools.ToolProvider.getSystemJavaCompiler().run(null,null,null,"-d",a[0],a[1],a[2])!=0)throw new AssertionError("Compile");
 try(URLClassLoader l=new URLClassLoader(new URL[]{new File(a[0]).toURI().toURL()})){l.loadClass("com.zeytindefteri.app.LeagueContract").getMethod("main",String[].class).invoke(null,(Object)new String[0]);}}}`);
  assert.match(cp.execFileSync('java',[launcher,dir,app,path.join(root,'app/src/main/java/com/zeytindefteri/app/ZeytinLeagueScore.java')],{encoding:'utf8',timeout:30000}),/contract OK/);
 }finally{fs.rmSync(dir,{recursive:true,force:true});}
});
