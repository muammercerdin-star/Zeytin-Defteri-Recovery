package com.zeytindefteri.app;

import android.content.ContentValues;
import android.database.Cursor;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Bounded shared reply stream; SQLite is the durable outbox and local cache. */
public final class CommentCloudRepository {
    public static final int PAGE_SIZE = 100;
    private final DatabaseHelper db;
    private final FirebaseFirestore cloud = FirebaseFirestore.getInstance();
    private final FirebaseAuth auth = FirebaseAuth.getInstance();
    private final Set<Long> inFlight = new HashSet<>();
    private ListenerRegistration listener;
    private FirebaseAuth.AuthStateListener authListener;
    private DocumentSnapshot oldest;
    private Callback streamCallback;
    private boolean closed;
    private boolean loadingOlder;
    private int generation;

    public interface Callback { void complete(boolean success); }
    public CommentCloudRepository(DatabaseHelper db) { this.db = db; }

    public long add(long postId, String text) { return add(postId, text, ""); }

    public long add(long postId, String text, String parentCloudId) {
        FirebaseUser user = auth.getCurrentUser();
        String content = text == null ? "" : text.trim();
        if (user == null || content.isEmpty() || content.length() > 500) return -1;
        String name = user.getDisplayName();
        long id;
        try { id = db.addPostComment(postId, user.getUid(),
                name == null || name.trim().isEmpty() ? "Üretici" : name.trim(), content, parentCloudId); } catch (RuntimeException error) { return -1; }
        if (id > 0) flushPending();
        return id;
    }

    public boolean delete(long id) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null || !db.deletePostComment(id, user.getUid())) return false;
        flushPending();
        return true;
    }

    public void flushPending() {
        FirebaseUser user = auth.getCurrentUser();
        if (closed || user == null) return;
        // Legacy rows are uploaded only by their exact Firebase owner; anonymous rows stay local.
        try (Cursor c = db.getReadableDatabase().rawQuery(
                "SELECT id FROM post_comments WHERE user_key = ? AND sync_state IN (0,1) " +
                "AND (is_active = 0 OR length(content) BETWEEN 1 AND 500) ORDER BY id LIMIT 50",
                new String[]{user.getUid()})) {
            while (c.moveToNext()) publish(c.getLong(0), user.getUid());
        }
    }

    private void publish(long id, String uid) {
        if (!inFlight.add(id)) return;
        try (Cursor c = db.getPostCommentById(id)) {
            if (!c.moveToFirst() || !uid.equals(string(c, "user_key"))) { inFlight.remove(id); return; }
            String post = string(c, "post_cloud_id"), comment = string(c, "cloud_id");
            if (post.isEmpty() || comment.isEmpty()) { inFlight.remove(id); return; }
            String parentId = string(c, "parent_comment_id");
            // A queued offline parent must reach Firestore before its child is published.
            if (!parentId.isEmpty()) {
                try (Cursor parent = db.getPostCommentByCloudId(parentId)) {
                    if (parent.moveToFirst() && number(parent, "sync_state") != 2) {
                        inFlight.remove(id); return;
                    }
                }
            }
            long version = number(c, "updated_at");
            Map<String,Object> data = new HashMap<>();
            data.put("cloud_id", comment);
            data.put("post_cloud_id", post);
            data.put("user_id", uid);
            data.put("user_name", string(c, "user_name"));
            data.put("content", number(c, "is_active") == 1 ? string(c, "content") : "");
            data.put("is_active", number(c, "is_active"));
            data.put("created_at", number(c, "created_at"));
            data.put("updated_at", version);
            data.put("parent_comment_id", parentId);
            String rootId = string(c, "root_comment_id");
            data.put("root_comment_id", rootId.isEmpty() ? comment : rootId);
            data.put("reply_to_uid", string(c, "reply_to_uid"));
            data.put("reply_to_name", string(c, "reply_to_name"));
            cloud.collection("social_posts").document(post).collection("comments").document(comment)
                    .set(data).addOnSuccessListener(unused -> {
                        inFlight.remove(id);
                        if (closed) return;
                        ContentValues v = new ContentValues(); v.put("sync_state", 2);
                        db.getWritableDatabase().update("post_comments", v,
                                "id = ? AND user_key = ? AND updated_at = ?",
                                new String[]{String.valueOf(id), uid, String.valueOf(version)});
                        if (streamCallback != null) streamCallback.complete(true);
                        flushPending();
                    }).addOnFailureListener(error -> inFlight.remove(id));
        } catch (RuntimeException error) { inFlight.remove(id); }
    }

    private Query query(String post) {
        return cloud.collection("social_posts").document(post).collection("comments")
                .orderBy("created_at", Query.Direction.DESCENDING).limit(PAGE_SIZE);
    }

    public void listen(long postId, Callback callback) {
        stop();
        streamCallback = callback;
        final int token = generation;
        FirebaseUser user = auth.getCurrentUser();
        String post = db.getPostCloudId(postId);
        if (closed || user == null || post.isEmpty()) { callback.complete(false); return; }
        final String uid = user.getUid();
        authListener = firebaseAuth -> {
            FirebaseUser current = firebaseAuth.getCurrentUser();
            if (current == null || !uid.equals(current.getUid())) { stop(); callback.complete(false); }
        };
        auth.addAuthStateListener(authListener);
        flushPending();
        listener = query(post).addSnapshotListener((snapshot, error) -> {
            if (closed || token != generation) return;
            if (error != null || snapshot == null) { callback.complete(false); return; }
            try {
                for (DocumentSnapshot doc : snapshot.getDocuments()) importReply(post, doc);
                if (oldest == null && !snapshot.isEmpty()) oldest = snapshot.getDocuments().get(snapshot.size()-1);
                // REMOVED can mean a limit-window eviction, so never delete SQLite rows on absence.
                callback.complete(true);
            } catch (RuntimeException failure) { callback.complete(false); }
        });
    }

    public void loadOlder(long postId, Callback callback) {
        if (closed || loadingOlder || auth.getCurrentUser() == null || oldest == null) { callback.complete(false); return; }
        final int token = generation;
        String post = db.getPostCloudId(postId);
        loadingOlder = true;
        query(post).startAfter(oldest).get().addOnSuccessListener(snapshot -> {
            if (closed || token != generation) return;
            loadingOlder = false;
            try {
                for (DocumentSnapshot doc : snapshot.getDocuments()) importReply(post, doc);
                if (!snapshot.isEmpty()) oldest = snapshot.getDocuments().get(snapshot.size()-1);
                callback.complete(!snapshot.isEmpty());
            } catch (RuntimeException failure) { callback.complete(false); }
        }).addOnFailureListener(error -> { if (!closed && token == generation) { loadingOlder = false; callback.complete(false); } });
    }

    private void importReply(String post, DocumentSnapshot doc) {
        // A pending local delete must not be resurrected by an older cached snapshot.
        try (Cursor c = db.getPostCommentByCloudId(doc.getId())) {
            if (c.moveToFirst()) {
                if (!post.equals(string(c,"post_cloud_id"))) return;
                // Bulutta kapatılmış yanıt (moderatör ya da sahibi) yerel bekleyen değişikliği geçersiz kılar:
                // kapalı yanıta yazma kurallarca reddedilir ve sonsuza dek yeniden denenirdi.
                if (number(c,"sync_state") == 1 && !cloudRemoved(doc)) return;
            }
        }
        long id = db.upsertPostCommentFromCloud(post, doc.getId(), value(doc,"user_id"),
                value(doc,"user_name"), value(doc,"content"),
                doc.contains("is_active") ? (int) numeric(doc,"is_active") : 1,
                numeric(doc,"created_at"), numeric(doc,"updated_at"),
                value(doc,"parent_comment_id"), value(doc,"root_comment_id"),
                value(doc,"reply_to_uid"), value(doc,"reply_to_name"));
        if (id > 0) {
            ContentValues v = new ContentValues(); v.put("sync_state", 2);
            db.getWritableDatabase().update("post_comments", v,
                    cloudRemoved(doc) ? "id = ?" : "id = ? AND sync_state <> 1",
                    new String[]{String.valueOf(id)});
        }
    }
    private static boolean cloudRemoved(DocumentSnapshot doc) {
        return doc.contains("is_active") && numeric(doc, "is_active") == 0;
    }
    public void stop() {
        generation++;
        streamCallback = null;
        if (listener != null) { listener.remove(); listener = null; }
        if (authListener != null) { auth.removeAuthStateListener(authListener); authListener = null; }
        oldest = null;
        loadingOlder = false;
    }
    public void close() { closed = true; stop(); }
    static String string(Cursor c, String key) {
        int i = c.getColumnIndex(key); return i < 0 || c.isNull(i) ? "" : c.getString(i);
    }
    static long number(Cursor c, String key) {
        int i = c.getColumnIndex(key); return i < 0 || c.isNull(i) ? 0 : c.getLong(i);
    }
    private static String value(DocumentSnapshot d, String k) {
        Object value = d.get(k); return value instanceof String ? (String)value : "";
    }
    private static long numeric(DocumentSnapshot d, String k) {
        Object value = d.get(k); return value instanceof Number ? ((Number)value).longValue() : 0;
    }
}
