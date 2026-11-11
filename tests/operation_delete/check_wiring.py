"""İşlem silme bağlantısı: onaylı, buluta işaretli, hasat bağı korunur."""
from pathlib import Path
a = Path('app/src/main/java/OperationHistoryActivity.java').read_text(encoding='utf-8')
r = Path('app/src/main/java/OperationRepository.java').read_text(encoding='utf-8')
h = Path('app/src/main/java/com/zeytindefteri/app/HarvestRepository.java').read_text(encoding='utf-8')
checks = {
    'detayda Sil düğmesi': 'delete.setOnClickListener(v -> confirmDelete(r))' in a,
    'onay penceresi (Vazgeç)': 'OperationDeleteText.title(linked)' in a and '.setNegativeButton("Vazgeç", null)' in a,
    'hasat hareketi hasatla silinir': 'deleteHarvestWithOperation(linkedHarvest)' in a and 'harvestIdForOperation(r.id)' in a,
    'buluta silme işareti': 'recordCloudDeletion("operations", operationId)' in r,
    'silme buluta hemen gönderilir': 'pushDeletions();' in a,
    'liste yenilenir': a.count('refreshOperations();') >= 2,
    'hasat eşleşmesi hesaba bağlı': "WHERE o.id = ? AND o.type = 'Hasat' AND TRIM(COALESCE(g.owner_uid, '')) = ?" in h,
}
for k, v in checks.items(): print(('OK  ' if v else 'FAIL') + ' ' + k)
raise SystemExit(0 if all(checks.values()) else 1)
