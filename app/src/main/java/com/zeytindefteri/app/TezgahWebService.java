package com.zeytindefteri.app;

import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.os.*;

/** Tek oturum, RAM anahtarları, telefonun verdiği ve yalnız düşürülebilen yetki. */
public final class TezgahWebService extends Service implements TezgahWebChanges.Listener {
    private static final String STOP = "com.zeytindefteri.app.WEB_STOP";
    private static final String APPROVE = "com.zeytindefteri.app.WEB_APPROVE";
    private static final String REJECT = "com.zeytindefteri.app.WEB_REJECT";
    private static final String CHANNEL = "tezgah_web";
    private static final String REVIEW_CHANNEL = "tezgah_web_review";
    private static final int ID = 7301;
    private static final int REVIEW_ID = 7302;
    private static volatile TezgahWebService instance;
    private static Pending pending;
    private static final class Pending {
        final long generation = TezgahWebChanges.resetGeneration();
        final TezgahWebPhone.Qr qr;
        final String owner;
        final String name;
        final boolean full;
        Pending(TezgahWebPhone.Qr qr, String owner, String name, boolean full) {
            this.qr = qr;
            this.owner = owner;
            this.name = name;
            this.full = full;
        }
    }
    private String owner = "";
    private String name = "";
    private String room = "";
    private String reviewId = "";
    private String reviewText = "";
    private long reviewUntil;
    private String trace = "";
    private volatile boolean ready;
    private volatile boolean ended;
    private volatile long generation;
    private int latestStartId;
    private String diagnosis = "Bağlanıyor";
    private DatabaseHelper helper;
    private TezgahWebClient client;
    private TezgahWebCommands commands;
    private final Handler main = new Handler(Looper.getMainLooper());

    public static void launch(Activity activity, TezgahWebPhone.Qr qr, String owner, String name) {
        launch(activity, qr, owner, name, false);
    }

    public static synchronized void launch(Activity activity, TezgahWebPhone.Qr qr, String owner, String name, boolean full) {
        if (!TezgahWebClient.configured()) return;
        pending = new Pending(qr, owner, name, full);
        Intent intent = new Intent(activity, TezgahWebService.class);
        try {
            if (Build.VERSION.SDK_INT >= 26) activity.startForegroundService(intent);
            else activity.startService(intent);
        } catch (RuntimeException e) {
            pending = null;
            throw e;
        }
    }

    public static boolean running(String owner) {
        TezgahWebService s = instance;
        return s != null && !s.ended && s.owner.equals(owner);
    }

    public static boolean connected(String owner) {
        TezgahWebService s = instance;
        return running(owner) && s.ready;
    }

    public static boolean connected(String owner, String id) {
        return connected(owner) && instance.room.equals(id);
    }

    public static boolean running(String owner, String id) {
        return running(owner) && instance.room.equals(id);
    }

    public static boolean writable(String owner) {
        TezgahWebService s = instance;
        return running(owner) && s.commands != null && s.commands.writable();
    }

    public static void downgrade(String owner) {
        TezgahWebService s = instance;
        if (!running(owner) || s.commands == null) return;
        // Kuyrukta bekleyen komut da bu andan itibaren yeni yetki alamaz.
        s.commands.revoke();
        s.client.downgrade();
        TezgahWebSessions.permission(s, owner, s.room, false);
        s.clearReview();
        s.refreshNotification();
    }

    public static void disconnect(Context context) {
        TezgahWebService s = instance;
        if (s != null) {
            s.diagnosis = "Bağlantı telefondan kapatıldı";
            s.end();
        }
        context.stopService(new Intent(context, TezgahWebService.class));
    }

    public static boolean hasReview(String owner) {
        TezgahWebService s = instance;
        return running(owner) && !s.reviewId.isEmpty() && SystemClock.elapsedRealtime() < s.reviewUntil;
    }

    public static void review(Activity activity, String owner) {
        TezgahWebService s = instance;
        if (!running(owner) || s.reviewId.isEmpty() || SystemClock.elapsedRealtime() >= s.reviewUntil) return;
        final String id = s.reviewId;
        final String room = s.room;
        new AlertDialog.Builder(activity).setTitle(s.name + " · İşlem onayı")
                .setMessage(s.reviewText + "\n60 saniye içinde onaylanmazsa iptal edilir.")
                .setPositiveButton("Onayla", (dialog, which) -> s.decide(room, id, true))
                .setNegativeButton("Reddet", (dialog, which) -> s.decide(room, id, false)).show();
    }

    public static String lastTrace(String owner) {
        return running(owner) ? instance.trace : "";
    }

    @Override public void onCreate() {
        super.onCreate();
        instance = this;
        TezgahWebChanges.listen(this);
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        latestStartId = startId;
        String action = intent == null ? "" : intent.getAction();
        if (STOP.equals(action)) {
            end();
            return START_NOT_STICKY;
        }
        if (APPROVE.equals(action) || REJECT.equals(action)) {
            decide(intent.getStringExtra("room"), intent.getStringExtra("id"), APPROVE.equals(action));
            if (client == null) stopSelf();
            return START_NOT_STICKY;
        }
        Pending p;
        synchronized (TezgahWebService.class) {
            p = pending;
            pending = null;
        }
        if (p == null || p.generation != TezgahWebChanges.resetGeneration() || !TezgahWebClient.configured()) {
            end();
            return START_NOT_STICKY;
        }
        generation++;
        final long currentGeneration = generation;
        if (client != null) client.close();
        owner = p.owner;
        name = p.name;
        room = p.qr.room;
        ended = false;
        ready = false;
        diagnosis = "Bağlanıyor";
        if (!sameOwner()) {
            end();
            return START_NOT_STICKY;
        }
        try {
            if (Build.VERSION.SDK_INT >= 29) startForeground(ID, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE);
            else startForeground(ID, notification());
            helper = new DatabaseHelper(this);
            final DatabaseHelper ownedHelper = helper;
            java.util.function.BooleanSupplier alive = () -> !ended && generation == currentGeneration && sameOwner();
            commands = new TezgahWebCommands(helper.getWritableDatabase(), owner, room, p.full, alive,
                    new TezgahWebCommands.Host() {
                        @Override public void approval(String id, String message) {
                            main.post(() -> {
                                if (!alive.getAsBoolean() || !commands.writable()) return;
                                reviewId = id;
                                reviewText = message;
                                reviewUntil = SystemClock.elapsedRealtime() + 60000;
                                showReviewNotification();
                                main.postDelayed(() -> {
                                    if (id.equals(reviewId)) clearReview();
                                }, 60000);
                            });
                        }
                        @Override public void trace(String message) {
                            main.post(() -> {
                                if (!alive.getAsBoolean()) return;
                                trace = name + ": " + message;
                                clearReview();
                                refreshNotification();
                            });
                        }
                        @Override public void backup(String id) {
                            main.post(() -> {
                                if (!alive.getAsBoolean() || !commands.writable()) return;
                                TezgahCloudBackup.backupForWeb(TezgahWebService.this, (ok, message) -> {
                                    if (alive.getAsBoolean()) client.backupDone(id, ok, message);
                                });
                            });
                        }
                    }, System::currentTimeMillis, SystemClock::elapsedRealtime,
                    () -> getSharedPreferences("zd_tezgah", MODE_PRIVATE).getFloat("rate_" + owner, 0f));
            client = new TezgahWebClient(p.qr, new TezgahWebRepository(helper.getWritableDatabase(), owner), alive, (on, detail) -> {
                if (generation != currentGeneration) return;
                ready = on;
                diagnosis = detail;
                TezgahWebSessions.update(this, owner, room, name, on, ended || (client != null && client.stopped()));
                TezgahWebSessions.diagnosis(this, owner, room, detail);
                main.post(() -> {
                    if (generation == currentGeneration && client != null && client.stopped()) end();
                });
            });
            client.enrollment((request, capability) -> {
                if (!alive.getAsBoolean()) throw new IllegalStateException("Oturum kapandı.");
                org.json.JSONObject existing = TezgahWebDevices.find(this, p.owner, request.getString("id"));
                if (existing != null) {
                    if (!existing.getString("browserKey").equals(request.getString("publicKey"))) throw new IllegalArgumentException("Cihaz anahtarı uyuşmuyor.");
                    return TezgahWebDto.json("id", existing.getString("id"), "room", existing.getString("room"), "phoneKey", existing.getString("phoneKey"));
                }
                org.json.JSONObject device = TezgahWebDevices.create(this, p.owner, p.name, commands.writable(), request);
                boolean saved = false;
                okhttp3.OkHttpClient registration = new okhttp3.OkHttpClient.Builder()
                        .callTimeout(15, java.util.concurrent.TimeUnit.SECONDS).build();
                try {
                    org.json.JSONObject body = TezgahWebDto.json("pairRoom", p.qr.room, "room", device.getString("room"),
                            "browserHash", TezgahWebCrypto.base64(TezgahWebCrypto.sha256(TezgahWebProtocol.utf8(device.getString("browserKey")))),
                            "phoneHash", TezgahWebCrypto.base64(TezgahWebCrypto.sha256(TezgahWebProtocol.utf8(device.getString("phoneKey")))));
                    String base = BuildConfig.TEZGAH_RELAY_URL.trim();
                    while (base.endsWith("/")) base = base.substring(0, base.length() - 1);
                    okhttp3.Request register = new okhttp3.Request.Builder().url(base + "/v2/devices")
                            .header("Authorization", "Bearer " + capability)
                            .post(okhttp3.RequestBody.create(okhttp3.MediaType.get("application/json; charset=utf-8"), body.toString())).build();
                    try (okhttp3.Response response = registration.newCall(register).execute()) {
                        if (response.code() != 201 || !alive.getAsBoolean()) throw new IllegalStateException("Cihaz kaydedilemedi.");
                    }
                    device.put("tamKullanim", commands.writable());
                    TezgahWebDevices.save(this, p.owner, device);
                    saved = true;
                    return TezgahWebDto.json("id", device.getString("id"), "room", device.getString("room"), "phoneKey", device.getString("phoneKey"));
                } finally {
                    if (!saved) TezgahWebDevices.discard(device);
                    registration.connectionPool().evictAll();
                    registration.dispatcher().executorService().shutdown();
                }
            });
            client.commands(commands);
            client.release(ownedHelper::close);
            TezgahWebSessions.update(this, owner, room, name, false, false);
            TezgahWebSessions.permission(this, owner, room, p.full);
            refreshNotification();
            client.start();
        } catch (Exception e) {
            if (client == null && helper != null) helper.close();
            end();
            android.widget.Toast.makeText(this, "Bilgisayar bağlantısı açılamadı. Yeni QR okut.", android.widget.Toast.LENGTH_LONG).show();
        }
        return START_NOT_STICKY;
    }

    private void decide(String expectedRoom, String id, boolean approve) {
        if (ended || !sameOwner() || !room.equals(expectedRoom) || !reviewId.equals(id) || id == null || id.isEmpty()) return;
        KeyguardManager lock = (KeyguardManager) getSystemService(KEYGUARD_SERVICE);
        if (approve && lock != null && lock.isKeyguardLocked()) {
            android.widget.Toast.makeText(this, "Onaylamak için telefonun kilidini aç.", android.widget.Toast.LENGTH_LONG).show();
            return;
        }
        if (client != null) client.decide(id, approve && SystemClock.elapsedRealtime() < reviewUntil);
        clearReview();
    }

    private boolean sameOwner() {
        SessionManager session = new SessionManager(this);
        return session.hasSession() && owner.equals(session.getUserUid());
    }

    private NotificationManager manager() {
        return (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
    }

    private Notification.Builder builder(String channel) {
        if (Build.VERSION.SDK_INT >= 26) {
            manager().createNotificationChannel(new NotificationChannel(channel, "Bilgisayar bağlantısı",
                    channel.equals(REVIEW_CHANNEL) ? NotificationManager.IMPORTANCE_DEFAULT : NotificationManager.IMPORTANCE_LOW));
            return new Notification.Builder(this, channel);
        }
        return new Notification.Builder(this);
    }

    private PendingIntent action(String action, int code) {
        Intent intent = new Intent(this, TezgahWebService.class).setAction(action);
        intent.setData(android.net.Uri.parse("zd-web://approval/" + room + "/" + reviewId + "/" + action));
        intent.putExtra("room", room);
        intent.putExtra("id", reviewId);
        return PendingIntent.getService(this, code, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private PendingIntent open() {
        return PendingIntent.getActivity(this, ID, new Intent(this, TezgahHomeActivity.class), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private Notification notification() {
        String permission = commands != null && commands.writable() ? "Tam kullanım" : "Yalnız görüntüleme";
        return builder(CHANNEL).setSmallIcon(android.R.drawable.stat_notify_sync)
                .setContentTitle("Bilgisayar bağlantısı açık").setContentText(trace.isEmpty() ? permission + " · " + name : trace)
                .setStyle(new Notification.BigTextStyle().bigText(permission + " · " + name + "\n" + trace))
                .setOngoing(true).setContentIntent(open())
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Bağlantıyı kes", action(STOP, ID)).build();
    }

    private void refreshNotification() {
        if (!ended) manager().notify(ID, notification());
    }

    private void showReviewNotification() {
        manager().notify(REVIEW_ID, builder(REVIEW_CHANNEL).setSmallIcon(android.R.drawable.stat_notify_sync)
                .setVisibility(Notification.VISIBILITY_PRIVATE)
                .setContentTitle(name + " · Onay bekleniyor").setContentText(reviewText)
                .setStyle(new Notification.BigTextStyle().bigText(reviewText)).setContentIntent(open())
                .addAction(android.R.drawable.ic_menu_save, "Onayla", action(APPROVE, REVIEW_ID))
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Reddet", action(REJECT, REVIEW_ID + 1)).build());
    }

    private void clearReview() {
        reviewId = "";
        reviewText = "";
        manager().cancel(REVIEW_ID);
    }

    private synchronized void end() {
        if (ended) return;
        ended = true;
        generation++;
        ready = false;
        if (commands != null) commands.revoke();
        if (client != null) client.close();
        if (!room.isEmpty()) TezgahWebSessions.update(this, owner, room, name, false);
        if (!room.isEmpty()) TezgahWebSessions.diagnosis(this, owner, room, diagnosis);
        final long endedGeneration = generation;
        final int endedStartId = latestStartId;
        main.post(() -> {
            // Önceki bağlantının gecikmiş kapanışı yeni launch'u durduramaz.
            if (!ended || generation != endedGeneration || latestStartId != endedStartId) return;
            clearReview();
            stopForeground(true);
            stopSelf(endedStartId);
        });
    }

    @Override public void changed(String changedOwner, long revision) {
        if (owner.equals(changedOwner) && client != null) client.changed(revision);
    }

    @Override public void reset(String changedOwner) {
        if (changedOwner == null || owner.equals(changedOwner)) end();
    }

    @Override public void onDestroy() {
        end();
        TezgahWebChanges.remove(this);
        if (instance == this) instance = null;
        // SQLite, istemcinin sıralı kuyruğu bittikten sonra release ile kapanır.
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) {
        return null;
    }
}
