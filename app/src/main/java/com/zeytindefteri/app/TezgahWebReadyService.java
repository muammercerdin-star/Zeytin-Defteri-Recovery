package com.zeytindefteri.app;

import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.os.*;
import java.util.*;
import java.util.concurrent.*;
import okhttp3.*;
import org.json.*;

/** Kayıtlı cihazların hazır soketleri; tüm komutlar aynı sıralı executor üzerinde. */
public final class TezgahWebReadyService extends Service implements TezgahWebChanges.Listener {
    private static final int ID = 7310;
    private static volatile TezgahWebReadyService instance;
    private final ScheduledExecutorService worker = Executors.newSingleThreadScheduledExecutor();
    private final Map<String, Link> links = new HashMap<>();
    private final Map<String, Long> approvalTimes = new HashMap<>();
    private final Map<String, String> approvals = new HashMap<>();
    private final OkHttpClient http = TezgahWebNet.builder().pingInterval(30, TimeUnit.SECONDS).build();
    private String owner;
    private DatabaseHelper helper;
    private volatile boolean stopped;
    private final java.util.concurrent.ConcurrentHashMap<String, String> statuses = new java.util.concurrent.ConcurrentHashMap<>();
    private boolean ticking;
    private long lastCheck;

    public static void prepare(Activity activity) {
        SessionManager session = new SessionManager(activity);
        if (!TezgahWebClient.configured() || !session.hasSession() || TezgahWebDevices.list(activity, session.getUserUid()).length() == 0 || instance != null) return;
        Intent intent = new Intent(activity, TezgahWebReadyService.class);
        try {
            if (Build.VERSION.SDK_INT >= 26) activity.startForegroundService(intent);
            else activity.startService(intent);
        } catch (RuntimeException ignored) { }
    }

    public static void changedDevice(Context context, String owner, String id) {
        TezgahWebReadyService service = instance;
        if (service == null || !owner.equals(service.owner)) return;
        service.worker.execute(() -> {
            Link old = service.links.remove(id);
            JSONObject row = TezgahWebDevices.find(context, owner, id);
            if (old != null) {
                if (row == null && old.socket != null) old.socket.send("{\"v\":1,\"type\":\"forget\"}");
                old.close();
            }
            if (row != null && service.alive()) {
                Link next = service.new Link(row);
                service.links.put(id, next);
                next.open();
            }
        });
    }

    public static int connectedCount(String owner) {
        TezgahWebReadyService service = instance;
        if (service == null || service.stopped || !owner.equals(service.owner)) return 0;
        int count = 0;
        for (String status : service.statuses.values()) if ("Bağlı".equals(status)) count++;
        return count;
    }

    public static String status(String owner, String id) {
        TezgahWebReadyService service = instance;
        return service != null && owner.equals(service.owner) ? service.statuses.getOrDefault(id, "Hazır bekleme kapalı") : "Hazır bekleme kapalı";
    }

    private boolean alive() {
        SessionManager session = new SessionManager(this);
        return !stopped && session.hasSession() && owner.equals(session.getUserUid());
    }

    @Override public void onCreate() {
        super.onCreate();
        instance = this;
        owner = new SessionManager(this).getUserUid();
        if (owner == null) owner = "";
        helper = new DatabaseHelper(this);
        TezgahWebChanges.listen(this);
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && "stop".equals(intent.getAction())) {
            stopSelf(startId);
            return START_NOT_STICKY;
        }
        if (!alive()) {
            stopSelf(startId);
            return START_NOT_STICKY;
        }
        if (intent != null && ("approve".equals(intent.getAction()) || "reject".equals(intent.getAction()))) {
            if (links.isEmpty()) {
                stopSelf(startId);
                return START_NOT_STICKY;
            }
            final String device = intent.getStringExtra("device");
            final String command = intent.getStringExtra("command");
            final boolean accept = "approve".equals(intent.getAction());
            KeyguardManager lock = (KeyguardManager) getSystemService(KEYGUARD_SERVICE);
            if (accept && lock != null && lock.isKeyguardLocked()) return START_NOT_STICKY;
            worker.execute(() -> {
                Link link = links.get(device);
                if (link != null && link.core != null && Objects.equals(approvals.remove(device), command) && SystemClock.elapsedRealtime() < approvalTimes.getOrDefault(device, 0L)) link.core.decide(command, accept);
            });
            return START_NOT_STICKY;
        }
        Notification notification = notification("Bilgisayarlar için hazır", null, null);
        if (Build.VERSION.SDK_INT >= 29) startForeground(ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE);
        else startForeground(ID, notification);
        worker.execute(() -> {
            JSONArray rows = TezgahWebDevices.list(this, owner);
            for (int i = 0; i < rows.length(); i++) {
                JSONObject row = rows.optJSONObject(i);
                if (row != null && !links.containsKey(row.optString("id"))) {
                    Link link = new Link(row);
                    links.put(row.optString("id"), link);
                    link.open();
                }
            }
        });
        if (!ticking) {
            ticking = true;
            worker.scheduleAtFixedRate(this::tick, 1, 1, TimeUnit.SECONDS);
        }
        return START_NOT_STICKY;
    }

    private Notification notification(String text, String device, String command) {
        Notification.Builder builder;
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(new NotificationChannel("tezgah_ready", "Kayıtlı bilgisayarlar", NotificationManager.IMPORTANCE_DEFAULT));
            builder = new Notification.Builder(this, "tezgah_ready");
        } else builder = new Notification.Builder(this);
        builder.setSmallIcon(android.R.drawable.stat_notify_sync).setContentTitle(text)
                .setVisibility(Notification.VISIBILITY_PRIVATE).setOngoing(device == null)
                .setContentIntent(PendingIntent.getActivity(this, ID, new Intent(this, TezgahHomeActivity.class), PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));
        if (device != null) {
            builder.addAction(android.R.drawable.ic_menu_save, "Onayla", action("approve", device, command));
            builder.addAction(android.R.drawable.ic_menu_close_clear_cancel, "Reddet", action("reject", device, command));
        } else builder.addAction(android.R.drawable.ic_menu_close_clear_cancel, "Kapat", action("stop", "", ""));
        return builder.build();
    }

    private PendingIntent action(String action, String device, String command) {
        Intent intent = new Intent(this, TezgahWebReadyService.class).setAction(action);
        intent.setData(android.net.Uri.parse("zd-ready://" + action + "/" + device + "/" + command));
        intent.putExtra("device", device);
        intent.putExtra("command", command);
        return PendingIntent.getService(this, ID, intent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private void tick() {
        if (!alive()) {
            stopSelf();
            return;
        }
        long now = SystemClock.elapsedRealtime();
        if (now - lastCheck < 1000) return;
        lastCheck = now;
        if (!alive()) {
            stopSelf();
            return;
        }
        for (String device : new ArrayList<>(approvals.keySet())) {
            if (SystemClock.elapsedRealtime() >= approvalTimes.getOrDefault(device, 0L)) {
                approvals.remove(device);
                approvalTimes.remove(device);
                ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).cancel(ID + 1);
            }
        }
        for (Link link : links.values()) {
            if (TezgahWebDevices.find(this, owner, link.device.optString("id")) == null) {
                if (link.socket != null) link.socket.send("{\"v\":1,\"type\":\"forget\"}");
                link.close();
                continue;
            }
            if (link.core != null) link.core.tick();
            if (link.socket != null && SystemClock.elapsedRealtime() - link.ping >= 30000) {
                link.socket.send("{\"v\":1,\"type\":\"ping\"}");
                link.ping = SystemClock.elapsedRealtime();
            }
        }
    }

    private final class Link {
        final JSONObject device;
        WebSocket socket;
        TezgahWebDeviceHandshake handshake;
        TezgahWebPhone core;
        long ping;
        int generation;
        boolean closed;
        final ArrayDeque<String> queue = new ArrayDeque<>();
        int bytes;
        boolean pumping;
        double tokens = 65536;
        long tokenAt = SystemClock.elapsedRealtime();
        Link(JSONObject device) { this.device = device; }
        void open() {
            if (!alive() || closed) return;
            final int version = ++generation;
            String bridge = TezgahWebNet.bridgeBase();
            final boolean viaBridge = TezgahWebNet.useBridge(TezgahWebNet.preferBridge, bridge);
            String base = viaBridge ? bridge : TezgahWebNet.directBase(BuildConfig.TEZGAH_RELAY_URL);
            socket = http.newWebSocket(new Request.Builder().url(TezgahWebNet.socketUrl(base, device.optString("room"))).build(), new WebSocketListener() {
                private void dispatch(Runnable task) {
                    if (stopped) return;
                    try {
                        worker.execute(() -> { if (alive() && generation == version) task.run(); });
                    } catch (RejectedExecutionException ignored) { }
                }
                @Override public void onMessage(WebSocket ws, String text) {
                    dispatch(() -> {
                        try { receive(text); }
                        catch (Exception e) { ws.close(1000, "DOGRULAMA_RED"); }
                    });
                }
                @Override public void onFailure(WebSocket ws, Throwable error, Response response) {
                    // Doğrudan yolda ağ hatası (zaman aşımı dahil): sonraki deneme Supabase köprüsünden.
                    if (!viaBridge && response == null && !TezgahWebNet.bridgeBase().isEmpty()) TezgahWebNet.preferBridge = true;
                    dispatch(() -> retry());
                }
                @Override public void onClosing(WebSocket ws, int code, String reason) {
                    ws.close(code, reason);
                    dispatch(() -> {
                        if ("CIHAZ_IPTAL".equals(reason) || "AUTH_RED".equals(reason)) close();
                        else retry();
                    });
                }
            });
            statuses.put(device.optString("id"), "Bilgisayar bekleniyor");
            ping = SystemClock.elapsedRealtime();
        }
        void retry() {
            reset();
            statuses.put(device.optString("id"), "Aktarıcı bağlantısı bekleniyor");
            generation++;
            worker.schedule(this::open, 3 + new java.security.SecureRandom().nextInt(5), TimeUnit.SECONDS);
        }
        void reset() {
            queue.clear();
            bytes = 0;
            approvals.remove(device.optString("id"));
            approvalTimes.remove(device.optString("id"));
            ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).cancel(ID + 1);
            if (core != null) core.close();
            core = null;
            queue.clear();
            bytes = 0;
            statuses.put(device.optString("id"), "Bilgisayar bekleniyor");
            if (handshake != null) handshake.close();
            handshake = null;
        }
        void close() {
            closed = true;
            generation++;
            reset();
            if (socket != null) socket.close(1000, "CIKIS");
            socket = null;
        }
        void receive(String raw) throws Exception {
            if (TezgahWebProtocol.utf8(raw).length > 65536) throw new IllegalArgumentException();
            JSONObject frame = new JSONObject(raw);
            String type = frame.optString("type");
            if (frame.optInt("v") != 1) throw new IllegalArgumentException();
            if ("device_challenge".equals(type)) {
                TezgahWebProtocol.token(frame.getString("nonce"), 32);
                byte[] transcript = TezgahWebDeviceCrypto.transcript("zd-web/device-relay/v1", device.getString("room"), "phone", frame.getString("nonce"));
                send(TezgahWebDto.json("v", 1, "type", "device_auth", "room", device.getString("room"), "role", "phone",
                        "publicKey", device.getString("phoneKey"), "signature", TezgahWebDeviceCrypto.sign(TezgahWebDevices.privateKey(device), transcript)));
            } else if ("auth_ok".equals(type) || "peer_joined".equals(type)) {
                reset();
                handshake = new TezgahWebDeviceHandshake(device.getString("room"), device.getString("id"), device.getString("browserKey"), TezgahWebDevices.privateKey(device));
                send(handshake.ready());
            } else if ("device_hello".equals(type)) {
                send(handshake.hello(frame));
            } else if ("device_finished".equals(type)) {
                handshake.finished(frame);
                final String id = device.getString("id");
                final int version = generation;
                TezgahWebCommands commands = new TezgahWebCommands(helper.getWritableDatabase(), owner, "device:" + id,
                        device.getBoolean("tamKullanim"), () -> {
                            JSONObject current = TezgahWebDevices.find(TezgahWebReadyService.this, owner, id);
                            return alive() && generation == version && current != null && current.optBoolean("tamKullanim") == device.optBoolean("tamKullanim");
                        },
                        new TezgahWebCommands.Host() {
                            public void approval(String command, String message) {
                                approvals.put(id, command);
                                approvalTimes.put(id, SystemClock.elapsedRealtime() + 60000);
                                ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).notify(ID + 1, notification(message, id, command));
                            }
                            public void trace(String message) {
                                approvals.remove(id);
                                ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).cancel(ID + 1);
                                ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).notify(ID, notification(device.optString("ad") + ": " + message, null, null));
                            }
                            public void backup(String command) {
                                new Handler(Looper.getMainLooper()).post(() -> {
                                    if (!alive() || generation != version) return;
                                    TezgahCloudBackup.backupForWeb(TezgahWebReadyService.this, (ok, message) -> {
                                        if (alive() && generation == version) worker.execute(() -> { if (core != null) core.backupDone(command, ok, message); });
                                    });
                                });
                            }
                        });
                core = new TezgahWebPhone(device.getString("room"), handshake.takeKeys(), new TezgahWebRepository(helper.getWritableDatabase(), owner),
                        this::enqueue, ready -> { }, commands);
                device.put("lastUsed", System.currentTimeMillis());
                TezgahWebDevices.save(TezgahWebReadyService.this, owner, device);
                send(TezgahWebDto.json("v", 1, "type", "device_active"));
                core.activateDevice();
                statuses.put(id, "Bağlı");
            } else if ("encrypted".equals(type) && core != null) core.receive(raw, SystemClock.elapsedRealtime());
            else if ("peer_left".equals(type)) reset();
        }
        void enqueue(String text) {
            int length = TezgahWebProtocol.utf8(text).length;
            if (queue.size() >= 128 || bytes + length > 2097152) {
                retry();
                throw new IllegalStateException("KUYRUK_DOLU");
            }
            queue.add(text);
            bytes += length;
            if (!pumping) {
                pumping = true;
                worker.execute(this::flush);
            }
        }
        void flush() {
            if (closed || !alive() || queue.isEmpty()) {
                pumping = false;
                return;
            }
            pump(SystemClock.elapsedRealtime());
            if (!queue.isEmpty()) worker.schedule(this::flush, 100, TimeUnit.MILLISECONDS);
            else pumping = false;
        }
        void pump(long now) {
            tokens = Math.min(65536, tokens + Math.max(0, now - tokenAt) * 32.768);
            tokenAt = now;
            if (socket == null || queue.isEmpty()) return;
            String text = queue.peek();
            int length = TezgahWebProtocol.utf8(text).length;
            if (tokens < length) return;
            if (socket.send(text)) {
                queue.remove();
                bytes -= length;
                tokens -= length;
            }
        }
        void send(JSONObject frame) { enqueue(frame.toString()); }
    }

    @Override public void changed(String changedOwner, long revision) {
        if (stopped || !owner.equals(changedOwner)) return;
        worker.execute(() -> { for (Link link : links.values()) if (link.core != null) link.core.changed(revision); });
    }
    @Override public void reset(String changedOwner) {
        if (changedOwner == null || owner.equals(changedOwner)) {
            stopped = true;
            stopSelf();
        }
    }
    @Override public void onDestroy() {
        stopped = true;
        instance = null;
        TezgahWebChanges.remove(this);
        worker.execute(() -> {
            for (Link link : links.values()) link.close();
            helper.close();
            http.connectionPool().evictAll();
            http.dispatcher().executorService().shutdown();
            worker.shutdown();
        });
        ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).cancel(ID + 1);
        super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent) { return null; }
}
