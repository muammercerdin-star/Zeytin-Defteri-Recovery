"""Exercise the preserved Release Gate snapshot on real SQLite harvested/graded fixtures."""
from pathlib import Path
import sqlite3,tempfile,subprocess
ROOT=Path(__file__).resolve().parents[2]
with tempfile.TemporaryDirectory() as tmp:
 t=Path(tmp);db=sqlite3.connect(t/'gate.db')
 db.execute('CREATE TABLE fixture(harvest REAL,elek REAL,h_records INTEGER,e_records INTEGER)')
 db.executemany('INSERT INTO fixture VALUES(?,?,?,?)',[(190,280,1,1),(280,190,1,1),(190,190,1,1),(0,0,0,0),(190,0,1,0),(0,190,0,1),(190,190.005,1,1)])
 rows=list(db.execute('SELECT * FROM fixture'));db.close()
 src=ROOT/'app/src/main/java/com/zeytindefteri/app/GardenAnalyticsSnapshot.java'
 (t/src.name).write_text(src.read_text())
 (t/'Gate.java').write_text('''package com.zeytindefteri.app;public class Gate {public static void main(String[] a){
 GardenAnalyticsSnapshot.Builder b=new GardenAnalyticsSnapshot.Builder();b.harvest=Double.parseDouble(a[0]);b.grades[0]=Double.parseDouble(a[1]);b.harvestRecords=Integer.parseInt(a[2]);b.elekRecords=Integer.parseInt(a[3]);GardenAnalyticsSnapshot s=new GardenAnalyticsSnapshot(b,2026);
 boolean expected=b.harvestRecords>0&&b.elekRecords>0&&Math.abs(b.grades[0]-b.harvest)>0.01;
 if(s.hasHarvestElekMismatch()!=expected||Math.abs(s.harvestElekDifferenceKg()-(b.grades[0]-b.harvest))>1e-8)throw new AssertionError();
 if(s.elekExcessKg()!=(s.hasComparableHarvestAndElek()?Math.max(0,b.grades[0]-b.harvest):0))throw new AssertionError();
 if(s.elekMissingKg()!=(s.hasComparableHarvestAndElek()?Math.max(0,b.harvest-b.grades[0]):0))throw new AssertionError();
 }}''')
 subprocess.run(['java','com.sun.tools.javac.Main','--release','8','-d',str(t),str(t/src.name),str(t/'Gate.java')],check=True)
 for row in rows:subprocess.run(['java','-cp',str(t),'com.zeytindefteri.app.Gate']+list(map(str,row)),check=True)
 print('PASS 7 SQLite Release Gate fixtures: excess/missing/equal/empty/unavailable/tolerance; production snapshot unchanged')
