#!/usr/bin/env python3
"""Host-only tests for the home screen model; no Android runtime."""
import pathlib, subprocess, tempfile
root = pathlib.Path(__file__).resolve().parents[2]
src = root / 'app/src/main/java/com/zeytindefteri/app'
with tempfile.TemporaryDirectory(prefix='zeytin-home-') as out:
    temp = pathlib.Path(out)
    # ZeytinAssistantPeriod only needs ZeytinAssistantData.date(); the real class needs Android.
    stub = temp / 'stub/com/zeytindefteri/app/ZeytinAssistantData.java'
    stub.parent.mkdir(parents=True)
    stub.write_text('''package com.zeytindefteri.app;
import java.text.*; import java.util.*;
final class ZeytinAssistantData { static Long date(String v){ try { SimpleDateFormat f=new SimpleDateFormat("yyyy-MM-dd",Locale.US);
 f.setLenient(false); f.setTimeZone(TimeZone.getTimeZone("UTC")); return f.parse(v).getTime(); } catch(Exception e){ return null; } } }''',
                    encoding='utf-8')
    classes = temp / 'classes'
    subprocess.run(['java', '-m', 'jdk.compiler/com.sun.tools.javac.Main', '-encoding', 'UTF-8', '-d', str(classes),
                    str(src / 'HomeDashboardModel.java'), str(src / 'GardenTodayAdvisor.java'),
                    str(src / 'ZeytinAssistantCommand.java'), str(src / 'ZeytinAssistantPeriod.java'), str(stub),
                    str(root / 'tests/home/HomeDashboardModelTest.java')], check=True)
    subprocess.run(['java', '-cp', str(classes), 'com.zeytindefteri.app.HomeDashboardModelTest'], cwd=root, check=True)
