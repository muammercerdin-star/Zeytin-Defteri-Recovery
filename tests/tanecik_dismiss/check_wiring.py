from pathlib import Path
s = Path('app/src/main/java/com/zeytindefteri/app/TanecikWalker.java').read_text(encoding='utf-8')
checks = {
    'sürükleme başlayınca X çıkar': 'showDismissTarget();' in s,
    'sürüklerken üstünde mi izlenir': 'trackDismissTarget();' in s,
    "X'e bırakınca kapanır": 'if (dragging && overDismiss) closeByDrop();' in s,
    'kapatma ayarı da kapatır': 'setEnabled(activity, false);\n        setActive(false);\n        Toast.makeText(activity, TanecikDismiss.CLOSED_TOAST' in s,
    'bırakınca X gizlenir': s.count('hideDismissTarget();') >= 3,
    'uzun basma sorusu korunur': 'askHide();' in s,
    'tur sırasında tutulmaz': 'if (tour != null) return false;' in s,
}
for k, v in checks.items(): print(('OK  ' if v else 'FAIL') + ' ' + k)
raise SystemExit(0 if all(checks.values()) else 1)
