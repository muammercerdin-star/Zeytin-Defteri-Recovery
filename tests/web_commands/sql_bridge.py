"""Kalıcı SQLite bağlantısı: gerçek transaction/rollback ve kilit davranışı."""
import json
import sqlite3
import sys
connection = sqlite3.connect(sys.argv[1], timeout=10, isolation_level=None)
for line in sys.stdin:
    try:
        request = json.loads(line)
        cursor = connection.execute(request['sql'], request.get('args') or [])
        mode = request['mode']
        if mode == 'query':
            value = '\n'.join('\x1f'.join('\x1e' if cell is None else str(cell) for cell in row) for row in cursor.fetchall())
        elif mode == 'insert':
            value = str(cursor.lastrowid)
        elif mode == 'change':
            value = str(cursor.rowcount)
        else:
            value = ''
        print(json.dumps({'value': value}), flush=True)
    except Exception as error:
        print(json.dumps({'error': str(error)}), flush=True)
