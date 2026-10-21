package com.zeytindefteri.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.database.Cursor;
import android.content.Intent;
import android.view.inputmethod.InputMethodManager;
import android.widget.HorizontalScrollView;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.content.SharedPreferences;
import com.bumptech.glide.Glide;
import android.text.TextUtils;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputFilter;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;

/** Full-screen, resize-safe text replies. The composer stays outside the scroll area. */
public final class PostRepliesActivity extends Activity {
    private String selectedParentCommentId = "", selectedRootCommentId = "", replyToUid = "", replyToName = "";
    private LinearLayout replyIndicator;
    private TextView replyIndicatorText;
    private final Map<Long, Reply> loadedReplies = new LinkedHashMap<>();
    private static final class Reply {
        long id, at, sync;
        String cloud, parent, root, uid, name, content, replyName;
        boolean active;
    }
    private SocialBlockRepository socialBlocks;
    private String postOwnerUid = "";
    private DatabaseHelper db;
    private CommentCloudRepository comments;
    private long postId;
    private LinearLayout replies;
    private EditText input;
    private TextView status;
    private int visibleLimit = 100;
    private boolean visible;
    private boolean sending;
    private String sessionUid;
    private String postCategory = "GENERAL";
    private ReactionManager reactions;
    private String ownAvatarSource;
    private final Locale tr = new Locale("tr", "TR");
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd MMM HH:mm", tr);
    private final SimpleDateFormat clockFormat = new SimpleDateFormat("HH:mm", tr);
    private static final String[] CATEGORIES = {"GENERAL","MARKET","HARVEST","PEST","IRRIGATION","SALE"};
    private static final String[] CATEGORY_LABELS = {"GENEL","PİYASA","HASAT","HASTALIK","SULAMA","ALIM-SATIM"};

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) { Toast.makeText(this,"Yanıtları görmek için giriş yapmalısın.",Toast.LENGTH_SHORT).show(); finish(); return; }
        sessionUid = user.getUid();
        socialBlocks = new SocialBlockRepository(this);
        postId = getIntent().getLongExtra("post_id", -1);
        db = new DatabaseHelper(this);
        try (Cursor c = db.getPostById(postId)) {
            if (!c.moveToFirst()) { finish(); return; }
            postOwnerUid = CommentCloudRepository.string(c,"user_id");
            if(socialBlocks.blocked(postOwnerUid)){finish();return;}
            postCategory = CommentCloudRepository.string(c,"post_type");
        }
        comments = new CommentCloudRepository(db);
        SharedPreferences photoPrefs = getSharedPreferences("zeytin_profile_prefs",MODE_PRIVATE);
        String photoMode = photoPrefs.getString("photo_mode_"+sessionUid,"AUTO");
        if (!"DEFAULT".equals(photoMode)) {
            if ("AUTO".equals(photoMode) || "CUSTOM".equals(photoMode))
                ownAvatarSource = photoPrefs.getString("custom_photo_uri_"+sessionUid,"");
            if ((ownAvatarSource == null || ownAvatarSource.isEmpty()) && user.getPhotoUrl()!=null)
                ownAvatarSource = user.getPhotoUrl().toString();
        }
        reactions = new ReactionManager(this,db,null);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        getWindow().setStatusBarColor(color(R.color.reply_bg));
        getWindow().setNavigationBarColor(color(R.color.reply_bg));
        getWindow().getDecorView().setSystemUiVisibility(0);
        LinearLayout root = column(); root.setFitsSystemWindows(true); root.setBackgroundColor(color(R.color.reply_bg));
        LinearLayout header = new LinearLayout(this); header.setGravity(Gravity.CENTER_VERTICAL);
        TextView back = label("←",24,R.color.zd_text,true); back.setGravity(Gravity.CENTER);
        back.setContentDescription("Geri"); back.setOnClickListener(v -> finish());
        header.addView(back,new LinearLayout.LayoutParams(dp(48),dp(52)));
        header.addView(label("MESAJ YANITLA",16,R.color.zd_text,true)); root.addView(header);
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true); scroll.setVerticalScrollBarEnabled(false);
        LinearLayout body = column(); body.setPadding(dp(16),0,dp(16),dp(12));
        body.addView(categoryChips());
        TextView market = label("Piyasa • Bölgesel fiyatları gör  ›",12,R.color.zd_text_soft,false);
        market.setPadding(dp(12),dp(10),dp(12),dp(10));
        market.setBackground(background(R.color.reply_surface));
        market.setOnClickListener(v -> startActivity(new Intent(this,MarketActivity.class)));
        LinearLayout.LayoutParams marketParams = new LinearLayout.LayoutParams(-1,-2);
        marketParams.bottomMargin = dp(16); body.addView(market,marketParams);
        try (Cursor c = db.getPostById(postId)) {
            if (c.moveToFirst()) {
                body.addView(conversationItem(
                    CommentCloudRepository.string(c,"user_id"),CommentCloudRepository.string(c,"user_name"),
                    CommentCloudRepository.string(c,"content"),CommentCloudRepository.number(c,"created_at"),
                    CommentCloudRepository.string(c,"district"),-1,2,true));
            }
        }
        TextView heading = label("YANITLAR",12,R.color.zd_muted,true); heading.setPadding(0,dp(10),0,dp(6)); body.addView(heading);
        TextView older = label("Önceki yanıtları yükle",13,R.color.zd_green,true);
        older.setPadding(0,dp(8),0,dp(12));
        older.setOnClickListener(v -> {
            visibleLimit += 100; render(); 
            comments.loadOlder(postId, ok -> {
                if (!visible || isFinishing()) return;
                 render();
                if (!ok) Toast.makeText(this,"Daha eski yanıt yok veya bağlantı kurulamadı.",Toast.LENGTH_SHORT).show();
            });
            // A stopped activity may drop the callback; onStart rebuilds enabled state.
        });
        body.addView(older);
        replies = column(); body.addView(replies); scroll.addView(body);
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        status = label("Telefondaki yanıtlar • bulut bağlantısı bekleniyor",11,R.color.zd_muted,false);
        status.setPadding(dp(16),dp(4),dp(16),dp(2)); status.setSingleLine(true); status.setEllipsize(TextUtils.TruncateAt.END); root.addView(status);
        replyIndicator = new LinearLayout(this); replyIndicator.setGravity(Gravity.CENTER_VERTICAL);
        replyIndicator.setPadding(dp(16),0,dp(8),0);
        replyIndicatorText = label("",12,R.color.zd_green,false);
        replyIndicatorText.setMaxLines(2); replyIndicatorText.setEllipsize(TextUtils.TruncateAt.END);
        replyIndicator.addView(replyIndicatorText,new LinearLayout.LayoutParams(0,-2,1));
        TextView cancelReply = label("×",22,R.color.zd_text,true); cancelReply.setGravity(Gravity.CENTER);
        cancelReply.setContentDescription("Yanıtlamayı iptal et"); cancelReply.setOnClickListener(v -> clearReply());
        replyIndicator.addView(cancelReply,new LinearLayout.LayoutParams(dp(44),dp(44)));
        root.addView(replyIndicator); replyIndicator.setVisibility(View.GONE);
        if (state != null) {
            selectedParentCommentId = state.getString("reply_parent", "");
            selectedRootCommentId = state.getString("reply_root", "");
            replyToUid = state.getString("reply_uid", ""); replyToName = state.getString("reply_name", "");
            updateReplyIndicator();
        }
        LinearLayout composer = new LinearLayout(this); composer.setGravity(Gravity.CENTER_VERTICAL);
        composer.setPadding(dp(12),dp(8),dp(12),dp(10)); composer.setBackgroundColor(color(R.color.zd_surface));
        input = new EditText(this); input.setBackgroundTintList(null); input.setHint("Mesaj yaz..."); input.setTextSize(14);
        input.setTextColor(color(R.color.zd_text)); input.setHintTextColor(color(R.color.zd_muted));
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE | android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setMaxLines(3); input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(500)});
        input.setBackground(background(R.color.zd_input)); input.setPadding(dp(14),dp(10),dp(14),dp(10));
        if (state != null) input.setText(state.getString("draft", ""));
        composer.addView(input,new LinearLayout.LayoutParams(0,-2,1));
        TextView category = label(categoryLabel(postCategory)+" ▾",10,R.color.zd_text_soft,true);
        category.setSingleLine(true); category.setEllipsize(TextUtils.TruncateAt.END);
        category.setGravity(Gravity.CENTER); category.setPadding(dp(5),0,dp(5),0);
        category.setContentDescription("Gönderi kategorisi: "+categoryLabel(postCategory)+". Meydan kategorilerini aç");
        category.setBackground(background(R.color.reply_surface));
        category.setOnClickListener(v -> new AlertDialog.Builder(this).setTitle("Meydan kategorisine git")
            .setItems(CATEGORY_LABELS,(dialog,index) -> openCategory(CATEGORIES[index])).setNegativeButton("Vazgeç",null).show());
        LinearLayout.LayoutParams categoryParams = new LinearLayout.LayoutParams(dp(68),dp(44));
        categoryParams.leftMargin=dp(6); composer.addView(category,categoryParams);
        TextView send = label("➤",24,R.color.zd_bg,true); send.setGravity(Gravity.CENTER);
        send.setBackground(background(R.color.zd_green)); send.setContentDescription("Yanıtı gönder");
        LinearLayout.LayoutParams sendParams = new LinearLayout.LayoutParams(dp(44),dp(44)); sendParams.leftMargin=dp(6);
        composer.addView(send,sendParams);
        send.setOnClickListener(v -> {
            if (sending) return;
            String text = input.getText().toString().trim();
            if (text.isEmpty()) { input.setError("Bir yanıt yazmalısın."); return; }
            // Moderasyon: küfür filtresi ve susturma (gönderilmeden, telefonda).
            if (!ChatGuard.allow(this, input, text)) return;
            sending = true; send.setEnabled(false);
            long id = comments.add(postId,text,selectedParentCommentId);
            if (id > 0) {
                input.setText(""); clearReply(); status.setText("Yanıt telefona kaydedildi • bulut gönderimi bekleniyor");
                render(); scroll.post(() -> {
                    View sentReply = replies.findViewWithTag("comment_"+id);
                    if(sentReply!=null)sentReply.requestRectangleOnScreen(
                        new android.graphics.Rect(0,0,sentReply.getWidth(),sentReply.getHeight()),false);
                    else scroll.fullScroll(View.FOCUS_DOWN);
                });
            } else input.setError("Yanıt kaydedilemedi. Girişini ve yanıtladığın mesajı kontrol et.");
            send.postDelayed(() -> { sending=false; send.setEnabled(true); },500);
        });
        root.addView(composer); setContentView(root); render();
        if(state==null && getIntent().getBooleanExtra("focus_composer",false)) {
            String requested=getIntent().getStringExtra("reply_parent_cloud_id");
            root.post(() -> {
                if(isFinishing() || isDestroyed())return;
                if(requested==null || requested.isEmpty()){clearReply();focusComposer();return;}
                for(Reply reply:loadedReplies.values()) {
                    if(requested.equals(reply.cloud)&&reply.active){selectReply(reply.id,false);return;}
                }
                Toast.makeText(this,"Yanıtlanacak mesaj artık görünmüyor. Listeden yeniden seçebilirsin.",Toast.LENGTH_LONG).show();
            });
        }
    }
    private void render() {
        if (replies == null || isFinishing()) return;
        if(socialBlocks==null || !socialBlocks.isCurrent() || socialBlocks.blocked(postOwnerUid)){finish();return;}
        if(socialBlocks.blocked(replyToUid))clearReply();
        replies.removeAllViews(); loadedReplies.clear();
        Map<String, Reply> byCloud = new LinkedHashMap<>();
        List<CommentThread.Node> nodes = new ArrayList<>();
        // Scan newest-first and apply the local block filter before the visible limit.
        // This avoids both blocked-message starvation and SQLite bind-count limits.
        try (Cursor c = db.getReadableDatabase().rawQuery(
                "SELECT * FROM post_comments WHERE post_id = ? ORDER BY created_at DESC, cloud_id DESC",
                new String[]{String.valueOf(postId)})) {
            while (c.moveToNext()) {
                Reply r = new Reply(); r.id = CommentCloudRepository.number(c,"id");
                r.cloud = CommentCloudRepository.string(c,"cloud_id");
                r.parent = CommentCloudRepository.string(c,"parent_comment_id");
                r.root = CommentCloudRepository.string(c,"root_comment_id");
                if(r.root.isEmpty())r.root=r.cloud;
                r.uid=CommentCloudRepository.string(c,"user_key"); r.name=CommentCloudRepository.string(c,"user_name");
                if(socialBlocks.blocked(r.uid))continue;
                r.replyName=socialBlocks.blocked(CommentCloudRepository.string(c,"reply_to_uid"))?"Önceki mesaj":CommentCloudRepository.string(c,"reply_to_name");
                r.content=CommentCloudRepository.string(c,"content"); r.at=CommentCloudRepository.number(c,"created_at");
                r.sync=CommentCloudRepository.number(c,"sync_state"); r.active=CommentCloudRepository.number(c,"is_active")==1;
                loadedReplies.put(r.id,r); byCloud.put(r.cloud,r);
                nodes.add(new CommentThread.Node(r.cloud,r.parent,r.active));
                if(nodes.size()>=visibleLimit)break;
            }
        }
        java.util.Collections.reverse(nodes);
        for(CommentThread.Placement item : CommentThread.order(nodes)) {
            Reply r=byCloud.get(item.id);
            if(!r.active && item.liveDescendants==0)continue;
            String message=r.active?r.content:"Bu yanıt silindi.";
            if(r.active && !r.parent.isEmpty() && !byCloud.containsKey(r.parent))
                message="↪ "+(r.replyName.isEmpty()?"Önceki mesaj":r.replyName)+" • üst yanıt bu sayfada yüklü değil\n"+message;
            LinearLayout view=conversationItem(r.uid,r.name,message,r.at,"",r.id,r.sync,false);
            view.setTag("comment_"+r.id);
            TextView reply=view.findViewWithTag("reply_action");
            if(reply!=null && item.liveDescendants>0)reply.setText("YANITLA ("+item.liveDescendants+")");
            LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,-2);
            params.leftMargin=dp(Math.min(2,item.depth)*24); params.bottomMargin=dp(12);
            replies.addView(view,params);
        }
        if(replies.getChildCount()==0)replies.addView(label("Henüz yanıt yok. İlk yanıtı sen yaz.",14,R.color.zd_muted,false));
    }
    private void selectReply(long commentId, boolean post) {
        if(post){clearReply();focusComposer();return;}
        Reply r=loadedReplies.get(commentId);
        if(r==null || !r.active || r.cloud.isEmpty())return;
        selectedParentCommentId=r.cloud; selectedRootCommentId=r.root;
        replyToUid=r.uid;replyToName=r.name;updateReplyIndicator();focusComposer();
    }
    private void clearReply() {
        selectedParentCommentId="";selectedRootCommentId="";replyToUid="";replyToName="";
        updateReplyIndicator();
    }
    private void updateReplyIndicator() {
        if(replyIndicator==null)return;
        replyIndicator.setVisibility(selectedParentCommentId.isEmpty()?View.GONE:View.VISIBLE);
        replyIndicatorText.setText("↪ "+replyToName+" adlı kullanıcıya yanıt veriyorsun");
    }
    @Override protected void onStart() {
        super.onStart(); visible=true;
        if(socialBlocks!=null)socialBlocks.start(fresh->{if(visible&&!isFinishing())render();});
        if (db != null && postId > 0) ZeytinMessagingService.visiblePost = db.getPostCloudId(postId);
        if (comments != null) comments.listen(postId,ok -> {
            if (!visible || isFinishing()) return;
            FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
            if (user == null || !user.getUid().equals(sessionUid)) { finish(); return; }
            status.setText(ok ? "Yanıtlar güncellendi • bekleyen mesajlar telefonda korunur" : "Telefondaki yanıtlar • bulut bağlantısı kurulamadı"); render();
        });
    }
    @Override protected void onStop() { if(socialBlocks!=null)socialBlocks.stop(); ZeytinMessagingService.visiblePost=""; visible=false; if (comments!=null) comments.stop(); super.onStop(); }
    @Override protected void onSaveInstanceState(Bundle state) { if(input!=null)state.putString("draft",input.getText().toString());
        state.putString("reply_parent",selectedParentCommentId);state.putString("reply_root",selectedRootCommentId);
        state.putString("reply_uid",replyToUid);state.putString("reply_name",replyToName);super.onSaveInstanceState(state); }
    @Override protected void onDestroy() { if(comments!=null)comments.close(); if(reactions!=null)reactions.release(); if(db!=null)db.close(); super.onDestroy(); }
    private LinearLayout conversationItem(String uid,String name,String message,long at,
            String district,long commentId,long sync,boolean parent) {
        LinearLayout row = new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.TOP);
        LinearLayout rail = column(); rail.setGravity(Gravity.CENTER_HORIZONTAL);
        View avatar = avatar(uid,name);
        rail.addView(avatar,new LinearLayout.LayoutParams(dp(36),dp(36)));
        // A subtle continuation mark; no full-height card around each reply.
        View thread = new View(this); thread.setBackgroundColor(color(R.color.reply_border));
        LinearLayout.LayoutParams lineParams = new LinearLayout.LayoutParams(dp(1),dp(24));
        lineParams.topMargin=dp(8); rail.addView(thread,lineParams);
        row.addView(rail,new LinearLayout.LayoutParams(dp(42),-2));
        LinearLayout content = column(); content.setPadding(dp(6),0,0,0);
        row.addView(content,new LinearLayout.LayoutParams(0,-2,1));
        LinearLayout top = new LinearLayout(this); top.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout identity = column();
        TextView author = label(name.isEmpty()?"Üretici":name,14,R.color.zd_text,true);
        author.setSingleLine(true);author.setEllipsize(TextUtils.TruncateAt.END);identity.addView(author);
        String meta = (at>0?dateFormat.format(new Date(at))+" • ":"")+"#"+categoryLabel(postCategory);
        if (!district.isEmpty()) meta += " • "+district;
        if (!parent && sync!=2) meta += " • Telefonda";
        TextView metadata=label(meta,10,R.color.zd_muted,false);
        metadata.setMaxLines(2);metadata.setEllipsize(TextUtils.TruncateAt.END);identity.addView(metadata);
        top.addView(identity,new LinearLayout.LayoutParams(0,-2,1));
        TextView menu=label("⋯",21,R.color.zd_muted,true);menu.setGravity(Gravity.CENTER);
        menu.setContentDescription("Mesaj işlemleri");top.addView(menu,new LinearLayout.LayoutParams(dp(40),dp(40)));
        menu.setOnClickListener(v -> {
            FirebaseUser current=FirebaseAuth.getInstance().getCurrentUser();
            boolean own=!parent && current!=null && current.getUid().equals(uid);
            boolean reportable=SocialReportManager.canReport(uid);
            String[] choices=own?new String[]{"Yanıtla","Yanıtı sil"}:
                    (reportable?new String[]{"Yanıtla","Şikâyet Et","Kullanıcıyı Şikâyet Et","Kullanıcıyı Engelle"}:new String[]{"Yanıtla"});
            new AlertDialog.Builder(this).setItems(choices,(dialog,which) -> {
                if(which==0){selectReply(commentId,parent);return;}
                if(!own && reportable) {
                    if(which==3){SocialBlockUi.confirm(this,socialBlocks,uid,name,district,false);return;}
                    Reply targetReply=loadedReplies.get(commentId);
                    String postCloudId=db.getPostCloudId(postId);
                    String type=parent?"POST":(targetReply!=null && !targetReply.parent.isEmpty()?"REPLY":"COMMENT");
                    String targetId=parent?postCloudId:(targetReply==null?"":targetReply.cloud);
                    SocialReportManager.showReasons(this,type,targetId,postCloudId,uid,which==2);
                    return;
                }
                if(!own)return;
                new AlertDialog.Builder(this).setMessage("Bu yanıt silinsin mi?")
                    .setNegativeButton("Vazgeç",null).setPositiveButton("Sil",(d,w) -> {
                        if(comments.delete(commentId)){
                            Reply deleted=loadedReplies.get(commentId);
                            if(deleted!=null && deleted.cloud.equals(selectedParentCommentId))clearReply();
                            render();
                        }
                        else Toast.makeText(this,"Yanıt silinemedi.",Toast.LENGTH_SHORT).show();
                    }).show();
            }).show();
        });
        content.addView(top);
        TextView text=label(message,15,R.color.zd_text,false);
        text.setLineSpacing(dp(2),1f);text.setPadding(dp(12),dp(10),dp(12),dp(10));
        text.setBackground(background(R.color.reply_surface));text.setTextIsSelectable(true);
        content.addView(text,new LinearLayout.LayoutParams(-1,-2));
        LinearLayout actions=new LinearLayout(this);actions.setGravity(Gravity.CENTER_VERTICAL);
        if(parent) {
            TextView reaction=label("",12,R.color.zd_text_soft,false);
            reactions.setupReactionButton(reaction,postId,sessionUid);
            actions.addView(reaction,new LinearLayout.LayoutParams(-2,dp(36)));
        }
        TextView reply=label("YANITLA",11,R.color.zd_text_soft,true);
        reply.setPadding(parent?dp(12):0,0,dp(4),0);reply.setGravity(Gravity.CENTER_VERTICAL);
        reply.setTag("reply_action");
        reply.setContentDescription("Bu mesaja yanıt yaz");reply.setOnClickListener(v -> selectReply(commentId,parent));
        Reply entry=loadedReplies.get(commentId);
        if(!parent && entry!=null && !entry.active){reply.setVisibility(View.INVISIBLE);menu.setVisibility(View.INVISIBLE);}
        actions.addView(reply,new LinearLayout.LayoutParams(0,dp(36),1));
        TextView clock=label(at>0?clockFormat.format(new Date(at)):"",10,R.color.zd_muted,false);
        actions.addView(clock);content.addView(actions);return row;
    }
    private View avatar(String uid,String name) {
        FrameLayout frame = new FrameLayout(this);
        StringBuilder initials = new StringBuilder();
        String[] words = name.trim().split("\\s+");
        if (!name.trim().isEmpty()) {
            initials.appendCodePoint(words[0].codePointAt(0));
            if(words.length>1)initials.appendCodePoint(words[words.length-1].codePointAt(0));
        } else initials.append("Ü");
        TextView fallback=label(initials.toString().toUpperCase(tr),12,R.color.zd_text,true);
        fallback.setGravity(Gravity.CENTER);
        GradientDrawable circle=background(R.color.zd_green_soft);circle.setCornerRadius(dp(36));
        fallback.setBackground(circle);frame.addView(fallback,new FrameLayout.LayoutParams(-1,-1));
        if(sessionUid.equals(uid) && ownAvatarSource!=null && !ownAvatarSource.isEmpty()) {
            ImageView image=new ImageView(this);image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            frame.addView(image,new FrameLayout.LayoutParams(-1,-1));
            // Existing Glide dependency shares/caches the image request across reply rows.
            Glide.with(this).load(ownAvatarSource).circleCrop().into(image);
        }
        return frame;
    }
    private void focusComposer() {
        if(input==null)return;
        input.requestFocus();
        ((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).showSoftInput(input,InputMethodManager.SHOW_IMPLICIT);
    }
    private View categoryChips() {
        HorizontalScrollView scroll=new HorizontalScrollView(this);scroll.setHorizontalScrollBarEnabled(false);
        LinearLayout strip=new LinearLayout(this);strip.setPadding(0,dp(8),0,dp(12));
        for(int i=0;i<CATEGORIES.length;i++) {
            final String code=CATEGORIES[i];boolean active=categoryLabel(code).equals(categoryLabel(postCategory));
            TextView chip=label("#"+CATEGORY_LABELS[i],11,active?R.color.zd_green:R.color.zd_text_soft,true);
            chip.setGravity(Gravity.CENTER);chip.setPadding(dp(12),0,dp(12),0);
            GradientDrawable bg=background(active?R.color.zd_green_soft:R.color.reply_surface);
            bg.setStroke(dp(1),color(active?R.color.zd_green:R.color.reply_border));chip.setBackground(bg);
            chip.setSelected(active);chip.setOnClickListener(v -> openCategory(code));
            LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-2,dp(36));cp.rightMargin=dp(7);strip.addView(chip,cp);
        }
        scroll.addView(strip);return scroll;
    }
    private String categoryLabel(String code) {
        for(int i=0;i<CATEGORIES.length;i++)if(CATEGORIES[i].equalsIgnoreCase(code))return CATEGORY_LABELS[i];
        if (code == null || code.isEmpty() || "ALL".equalsIgnoreCase(code)) return "GENEL";
        if ("QUESTION".equalsIgnoreCase(code)) return "SORU";
        return code.toUpperCase(tr);
    }
    private void openCategory(String code) {
        if(input!=null&&!input.getText().toString().trim().isEmpty()) {
            Toast.makeText(this,"Kategoriye geçmeden önce yazdığın yanıtı gönder veya temizle.",Toast.LENGTH_SHORT).show();return;
        }
        Intent intent=new Intent(this,MainActivity.class).putExtra("open_section","MEYDAN")
            .putExtra("open_category","GENERAL".equals(code)?"ALL":code);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);startActivity(intent);finish();
    }
    private int color(int id) { return getResources().getColor(id,null); }
    private int dp(int n) { return Math.round(n*getResources().getDisplayMetrics().density); }
    private LinearLayout column() { LinearLayout l=new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); return l; }
    private GradientDrawable background(int id) { GradientDrawable d=new GradientDrawable(); d.setColor(color(id)); d.setCornerRadius(dp(16)); d.setStroke(dp(1),color(R.color.reply_border)); return d; }
    private TextView label(String text,int size,int color,boolean bold) { TextView t=new TextView(this); t.setText(text); t.setTextSize(size); t.setTextColor(color(color)); if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD); return t; }
}
