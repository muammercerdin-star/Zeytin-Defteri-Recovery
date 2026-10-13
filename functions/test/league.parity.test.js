'use strict';
const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),os=require('node:os'),path=require('node:path'),cp=require('node:child_process');
const P=require('../league/policy');
test('backend formula matches the actual unchanged Java ZeytinLeagueScore (40 cases)',t=>{
 if(cp.spawnSync('java',['-version']).status!==0){t.skip('JDK 11+ needed for actual Java parity');return;}
 const dir=fs.mkdtempSync(path.join(os.tmpdir(),'league-parity-'));
 try{
  const launcher=path.join(dir,'LeagueParity.java');
  fs.writeFileSync(launcher,`import java.net.*;import java.io.*;import java.lang.reflect.*;import java.util.*;
class LeagueParity { public static void main(String[] a)throws Exception{
 if(javax.tools.ToolProvider.getSystemJavaCompiler().run(null,null,null,"-d",a[1],a[0])!=0)throw new Exception("Java compile failed");
 URLClassLoader l=new URLClassLoader(new URL[]{new File(a[1]).toURI().toURL()});
 Class<?> s=l.loadClass("com.zeytindefteri.app.ZeytinLeagueScore"),p=l.loadClass("com.zeytindefteri.app.ZeytinLeagueScore$Producer");
 for(int n=0;n<40;n++){
 Object o=p.getConstructor().newInstance();double[] g=(double[])p.getField("grades").get(o);double kg=0,expected=0;double[] m={100,90,75,55,30,10};
 for(int i=0;i<6;i++){g[i]=(n+i*7)%31+1;kg+=g[i];expected+=g[i]*m[i];}
 double ratio=new double[]{.2,.5,1,1.5,4}[n%5];p.getField("kg").setDouble(o,kg);p.getField("amount").setDouble(o,expected*ratio);p.getField("records").setInt(o,1);
 s.getMethod("calculate",p,double[].class,int[].class).invoke(null,o,n%7==0?null:m,n%7==0?null:new int[]{4,4,4,4,4,4});
 System.out.printf(Locale.ROOT,"{\\"quality\\":%.15f,\\"price\\":%.15f,\\"volume\\":%.15f,\\"score\\":%.15f,\\"eligible\\":%s,\\"benchmarkReady\\":%s,\\"outlier\\":%s}%n",
 p.getField("quality").getDouble(o),p.getField("price").getDouble(o),p.getField("volume").getDouble(o),p.getField("score").getDouble(o),p.getField("eligible").getBoolean(o),p.getField("benchmarkReady").getBoolean(o),p.getField("outlier").getBoolean(o));
 }l.close();}}
`);
  const src=path.resolve(__dirname,'../../app/src/main/java/com/zeytindefteri/app/ZeytinLeagueScore.java');
  const rows=cp.execFileSync('java',[launcher,src,dir],{encoding:'utf8',timeout:30000}).trim().split('\n').map(JSON.parse);
  assert.equal(rows.length,40);
  rows.forEach((expected,n)=>{
   const med=[100,90,75,55,30,10],grades=med.map((_,i)=>(n+i*7)%31+1),kg=grades.reduce((a,b)=>a+b,0);
   const amount=grades.reduce((a,g,i)=>a+g*med[i],0)*[.2,.5,1,1.5,4][n%5];
   const actual=P.score({kg,amount,grades,records:1},n%7===0?null:med,n%7===0?null:[4,4,4,4,4,4]);
   for(const k of ['quality','price','volume','score'])assert.ok(Math.abs(actual[k]-expected[k])<1e-10,`${n}: ${k}`);
   for(const k of ['eligible','benchmarkReady','outlier'])assert.equal(actual[k],expected[k]);
  });
 }finally{fs.rmSync(dir,{recursive:true,force:true});}
});
