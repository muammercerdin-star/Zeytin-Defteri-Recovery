#!/usr/bin/env python3
"""Host-only tests for ElekInsights; no Android runtime."""
import pathlib, subprocess, tempfile
root = pathlib.Path(__file__).resolve().parents[2]
with tempfile.TemporaryDirectory(prefix='zeytin-elek-') as out:
    subprocess.run(['java', '-m', 'jdk.compiler/com.sun.tools.javac.Main', '-encoding', 'UTF-8', '-d', out,
                    str(root / 'app/src/main/java/com/zeytindefteri/app/ElekInsights.java'),
                    str(root / 'tests/elek_insights/ElekInsightsTest.java')], check=True)
    subprocess.run(['java', '-cp', out, 'com.zeytindefteri.app.ElekInsightsTest'], cwd=root, check=True)
subprocess.run(['python3', str(root / 'tests/elek_insights/check_archive_query.py')], cwd=root, check=True)
