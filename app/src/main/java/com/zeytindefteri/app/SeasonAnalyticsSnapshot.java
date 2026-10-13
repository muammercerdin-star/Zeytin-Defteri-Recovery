package com.zeytindefteri.app;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class SeasonAnalyticsSnapshot {
    public final GardenAnalyticsSnapshot total;
    public final Map<Long,GardenAnalyticsSnapshot> gardens;
    SeasonAnalyticsSnapshot(GardenAnalyticsSnapshot total, Map<Long,GardenAnalyticsSnapshot> gardens) {
        this.total=total; this.gardens=Collections.unmodifiableMap(new LinkedHashMap<>(gardens));
    }
    public GardenAnalyticsSnapshot garden(long id) { return id==-1?total:gardens.get(id); }
}
