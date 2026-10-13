package com.zeytindefteri.app;

/** Canonical report identity shared by both social entry points; no Android dependency. */
public final class SocialReportTarget {
    public final String type, targetId, postId, ownerUid, commentId;
    public SocialReportTarget(String contentType, String contentId, String postId, String ownerUid, boolean user) {
        if (!("POST".equals(contentType) || "COMMENT".equals(contentType) || "REPLY".equals(contentType))
                || !validId(contentId) || !validId(postId) || !validId(ownerUid)
                || ("POST".equals(contentType) && !contentId.equals(postId))) throw new IllegalArgumentException("Invalid report target");
        this.type = user ? "USER" : contentType;
        this.targetId = user ? ownerUid : contentId;
        this.postId = postId;
        this.ownerUid = ownerUid;
        this.commentId = "POST".equals(contentType) ? "" : contentId;
    }
    public static boolean validId(String value) {
        return value != null && value.matches("[A-Za-z0-9_-]{1,128}");
    }
    public String key() {
        // User identity does not vary with the post/comment from which it was reported.
        return type + "|" + ("USER".equals(type) ? "" : postId) + "|" + targetId;
    }
}
