package com.zeytindefteri.app;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.*;
import android.speech.*;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.net.Uri;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import com.zeytindefteri.app.ZeytinAssistantConversation.State;
import com.zeytindefteri.app.ZeytinAssistantConversation.Fault;
import android.view.*;
import android.widget.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;
import com.zeytindefteri.app.ZeytinAssistantCommand.*;

/**
 * Tek seferlik, kullanıcının başlattığı sesli soru + yazılı soru. Arka planda sürekli dinleme yok.
 * Panel tasarımı tuvaldeki "Sesli asistan · öneri" panolarından: alt menünün üstünde yüzen kart;
 * dinleme, cevap, yazarak sorma ve hata/izin hâlleri.
 */
public final class ZeytinAssistantController {
    public static final int MICROPHONE_REQUEST=8701;
    public interface Host {
        String owner(); int season(); long activeGarden();
        default void visual(State state) {}
        default void rms(float level) {}
        /** Alt menünün ekrandaki üst kenarı (px); -1 ise alt menü yok (eski ayrı küre kullanılır). */
        default int navTopOnScreen() {return -1;}
        /** Asistan açıldı / kapandı: alt menüdeki asistan düğmesi canlanır (Siri gibi), yazısı durumu gösterir. */
        default void opened(boolean open) {}
        void navigate(String target,long gardenId);
        /** Sesle söylenen elek kilolarıyla elek formunu dolu aç (kaydetmez; kullanıcı onaylar). */
        default void openElekEntry(long gardenId,ElekVoiceEntry entry){navigate("ELEK",gardenId);}
        /** "Bugün Girelli'yi ilaçladım": işlem formunu dolu aç (kaydetmez; kullanıcı Kaydet'e basar). */
        default void openOperationEntry(long gardenId,OperationVoiceEntry entry){navigate("OPERATION",gardenId);}
        /** Sohbetle tamamlanmış bahçe işini kaydeder ("evet" dendikten sonra); kayıt id'si ya da -1. */
        /** Sesle konuşulan işi kaydeder; yalnız çiftçinin "evet"inden doğan onay belgesiyle (Approval). */
        default long saveOperation(Approval approval,long gardenId,String type,String date,double quantity,String unit,double cost,String note){return -1;}
        /** Sohbetle toplanan hasat / salamura / bahçe / gönderi: ilgili form her şey yazılı açılır (kayıt Kaydet'le). */
        default void openRecordForm(VoiceRecordDialog d,long gardenId){}
        /** "Elek gireceğim": elek ekranı açılır ve sesli elek konuşması hemen başlar. */
        default void openElekVoice(long gardenId){navigate("ELEK",gardenId);}
        /** "Girelli'den 300 kilo topladık": hasat formunu dolu aç (kaydetmez). */
        default void openHarvestEntry(long gardenId,HarvestVoiceEntry entry){navigate("GARDENS",gardenId);}
        void weather(Garden garden,WeatherRepository.ForecastCallback callback);
    }
    private final Activity activity;
    private final Host host;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private Dialog dialog;
    private final ZeytinAssistantConversation conversation=new ZeytinAssistantConversation();
    private enum PermissionStage { EXPLAIN, READY, REQUESTING, BLOCKED }
    private PermissionStage permissionStage=PermissionStage.EXPLAIN;
    private String speakingId="";
    private SpeechRecognizer recognizer;
    private TextToSpeech tts;
    private boolean ttsReady,ttsLanguageMissing,closed,typing,speaking;
    private int session,listenVersion;
    private String lastAnswer="",lastQuestion="";
    private String pendingQuestion;
    private DatabaseHelper marketDb;
    private MarketCloudRepository market;
    private String marketOwner="";

    // Panel görünümü
    private View dot;
    private TextView title,state,heard,question,source,answer,ttsNotice,problemTitle,problemText;
    private ProgressBar progress;
    private AssistantVoiceWaveView wave;
    private LinearLayout answerCard,problemCard,typed,examples,followUps,ttsRow;
    private HorizontalScrollView followScroll;
    private TextView primary,secondary,problemPrimary,problemSecondary;
    private ImageView speaker;
    private EditText input;
    // Anlama katmanı (tuval: "yarım anlama / konu kartları / anladığım etiketleri / etiketi düzelt")
    private LinearLayout tagsBox,tagsRow,hintBox;
    private boolean hinting,offerTopics;
    private Set<ZeytinAssistantHints.Topic> recordedTopics=EnumSet.of(ZeytinAssistantHints.Topic.WEATHER,ZeytinAssistantHints.Topic.MARKET);
    private String lastRaw="";
    private ZeytinAssistantCommand lastCmd;
    private AnswerScope lastScope;
    private Dialog tagSheet;
    /** Cevabın hangi bahçe(ler) için hesaplandığı; etiketten bahçe/zaman değiştirmek için saklanır. */
    private static final class AnswerScope {
        final Garden garden; final boolean all; final List<Garden> gardens;
        AnswerScope(Garden garden,boolean all,List<Garden> gardens){this.garden=garden;this.all=all;this.gardens=gardens==null?Collections.<Garden>emptyList():gardens;}
        long gardenId(){return garden==null?0:garden.id;}
    }
    /** Etiketten seçilen bahçe ya da "Tüm bahçeler": sorudaki bahçe adı yeniden çözülmez. */
    private static final class Pick {
        final long gardenId; final boolean all;
        Pick(long gardenId,boolean all){this.gardenId=gardenId;this.all=all;}
        Resolution resolve(List<Garden> gardens){
            Resolution r=new Resolution();
            for(Garden g:gardens)if(g.id==gardenId){r.garden=g;r.explicit=true;return r;}
            if(!all)r.error="Seçtiğin bahçe kayıtlarında bulunamadı.";
            return r;
        }
    }
    private final int bg,surface,card,border,text,soft,muted,green,greenSoft,warning,warningSoft,danger;
    /** Yüzen yeşil küre: dinlerken sesle büyür, düşünürken döner, konuşurken nefes alır. */
    private ElekVoiceOrbView orb;
    /** Siri tarzı: ayrı küre yok; alt menüdeki düğme canlanır, ekran kenarı ışır, karartma alt menüye inmez. */
    private boolean navMode;
    private AssistantEdgeGlowView glow;
    private android.widget.FrameLayout frame;
    /** Küre boyu ve alt menünün üstünde bıraktığı boşluk (menü yazıları örtülmesin). */
    private static final int ORB_DP=112, ORB_LIFT_DP=74;
    private LinearLayout headBox,footBox;
    /** Üst üste duyulamayan dinleme sayısı: iki kez olunca küre bekler (kart göstermeden). */
    private int quietMisses;
    /** Karne (İP1): bu cevabın turu; bir tur yalnız bir kez sayılır (anlaşılan ya da anlaşılmayan). */
    private volatile int meterTurn=-1;
    private volatile int meteredTurn=-1;
    /** Aynı cümle yeniden soruluyor ("Anladığım" etiketi, internet izni): karnede ikinci kez sayılmaz. */
    private boolean meterAgain;
    private boolean closeAfterSpeech;
    /** Sohbet kişiliği (ad, yaş, hitap): yalnız bu telefonda saklanır. */
    private AssistantPersona persona;
    /** Cümleyi eyleme çeviren beyin (İP3): bugün kurallar; değiştirilebilir parça, yürütme bu sınıfta. */
    private final RuleBrain rules=new RuleBrain();
    /** İnternet yardımcısı aynı arayüzde (yalnız kuralların anlamadığı ya da soru sandığı iş cümlesinde, izinle). */
    private final CloudBrain cloudBrain=new CloudBrain(AssistantCloudClient::ask);
    /** Öğrenilen sözler beyni (İP4): kuralların önünde, hesabın telefondaki sözlüğüyle. */
    private LearnedBrain learned;
    private String learnedOwner="";
    /** Öğretme sorusu bekliyor: {cümle, söz, kaynak}; null = beklemiyor. */
    private String[] pendingTeach;
    /** Son cümle mikrofondan mı geldi (öğrenilen sözün kaynağı: ses/yazı). */
    private boolean heardByVoice;
    /** Öğretilen işten sonra: işlem formu sorusuz, dolu açılsın (bir kerelik). */
    private boolean teachOpenForm;
    /** İnternet yardımcısının çözdüğü cümle: çiftçi "evet"le kaydederse sözü öğrenilir (bulut-onaylı). */
    private String opCloudRaw;
    private int answersThisPanel;

    private android.content.SharedPreferences personaPrefs(){return activity.getSharedPreferences("zd_assistant_persona",Context.MODE_PRIVATE);}
    private AssistantPersona persona(){
        if(persona==null)persona=AssistantPersona.decode(personaPrefs().getString("p_"+host.owner(),""));
        return persona;
    }
    private void savePersona(){if(persona!=null)personaPrefs().edit().putString("p_"+host.owner(),persona.encode()).apply();}
    /**
     * İnternet yardımcısı komutları (kararı RuleBrain verir): durum, aç/kapat ve tek seferlik izin cevabı.
     * asked: izin sorusu beklenirken saklanan cümle (izin verilince o cümle yeniden işlenir).
     */
    private void cloudCommand(Decision d,String asked){
        if(d.is(ActionCatalog.BULUT_DURUM)){
            String mode=AssistantCloudClient.mode(activity,host.owner());
            String state=!AssistantCloudClient.configured()?"İnternet yardımcısı bu sürümde kapalı (adres yok).":"off".equals(mode)?"İnternet yardımcısı kapalı; açmak için “internet yardımcısını aç” de.":mode.isEmpty()?"İnternet yardımcısı için henüz izin sorulmadı.":"İnternet yardımcısı açık.";
            postDialog(new OperationVoiceDialog.Reply(state+" "+AssistantCloudClient.lastStatus,OperationVoiceDialog.Action.ASK),"Zeytin · internet yardımcısı");
            return;
        }
        if(d.is(ActionCatalog.BULUT_AC)||d.is(ActionCatalog.BULUT_KAPAT)){
            boolean on=d.is(ActionCatalog.BULUT_AC);
            AssistantCloudClient.setMode(activity,host.owner(),on);
            postDialog(new OperationVoiceDialog.Reply(on?"Tamam, anlamadığım iş cümlelerini internetteki yardımcıya soracağım.":"Tamam, internete hiçbir şey sormayacağım.",OperationVoiceDialog.Action.ASK),"Zeytin · internet yardımcısı");
            return;
        }
        if(d.is(ActionCatalog.BULUT_IZIN_EVET)){
            AssistantCloudClient.setMode(activity,host.owner(),true);
            lastRaw=asked;
            meterAgain=true;
            run(asked,ZeytinAssistantCommand.parse(asked),null);
            return;
        }
        AssistantCloudClient.setMode(activity,host.owner(),false);
        postDialog(new OperationVoiceDialog.Reply("Peki, internete sormayacağım. O cümleyi başka türlü söyleyebilir ya da formdan ekleyebilirsin. Fikrini değiştirirsen “internet yardımcısını aç” de.",OperationVoiceDialog.Action.ASK),"Zeytin · internet yardımcısı");
    }
    /**
     * Konuşma aşamasında internet yardımcısı basamağına gelindi mi (eski sırada: kapanış, anlamadıklarım, karne ve
     * durum sorusu o basamaktan önce cevaplanır). Gelindiyse bekleyen izin sorusu bu cümleyle kapanır.
     */
    private static boolean pastCloudStep(Decision d){
        if(d==null)return true;
        return !(d.is(ActionCatalog.KAPAT)||d.is(ActionCatalog.ANLAMADIKLARI_SIL)||d.is(ActionCatalog.ANLAMADIKLARI_GOSTER)
                ||d.is(ActionCatalog.KONUSMA_KAYDI_SIL)||d.is(ActionCatalog.KARNE_GOSTER)||d.is(ActionCatalog.BULUT_DURUM));
    }

    /** Anlamadığı cümleler: yalnız bu telefonda, kullanıcı isterse gösterilir (AssistantMisses). */
    private void rememberMiss(String raw,String owner){
        try{
            android.content.SharedPreferences p=activity.getSharedPreferences("zd_assistant_misses",Context.MODE_PRIVATE);
            String day=new java.text.SimpleDateFormat("d MMM",new java.util.Locale("tr","TR")).format(new java.util.Date());
            p.edit().putString("m_"+owner,AssistantMisses.add(p.getString("m_"+owner,""),raw,day)).apply();
        }catch(RuntimeException ignored){}
        meter(meterTurn,raw,owner,null);
    }
    /** Karne ve cümle kaydı (yalnız telefonda): action null ise anlaşılmadı. Bir tur bir kez sayılır. */
    private void meter(int turn,String raw,String owner,String action){
        if(turn==meteredTurn)return;
        meteredTurn=turn;
        if(action==null){
            AssistantMeter.missed(activity,owner,raw);
            return;
        }
        AssistantMeter.understood(activity,owner,raw,action);
    }
    /** "Karneni göster" / "konuşma kaydını sil": yalnız sayılar; asistan ekranında okunur, yeni ekran açılmaz. */
    private void scorecardCommand(Decision d){
        if(d.is(ActionCatalog.KONUSMA_KAYDI_SIL)){
            AssistantMeter.clearAll(activity,host.owner());
            String done="Tamam, bu telefondaki konuşma kaydını, karneyi ve anlamadığım cümleleri sildim.";
            postDialog(new OperationVoiceDialog.Reply(done,OperationVoiceDialog.Action.ASK),"Zeytin · karne");
            return;
        }
        String[] shown=AssistantMeter.show(activity,host.owner());
        postDialog(new OperationVoiceDialog.Reply(shown[0],OperationVoiceDialog.Action.ASK),"Zeytin · karne");
        answer.setTextSize(14);
        answer.setText(shown[1]);
    }
    /** "Anlamadığın cümleleri göster / sil": yalnız bu telefondaki liste. */
    private void missesCommand(Decision d){
        android.content.SharedPreferences p=activity.getSharedPreferences("zd_assistant_misses",Context.MODE_PRIVATE);
        String key="m_"+host.owner();
        if(d.is(ActionCatalog.ANLAMADIKLARI_SIL)){
            p.edit().remove(key).apply();
            AssistantMeter.clearMissed(activity,host.owner());
            postDialog(new OperationVoiceDialog.Reply("Tamam, anlamadığım cümleler listesini sildim.",OperationVoiceDialog.Action.ASK),"Zeytin · anlamadıklarım");
            return;
        }
        String[] shown=AssistantMisses.show(p.getString(key,""));
        postDialog(new OperationVoiceDialog.Reply(shown[0],OperationVoiceDialog.Action.ASK),"Zeytin · anlamadıklarım");
        answer.setTextSize(14);answer.setText(shown[1]);   // okunan kısa söz; ekranda liste
    }

    public ZeytinAssistantController(Activity activity,Host host){
        this.activity=activity;this.host=host;
        bg=GreenTheme.color(R.color.zd_bg);surface=GreenTheme.color(R.color.zd_surface);card=GreenTheme.color(R.color.zd_card);
        border=GreenTheme.color(R.color.zd_border);text=GreenTheme.color(R.color.zd_text);soft=GreenTheme.color(R.color.zd_text_soft);
        muted=GreenTheme.color(R.color.zd_muted);green=GreenTheme.color(R.color.zd_green);greenSoft=GreenTheme.color(R.color.zd_green_soft);
        warning=GreenTheme.color(R.color.zd_warning);warningSoft=GreenTheme.color(R.color.zd_warning_soft);danger=GreenTheme.color(R.color.zd_danger);
    }

    public void show() {
        if(closed || (dialog!=null && dialog.isShowing()))return;
        final int panel=++session;
        conversation.nextTurn();lastAnswer="";lastQuestion="";ttsReady=false;ttsLanguageMissing=false;typing=false;speaking=false;
        int navTop=host.navTopOnScreen();
        navMode=navTop>dp(200);
        View root=buildPanel();
        frame=new android.widget.FrameLayout(activity);
        glow=new AssistantEdgeGlowView(activity);
        frame.addView(glow,new android.widget.FrameLayout.LayoutParams(-1,-1));
        android.widget.FrameLayout.LayoutParams rp=new android.widget.FrameLayout.LayoutParams(getPanelWidth(),-2,Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL);
        rp.bottomMargin=navMode?dp(14):0;
        frame.addView(root,rp);
        dialog=new Dialog(activity);dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);dialog.setContentView(frame);
        dialog.setCanceledOnTouchOutside(false);
        dialog.setOnDismissListener(d->releasePanel());dialog.show();root.requestFocus();
        // Alt menüdeki asistan düğmesi büyüyerek küreye dönüşür (aşağıdan, küçükten).
        host.opened(true);
        if(orb!=null){orb.setScaleX(.4f);orb.setScaleY(.4f);orb.setTranslationY(dp(ORB_LIFT_DP+ORB_DP/2-28));orb.setAlpha(.7f);
            orb.animate().scaleX(1f).scaleY(1f).translationY(0).alpha(1f).setDuration(460).setInterpolator(new android.view.animation.OvershootInterpolator(1.05f)).start();}
        Window window=dialog.getWindow();if(window!=null){
            window.setBackgroundDrawableResource(android.R.color.transparent);
            applyWindow();
            // Alt menüdeki küreye dokunulabilsin (dinlemeyi bitirmek için).
            window.addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL);
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE|WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        }
        transition(State.LISTENING,"Mikrofon hazırlanıyor…");
        try {tts=new TextToSpeech(activity.getApplicationContext(),status->main.post(()->{
            if(panel!=session || !visible() || tts==null)return;
            int language=TextToSpeech.LANG_NOT_SUPPORTED;
            try {if(status==TextToSpeech.SUCCESS)language=tts.setLanguage(new Locale("tr","TR"));}catch(Exception e){language=TextToSpeech.LANG_NOT_SUPPORTED;}
            ttsReady=status==TextToSpeech.SUCCESS && language>=0;
            // Motor çalışıyor ama Türkçe ses yok: kullanıcıya ses paketini kurmasını öner (sessizce susmak yerine).
            ttsLanguageMissing=status==TextToSpeech.SUCCESS && (language==TextToSpeech.LANG_MISSING_DATA || language==TextToSpeech.LANG_NOT_SUPPORTED);
            if(conversation.state()==State.ANSWERING){refreshTtsNotice();speak();
                if(!ttsReady&&listenAfterSpeech){listenAfterSpeech=false;main.postDelayed(()->{if(visible())listen();},1500);}}
        }));
            tts.setOnUtteranceProgressListener(new UtteranceProgressListener(){
                private void update(String id,boolean on,String message){main.post(()->{
                    if(panel==session && visible() && id.equals(speakingId) && conversation.state()==State.ANSWERING){
                        speaking=on;transition(State.ANSWERING,message);
                    }
                });}
                public void onStart(String id){update(id,true,"Cevabı okuyorum…");}
                public void onDone(String id){update(id,false,"Cevap hazır");
                    main.post(()->{
                        if(closeAfterSpeech&&panel==session&&visible()){closeAfterSpeech=false;if(dialog!=null)dialog.dismiss();return;}
                        if(listenAfterSpeech&&panel==session&&visible()&&id.equals(speakingId)){listenAfterSpeech=false;listen();}});}
                public void onError(String id){update(id,false,"Sesli okuma kullanılamadı; yazılı cevap hazır");}
                public void onStop(String id,boolean interrupted){update(id,false,"Cevap hazır");}
            });
        }catch(Exception e){ttsReady=false;}
        main.post(()->{if(panel==session && visible()){
            String q=pendingQuestion;pendingQuestion=null;answersThisPanel=0;
            if(q!=null)submit(q);
            else postDialog(new OperationVoiceDialog.Reply(persona().greeting(AssistantPersona.hourNow()),OperationVoiceDialog.Action.ASK),"Zeytin");
        }});
    }
    /** Ana sayfadaki hazır sorular için: paneli açar ve soruyu mikrofon açmadan yazılı gönderir. */
    public void ask(String q){
        if(q==null||q.trim().isEmpty())return;
        if(visible()){submit(q);return;}
        pendingQuestion=q;
        show();
        if(!visible())pendingQuestion=null;
    }
    public boolean isOpen(){return visible();}
    public void orbTap(){
        if(!visible())return;
        if(conversation.state()==State.LISTENING && recognizer!=null){
            try{recognizer.stopListening();transition(State.PROCESSING,"Kontrol ediyorum…");}
            catch(Exception e){stopRecognizer();problem(Fault.TIMEOUT);}
        }else if(conversation.state()!=State.PROCESSING)listen();
    }

    // ============================================================ PANEL

    private View buildPanel(){
        // Çerçevesiz: koyulaşmış ekranın üstünde yüzen yeşil küre (yapay zekâ sesli asistanları gibi); kart yok.
        LinearLayout root=column();root.setPadding(dp(18),dp(6),dp(18),0);root.setFocusableInTouchMode(true);
        root.setBackground(null);

        // Üstte yalnız iki küçük yuvarlak: yazarak sor ve kapat. Küre en altta, alt menüdeki asistan düğmesinin üstünde.
        LinearLayout topBar=row();topBar.setGravity(Gravity.END|Gravity.CENTER_VERTICAL);
        ImageView keys=icon(R.drawable.ic_op_keyboard,text);keys.setPadding(dp(10),dp(10),dp(10),dp(10));keys.setBackground(round(0x33FFFFFF,0x00000000,20));
        keys.setContentDescription("Yazarak sor");keys.setOnClickListener(v->secondaryAction());
        LinearLayout.LayoutParams kp=new LinearLayout.LayoutParams(dp(40),dp(40));kp.rightMargin=dp(10);topBar.addView(keys,kp);
        ImageView close=icon(R.drawable.ic_op_close,text);close.setPadding(dp(10),dp(10),dp(10),dp(10));close.setBackground(round(0x33FFFFFF,0x00000000,20));
        close.setContentDescription("Asistanı kapat");close.setOnClickListener(v->{if(dialog!=null)dialog.dismiss();});
        topBar.addView(close,new LinearLayout.LayoutParams(dp(40),dp(40)));
        root.addView(topBar,new LinearLayout.LayoutParams(-1,-2));

        orb=null;
        if(!navMode){   // alt menü yoksa eski ayrı küre
            orb=new ElekVoiceOrbView(activity);
            orb.setClickable(true);orb.setContentDescription("Zeytin Asistanı. Dinlemek ya da bitirmek için dokun.");
            orb.setOnClickListener(v->orbTap());
        }

        LinearLayout head=column();head.setGravity(Gravity.CENTER_HORIZONTAL);headBox=head;
        dot=new View(activity);dot.setVisibility(View.GONE);head.addView(dot,new LinearLayout.LayoutParams(dp(1),dp(1)));
        title=label("Zeytin Asistanı",20,text,true);title.setGravity(Gravity.CENTER);head.addView(title,new LinearLayout.LayoutParams(-1,-2));
        state=label("",13,soft,false);state.setGravity(Gravity.CENTER);state.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);head.addView(state,new LinearLayout.LayoutParams(-1,-2));

        wave=new AssistantVoiceWaveView(activity);root.addView(wave,margins(-1,dp(48),dp(12),0));
        heard=label("",20,text,true);heard.setGravity(Gravity.CENTER);heard.setLineSpacing(dp(2),1f);heard.setMaxLines(4);root.addView(heard,margins(-1,-2,dp(8),0));

        question=label("",14,text,false);question.setPadding(dp(14),dp(10),dp(14),dp(10));
        GradientDrawable bubble=new GradientDrawable();bubble.setColor(greenSoft);float r=dp(16);bubble.setCornerRadii(new float[]{r,r,r,r,dp(4),dp(4),r,r});
        question.setBackground(bubble);question.setMaxLines(4);question.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams qp=new LinearLayout.LayoutParams(-2,-2);qp.gravity=Gravity.END;qp.topMargin=dp(14);qp.leftMargin=dp(40);root.addView(question,qp);

        // Anladığım: konu · bahçe · zaman. Ok işaretli etikete dokununca değiştirilebilir.
        tagsBox=column();
        TextView tagsOver=label("ANLADIĞIM",11,muted,true);tagsOver.setLetterSpacing(0.08f);tagsOver.setPadding(0,0,0,dp(6));tagsBox.addView(tagsOver);
        tagsRow=row();HorizontalScrollView tagScroll=new HorizontalScrollView(activity);tagScroll.setHorizontalScrollBarEnabled(false);tagScroll.addView(tagsRow);
        tagsBox.addView(tagScroll);
        root.addView(tagsBox,margins(-1,-2,dp(12),0));

        progress=new ProgressBar(activity);progress.setIndeterminate(true);
        LinearLayout.LayoutParams pp=new LinearLayout.LayoutParams(dp(24),dp(24));pp.gravity=Gravity.CENTER;pp.topMargin=dp(14);root.addView(progress,pp);

        answerCard=column();answerCard.setPadding(dp(16),dp(14),dp(16),dp(14));answerCard.setBackground(round(0xD9142430,0x00000000,22));
        source=label("",11,muted,true);source.setLetterSpacing(0.06f);answerCard.addView(source);
        answer=label("",17,text,true);answer.setLineSpacing(dp(3),1f);answer.setTextIsSelectable(true);answer.setPadding(0,dp(6),0,0);answerCard.addView(answer);
        hintBox=column();answerCard.addView(hintBox);
        ttsRow=row();ttsRow.setPadding(0,dp(10),0,0);
        ImageView mute=icon(R.drawable.ic_op_speaker,muted);ttsRow.addView(mute,new LinearLayout.LayoutParams(dp(16),dp(16)));
        ttsNotice=label("Türkçe ses paketi yok; cevap yalnızca yazılı.",12,muted,false);ttsNotice.setPadding(dp(8),0,dp(8),0);ttsRow.addView(ttsNotice,new LinearLayout.LayoutParams(0,-2,1));
        TextView fix=label("Ses ayarları",12,green,true);fix.setOnClickListener(v->openTtsSettings());fix.setPadding(dp(6),dp(6),0,dp(6));ttsRow.addView(fix);
        answerCard.addView(ttsRow);
        root.addView(answerCard,margins(-1,-2,dp(12),0));

        problemCard=column();problemCard.setPadding(dp(16),dp(14),dp(16),dp(14));problemCard.setBackground(round(0xD9142430,0x00000000,22));
        problemTitle=label("",15,text,true);problemCard.addView(problemTitle);
        problemText=label("",13,soft,false);problemText.setLineSpacing(dp(2),1f);problemText.setPadding(0,dp(6),0,dp(12));problemCard.addView(problemText);
        LinearLayout pb=row();
        problemPrimary=button("",0,true);problemSecondary=button("",0,false);
        LinearLayout.LayoutParams pl=new LinearLayout.LayoutParams(0,dp(46),1);pl.rightMargin=dp(8);pb.addView(problemPrimary,pl);
        pb.addView(problemSecondary,new LinearLayout.LayoutParams(0,dp(46),1));problemCard.addView(pb);
        root.addView(problemCard,margins(-1,-2,dp(12),0));

        typed=column();
        LinearLayout inputRow=row();
        input=new EditText(activity);input.setTextColor(text);input.setHintTextColor(muted);input.setTextSize(15);
        input.setHint("Sorunu yaz…");input.setContentDescription("Zeytin Asistanına yazılı soru");input.setSingleLine(true);
        input.setImeOptions(EditorInfo.IME_ACTION_SEND);input.setPadding(dp(16),0,dp(16),0);input.setBackground(round(bg,green,25));
        input.setOnEditorActionListener((v,id,event)->{if(id==EditorInfo.IME_ACTION_SEND){submit(input.getText().toString());return true;}return false;});
        inputRow.addView(input,new LinearLayout.LayoutParams(0,dp(50),1));
        ImageView send=icon(R.drawable.ic_op_send,bg);send.setPadding(dp(14),dp(14),dp(14),dp(14));send.setBackground(round(green,green,25));
        send.setContentDescription("Soruyu gönder");send.setOnClickListener(v->submit(input.getText().toString()));
        LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(dp(50),dp(50));sp.leftMargin=dp(8);inputRow.addView(send,sp);
        typed.addView(inputRow);
        examples=column();
        for(String[] group:ZeytinAssistantText.EXAMPLES){
            TextView over=label(group[0],11,muted,true);over.setLetterSpacing(0.08f);over.setPadding(0,dp(14),0,dp(8));examples.addView(over);
            examples.addView(chipRow(Arrays.copyOfRange(group,1,group.length)));
        }
        typed.addView(examples);
        root.addView(typed,margins(-1,-2,dp(14),0));

        followUps=row();followScroll=new HorizontalScrollView(activity);followScroll.setHorizontalScrollBarEnabled(false);followScroll.addView(followUps);
        root.addView(followScroll,margins(-1,-2,dp(12),0));

        // Durum yazısı ve küre en altta: alt menüdeki asistan düğmesi büyüyüp bu küreye dönüşür.
        root.addView(headBox,margins(-1,-2,dp(14),0));
        // Küre alt menünün hemen üstünde, menü yazılarını (Elek, Zeytin Ligi) örtmeden; açılırken alttaki asistan
        // düğmesinden yukarı doğru büyüyerek çıkar (bkz. açılış animasyonu).
        LinearLayout.LayoutParams op=new LinearLayout.LayoutParams(dp(ORB_DP),dp(ORB_DP));op.gravity=Gravity.CENTER_HORIZONTAL;op.topMargin=dp(4);op.bottomMargin=dp(ORB_LIFT_DP);
        if(orb!=null)root.addView(orb,op);

        LinearLayout foot=row();
        speaker=icon(R.drawable.ic_op_speaker,text);speaker.setPadding(dp(14),dp(14),dp(14),dp(14));speaker.setBackground(round(0x33FFFFFF,0x00000000,24));
        speaker.setOnClickListener(v->toggleSpeech());
        LinearLayout.LayoutParams spk=new LinearLayout.LayoutParams(dp(52),dp(48));spk.rightMargin=dp(10);foot.addView(speaker,spk);
        primary=button("Tekrar sor",R.drawable.ic_op_mic,true);primary.setOnClickListener(v->primaryAction());
        LinearLayout.LayoutParams prl=new LinearLayout.LayoutParams(0,dp(48),1);prl.rightMargin=dp(10);foot.addView(primary,prl);
        secondary=button("Yaz",R.drawable.ic_op_keyboard,false);secondary.setOnClickListener(v->secondaryAction());
        foot.addView(secondary,new LinearLayout.LayoutParams(dp(104),dp(48)));
        footBox=foot;
        root.addView(foot,margins(-1,-2,dp(16),0));
        return root;
    }
    /**
     * Pencere düzeni. Siri tarzında pencere ekranın tepesinden alt menünün üst kenarına kadar uzanır: kendi
     * karartması ve kenar ışığı vardır, alt menü (canlanan asistan düğmesi) karartılmaz ve dokunulabilir kalır.
     * Yazarken klavyeye yer açmak için eski düzene (altta, içerik kadar, arka plan karartmalı) geçer.
     */
    private void applyWindow(){
        Window w=dialog==null?null:dialog.getWindow();if(w==null||frame==null)return;
        WindowManager.LayoutParams wp=w.getAttributes();
        int navTop=navMode?host.navTopOnScreen():-1;
        if(navMode&&!typing&&navTop>dp(200)){
            w.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            w.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN|WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
            wp.gravity=Gravity.TOP|Gravity.CENTER_HORIZONTAL;wp.x=0;wp.y=0;wp.width=-1;wp.height=navTop;
            frame.setBackgroundColor(0xB8000000);
            if(glow!=null)glow.setVisibility(View.VISIBLE);
        }else{
            w.clearFlags(WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN|WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
            w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);wp.dimAmount=.72f;
            wp.gravity=Gravity.BOTTOM;wp.x=0;wp.y=0;wp.width=getPanelWidth();wp.height=-2;
            frame.setBackgroundColor(0x00000000);
            if(glow!=null)glow.setVisibility(View.GONE);
        }
        w.setAttributes(wp);
    }
    private int getPanelWidth(){return Math.min(activity.getResources().getDisplayMetrics().widthPixels,dp(480));}

    private void primaryAction(){
        if(typing){setTyping(false);listen();return;}
        if(conversation.state()==State.LISTENING){conversation.nextTurn();stopRecognizer();stopTts();transition(State.IDLE,"Dinleme durduruldu");return;}
        listen();
    }
    private void secondaryAction(){
        if(typing){setTyping(false);listen();return;}
        // Yakın soru önerilerinde ikinci düğme "Konular": konu kartlarını açar (yeni kayıt okuması yok).
        if(conversation.state()==State.ANSWERING && offerTopics){stopTts();showHint(ZeytinAssistantHints.cards("Bir konu seç:"),false);return;}
        if(conversation.state()==State.LISTENING){conversation.nextTurn();stopRecognizer();}
        stopTts();setTyping(true);
    }
    private void setTyping(boolean on){
        typing=on;
        if(navMode)applyWindow();
        if(on){transition(State.IDLE,"Kayıtların, hava ve ekranlar hakkında");input.requestFocus();
            InputMethodManager im=(InputMethodManager)activity.getSystemService(Context.INPUT_METHOD_SERVICE);
            if(im!=null)input.post(()->im.showSoftInput(input,InputMethodManager.SHOW_IMPLICIT));}
        else hideKeyboard();
    }
    private void hideKeyboard(){
        InputMethodManager im=(InputMethodManager)activity.getSystemService(Context.INPUT_METHOD_SERVICE);
        if(im!=null && input!=null)im.hideSoftInputFromWindow(input.getWindowToken(),0);
    }

    /** Panelin durum, başlık, görünürlük ve düğme anlamlarının tek sahibi. */
    private void transition(State next,String message){
        boolean changed=conversation.move(next);host.visual(next);
        if(glow!=null)glow.state(typing?State.IDLE:next,speaking);
        if(state==null)return;
        if(!message.contentEquals(state.getText()))state.setText(message);
        boolean listening=next==State.LISTENING, processing=next==State.PROCESSING, answering=next==State.ANSWERING;
        title.setText(typing?"Yazarak sor":listening?"Dinliyorum":processing?"Kontrol ediyorum":"Zeytin Asistanı");
        int tone=typing?warning:next==State.ERROR?danger:answering && hinting?warning:green;
        dot.setBackground(round(tone,tone,5));
        wave.setVisibility(View.GONE);wave.listening(listening && !typing);
        if(orb!=null)orb.setVisibility(typing?View.GONE:View.VISIBLE);   // yazarken klavyeye yer kalsın
        if(footBox!=null)footBox.setVisibility(typing?View.VISIBLE:View.GONE);   // sesli sohbette düğme yok: küreye dokun
        if(orb!=null)orb.mode(typing?ElekVoiceOrbView.Mode.IDLE:listening?ElekVoiceOrbView.Mode.LISTENING:processing?ElekVoiceOrbView.Mode.THINKING
                :answering&&speaking?ElekVoiceOrbView.Mode.SPEAKING:ElekVoiceOrbView.Mode.IDLE);
        heard.setVisibility(listening && !typing && heard.length()>0?View.VISIBLE:View.GONE);
        question.setVisibility(!listening && !typing && lastQuestion.length()>0 && next!=State.ERROR?View.VISIBLE:View.GONE);
        progress.setVisibility(processing?View.VISIBLE:View.GONE);
        // Son cevap, küre beklerken de ekranda kalır (sohbetin akışı kaybolmasın).
        answerCard.setVisibility((answering || next==State.IDLE) && !typing && lastAnswer.length()>0?View.VISIBLE:View.GONE);
        if(next!=State.ERROR || typing)problemCard.setVisibility(View.GONE);
        typed.setVisibility(typing?View.VISIBLE:View.GONE);
        tagsBox.setVisibility(answering && !typing && !hinting && tagsRow.getChildCount()>0?View.VISIBLE:View.GONE);
        followScroll.setVisibility(answering && !typing && !hinting && followUps.getChildCount()>0?View.VISIBLE:View.GONE);
        speaker.setVisibility(answering && !typing && ttsReady && lastAnswer.length()>0?View.VISIBLE:View.GONE);
        speaker.setContentDescription(speaking?"Sesli okumayı durdur":"Cevabı sesli oku");
        speaker.setColorFilter(speaking?green:text);
        refreshTtsNotice();
        // Alt düğmeler
        primary.setVisibility(typing?View.GONE:View.VISIBLE);
        if(listening){setButton(primary,"Durdur",R.drawable.ic_op_stop,false);primary.setContentDescription("Dinlemeyi durdur");}
        else{setButton(primary,"Tekrar sor",R.drawable.ic_op_mic,true);primary.setContentDescription("Sesle tekrar sor");}
        primary.setEnabled(!processing && permissionStage!=PermissionStage.REQUESTING);primary.setAlpha(primary.isEnabled()?1f:.5f);
        if(typing){setButton(secondary,"Sesle sor",R.drawable.ic_op_mic,false);secondary.setContentDescription("Sesle sor");}
        else if(answering && offerTopics){setButton(secondary,"Konular",0,false);secondary.setContentDescription("Konulara göz at");}
        else{setButton(secondary,"Yaz",R.drawable.ic_op_keyboard,false);secondary.setContentDescription("Yazarak sor");}
        LinearLayout.LayoutParams lp=(LinearLayout.LayoutParams)secondary.getLayoutParams();
        if(lp!=null){lp.width=typing?0:dp(104);lp.weight=typing?1:0;secondary.setLayoutParams(lp);}
        if(changed && visible())state.announceForAccessibility(title.getText()+". "+message);
        log("state="+next);
    }
    /** Hata/izin kartı: ne oldu, neden, iki net seçenek. */
    private void showProblem(String head,String body,String first,Runnable firstAction,boolean firstPrimary,String second,Runnable secondAction){
        typing=false;hideKeyboard();
        transition(State.ERROR,head);
        problemTitle.setText(head);problemText.setText(body);
        setButton(problemPrimary,first,firstPrimary?R.drawable.ic_op_mic:0,firstPrimary);problemPrimary.setOnClickListener(v->firstAction.run());
        setButton(problemSecondary,second,0,false);problemSecondary.setOnClickListener(v->secondAction.run());
        problemCard.setVisibility(View.VISIBLE);
        problemCard.announceForAccessibility(head+". "+body);
    }
    private void problem(Fault fault){
        switch(fault){
            case NO_MATCH: case TIMEOUT:
                // Sohbet kesilmesin: kart göstermeden bir kez daha dinler; yine olmazsa küre bekler (dokununca dinler).
                if(quietMisses<1){quietMisses++;transition(State.LISTENING,"Dinliyorum…");main.postDelayed(()->{if(visible())listen();},400);return;}
                quietMisses=0;stopTts();transition(State.IDLE,lastAnswer.isEmpty()?"Konuşmak için küreye dokun":"Devam etmek için küreye dokun");return;
            case NETWORK:
                showProblem("Ses tanıma bağlantısı kurulamadı","İnternet bağlantını kontrol et. Kayıtlarınla ilgili soruları yazarak sormaya devam edebilirsin.",
                        "Tekrar dene",this::listen,false,"Yazarak sor",()->setTyping(true));return;
            case PERMISSION:
                if(permissionStage==PermissionStage.BLOCKED){
                    showProblem("Mikrofon izni kapalı","İzni daha önce reddettin. Telefon ayarlarından açabilir ya da yazarak sorabilirsin.",
                            "Ayarları aç",this::openAppSettings,false,"Yazarak sor",()->setTyping(true));
                }else{
                    showProblem("Mikrofon izni gerekiyor","Söylediklerini metne çevirmek için mikrofon kullanılır; ses kaydı Zeytin Defteri’ne gönderilmez ya da saklanmaz.",
                            "İzin ver",this::requestMicrophone,true,"Yazarak sor",()->setTyping(true));
                }return;
            case BUSY:
                showProblem("Ses tanıma meşgul","Telefondaki ses tanıma hizmeti başka bir işle meşgul. Biraz sonra tekrar dene.",
                        "Tekrar dene",this::listen,false,"Yazarak sor",()->setTyping(true));return;
            default:
                showProblem("Sesli komut kullanılamıyor",ZeytinAssistantConversation.error(fault)+" ",
                        "Tekrar dene",this::listen,false,"Yazarak sor",()->setTyping(true));
        }
    }
    private void refreshTtsNotice(){
        if(ttsRow==null)return;
        ttsRow.setVisibility(ttsLanguageMissing && lastAnswer.length()>0?View.VISIBLE:View.GONE);
    }
    private void openTtsSettings(){
        try{activity.startActivity(new Intent("com.android.settings.TTS_SETTINGS").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));return;}catch(Exception ignored){}
        try{activity.startActivity(new Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));}
        catch(Exception e){Toast.makeText(activity,"Telefon ayarlarında “Metin okuma” bölümünden Türkçe sesi indirebilirsin.",Toast.LENGTH_LONG).show();}
    }
    private void openAppSettings(){
        try{activity.startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+activity.getPackageName())));}
        catch(Exception e){Toast.makeText(activity,"Mikrofon iznini uygulama ayarlarından açabilirsin.",Toast.LENGTH_LONG).show();}
    }
    private void haptic(){try{primary.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);}catch(Exception ignored){}}
    private void log(String event){if((activity.getApplicationInfo().flags & android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE)!=0)Log.d("ZeytinAssistant",event);}
    private boolean visible(){return !closed && !activity.isFinishing() && dialog!=null && dialog.isShowing();}

    // ============================================================ DİNLEME

    private void requestMicrophone(){
        permissionStage=PermissionStage.REQUESTING;transition(State.IDLE,"Mikrofon izni bekleniyor…");
        try{activity.requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},MICROPHONE_REQUEST);}
        catch(Exception e){permissionStage=PermissionStage.READY;problem(Fault.PERMISSION);}
    }
    private void listen() {
        if(!visible() || permissionStage==PermissionStage.REQUESTING)return;
        typing=false;hideKeyboard();
        conversation.nextTurn();stopTts();stopRecognizer();
        if(activity.checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){
            if(permissionStage==PermissionStage.EXPLAIN){permissionStage=PermissionStage.READY;problem(Fault.PERMISSION);return;}
            if(permissionStage==PermissionStage.BLOCKED){problem(Fault.PERMISSION);return;}
            requestMicrophone();return;
        }
        permissionStage=PermissionStage.READY;heard.setText("");answer.setText("");lastAnswer="";lastQuestion="";followUps.removeAllViews();clearAnswerExtras();
        transition(State.LISTENING,"Mikrofon hazırlanıyor…");
        try {
            if(!SpeechRecognizer.isRecognitionAvailable(activity)){
                showProblem("Ses tanıma hizmeti yok","Bu telefonda konuşma tanıma hizmeti bulunamadı. Sorularını yazarak sorabilirsin.",
                        "Yazarak sor",()->setTyping(true),false,"Kapat",()->{if(dialog!=null)dialog.dismiss();});return;
            }
            final int version=++listenVersion;
            recognizer=SpeechRecognizer.createSpeechRecognizer(activity);
            recognizer.setRecognitionListener(new RecognitionListener(){
                private boolean valid(){return visible() && version==listenVersion;}
                public void onReadyForSpeech(Bundle b){if(valid()){transition(State.LISTENING,"Konuş, bitince kendiliğinden anlarım");haptic();log("recognizer ready");}}
                public void onBeginningOfSpeech(){}
                public void onRmsChanged(float rms){if(valid() && conversation.state()==State.LISTENING){host.rms(rms);wave.rms(rms);if(orb!=null)orb.rms(rms);if(glow!=null)glow.rms(rms);}}
                public void onBufferReceived(byte[] bytes){}
                public void onEndOfSpeech(){if(valid())transition(State.PROCESSING,"Söylediklerini kontrol ediyorum…");}
                public void onError(int error){if(valid()){stopRecognizer();log("speech error="+error);problem(speechFault(error));}}
                public void onResults(Bundle results){
                    if(!valid())return;log("final result received");
                    ArrayList<String> words=results==null?null:results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    if(words==null||words.isEmpty()||words.get(0)==null||words.get(0).trim().isEmpty()){stopRecognizer();problem(Fault.NO_MATCH);}
                    else{heardByVoice=true;submit(words.get(0));}
                }
                public void onPartialResults(Bundle b){
                    if(!valid() || conversation.state()!=State.LISTENING || b==null)return;
                    ArrayList<String> words=b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    if(words!=null && !words.isEmpty() && words.get(0)!=null && !words.get(0).trim().isEmpty()){
                        heard.setText("“"+words.get(0)+"”");heard.setVisibility(View.VISIBLE);log("partial result received");
                    }
                }
                public void onEvent(int type,Bundle b){}
            });
            Intent intent=new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE,"tr-TR");intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS,1);
            intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS,true);
            recognizer.startListening(intent);
        }catch(Exception e){stopRecognizer();problem(Fault.UNAVAILABLE);}
    }
    private Fault speechFault(int error){
        switch(error){
            case SpeechRecognizer.ERROR_NO_MATCH:return Fault.NO_MATCH;
            case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:return Fault.TIMEOUT;
            case SpeechRecognizer.ERROR_NETWORK:case SpeechRecognizer.ERROR_NETWORK_TIMEOUT:return Fault.NETWORK;
            case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS:return Fault.PERMISSION;
            case SpeechRecognizer.ERROR_RECOGNIZER_BUSY:return Fault.BUSY;
            default:return Fault.UNAVAILABLE;
        }
    }
    public void permissionResult(int code,int[] results){
        if(code!=MICROPHONE_REQUEST)return;
        boolean granted=results.length>0 && results[0]==PackageManager.PERMISSION_GRANTED;
        permissionStage=granted || activity.shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)?PermissionStage.READY:PermissionStage.BLOCKED;
        if(!visible())return;
        if(granted)listen();else problem(Fault.PERMISSION);
    }

    // ============================================================ CEVAP

    private boolean current(int token,String owner,int season,long active){
        return visible() && conversation.current(token) && owner.equals(host.owner()) && season==host.season() && active==host.activeGarden();
    }
    /**
     * Yeni cümle (ses, yazı ve öneri kartı aynı kapıdan): açık sorulu konuşma varsa cümle ona gider; yoksa kararı
     * beyin verir (RuleBrain), asistan yalnız yürütür. Konuşma aşaması burada, kayıt aşaması run() içinde.
     */
    private void submit(String raw){
        if(!visible()||raw.trim().isEmpty())return;
        typing=false;hideKeyboard();input.setText("");
        quietMisses=0;
        lastQuestion=raw.trim();lastRaw=raw;question.setText(lastQuestion);heard.setText("");
        final boolean voice=heardByVoice;
        heardByVoice=false;
        if(opDialog!=null&&opDialog.step()!=OperationVoiceDialog.Step.DONE){answerDialog(raw);return;}
        if(pendingTeach!=null&&answerTeach(raw))return;
        boolean commands=recDialog==null;
        final String asked=pendingCloud;
        java.util.function.Function<String,String> chat=null;
        if(commands)chat=persona()::handle;
        BrainContext context=BrainContext.assistant(opDialog==null&&recDialog==null,commands,asked!=null,chat);
        Decision talk=learned().conversationStage(new Brain.Input(raw),context);
        if(commands&&pastCloudStep(talk))pendingCloud=null;   // izin sorusu yalnız bir cevap bekler
        if(talk!=null){converse(talk,raw,asked);return;}
        if(recDialog!=null&&!recDialog.done()){answerRecord(raw);return;}
        lastSource=voice?LearnedTerms.SOURCE_VOICE:LearnedTerms.SOURCE_TEXT;
        run(raw,ZeytinAssistantCommand.parse(raw),null);
    }
    /** Cümlenin geldiği yol (öğretilen sözün kaynağı). */
    private String lastSource=LearnedTerms.SOURCE_TEXT;
    /** Hesabın sözlüğüyle öğrenilen sözler beyni (hesap değişirse yeniden). */
    private LearnedBrain learned(){
        String owner=host.owner();
        if(learned==null||!owner.equals(learnedOwner)){
            // İP8: önce çiftçinin öğrettikleri, sonra bilgi paketinin açık sözleri (paket arka planda doğrulanır).
            learned=new LearnedBrain(PackDb.store(activity,owner),rules);
            PackDb.warm(activity);
            learnedOwner=owner;
        }
        return learned;
    }
    /** Konuşma aşamasının kararını yürütür (kapanış, anlamadıklarım, karne, internet yardımcısı, sohbet). */
    private void converse(Decision d,String raw,String asked){
        if(d.is(ActionCatalog.KAPAT)){
            closeAfterSpeech=true;
            postDialog(new OperationVoiceDialog.Reply("Rica ederim, kolay gelsin.",OperationVoiceDialog.Action.CANCEL),"Zeytin Asistanı");
            if(!ttsReady||tts==null)main.postDelayed(()->{if(visible()&&dialog!=null)dialog.dismiss();},1500);
            return;
        }
        if(d.is(ActionCatalog.ANLAMADIKLARI_SIL)||d.is(ActionCatalog.ANLAMADIKLARI_GOSTER)){missesCommand(d);return;}
        if(d.is(ActionCatalog.KONUSMA_KAYDI_SIL)||d.is(ActionCatalog.KARNE_GOSTER)){scorecardCommand(d);return;}
        if(d.is(ActionCatalog.SOZLER_GOSTER)){
            // İP6: "öğrendiklerini göster / söyle" Öğrendiklerim ekranını açar; kısa özet sesli söylenir.
            int taught=LearnedScreen.taught(learned().store().all(),0,java.util.TimeZone.getDefault()).size();
            postDialog(new OperationVoiceDialog.Reply(LearnedScreen.spokenSummary(taught),OperationVoiceDialog.Action.CANCEL),"Zeytin · öğrendiklerim");
            LearnedActivity.open(activity);
            return;
        }
        if(d.is(ActionCatalog.SOZ_UNUT)){
            forget(d.payload(String.class));
            return;
        }
        if(d.is(ActionCatalog.SOHBET)){
            String chat=d.payload(String.class);
            AssistantMeter.understood(activity,host.owner(),raw,d.action);
            savePersona();
            postDialog(new OperationVoiceDialog.Reply(chat,OperationVoiceDialog.Action.ASK),"Zeytin · sohbet");
            return;
        }
        cloudCommand(d,asked);
    }
    /** "Anladığım" etiketinden zaman ya da bahçe değişti: soru balonu aynı kalır, cevap yeniden hesaplanır. */
    private void rerun(ZeytinAssistantCommand cmd,Pick pick){
        if(!visible()||cmd==null||lastRaw.isEmpty())return;
        meterAgain=true;
        run(lastRaw,cmd,pick);
    }
    private void run(String raw,final ZeytinAssistantCommand cmd,final Pick pick){
        stopRecognizer();stopTts();
        answer.setText("");lastAnswer="";followUps.removeAllViews();clearAnswerExtras();
        transition(State.PROCESSING,"Kayıtlarını kontrol ediyorum…");
        final int token=conversation.nextTurn();final String owner=host.owner();final int season=host.season();final long active=host.activeGarden();
        final boolean again=meterAgain;
        meterAgain=false;
        final boolean openForm=teachOpenForm;
        teachOpenForm=false;
        final LearnedBrain brain=learned();
        log("intent="+cmd.kind);
        worker.execute(()->{
            meterTurn=token;
            if(again)meteredTurn=token;
            try(ZeytinAssistantData data=new ZeytinAssistantData(activity.getApplicationContext())){
                if(!data.owner.equals(owner)||data.season!=season)return;
                List<Garden> gardens=data.gardens();
                Resolution resolution=pick!=null?pick.resolve(gardens):ZeytinAssistantCommand.resolve(raw,gardens,active,cmd.kind==Kind.MARKET);
                String gardenName=resolution.garden==null?"":resolution.garden.name;
                // Karar beyinde (RuleBrain): hasat, alacak, elek, işlem, sorular. "Anladığım" etiketinden yeniden sorma
                // cümleyi yeniden ayrıştırmaz, etiketle değişen soruyla yalnız soru cevabına gider.
                final List<String> gardenNames=new java.util.ArrayList<>();
                for(Garden g:gardens)gardenNames.add(g.name);
                final BrainContext records=BrainContext.recordsOnly(gardenNames);
                final Decision decision=pick!=null?RuleBrain.question(raw,cmd):brain.recordStage(new Brain.Input(raw),records,cmd);
                // Ekranda ne yapılacağını saf AssistantOutcome seçer (testli); burada yalnız yürütülür.
                final AssistantOutcome.Kind path=AssistantOutcome.records(decision,openForm);
                // "Bugün 3 numara 120 kilo (sattım)": cevap yerine elek formu dolu açılır; kayıt kullanıcının onayıyla.
                // "Girelli'den 300 kilo topladık": hasat (elekten önce: boy söylense de "topladık" hasattır).
                if(path==AssistantOutcome.Kind.HARVEST_QUESTIONS){
                    meter(token,raw,owner,decision.action);
                    startRecord(VoiceRecordDialog.Kind.HARVEST,raw,gardens,resolution,token,owner,season,active);
                    return;
                }
                final HarvestVoiceEntry harvest=LearnedBrain.payload(decision,HarvestVoiceEntry.class);
                if(path==AssistantOutcome.Kind.HARVEST_FORM){
                    meter(token,raw,owner,decision.action);
                    final long hid=resolution.garden==null?0:resolution.garden.id;
                    final String where=resolution.garden==null?"":resolution.garden.name+", ";
                    main.post(()->{if(current(token,owner,season,active)){transition(State.ANSWERING,"Hasat formunu dolduruyorum…");haptic();
                        Toast.makeText(activity,"Hasat formu açılıyor: "+where+ElekVoiceDialog.num(harvest.total)+" kilo. Kontrol edip Kaydet'e bas.",Toast.LENGTH_LONG).show();dialog.dismiss();host.openHarvestEntry(hid,harvest);}});
                    return;
                }
                // "Ahmet'ten ne kadar alacağım var?", "Kim bana borçlu?": alıcı listesindeki açık alacaklardan.
                if(path==AssistantOutcome.Kind.RECEIVABLES){
                    meter(token,raw,owner,decision.action);
                    post(token,owner,season,active,cmd,true,"","Alıcılar · açık alacaklar",AssistantReceivables.answer(raw,AssistantReceivablesData.load(activity,owner,season)));
                    return;
                }
                final ElekVoiceEntry voice=LearnedBrain.payload(decision,ElekVoiceEntry.class);
                if(path==AssistantOutcome.Kind.ELEK_FORM){
                    meter(token,raw,owner,decision.action);
                    final long gid=resolution.garden==null?0:resolution.garden.id;
                    main.post(()->{if(current(token,owner,season,active)){transition(State.ANSWERING,"Elek formunu dolduruyorum…");haptic();
                        Toast.makeText(activity,"Elek formu açılıyor: "+voice.summary(),Toast.LENGTH_SHORT).show();dialog.dismiss();host.openElekEntry(gid,voice);}});
                    return;
                }
                // "Elek gireceğim / elek kaydı": elek ekranı açılır, sesli elek hemen başlar.
                if(path==AssistantOutcome.Kind.ELEK_SCREEN){
                    meter(token,raw,owner,decision.action);
                    final long eg=resolution.garden==null?0:resolution.garden.id;
                    main.post(()->{if(current(token,owner,season,active)){transition(State.ANSWERING,"Elek ekranını açıyorum…");haptic();dialog.dismiss();host.openElekVoice(eg);}});
                    return;
                }
                final VoiceRecordDialog.Kind recKind=LearnedBrain.payload(decision,VoiceRecordDialog.Kind.class);
                if(path==AssistantOutcome.Kind.RECORD_QUESTIONS){
                    meter(token,raw,owner,decision.action);
                    startRecord(recKind,raw,gardens,resolution,token,owner,season,active);
                    return;
                }
                // "Bugün Girelli'yi ilaçladım", "Mazot aldım 500 lira": bahçe varsa eksikler sorulur, yoksa işlem formu dolu açılır.
                final OperationVoiceEntry op=LearnedBrain.payload(decision,OperationVoiceEntry.class);
                if(op!=null)meter(token,raw,owner,decision.action);
                final LearnedTerms.Term taught=LearnedBrain.term(decision);
                // Paketten gelen söz için "öğrendim" denmez (çiftçi öğretmedi); yalnız öğretilen sözde.
                final boolean own=taught!=null&&LearnedTerms.TAUGHT.equals(taught.kind);
                final String told=own&&taught.uses<LearnedTerms.TELL_USES?LearnedTerms.told(taught)+" ":"";
                if(path==AssistantOutcome.Kind.OPERATION_QUESTIONS){
                    // Karşısında insan varmış gibi: eksikleri tek tek sorar ("Hangi bahçeyi?", "Ne attın?"), "evet"le kaydeder.
                    final List<String> names=new java.util.ArrayList<>();for(Garden g:gardens)names.add(g.name);
                    final int gi=resolution.explicit&&resolution.garden!=null?gardens.indexOf(resolution.garden):-1;
                    final List<Garden> owned=gardens;
                    main.post(()->{if(current(token,owner,season,active)){
                        opDialog=new OperationVoiceDialog(op,names,gi);opGardens=owned;opCloudRaw=null;
                        OperationVoiceDialog.Reply first=opDialog.start();
                        postDialog(new OperationVoiceDialog.Reply(told+first.speech,first.action),"Bahçe işi · sesle kayıt");
                        if(taught!=null&&!told.isEmpty())addForgetButton(taught);}});
                    return;
                }
                if(path==AssistantOutcome.Kind.OPERATION_FORM){
                    final long gid=resolution.garden==null?0:resolution.garden.id;
                    final String where=resolution.garden==null?"":resolution.garden.name+", ";
                    main.post(()->{if(current(token,owner,season,active)){transition(State.ANSWERING,"İşlem formunu dolduruyorum…");haptic();
                        Toast.makeText(activity,told+"İşlem formu açılıyor: "+where+op.summary()+". Kontrol edip Kaydet'e bas.",Toast.LENGTH_LONG).show();dialog.dismiss();host.openOperationEntry(gid,op);}});
                    return;
                }
                // İP7: çok adımlı satış ("…sattım, parasını haftaya verecek"): satış formu açılır, Tanecik görünür doldurur,
                // sonunda tek "Kaydedeyim mi?". Onaydan önce hiçbir şey yazılmaz; hepsi tek "Geri al" ile geri alınır.
                if(path==AssistantOutcome.Kind.MULTI_STEP){
                    main.post(()->{if(current(token,owner,season,active)){transition(State.ANSWERING,"Satışı hazırlıyorum…");haptic();
                        Toast.makeText(activity,"Satışı birlikte dolduruyoruz; sonunda sana soracağım.",Toast.LENGTH_LONG).show();dialog.dismiss();
                        activity.startActivity(new android.content.Intent(activity,SaleActivity.class).putExtra(SaleActivity.EXTRA_PLAN,raw));}});
                    return;
                }
                // Eğitim turu 1: tahsilat ("Ali Usta'dan 2000 lira aldım"). Alıcının "Ödeme aldım" penceresi tutarla açılır;
                // kayıt yalnız çiftçinin Kaydet'iyle. Alıcı yoksa ya da açık borcu yoksa hiçbir şey kaydedilmez.
                if(path==AssistantOutcome.Kind.COLLECTION){
                    meter(token,raw,owner,decision.action);
                    collect(LearnedBrain.payload(decision,CollectionEntry.class),token,owner,season,active,cmd);
                    return;
                }
                // Eğitim turu 1: yağhane ("fabrikaya 40 kasa götürdük"): sıkım formu dolu açılır; kasa notta, eksikler
                // formda sorulur; kayıt Kaydet'le.
                if(path==AssistantOutcome.Kind.OIL_FORM){
                    meter(token,raw,owner,decision.action);
                    final OilVoiceEntry oilEntry=LearnedBrain.payload(decision,OilVoiceEntry.class);
                    main.post(()->{
                        if(!current(token,owner,season,active))return;
                        transition(State.ANSWERING,"Yağhane formunu dolduruyorum…");
                        haptic();
                        Toast.makeText(activity,"Sıkım formu açılıyor: "+oilEntry.summary()+". Eksikleri yazıp Kaydet'e bas.",Toast.LENGTH_LONG).show();
                        dialog.dismiss();
                        activity.startActivity(new android.content.Intent(activity,OilActivity.class).putExtra(OilActivity.EXTRA_VOICE,raw));
                    });
                    return;
                }
                // Eğitim turu 1: gelecek zamanlı iş ("yarın Girelli'yi sulayacağız") kayıt değil, hatırlatma: her zaman sorulur.
                if(path==AssistantOutcome.Kind.REMINDER){
                    meter(token,raw,owner,decision.action);
                    final TaskReminderEntry task=LearnedBrain.payload(decision,TaskReminderEntry.class);
                    final long rg=resolution.garden==null?0:resolution.garden.id;
                    final String rname=resolution.garden==null?"":resolution.garden.name;
                    main.post(()->{
                        if(!current(token,owner,season,active))return;
                        askReminder(task,rg,rname,token,owner,season,active,cmd);
                    });
                    return;
                }
                // Kuralların anlamadığı iş cümlesi: izin varsa internetteki yardımcıya (Gemma 4) sorulur. Cevap cümleyle
                // karşılaştırılarak denetlenir (AssistantCloud.accept), okunur, "evet" denmeden kaydedilmez.
                // Soru kalıbı değil, geçmiş zamanlı bir iş cümlesiyse ("ağaçların altını temizledik, 2 amele tuttuk") kayıt sorusu
                // (TREES vb.) sanılsa bile yardımcıya gider; ekran açma komutları gitmez (eligible soru ve komutları ayırır).
                if(pick==null&&AssistantCloudClient.configured()&&CloudBrain.wants(raw,cmd,gardenNames)){
                    String mode=AssistantCloudClient.mode(activity,owner);
                    if(!"off".equals(mode)&&AssistantCloudClient.online(activity)){
                        rememberMiss(raw,owner);
                        if(mode.isEmpty()){
                            main.post(()->{if(current(token,owner,season,active)){pendingCloud=raw;postDialog(new OperationVoiceDialog.Reply(AssistantCloud.ASK,OperationVoiceDialog.Action.ASK),"Zeytin · internet yardımcısı");}});
                            return;
                        }
                        main.post(()->{if(current(token,owner,season,active))transition(State.PROCESSING,"Düşünüyorum…");});
                        final Decision help=cloudBrain.decide(new Brain.Input(raw),records);
                        final AssistantCloud.Result cloud=help.payload(AssistantCloud.Result.class);
                        if(cloud!=null){
                            final List<Garden> owned=gardens;
                            main.post(()->{if(current(token,owner,season,active)){
                                opDialog=new OperationVoiceDialog(cloud.entry,gardenNames,cloud.garden);opGardens=owned;opCloudRaw=raw;
                                postDialog(opDialog.start(),"Bahçe işi · internet yardımcısı");}});
                            return;
                        }
                        // Anlaşılamadı / internet koptu / kota doldu: aşağıda bugünkü gibi devam; nedeni kısa bildirimle görünür.
                        final String why=AssistantCloudClient.lastOk?"Yardımcının cevabı denetimden geçmedi.":AssistantCloudClient.lastStatus;
                        if(AssistantCloudClient.lastOk)AssistantCloudClient.lastStatus="Yardımcı cevap verdi ama cevap cümleyle uyuşmadı (denetimden geçmedi).";
                        main.post(()->{if(visible())Toast.makeText(activity,"İnternet yardımcısı: "+why,Toast.LENGTH_LONG).show();});
                    }
                }
                // Tanınmayan iş fiili ("bakırladık"): tek soru, büyük düğmeler; öğretilen söz hep anlaşılır (İP4).
                // Sohbet cümlesinde ("çok yoruldum") sorulmaz, aşağıdaki sohbet cevabı gelir; "bugün bakırladık" sorulur.
                final String teach=pick==null?LearnedBrain.teachQuestion(raw,decision,brain.store(),gardenNames):null;
                final boolean regionMarket=cmd.kind==Kind.MARKET && !ZeytinAssistantMarket.ownSales(cmd.text);
                final AssistantOutcome.Kind later=AssistantOutcome.rest(decision,teach,cmd.kind==Kind.NAVIGATION,regionMarket);
                if(later==AssistantOutcome.Kind.SPECIAL||later==AssistantOutcome.Kind.TEACH||later==AssistantOutcome.Kind.CHAT||later==AssistantOutcome.Kind.HINT){
                    // Selam, teşekkür, kayıt silme, ilaç dozu: sabit cevap önce (dozda ilaçlama sorusu önerilmez).
                    String special=LearnedBrain.payload(decision,String.class);
                    if(special!=null)meter(token,raw,owner,decision.action);
                    if(later==AssistantOutcome.Kind.SPECIAL){post(token,owner,season,active,cmd,false,"",special);return;}
                    if(later==AssistantOutcome.Kind.TEACH){
                        rememberMiss(raw,owner);
                        final String source=lastSource;
                        // Düğme sırası: çiftçinin kendi kayıtlarında en sık yaptığı iş üstte (İP5).
                        final Map<String,Integer> counts=WorkCounts.load(activity,owner);
                        main.post(()->{if(current(token,owner,season,active))askTeach(raw,teach,source,counts);});
                        return;
                    }
                    // Kalıba uymadı: sorudaki konu kelimelerinden yakın sorular ya da konu kartları.
                    Set<ZeytinAssistantHints.Topic> recorded=data.recordedTopics(gardens);
                    String named=resolution.explicit && resolution.garden!=null?resolution.garden.name:"";
                    if(later==AssistantOutcome.Kind.CHAT){
                        // Konusu olmayan sohbet: soğuk "bilmiyorum + kartlar" yerine dostça cevap, sohbet sürer.
                        final String line=persona().fallback(raw);
                        rememberMiss(raw,owner);
                        main.post(()->{if(current(token,owner,season,active))postDialog(new OperationVoiceDialog.Reply(line,OperationVoiceDialog.Action.ASK),"Zeytin · sohbet");});
                        return;
                    }
                    ZeytinAssistantHints.Hint hint=ZeytinAssistantHints.hint(raw,named,recorded);
                    rememberMiss(raw,owner);
                    if(data.unchanged())postHint(token,owner,season,active,hint,recorded);
                    return;
                }
                meter(token,raw,owner,decision.action);   // EKRAN_AC ya da SORU_<tür>
                if(later==AssistantOutcome.Kind.NAVIGATE){
                    if(!resolution.error.isEmpty() && (!isGlobal(cmd.target) || !resolution.error.startsWith("Önce"))){post(token,owner,season,active,cmd,false,gardenName,resolution.error);return;}
                    main.post(()->{if(current(token,owner,season,active)){long id=resolution.garden==null?0:resolution.garden.id;transition(State.ANSWERING,"Ekranı açıyorum…");haptic();Toast.makeText(activity,"Ekranı açıyorum…",Toast.LENGTH_SHORT).show();dialog.dismiss();host.navigate(cmd.target,id);}});return;
                }
                if(later==AssistantOutcome.Kind.MARKET){
                    // Piyasa sorusu: kendi bölgenin (ya da söylenen bölgenin) anonim fiyatları. Bölgeye
                    // ulaşılamazsa kendi satış kayıtlarından cevap, bunu açıkça söyleyerek.
                    String regionId="",regionName="";
                    DatabaseHelper helper=new DatabaseHelper(activity.getApplicationContext());
                    try{
                        ZeytinLeagueRepository.Result local=new ZeytinLeagueRepository().local(helper,season,owner);
                        if(local.local!=null && local.local.region!=null)regionId=local.local.region;
                        for(ZeytinLeagueRepository.Region r:local.regions)if(r!=null && regionId.equals(r.id) && r.name!=null)regionName=r.name;
                    }catch(RuntimeException ignored){regionId="";}finally{helper.close();}
                    if("Bölge belirtilmemiş".equals(regionName))regionId="";
                    final String ownId=regionId,ownName=regionName;
                    final String fallback=data.answer(cmd,resolution.garden,gardens,false);
                    if(!data.unchanged())return;
                    main.post(()->regionMarket(token,owner,season,active,cmd,ownId,ownName,fallback));
                    return;
                }
                boolean all=pick!=null?pick.all:cmd.total && !resolution.explicit && cmd.kind!=Kind.ACTIVE;
                final AnswerScope scope=new AnswerScope(resolution.garden,all,gardens);
                if(!resolution.error.isEmpty() && !(all && resolution.error.startsWith("Önce")) && cmd.kind!=Kind.MARKET && cmd.kind!=Kind.COMPARISON && !cmd.crossGarden){post(token,owner,season,active,cmd,all,gardenName,resolution.error);return;}
                if(cmd.kind==Kind.TODAY){
                    main.post(()->{if(!current(token,owner,season,active))return;
                        transition(State.PROCESSING,"Hava tahminine bakıyorum…");
                        host.weather(resolution.garden,new WeatherRepository.ForecastCallback(){
                            public void onSuccess(WeatherRepository.WeatherForecast forecast){advisorReply(token,owner,season,active,cmd,resolution.garden,forecast,scope);}
                            public void onError(String message){advisorReply(token,owner,season,active,cmd,resolution.garden,null,scope);}
                        });
                    });return;
                }
                if(cmd.kind==Kind.WEATHER){
                    main.post(()->{if(!current(token,owner,season,active))return;
                        transition(State.PROCESSING,"Hava tahminine bakıyorum…");
                        host.weather(resolution.garden,new WeatherRepository.ForecastCallback(){
                            public void onSuccess(WeatherRepository.WeatherForecast forecast){
                                Calendar date=Calendar.getInstance(TimeZone.getTimeZone("Europe/Istanbul"));date.add(Calendar.DAY_OF_MONTH,1);
                                SimpleDateFormat format=new SimpleDateFormat("yyyy-MM-dd",Locale.US);format.setTimeZone(date.getTimeZone());
                                post(token,owner,season,active,cmd,false,gardenName,null,ZeytinAssistantWeather.answer(cmd,resolution.garden.name,forecast,format.format(date.getTime())),scope);
                            }
                            public void onError(String message){post(token,owner,season,active,cmd,false,gardenName,"Hava verisi alınamadı. Kayıtlarınla ilgili soruları sormaya devam edebilirsin.");}
                        });
                    });return;
                }
                String result=data.answer(cmd,resolution.garden,gardens,all);
                if(data.unchanged())post(token,owner,season,active,cmd,all,gardenName,null,result,scope);
            }catch(Exception e){post(token,owner,season,active,cmd,false,"","Kayıtlar okunamadı. Daha sonra tekrar deneyebilirsin; herhangi bir kayıt değiştirilmedi.");}
        });
    }
    private void advisorReply(int token,String owner,int season,long active,ZeytinAssistantCommand cmd,Garden garden,WeatherRepository.WeatherForecast forecast,AnswerScope scope){
        main.post(()->{
        if(!current(token,owner,season,active))return;
        worker.execute(()->{try(ZeytinAssistantData data=new ZeytinAssistantData(activity.getApplicationContext())){
            if(!owner.equals(data.owner)||season!=data.season)return;
            String result=data.advice(garden,forecast);if(data.unchanged())post(token,owner,season,active,cmd,false,garden==null?"":garden.name,null,result,scope);
        }catch(Exception e){post(token,owner,season,active,cmd,false,"","Bugünkü kayıtlar okunamadı. Tekrar deneyebilirsin.");}});
        });
    }
    private void regionMarket(int token,String owner,int season,long active,ZeytinAssistantCommand cmd,String ownId,String ownName,String fallback){
        if(!current(token,owner,season,active))return;
        final String own="KENDİ SATIŞ KAYITLARIN";
        transition(State.PROCESSING,"Bölge fiyatlarına bakıyorum…");
        MarketCloudRepository cloud=marketRepository(owner);
        if(cloud==null||!cloud.canUseCloud()){post(token,owner,season,active,cmd,false,"",own,"Bölge fiyatlarına şu an ulaşılamıyor. "+fallback);return;}
        cloud.loadLeagueRegions(season,(regions,error)->{
            if(!current(token,owner,season,active))return;
            Map<String,String> names=new LinkedHashMap<>();
            if(regions!=null)for(MarketCloudRepository.LeagueRegionStat r:regions)if(r!=null && !r.id.isEmpty())names.put(r.id,r.name);
            // Bölge dizini gelmese de kullanıcı kendi bölgesini adıyla sorabilsin ("Alaşehir'de 1 numara").
            if(!ownId.isEmpty() && !names.containsKey(ownId))names.put(ownId,ownName);
            String named=ZeytinAssistantMarket.regionFor(cmd.text,names);
            if(named==null){post(token,owner,season,active,cmd,false,"","ANONİM BÖLGE VERİSİ",ZeytinAssistantMarket.unknownRegion());return;}
            final String id=named.isEmpty()?ownId:named;
            final String name=named.isEmpty()?ownName:names.get(named);
            if(id==null||id.isEmpty()){post(token,owner,season,active,cmd,false,"",own,ZeytinAssistantMarket.noRegion());return;}
            cloud.loadLeagueRegionReport(season,id,name==null?"":name,(report,failure)->{
                if(!current(token,owner,season,active))return;
                if(report==null||(failure!=null && report.producerCount==0 && report.grades.isEmpty())){
                    post(token,owner,season,active,cmd,false,"",own,"Bölge fiyatlarına şu an ulaşılamıyor. "+fallback);return;
                }
                List<ZeytinAssistantMarket.Grade> grades=new ArrayList<>();
                for(MarketCloudRepository.LeagueGradeReport g:report.grades)if(g!=null)
                    grades.add(new ZeytinAssistantMarket.Grade(g.grade,g.producerCount,g.medianPrice,g.averagePrice,g.trendAvailable,g.trendPct));
                String label=report.regionName.isEmpty()?(name==null?"":name):report.regionName;
                String place=MeydanMarketStrip.shortRegion(label).toUpperCase(new Locale("tr","TR"));
                post(token,owner,season,active,cmd,false,"",place.isEmpty()?"ANONİM BÖLGE VERİSİ":"ANONİM BÖLGE VERİSİ · "+place,
                        ZeytinAssistantMarket.answer(label,report.producerCount,report.available,grades,ZeytinAssistantMarket.grade(cmd.grade)));
            });
        });
    }
    private MarketCloudRepository marketRepository(String owner){
        try{
            if(marketDb==null)marketDb=new DatabaseHelper(activity.getApplicationContext());
            if(market==null||!owner.equals(marketOwner)){market=new MarketCloudRepository(marketDb,owner);marketOwner=owner;}
            return market;
        }catch(RuntimeException e){return null;}
    }
    private static boolean isGlobal(String target){return Arrays.asList("HOME","GARDENS","MARKET","MEYDAN","REPORTS","BRINE","ELEK").contains(target);}
    private void post(int token,String owner,int season,long active,ZeytinAssistantCommand cmd,boolean all,String gardenName,String raw){
        post(token,owner,season,active,cmd,all,gardenName,null,raw);
    }
    private void post(int token,String owner,int season,long active,ZeytinAssistantCommand cmd,boolean all,String gardenName,String sourceLabel,String raw){
        post(token,owner,season,active,cmd,all,gardenName,sourceLabel,raw,null);
    }
    /** @param scope kayıtlardan hesaplanmış cevapta dolu: "Anladığım" etiketleri çizilir. Hata cevaplarında null. */
    private void post(int token,String owner,int season,long active,ZeytinAssistantCommand cmd,boolean all,String gardenName,String sourceLabel,String raw,AnswerScope scope){
        // Tarihler ekranda ve sesli okumada aynı okunur biçimde: "1 Ekim (16 gün önce)".
        String intro=++answersThisPanel==2?persona().introduceOnce():"";
        if(!intro.isEmpty())savePersona();
        final String shown=ZeytinAssistantText.humanDates(raw,System.currentTimeMillis())+intro;
        main.post(()->{
            if(!current(token,owner,season,active))return;
            clearAnswerExtras();
            lastAnswer=shown;answer.setText(shown);source.setText(sourceLabel!=null?sourceLabel:ZeytinAssistantText.source(cmd.kind,all,gardenName));
            lastCmd=scope==null?null:cmd;lastScope=scope;
            if(scope!=null)renderTags(ZeytinAssistantHints.understood(cmd,gardenName,all,scope.gardens.size()),cmd);
            followUps.removeAllViews();
            for(String q:ZeytinAssistantText.followUps(cmd.kind,lastQuestion))followUps.addView(chip(q));
            listenAfterSpeech=true;   // sohbet sürer: cevaptan sonra yeniden dinler
            transition(State.ANSWERING,ttsReady?"Cevabı okuyorum…":"Cevap hazır");haptic();speak();
            if(!ttsReady||tts==null){listenAfterSpeech=false;main.postDelayed(()->{if(visible())listen();},2500);}
        });
    }
    private void postHint(int token,String owner,int season,long active,ZeytinAssistantHints.Hint hint,Set<ZeytinAssistantHints.Topic> recorded){
        main.post(()->{
            if(!current(token,owner,season,active))return;
            recordedTopics=recorded;
            showHint(hint,true);
        });
    }

    // ============================================================ ANLAMA: yakın sorular, konu kartları, etiketler

    /** Yakın sorular (A) ya da konu kartları (B). Kart/konu geçişleri yerel: yeni kayıt okuması yok. */
    private void showHint(ZeytinAssistantHints.Hint hint,boolean speakIt){
        clearAnswerExtras();lastCmd=null;lastScope=null;
        hinting=true;offerTopics=!hint.cards;
        lastAnswer=hint.message;answer.setText(hint.message);answer.setTextSize(15);
        source.setText(hint.header);source.setTextColor(warning);
        android.graphics.drawable.Drawable lens=tinted(R.drawable.ic_op_search,warning,14);
        source.setCompoundDrawablesRelative(lens,null,null,null);source.setCompoundDrawablePadding(dp(6));
        if(hint.cards){
            // Kartlar doğrudan panelde (tasarım B): cevap kartının çerçevesi yok.
            answerCard.setBackground(null);answerCard.setPadding(0,0,0,0);source.setVisibility(View.GONE);
            ZeytinAssistantHints.Topic[] topics=ZeytinAssistantHints.Topic.values();
            for(int i=0;i<topics.length;i+=2){
                LinearLayout line=row();line.setGravity(Gravity.TOP);
                LinearLayout.LayoutParams left=new LinearLayout.LayoutParams(0,-2,1);left.rightMargin=dp(10);
                line.addView(tile(topics[i]),left);
                if(i+1<topics.length)line.addView(tile(topics[i+1]),new LinearLayout.LayoutParams(0,-2,1));
                hintBox.addView(line,margins(-1,-2,dp(10),0));
            }
        }else{
            int icon=topicIcon(hint.topics.isEmpty()?null:hint.topics.get(0));
            for(int i=0;i<hint.questions.size();i++){
                String q=hint.questions.get(i);
                ZeytinAssistantCommand qc=ZeytinAssistantCommand.parse(q);
                hintBox.addView(suggestion(q,qc.kind==Kind.NAVIGATION?R.drawable.ic_op_add:icon,i==0),margins(-1,-2,dp(10),0));
            }
        }
        transition(State.ANSWERING,hint.cards?"Hangi konuda soracaktın?":"Tam anlayamadım, yakın sorular buldum");
        if(speakIt){haptic();speak();}
    }
    private void showTopic(ZeytinAssistantHints.Topic topic){
        if(!visible())return;
        stopTts();showHint(ZeytinAssistantHints.topic(topic,recordedTopics),false);
    }
    /** Önceki cevabın öneri, kart ve etiketlerini temizler; cevap kartını varsayılan görünümüne döndürür. */
    private void clearAnswerExtras(){
        if(hintBox==null)return;
        hintBox.removeAllViews();tagsRow.removeAllViews();hinting=false;offerTopics=false;
        answer.setTextSize(17);source.setTextColor(muted);source.setCompoundDrawablesRelative(null,null,null,null);source.setVisibility(View.VISIBLE);
        answerCard.setBackground(round(surface,border,18));answerCard.setPadding(dp(14),dp(14),dp(14),dp(14));
        if(tagSheet!=null){try{tagSheet.dismiss();}catch(Exception ignored){}tagSheet=null;}
    }
    private View suggestion(String q,int iconRes,boolean first){
        LinearLayout r=row();r.setMinimumHeight(dp(52));r.setPadding(dp(14),dp(8),dp(12),dp(8));
        r.setBackground(round(bg,first?green:border,16));
        ImageView i=icon(iconRes,green);r.addView(i,new LinearLayout.LayoutParams(dp(18),dp(18)));
        TextView t=label(q,14,text,true);t.setPadding(dp(10),0,dp(8),0);r.addView(t,new LinearLayout.LayoutParams(0,-2,1));
        r.addView(icon(R.drawable.ic_op_chevron,muted),new LinearLayout.LayoutParams(dp(16),dp(16)));
        r.setContentDescription("Sor: "+q);r.setOnClickListener(v->submit(q));
        return r;
    }
    private View tile(ZeytinAssistantHints.Topic topic){
        LinearLayout t=column();t.setPadding(dp(12),dp(12),dp(12),dp(12));t.setMinimumHeight(dp(78));
        t.setBackground(round(surface,border,18));
        t.addView(icon(topicIcon(topic),green),new LinearLayout.LayoutParams(dp(22),dp(22)));
        TextView name=label(topic.label,14,text,true);name.setPadding(0,dp(6),0,0);t.addView(name);
        t.addView(label(topic.sub,11,muted,false));
        t.setContentDescription(topic.label+": "+topic.sub);t.setOnClickListener(v->showTopic(topic));
        return t;
    }
    private static int topicIcon(ZeytinAssistantHints.Topic topic){
        if(topic==null)return R.drawable.ic_op_leaf;
        switch(topic){
            case IRRIGATION:return R.drawable.ic_op_water;
            case CARE:return R.drawable.ic_op_flask;
            case HARVEST:return R.drawable.ic_op_olive;
            case MONEY:return R.drawable.ic_op_tag;
            case WEATHER:return R.drawable.ic_op_cloud;
            default:return R.drawable.ic_op_chart;
        }
    }
    private static int kindIcon(Kind kind){
        switch(kind){
            case IRRIGATION:case WATER_TYPE:return R.drawable.ic_op_water;
            case FERTILIZER:case SPRAY:return R.drawable.ic_op_flask;
            case HARVEST:case ELEK:case BRINE:case READY:return R.drawable.ic_op_olive;
            case EXPENSE:case REVENUE:case SOLD:case SUMMARY:return R.drawable.ic_op_tag;
            case WEATHER:return R.drawable.ic_op_cloud;
            case MARKET:return R.drawable.ic_op_chart;
            case TREES:case AGE:case VARIETY:case AREA:case LOCATION:case NOTE:case ACTIVE:return R.drawable.ic_op_tree;
            default:return R.drawable.ic_op_leaf;
        }
    }
    private void renderTags(List<ZeytinAssistantHints.Tag> tags,ZeytinAssistantCommand cmd){
        tagsRow.removeAllViews();
        for(ZeytinAssistantHints.Tag tag:tags){
            TextView t=label(tag.text,12,text,true);t.setGravity(Gravity.CENTER_VERTICAL);t.setMinHeight(dp(34));t.setPadding(dp(10),0,dp(10),0);
            t.setBackground(round(surface,border,17));
            int iconRes=tag.type==ZeytinAssistantHints.TagType.TOPIC?kindIcon(cmd.kind):tag.type==ZeytinAssistantHints.TagType.GARDEN?R.drawable.ic_op_tree:R.drawable.ic_op_calendar;
            t.setCompoundDrawablesRelative(tinted(iconRes,green,14),null,tag.editable?tinted(R.drawable.ic_op_down,muted,14):null,null);
            t.setCompoundDrawablePadding(dp(6));
            String what=tag.type==ZeytinAssistantHints.TagType.TOPIC?"Konu":tag.type==ZeytinAssistantHints.TagType.GARDEN?"Bahçe":"Zaman";
            t.setContentDescription(what+": "+tag.text+(tag.editable?". Değiştirmek için dokun":""));
            if(tag.editable){final ZeytinAssistantHints.TagType type=tag.type;t.setOnClickListener(v->openTagSheet(type));}
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,dp(34));lp.rightMargin=dp(6);tagsRow.addView(t,lp);
        }
    }
    /** Etiketi düzeltme sayfası (tasarım D): seçince cevap aynı soru için yeniden hesaplanır. */
    private void openTagSheet(ZeytinAssistantHints.TagType type){
        if(!visible()||lastCmd==null||lastScope==null)return;
        final ZeytinAssistantCommand cmd=lastCmd;final AnswerScope scope=lastScope;
        stopTts();
        final Dialog sheet=new Dialog(activity);sheet.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout box=column();box.setPadding(dp(18),dp(10),dp(18),dp(28));
        GradientDrawable shape=new GradientDrawable();shape.setColor(card);float r=dp(26);shape.setCornerRadii(new float[]{r,r,r,r,0,0,0,0});shape.setStroke(dp(1),border);
        box.setBackground(shape);
        View handle=new View(activity);handle.setBackground(round(border,border,2));
        LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(dp(40),dp(4));hp.gravity=Gravity.CENTER_HORIZONTAL;hp.bottomMargin=dp(14);box.addView(handle,hp);
        boolean period=type==ZeytinAssistantHints.TagType.PERIOD;
        box.addView(label(period?"Hangi zaman?":"Hangi bahçe?",16,text,true));
        TextView sub=label("Seçince cevap hemen yeniden hesaplanır, tekrar konuşman gerekmez.",12,muted,false);sub.setPadding(0,dp(4),0,dp(10));box.addView(sub);
        LinearLayout options=column();
        if(period){
            for(String[] p:ZeytinAssistantHints.periodOptions(cmd.period)){
                final String key=p[0];
                options.addView(option(p[1],key.equals(cmd.period.name),()->{sheet.dismiss();rerun(cmd.withPeriod(key),new Pick(scope.gardenId(),scope.all));}));
            }
        }else{
            boolean every=ZeytinAssistantHints.everyGarden(cmd,scope.all);
            for(Garden g:scope.gardens){
                final long id=g.id;
                options.addView(option(g.name,!every && id==scope.gardenId(),()->{sheet.dismiss();rerun(cmd,new Pick(id,false));}));
            }
            if(ZeytinAssistantHints.supportsAll(cmd))
                options.addView(option("Tüm bahçeler",every,()->{sheet.dismiss();rerun(cmd,new Pick(scope.gardenId(),true));}));
        }
        ScrollView scroll=new ScrollView(activity);scroll.addView(options);box.addView(scroll,new LinearLayout.LayoutParams(-1,-2));
        sheet.setContentView(box);
        sheet.setOnDismissListener(d->{if(tagSheet==sheet)tagSheet=null;});
        tagSheet=sheet;sheet.show();
        Window window=sheet.getWindow();if(window!=null){
            window.setBackgroundDrawableResource(android.R.color.transparent);window.setGravity(Gravity.BOTTOM);
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);
            WindowManager.LayoutParams wp=window.getAttributes();wp.dimAmount=.55f;window.setAttributes(wp);window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        }
    }
    private View option(String name,boolean selected,Runnable action){
        TextView o=label(name,15,text,selected);o.setGravity(Gravity.CENTER_VERTICAL);o.setMinHeight(dp(50));o.setPadding(dp(14),0,dp(14),0);
        if(selected){o.setBackground(round(greenSoft,greenSoft,14));o.setCompoundDrawablesRelative(null,null,tinted(R.drawable.ic_op_check,green,18),null);}
        o.setContentDescription(name+(selected?", seçili":""));o.setOnClickListener(v->action.run());
        return o;
    }
    private android.graphics.drawable.Drawable tinted(int res,int color,int size){
        android.graphics.drawable.Drawable d=activity.getDrawable(res);if(d==null)return null;
        d=d.mutate();d.setTint(color);d.setBounds(0,0,dp(size),dp(size));return d;
    }
    // ============================================================ SOHBETLE KAYIT

    private OperationVoiceDialog opDialog;
    /** İnternet yardımcısına sorulsun mu diye bekleyen cümle (tek seferlik izin sorusu). */
    private String pendingCloud;
    private List<ZeytinAssistantCommand.Garden> opGardens;
    /** Soru sorulduysa okuma bitince mikrofon kendiliğinden açılır (sohbet sürer). */
    private boolean listenAfterSpeech;

    private VoiceRecordDialog recDialog;
    private List<ZeytinAssistantCommand.Garden> recGardens;

    private void startRecord(VoiceRecordDialog.Kind kind,String raw,List<ZeytinAssistantCommand.Garden> gardens,Resolution resolution,int token,String owner,int season,long active){
        final List<String> names=new java.util.ArrayList<>();for(ZeytinAssistantCommand.Garden g:gardens)names.add(g.name);
        final int gi=resolution.explicit&&resolution.garden!=null?gardens.indexOf(resolution.garden):-1;
        main.post(()->{if(current(token,owner,season,active)){
            recDialog=new VoiceRecordDialog(kind,names);recGardens=gardens;recDialog.prefill(raw,gi);
            VoiceRecordDialog.Reply r=recDialog.start();
            postDialog(new OperationVoiceDialog.Reply(r.speech,OperationVoiceDialog.Action.ASK),label(kind));}});
    }

    private static String label(VoiceRecordDialog.Kind k){
        switch(k){case HARVEST:return "Hasat · sesle kayıt";case BRINE:return "Salamura · sesle kayıt";case GARDEN:return "Yeni bahçe · sesle";default:return "Meydan gönderisi · sesle";}
    }

    private void answerRecord(String raw){
        VoiceRecordDialog d=recDialog;
        VoiceRecordDialog.Reply r=d.handle(raw);
        int gi=d.gardenIndex();
        long gid=gi>=0&&recGardens!=null&&gi<recGardens.size()?recGardens.get(gi).id:0;
        if(r.action==VoiceRecordDialog.Action.ASK){postDialog(new OperationVoiceDialog.Reply(r.speech,OperationVoiceDialog.Action.ASK),label(d.kind));return;}
        recDialog=null;
        postDialog(new OperationVoiceDialog.Reply(r.speech,OperationVoiceDialog.Action.CANCEL),label(d.kind));
        if(r.action==VoiceRecordDialog.Action.OPEN_FORM){final long g=gid;main.postDelayed(()->{if(visible())dialog.dismiss();host.openRecordForm(d,g);},1600);}
    }

    private void answerDialog(String raw){
        OperationVoiceDialog d=opDialog;
        OperationVoiceDialog.Reply r=d.handle(raw);
        long gid=d.garden>=0&&opGardens!=null&&d.garden<opGardens.size()?opGardens.get(d.garden).id:0;
        switch(r.action){
            case SAVE:{
                java.util.Calendar c=java.util.Calendar.getInstance();c.add(java.util.Calendar.DAY_OF_MONTH,d.entry.dayOffset);
                String date=new java.text.SimpleDateFormat("dd.MM.yyyy",new Locale("tr","TR")).format(c.getTime());
                Approval approval=Approval.operationConfirmed(d);
                long id=host.saveOperation(approval,gid,OperationKinds.TYPES[d.entry.type],date,d.quantityForSave(),d.unitForSave(),d.entry.cost,d.noteForSave());
                if(id>0&&opCloudRaw!=null)learnFromCloud(opCloudRaw,d.entry);
                opDialog=null;opCloudRaw=null;
                postDialog(new OperationVoiceDialog.Reply(id>0?"Kaydettim. "+persona().thanks():"Kaydedemedim. Formu açıyorum, oradan kaydedersin.",OperationVoiceDialog.Action.SAVE),"Bahçe işi · kaydedildi");
                if(id<=0){final long g=gid;main.postDelayed(()->{if(visible())dialog.dismiss();host.openOperationEntry(g,d.entry);},1500);}
                break;
            }
            case OPEN_FORM:{
                opDialog=null;final long g=gid;
                postDialog(r,"Bahçe işi · form");
                main.postDelayed(()->{if(visible())dialog.dismiss();host.openOperationEntry(g,d.entry);},1500);
                break;
            }
            case CANCEL: opDialog=null; postDialog(r,"Bahçe işi"); break;
            default: postDialog(r,"Bahçe işi · sesle kayıt");
        }
    }

    // ============================================================ ÖĞRENİLEN SÖZLER (İP4)

    /** "Bakırladık ne işi?": tek soru, okunur, büyük düğmeler; sesle de cevaplanabilir. */
    private void askTeach(String raw,String word,String source,Map<String,Integer> counts){
        pendingTeach=new String[]{raw,word,source};
        postDialog(new OperationVoiceDialog.Reply(LearnedTerms.capital(word)+" ne işi?",OperationVoiceDialog.Action.ASK),"Zeytin · yeni söz");
        answer.setTextSize(24);
        List<String> labels=new java.util.ArrayList<>();
        for(LearnedTerms.Choice c:LearnedTerms.ordered(counts))labels.add(c.label);
        labels.add("Başka");
        labels.add("Vazgeç");
        // Düğmeler çoğaldı: ekrana sığmazsa kendi içinde kayar (soru üstte kalır, "Başka" ve "Vazgeç" en altta).
        LinearLayout buttons=column();
        int rows=(labels.size()+1)/2;
        int height=rows*(dp(64)+dp(10));
        int most=activity.getResources().getDisplayMetrics().heightPixels*45/100;
        ScrollView scroll=new ScrollView(activity);
        scroll.addView(buttons);
        hintBox.addView(scroll,new LinearLayout.LayoutParams(-1,Math.min(height,most)));
        for(int i=0;i<labels.size();i+=2){
            LinearLayout line=row();
            LinearLayout.LayoutParams left=new LinearLayout.LayoutParams(0,dp(64),1);
            left.rightMargin=dp(10);
            line.addView(teachButton(labels.get(i)),left);
            if(i+1<labels.size())line.addView(teachButton(labels.get(i+1)),new LinearLayout.LayoutParams(0,dp(64),1));
            else line.addView(new View(activity),new LinearLayout.LayoutParams(0,dp(64),1));
            buttons.addView(line,margins(-1,-2,dp(10),0));
        }
    }
    private View teachButton(String label){
        boolean quiet="Vazgeç".equals(label)||"Başka".equals(label);
        TextView b=label(label,20,quiet?soft:text,true);
        b.setGravity(Gravity.CENTER);
        b.setBackground(round(quiet?surface:greenSoft,quiet?border:green,16));
        b.setContentDescription(label);
        b.setOnClickListener(v->{stopTts();answerTeach(label);});
        return b;
    }
    /** Öğretme sorusunun cevabı (düğme ya da ses). Cevap değilse false: cümle normal işlenir. */
    private boolean answerTeach(String answerText){
        String[] p=pendingTeach;
        pendingTeach=null;
        if(p==null)return false;
        String a=TurkishText.normalize(answerText);
        LearnedTerms.Choice c=LearnedTerms.choiceFor(answerText);
        if(c!=null){
            LearnedTerms.Term taught=LearnedTerms.teach(p[1],c,p[2],System.currentTimeMillis());
            learned().store().put(taught);
            // İP8: izin açıksa temizlenmiş katkı kuyruğa (izin varsayılan kapalı; adres yokken hiçbir şey gönderilmez).
            CommunitySync.taught(activity,learnedOwner,taught);
            lastRaw=p[0];
            meterAgain=true;   // aynı cümle: karnede ikinci kez sayılmaz
            if(ActionCatalog.ISLEM.equals(c.action))teachOpenForm=true;
            run(p[0],ZeytinAssistantCommand.parse(p[0]),null);
            return true;
        }
        if(a.matches("(vazgec\\p{L}*|bosver|iptal|hicbiri|gerek yok|sorma)( .*)?")){
            learned().store().decline(LearnedTerms.sentenceKey(p[0]));
            postDialog(new OperationVoiceDialog.Reply("Tamam, bunu bir daha sormayacağım.",OperationVoiceDialog.Action.CANCEL),"Zeytin · yeni söz");
            return true;
        }
        if(a.matches("baska( .*)?|hicbiri degil")){
            postDialog(new OperationVoiceDialog.Reply("Tamam, öğrenmedim. Bu işi elle girmek için bahçenin işlem formunu kullanabilirsin.",OperationVoiceDialog.Action.CANCEL),"Zeytin · yeni söz");
            return true;
        }
        return false;
    }
    /** Öğrenilen sözle başlayan konuşmada "Unut" düğmesi. */
    private void addForgetButton(LearnedTerms.Term t){
        TextView b=label("Unut: “"+t.word+"”",16,soft,true);
        b.setGravity(Gravity.CENTER);
        b.setBackground(round(surface,border,16));
        b.setContentDescription(t.word+" sözünü unut");
        b.setOnClickListener(v->{stopTts();opDialog=null;opCloudRaw=null;forget(t.word);});
        hintBox.addView(b,margins(-1,dp(52),dp(10),0));
    }
    /** Sözü unutur ("bakırladığı unut" ya da "Unut" düğmesi). */
    private void forget(String word){
        String root=LearnedTerms.verbRoot(word==null?"":word);
        LearnedTerms.Term t=root.isEmpty()?null:learned().store().get(root);
        if(t!=null&&KnowledgePack.KIND.equals(t.kind)){
            // Paket sözü: çiftçi için kapatılır (Öğrendiklerim'de "Herkesten öğrendiklerim"den yeniden açılabilir).
            PackDb.setDisabled(activity,learnedOwner,t.term,true);
            postDialog(new OperationVoiceDialog.Reply("Tamam, “"+t.word+"” sözünü artık kullanmayacağım.",OperationVoiceDialog.Action.CANCEL),"Zeytin · öğrendiklerim");
            return;
        }
        if(t==null){
            postDialog(new OperationVoiceDialog.Reply("Öyle bir söz öğrenmemiştim.",OperationVoiceDialog.Action.ASK),"Zeytin · öğrendiklerim");
            return;
        }
        learned().store().remove(t.term);
        CommunitySync.forgot(activity,learnedOwner,t.term);   // İP8: kuyruktan düşer ya da geri çekilir
        postDialog(new OperationVoiceDialog.Reply("Tamam, “"+t.word+"” sözünü unuttum.",OperationVoiceDialog.Action.CANCEL),"Zeytin · öğrendiklerim");
    }
    /** İnternet yardımcısının çözdüğü ve çiftçinin "evet"le kaydettiği iş: söz yerel sözlüğe (bulut-onaylı). */
    private void learnFromCloud(String raw,OperationVoiceEntry entry){
        String word=LearnedTerms.candidate(raw);
        if(word==null)return;
        String type=OperationKinds.TYPES[entry.type];
        for(LearnedTerms.Choice c:LearnedTerms.CHOICES){
            if(!c.value.equals(type)||!c.note.equals(entry.note))continue;
            LearnedTerms.Store store=learned().store();
            if(store.get(LearnedTerms.verbRoot(word))==null){
                LearnedTerms.Term cloudTaught=LearnedTerms.teach(word,c,LearnedTerms.SOURCE_CLOUD,System.currentTimeMillis());
                store.put(cloudTaught);
                CommunitySync.taught(activity,learnedOwner,cloudTaught);
            }
            return;
        }
    }

    /** Sohbet cevabı: ekrana yazılır, okunur; soruysa okuma bitince yeniden dinler. */
    /**
     * Tahsilat (eğitim turu 1; çalışan iş parçacığında çağrılır). Rehberde bu ada uyan alıcı aranır: yoksa söylenir,
     * birden çoksa hangisi olduğu sorulur, tekse "Ödeme aldım" penceresi açılır. Hiçbir şey kendiliğinden yazılmaz.
     */
    private void collect(CollectionEntry entry,int token,String owner,int season,long active,ZeytinAssistantCommand cmd){
        final List<BuyerDirectory.Summary> found=new java.util.ArrayList<>();
        DatabaseHelper db=new DatabaseHelper(activity.getApplicationContext());
        try{
            List<BuyerDirectory.Summary> all=BuyerDirectory.list(db.getReadableDatabase(),owner,season);
            List<String> names=new java.util.ArrayList<>();
            for(BuyerDirectory.Summary b:all)names.add(b.name);
            for(int i:CollectionEntry.matches(names,entry.buyer))found.add(all.get(i));
        }catch(RuntimeException e){
            found.clear();
        }finally{
            db.close();
        }
        if(found.isEmpty()){
            String text="Alıcılarında “"+entry.buyer+"” adlı birini bulamadım. Hiçbir şey kaydetmedim.";
            post(token,owner,season,active,cmd,true,"","Alıcılar · tahsilat",text);
            return;
        }
        main.post(()->{
            if(!current(token,owner,season,active))return;
            if(found.size()==1){
                openCollection(entry,found.get(0),token,owner,season,active,cmd);
                return;
            }
            String[] labels=new String[found.size()];
            for(int i=0;i<labels.length;i++)labels[i]=found.get(i).name;
            new android.app.AlertDialog.Builder(activity)
                    .setTitle("Hangi "+entry.buyer+"?")
                    .setItems(labels,(d,which)->openCollection(entry,found.get(which),token,owner,season,active,cmd))
                    .setNegativeButton("Vazgeç",null)
                    .show();
        });
    }
    /**
     * Gelecek zamanlı iş (eğitim turu 1): "Yarın 09:00'da Sulama · Girelli hatırlatayım mı?" diye sorulur; gün
     * söylenmediyse önce gün sorulur. Sormadan kurulmaz; geçmiş ya da gece saatine kurulmaz; kurulunca Geri al çıkar.
     */
    private void askReminder(TaskReminderEntry task,long gardenId,String garden,int token,String owner,int season,long active,ZeytinAssistantCommand cmd){
        long at=task.when(Calendar.getInstance());
        if(at<0){
            String[] days={"Yarın sabah","3 gün sonra","1 hafta sonra"};
            int[] offsets={1,3,7};
            new android.app.AlertDialog.Builder(activity)
                    .setTitle(task.title(garden)+" · ne zaman hatırlatayım?")
                    .setItems(days,(d,which)->confirmReminder(task,gardenId,garden,TaskReminderEntry.at(Calendar.getInstance(),offsets[which],task.hour),token,owner,season,active,cmd))
                    .setNegativeButton("Vazgeç",null)
                    .show();
            return;
        }
        confirmReminder(task,gardenId,garden,at,token,owner,season,active,cmd);
    }
    private void confirmReminder(TaskReminderEntry task,long gardenId,String garden,long at,int token,String owner,int season,long active,ZeytinAssistantCommand cmd){
        if(!TaskReminderEntry.allowed(at,System.currentTimeMillis())){
            post(token,owner,season,active,cmd,true,"","Hatırlatma","O saat geçmiş ya da gece; hatırlatma kurmadım.");
            return;
        }
        String when=TaskReminderEntry.spoken(at,Calendar.getInstance());
        String title=task.title(garden);
        new android.app.AlertDialog.Builder(activity)
                .setTitle("Hatırlatayım mı?")
                .setMessage(when+" · "+title)
                .setPositiveButton("Evet, hatırlat",(d,x)->{
                    long id=TaskReminders.add(activity,owner,gardenId,title,"Asistanla kuruldu.",at);
                    if(id<=0){
                        post(token,owner,season,active,cmd,true,"","Hatırlatma","Hatırlatma kurulamadı.");
                        return;
                    }
                    post(token,owner,season,active,cmd,true,"","Hatırlatma","Kuruldu: "+when+" · "+title+".");
                    new android.app.AlertDialog.Builder(activity)
                            .setMessage("Hatırlatma kuruldu: "+when+" · "+title)
                            .setPositiveButton("Tamam",null)
                            .setNegativeButton("Geri al",(d2,y)->{
                                boolean ok=TaskReminders.remove(activity,id);
                                Toast.makeText(activity,ok?"Geri alındı: hatırlatma silindi.":"Geri alınamadı; Hatırlatmalar'dan sil.",Toast.LENGTH_LONG).show();
                            })
                            .show();
                })
                .setNegativeButton("Hayır",null)
                .show();
    }
    /** Seçilen alıcının "Ödeme aldım" penceresi; açık borç yoksa açılmaz ve hiçbir şey kaydedilmez. */
    private void openCollection(CollectionEntry entry,BuyerDirectory.Summary buyer,int token,String owner,int season,long active,ZeytinAssistantCommand cmd){
        if(marketDb==null)marketDb=new DatabaseHelper(activity.getApplicationContext());
        DatabaseHelper db=marketDb;
        double open=0;
        try{
            open=BuyerPayPlan.total(BuyerDirectory.openSales(db.getReadableDatabase(),owner,buyer.id));
        }catch(RuntimeException ignored){
            open=0;
        }
        double amount=entry.amountFor(open);
        if(amount<=0){
            String text=buyer.name+" için açık borç görünmüyor. Hiçbir şey kaydetmedim.";
            post(token,owner,season,active,cmd,true,"","Alıcılar · tahsilat",text);
            return;
        }
        transition(State.ANSWERING,"Ödeme penceresini açıyorum…");
        dialog.dismiss();
        BuyerPaymentSheet.show(activity,db,buyer.id,buyer.name,amount,null);
    }
    private void postDialog(OperationVoiceDialog.Reply r,String label){
        if(!visible())return;
        conversation.nextTurn();
        clearAnswerExtras();
        lastAnswer=r.speech;answer.setTextSize(17);answer.setText(r.speech);source.setText(label);
        lastCmd=null;lastScope=null;followUps.removeAllViews();
        listenAfterSpeech=r.action==OperationVoiceDialog.Action.ASK;
        transition(State.ANSWERING,ttsReady?"Soruyorum…":"Cevap hazır");haptic();
        if(ttsReady&&tts!=null)speak();
        else if(tts==null&&listenAfterSpeech){listenAfterSpeech=false;main.postDelayed(()->{if(visible())listen();},1500);}
        // tts henüz hazırlanıyorsa: hazır olunca okunur (durum ANSWERING), okuma bitince dinler.
    }

    private void speak(){
        if(!visible()||lastAnswer.isEmpty()||conversation.state()!=State.ANSWERING)return;
        if(!ttsReady||tts==null){transition(State.ANSWERING,"Cevap hazır");return;}
        if(!conversation.claimSpeech(conversation.turn()))return;
        say();
    }
    /** Ekranda görünen cevabın aynısı okunur; ikinci bir cevap ya da sayı yeniden yazımı yok. */
    private void say(){
        stopRecognizer();stopTts();
        speakingId="zeytin-"+session+"-"+conversation.turn()+"-"+SystemClock.uptimeMillis();
        try{
            if(tts.speak(lastAnswer,TextToSpeech.QUEUE_FLUSH,null,speakingId)==TextToSpeech.ERROR)
                transition(State.ANSWERING,"Sesli okuma kullanılamadı; yazılı cevap hazır");
        }catch(Exception e){transition(State.ANSWERING,"Sesli okuma kullanılamadı; yazılı cevap hazır");}
    }
    private void toggleSpeech(){
        if(!visible()||!ttsReady||tts==null||lastAnswer.isEmpty())return;
        if(speaking){stopTts();transition(State.ANSWERING,"Cevap hazır");}
        else say();
    }
    private void stopTts(){speakingId="";speaking=false;if(tts!=null)try{tts.stop();}catch(Exception ignored){}}
    private void stopRecognizer(){
        listenVersion++;SpeechRecognizer previous=recognizer;recognizer=null;
        if(previous!=null){try{previous.cancel();}catch(Exception ignored){}try{previous.destroy();}catch(Exception ignored){}}
    }
    private void releasePanel(){
        opDialog=null;recDialog=null;listenAfterSpeech=false;
        session++;conversation.nextTurn();stopRecognizer();stopTts();hideKeyboard();
        if(tagSheet!=null){try{tagSheet.dismiss();}catch(Exception ignored){}tagSheet=null;}
        transition(State.IDLE,"Asistan kapatıldı");if(wave!=null)wave.listening(false);ttsReady=false;typing=false;
        host.opened(false);
        if(tts!=null){try{tts.shutdown();}catch(Exception ignored){}tts=null;}
    }
    public void pause(){
        conversation.nextTurn();stopRecognizer();stopTts();
        if(visible() && permissionStage!=PermissionStage.REQUESTING)transition(State.IDLE,"Devam etmek için Tekrar sor’a dokun");
        if(wave!=null)wave.listening(false);
    }
    public void stop(){if(dialog!=null&&dialog.isShowing())dialog.dismiss();else releasePanel();}
    public void destroy(){
        stop();closed=true;worker.shutdownNow();main.removeCallbacksAndMessages(null);
        market=null;if(marketDb!=null){try{marketDb.close();}catch(Exception ignored){}marketDb=null;}
    }

    // ============================================================ GÖRÜNÜM PARÇALARI

    private LinearLayout chipRow(String[] questions){
        LinearLayout wrap=column();LinearLayout line=null;int count=0;
        for(String q:questions){
            if(line==null||count==2){line=row();wrap.addView(line,margins(-1,-2,0,dp(8)));count=0;}
            line.addView(chip(q));count++;
        }
        return wrap;
    }
    private TextView chip(String q){
        TextView c=label(q,12,soft,true);c.setGravity(Gravity.CENTER);c.setMinHeight(dp(36));c.setPadding(dp(12),0,dp(12),0);
        c.setBackground(round(surface,border,18));c.setOnClickListener(v->submit(q));c.setContentDescription("Sor: "+q);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,dp(36));lp.rightMargin=dp(8);c.setLayoutParams(lp);return c;
    }
    private TextView button(String label,int iconRes,boolean filled){TextView b=label(label,14,text,true);b.setGravity(Gravity.CENTER);setButton(b,label,iconRes,filled);return b;}
    private void setButton(TextView b,String label,int iconRes,boolean filled){
        int fg=filled?bg:text;b.setText(label);b.setTextColor(fg);b.setBackground(round(filled?green:0x33FFFFFF,filled?green:0x00000000,24));
        android.graphics.drawable.Drawable d=null;
        if(iconRes!=0){d=activity.getDrawable(iconRes);if(d!=null){d=d.mutate();d.setTint(fg);d.setBounds(0,0,dp(18),dp(18));}}
        b.setCompoundDrawablesRelative(d,null,null,null);b.setCompoundDrawablePadding(dp(8));
    }
    private ImageView icon(int res,int color){ImageView v=new ImageView(activity);v.setImageResource(res);v.setColorFilter(color);return v;}
    private GradientDrawable round(int fill,int stroke,int radius){GradientDrawable g=new GradientDrawable();g.setColor(fill);g.setCornerRadius(dp(radius));g.setStroke(dp(1),stroke);return g;}
    private LinearLayout column(){LinearLayout l=new LinearLayout(activity);l.setOrientation(LinearLayout.VERTICAL);return l;}
    private LinearLayout row(){LinearLayout l=new LinearLayout(activity);l.setOrientation(LinearLayout.HORIZONTAL);l.setGravity(Gravity.CENTER_VERTICAL);return l;}
    private LinearLayout.LayoutParams margins(int w,int h,int top,int bottom){LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(w,h);lp.topMargin=top;lp.bottomMargin=bottom;return lp;}
    private int dp(int n){return Math.round(n*activity.getResources().getDisplayMetrics().density);}
    private TextView label(String s,int sp,int color,boolean bold){
        TextView t=new TextView(activity);t.setText(s);t.setTextSize(sp);t.setTextColor(color);if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return t;
    }
}
