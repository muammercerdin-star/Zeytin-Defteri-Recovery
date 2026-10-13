package com.zeytindefteri.app;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Callable wire model. No Firebase writes or authority decisions live here. */
final class ModeratorManagementState {
    static boolean superAllowed(Map<String,Object> claims) {
        return claims != null && Boolean.TRUE.equals(claims.get("super_admin"));
    }
    static final class User {
        final String uid, name, email;
        final boolean disabled, moderator, superAdmin;
        User(String uid,String name,String email,boolean disabled,boolean moderator,boolean superAdmin) {
            this.uid=uid;this.name=name;this.email=email;this.disabled=disabled;this.moderator=moderator;this.superAdmin=superAdmin;
        }
        String role(){return superAdmin?"Süper Admin":moderator?"Moderatör":"Normal Kullanıcı";}
        boolean canChange(){return !superAdmin;}
        User withModerator(boolean value){return new User(uid,name,email,disabled,value,superAdmin);}
        static User parse(Object raw) {
            Map<?,?> m=map(raw);
            String uid=string(m.get("uid"));
            if(uid.isEmpty()||uid.length()>128||!(m.get("disabled") instanceof Boolean)
                    ||!(m.get("moderation_admin") instanceof Boolean)||!(m.get("super_admin") instanceof Boolean))throw new IllegalArgumentException();
            return new User(uid,string(m.get("display_name")),string(m.get("email")),
                    Boolean.TRUE.equals(m.get("disabled")),Boolean.TRUE.equals(m.get("moderation_admin")),Boolean.TRUE.equals(m.get("super_admin")));
        }
    }
    final LinkedHashMap<String,User> users=new LinkedHashMap<>();
    User selected;
    String cursor;
    void clear(){users.clear();selected=null;cursor=null;}
    void resetList(){users.clear();cursor=null;}
    static Map<String,Object> lookup(String input){
        String value=input==null?"":input.trim();
        boolean explicitUid=value.startsWith("uid:"),explicitEmail=value.startsWith("email:");
        if(explicitUid)value=value.substring(4).trim();
        if(explicitEmail)value=value.substring(6).trim();
        boolean email=explicitEmail||(!explicitUid&&value.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"));
        if(value.isEmpty()||value.length()>(email?254:128)||value.matches(".*[\\p{Cntrl}].*"))throw new IllegalArgumentException();
        if(email&&!value.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"))throw new IllegalArgumentException();
        Map<String,Object> d=new LinkedHashMap<>();d.put(email?"email":"uid",value);return d;
    }
    Map<String,Object> listRequest(boolean append){Map<String,Object> d=new LinkedHashMap<>();d.put("limit",25);if(append&&cursor!=null)d.put("cursor",cursor);return d;}
    static Map<String,Object> target(User user){if(!user.canChange())throw new IllegalArgumentException();Map<String,Object> d=new LinkedHashMap<>();d.put("target_uid",user.uid);return d;}
    void acceptLookup(Object raw){selected=User.parse(map(raw).get("user"));}
    void acceptList(Object raw){
        Map<?,?> m=map(raw);Object next=m.get("next_cursor"),rows=m.get("moderators");
        if(!(rows instanceof List)||((List<?>)rows).size()>25||(next!=null&&(!(next instanceof String)||!((String)next).matches("[a-f0-9]{64}"))))throw new IllegalArgumentException();
        List<User> parsed=new ArrayList<>();
        for(Object row:(List<?>)rows){User u=User.parse(row);if(!u.moderator)throw new IllegalArgumentException();parsed.add(u);}
        for(User u:parsed)users.put(u.uid,u);cursor=(String)next;
    }
    void acceptChange(User target,boolean granted,Object raw){
        Map<?,?> m=map(raw);
        if(!target.canChange()||!target.uid.equals(m.get("uid"))||!Boolean.valueOf(granted).equals(m.get("moderation_admin")))throw new IllegalArgumentException();
        User updated=target.withModerator(granted);
        if(selected!=null&&selected.uid.equals(target.uid))selected=updated;
        if(granted)users.put(updated.uid,updated);else users.remove(updated.uid);
    }
    private static Map<?,?> map(Object raw){if(!(raw instanceof Map))throw new IllegalArgumentException();return (Map<?,?>)raw;}
    private static String string(Object raw){if(raw==null)return "";if(!(raw instanceof String))throw new IllegalArgumentException();return (String)raw;}
}
