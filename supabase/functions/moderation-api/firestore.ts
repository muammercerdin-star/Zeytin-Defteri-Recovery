import {PROJECT,ServiceError} from './firebase.ts';
type Dict=Record<string,any>;
export class Time {
 constructor(public seconds:number,public nanoseconds:number){}
 toDate(){return new Date(this.seconds*1000+this.nanoseconds/1e6);}
 toString(){return new Date(this.seconds*1000).toISOString().replace(/\.\d{3}Z$/,'.'+String(this.nanoseconds).padStart(9,'0')+'Z');}
 static parse(s:string){const ms=Date.parse(s),m=s.match(/\.(\d+)Z$/);if(!Number.isFinite(ms))throw new ServiceError('INTERNAL');return new Time(Math.floor(ms/1000),Number((m?.[1]||'').padEnd(9,'0')));}
}
const STAMP=Symbol('serverTimestamp');
export const serverTimestamp=()=>STAMP;
export function decode(v:Dict):any{
 if('nullValue'in v)return null;if('stringValue'in v)return v.stringValue;if('booleanValue'in v)return v.booleanValue;
 if('integerValue'in v)return Number(v.integerValue);if('doubleValue'in v)return v.doubleValue;if('timestampValue'in v)return Time.parse(v.timestampValue);
 if('referenceValue'in v)return v.referenceValue;if('arrayValue'in v)return (v.arrayValue.values||[]).map(decode);
 if('mapValue'in v)return Object.fromEntries(Object.entries(v.mapValue.fields||{}).map(([k,x])=>[k,decode(x as Dict)]));
 return undefined;
}
function encode(v:any):Dict{
 if(v instanceof Time)return {timestampValue:v.toString()};if(v instanceof Ref)return {referenceValue:v.name};
 if(v===null)return {nullValue:null};if(typeof v==='boolean')return {booleanValue:v};if(typeof v==='string')return {stringValue:v};
 if(typeof v==='number'&&Number.isFinite(v))return Number.isInteger(v)?{integerValue:String(v)}:{doubleValue:v};
 if(Array.isArray(v))return {arrayValue:{values:v.map(encode)}};
 if(v&&typeof v==='object')return {mapValue:{fields:Object.fromEntries(Object.entries(v).map(([k,x])=>[k,encode(x)]))}};
 throw new ServiceError('INTERNAL');
}
class Ref {
 constructor(public db:RestFirestore,public path:string){}
 get id(){return this.path.split('/').at(-1)!;}get name(){return this.db.root+'/'+this.path;}
 get(){return this.db.read(this);}
}
class Snapshot {
 id:string;exists:boolean;
 constructor(public ref:Ref,private document?:Dict){this.id=ref.id;this.exists=!!document;}
 data(){return this.document?Object.fromEntries(Object.entries(this.document.fields||{}).map(([k,v])=>[k,decode(v as Dict)])):undefined;}
}
class Query {
 constructor(private db:RestFirestore,private collection:string,private group:boolean,private spec:Dict={}){}
 private copy(next:Dict){return new Query(this.db,this.collection,this.group,{...this.spec,...next});}
 where(field:string,op:string,value:unknown){if(op!=='==')throw new ServiceError('INTERNAL');return this.copy({where:{fieldFilter:{field:{fieldPath:field},op:'EQUAL',value:encode(value)}}});}
 orderBy(field:string,direction='asc'){return this.copy({orderBy:[...(this.spec.orderBy||[]),{field:{fieldPath:field},direction:direction==='desc'?'DESCENDING':'ASCENDING'}]});}
 limit(limit:number){return this.copy({limit});}
 startAfter(...values:any[]){
  if(values.length===1&&values[0] instanceof Snapshot){const doc=values[0];values=this.spec.orderBy.map((order:Dict)=>order.field.fieldPath==='__name__'?doc.ref:doc.data()![order.field.fieldPath]);}
  else if(values.length===1&&typeof values[0]==='string'&&this.spec.orderBy?.[0]?.field.fieldPath==='__name__')values=[this.db.doc(this.collection+'/'+values[0])];
  return this.copy({startAt:{values:values.map(encode),before:false}});
 }
 async get(){
  const rows=await this.db.post(this.db.url+':runQuery',{structuredQuery:{from:[{collectionId:this.collection,allDescendants:this.group}],...this.spec}});
  if(!Array.isArray(rows))throw new ServiceError('INTERNAL');
  return {docs:rows.filter(r=>r.document).map(r=>this.db.snapshot(r.document))};
 }
}
class Batch {
 writes:Dict[]=[];
 constructor(private db:RestFirestore,public transaction?:string){}
 get(ref:Ref){return this.db.read(ref,this.transaction);}
 set(ref:Ref,data:Dict){this.write(ref,data,false);return this;}
 update(ref:Ref,data:Dict){this.write(ref,data,true);return this;}
 delete(ref:Ref){this.writes.push({delete:ref.name});return this;}
 private write(ref:Ref,data:Dict,update:boolean){
  const fields:Dict={},transforms:Dict[]=[];
  for(const [k,v] of Object.entries(data)){if(v===STAMP)transforms.push({fieldPath:k,setToServerValue:'REQUEST_TIME'});else fields[k]=encode(v);}
  const write:Dict={update:{name:ref.name,fields}};
  if(update){write.updateMask={fieldPaths:Object.keys(fields)};write.currentDocument={exists:true};}
  if(transforms.length)write.updateTransforms=transforms;this.writes.push(write);
 }
 commit(){return this.db.post(this.db.url+':commit',{writes:this.writes,...(this.transaction?{transaction:this.transaction}:{})});}
}
export class RestFirestore {
 root:string;url:string;
 constructor(public post:(url:string,body:unknown)=>Promise<any>,project=PROJECT,origin='https://firestore.googleapis.com'){
  this.root=`projects/${project}/databases/(default)/documents`;this.url=origin+'/v1/'+this.root;
 }
 doc(path:string){return new Ref(this,path);}
 collection(name:string){return {doc:(id=crypto.randomUUID())=>this.doc(name+'/'+id),orderBy:(field:string,direction?:string)=>new Query(this,name,false).orderBy(field,direction)};}
 collectionGroup(name:string){return new Query(this,name,true);}
 snapshot(document:Dict){if(typeof document.name!=='string'||!document.name.startsWith(this.root+'/'))throw new ServiceError('INTERNAL');return new Snapshot(this.doc(document.name.slice(this.root.length+1)),document);}
 async read(ref:Ref,transaction?:string){
  const rows=await this.post(this.url+':batchGet',{documents:[ref.name],...(transaction?{transaction}:{})});
  const row=Array.isArray(rows)?rows.find(r=>r.found?.name===ref.name||r.missing===ref.name):undefined;
  if(!row)throw new ServiceError('INTERNAL');return row.found?this.snapshot(row.found):new Snapshot(ref);
 }
 batch(){return new Batch(this);}
 async runTransaction(fn:(tx:Batch)=>Promise<any>){
  for(let attempt=0;attempt<4;attempt++){
   const begun=await this.post(this.url+':beginTransaction',{options:{readWrite:{}}});
   if(typeof begun.transaction!=='string')throw new ServiceError('INTERNAL');
   const tx=new Batch(this,begun.transaction);
   try{const result=await fn(tx);await tx.commit();return result;}
   catch(e){await this.post(this.url+':rollback',{transaction:begun.transaction}).catch(()=>{});
    if((e as ServiceError).code==='ABORTED'&&attempt<3){await new Promise(r=>setTimeout(r,25*(attempt+1)));continue;}throw e;}
  }
 }
}
