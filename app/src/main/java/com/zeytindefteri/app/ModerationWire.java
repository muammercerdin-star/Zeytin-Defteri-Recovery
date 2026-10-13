package com.zeytindefteri.app;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import org.json.JSONArray;
import org.json.JSONObject;

/** Exact JSON-to-model conversion; cursor objects and nulls stay opaque. */
final class ModerationWire {
    static Object parse(int status,String body)throws ModerationBackendException{
        try{
            JSONObject root=new JSONObject(body);
            if(status<200||status>=300){
                JSONObject error=root.optJSONObject("error");
                ModerationBackendException.Code code=status==401?ModerationBackendException.Code.UNAUTHENTICATED:status==403?ModerationBackendException.Code.PERMISSION_DENIED:
                        ModerationBackendException.fromWire(error==null?"INTERNAL":error.optString("code","INTERNAL"));
                throw new ModerationBackendException(code);
            }
            if(root.has("error")||!root.has("data")||!(root.get("data") instanceof JSONObject))throw new IllegalArgumentException();
            return convert(root.get("data"),0);
        }catch(ModerationBackendException e){throw e;}
        catch(Exception e){throw new ModerationBackendException(status==401?ModerationBackendException.Code.UNAUTHENTICATED:status==403?ModerationBackendException.Code.PERMISSION_DENIED:ModerationBackendException.Code.INTERNAL);}
    }
    private static Object convert(Object value,int depth)throws Exception{
        if(depth>32)throw new IllegalArgumentException();if(value==JSONObject.NULL)return null;
        if(value instanceof JSONObject){LinkedHashMap<String,Object> map=new LinkedHashMap<>();JSONObject object=(JSONObject)value;
            for(Iterator<String> keys=object.keys();keys.hasNext();){String key=keys.next();map.put(key,convert(object.get(key),depth+1));}return map;}
        if(value instanceof JSONArray){ArrayList<Object> list=new ArrayList<>();JSONArray array=(JSONArray)value;for(int i=0;i<array.length();i++)list.add(convert(array.get(i),depth+1));return list;}
        return value;
    }
}
