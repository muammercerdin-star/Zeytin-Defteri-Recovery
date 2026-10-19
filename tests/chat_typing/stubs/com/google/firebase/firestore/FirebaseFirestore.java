package com.google.firebase.firestore;
import java.util.*;
public class FirebaseFirestore {
  public static FirebaseFirestore getInstance(){ return new FirebaseFirestore(); }
  public Query collection(String c){ return new Query(); }
  public static class QuerySnapshot { public List<DocumentSnapshot> getDocuments(){ return new ArrayList<>(); } }
  public interface EventListener { void onEvent(QuerySnapshot s, Exception e); }
  public static class DocRef { public Object set(Map<String,Object> m){return null;} public Object delete(){return null;} }
  public static class Query { public Query whereGreaterThan(String f, Object v){return this;} public Query limit(long n){return this;}
    public ListenerRegistration addSnapshotListener(EventListener l){ return () -> {}; } public DocRef document(String id){ return new DocRef(); } }
}
