package com.zeytindefteri.app;
public class ScreenFitTest { public static void main(String[] a){
 int[] r=ScreenFit.fit(411,420); if(r[1]!=420) throw new Error("normal");
 r=ScreenFit.fit(320,480); if(r[0]!=360||r[1]!=427) throw new Error("narrow "+r[1]);
 r=ScreenFit.fit(360,440); if(r[1]!=440) throw new Error("edge");
 r=ScreenFit.fit(0,440); if(r[1]!=440) throw new Error("unknown");
 System.out.println("PASS fit");}}
