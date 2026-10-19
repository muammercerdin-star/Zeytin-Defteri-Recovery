package com.google.android.gms.tasks;
public abstract class Task<T> { public abstract boolean isSuccessful(); public abstract T getResult();
  public interface Listener<T> { void onComplete(Task<T> t); }
  public Task<T> addOnCompleteListener(Listener<T> l) { return this; } }
