package com.google.firebase.auth;
public abstract class FirebaseUser { public abstract String getUid();
  public abstract com.google.android.gms.tasks.Task<GetTokenResult> getIdToken(boolean f); }
