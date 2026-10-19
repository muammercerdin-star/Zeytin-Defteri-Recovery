#!/usr/bin/env python3
"""Alıcı rehberi (BuyerDirectory) host testi: Android stub'ları gerçek SQLite dosyasında çalışır."""
import pathlib, subprocess, tempfile
here = pathlib.Path(__file__).resolve().parent
root = here.parents[1]
with tempfile.TemporaryDirectory(prefix='zeytin-buyers-') as out:
    stubs = [str(p) for p in (here / 'stubs').rglob('*.java')]
    subprocess.run(['java', '-m', 'jdk.compiler/com.sun.tools.javac.Main', '-encoding', 'UTF-8', '-d', out, *stubs,
                    str(root / 'app/src/main/java/com/zeytindefteri/app/BuyerDirectory.java'),
                    str(here / 'BuyerDirectoryTest.java')], check=True)
    db = pathlib.Path(out) / 'test.sqlite'
    subprocess.run(['java', '-cp', out, 'com.zeytindefteri.app.BuyerDirectoryTest', str(db), str(here / 'sql_helper.py')],
                   cwd=root, check=True)
