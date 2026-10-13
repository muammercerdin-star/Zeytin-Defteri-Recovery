package com.zeytindefteri.app;
import java.util.*;
import org.json.JSONObject;
/** Run with Android's org.json or org.json:json:20240303; no Firebase/network needed. */
public class ModerationWireCheck {
 static void ok(boolean value){if(!value)throw new AssertionError();}
 static void error(int status,String body,ModerationBackendException.Code code)throws Exception{
  try{ModerationWire.parse(status,body);throw new AssertionError();}catch(ModerationBackendException e){ok(e.getCode()==code);ok(!e.getMessage().contains("private"));}
 }
 public static void main(String[] args)throws Exception{
  String cursor="{\"version\":1,\"status\":\"OPEN\",\"created_at\":{\"seconds\":200,\"nanoseconds\":2},\"path\":\"reports/a/items/POST|b|b\"}";
  String row="{\"reporter_uid\":\"a\",\"report_id\":\"POST|b|b\",\"status\":\"OPEN\"}";
  ModerationPage page=new ModerationPage();page.accept(ModerationWire.parse(200,"{\"data\":{\"reports\":["+row+"],\"next_cursor\":"+cursor+"}}"));
  ok(page.rows.size()==1&&page.cursor instanceof Map);
  JSONObject outgoing=new JSONObject(page.request(true));ok(outgoing.getJSONObject("cursor").getJSONObject("created_at").getInt("nanoseconds")==2);
  page.accept(ModerationWire.parse(200,"{\"data\":{\"reports\":["+row+"],\"next_cursor\":null}}"));ok(page.rows.size()==1&&page.cursor==null);
  page.reset("REVIEWED");ok(page.rows.isEmpty()&&page.cursor==null);
  ModeratorManagementState state=new ModeratorManagementState();state.acceptLookup(ModerationWire.parse(200,"{\"data\":{\"user\":{\"uid\":\"target\",\"display_name\":null,\"email\":\"a@example.com\",\"disabled\":false,\"moderation_admin\":false,\"super_admin\":false}}}"));
  ok(state.selected.role().equals("Normal Kullanıcı"));
  state.acceptChange(state.selected,true,ModerationWire.parse(200,"{\"data\":{\"uid\":\"target\",\"moderation_admin\":true}}"));ok(state.selected.moderator);
  state.acceptChange(state.selected,false,ModerationWire.parse(200,"{\"data\":{\"uid\":\"target\",\"moderation_admin\":false}}"));ok(!state.selected.moderator);
  error(401,"non-json gateway failure",ModerationBackendException.Code.UNAUTHENTICATED);
  error(403,"{\"error\":{\"code\":\"FORBIDDEN\"}}",ModerationBackendException.Code.PERMISSION_DENIED);
  error(409,"{\"error\":{\"code\":\"CONFLICT\"}}",ModerationBackendException.Code.FAILED_PRECONDITION);
  error(429,"{\"error\":{\"code\":\"RATE_LIMITED\",\"message\":\"private\"}}",ModerationBackendException.Code.INTERNAL);
  error(500,"{\"error\":{\"code\":\"INTERNAL\",\"stack\":\"private\"}}",ModerationBackendException.Code.INTERNAL);
  error(200,"{\"data\":[]}",ModerationBackendException.Code.INTERNAL);
  error(200,"{\"error\":{\"code\":\"FORBIDDEN\"}}",ModerationBackendException.Code.INTERNAL);
  System.out.println("Moderation HTTPS wire/model contract PASS");
 }
}
