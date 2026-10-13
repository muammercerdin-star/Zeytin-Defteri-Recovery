#!/usr/bin/env python3
"""Host-only tests for the Bahçelerim screen model and summary SQL."""
import pathlib, subprocess, tempfile
root = pathlib.Path(__file__).resolve().parents[2]
src = root / 'app/src/main/java/com/zeytindefteri/app'
with tempfile.TemporaryDirectory(prefix='zeytin-gardens-') as out:
    subprocess.run(['java', '-m', 'jdk.compiler/com.sun.tools.javac.Main', '-encoding', 'UTF-8', '-d', out,
                    str(src / 'GardenListModel.java'), str(src / 'GardenTodayAdvisor.java'),
                    str(root / 'tests/gardens/GardenListModelTest.java')], check=True)
    subprocess.run(['java', '-cp', out, 'com.zeytindefteri.app.GardenListModelTest'], cwd=root, check=True)
subprocess.run(['python3', str(root / 'tests/gardens/check_summary_query.py')], cwd=root, check=True)
