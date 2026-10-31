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
        void navigate(String target,long gardenId);
        /** Sesle söylenen elek kilolarıyla elek formunu dolu aç (kaydetmez; kullanıcı onaylar). */
        default void openElekEntry(long gardenId,ElekVoiceEntry entry){navigate("ELEK",gardenId);}
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
        View root=buildPanel();
        dialog=new Dialog(activity);dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);dialog.setContentView(root);
        dialog.setCanceledOnTouchOutside(false);
        dialog.setOnDismissListener(d->releasePanel());dialog.show();root.requestFocus();
        Window window=dialog.getWindow();if(window!=null){
            window.setBackgroundDrawableResource(android.R.color.transparent);window.setGravity(Gravity.BOTTOM);window.setLayout(getPanelWidth(),-2);
            WindowManager.LayoutParams wp=window.getAttributes();wp.y=dp(88);wp.dimAmount=.25f;window.setAttributes(wp);
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
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
            if(conversation.state()==State.ANSWERING){refreshTtsNotice();speak();}
        }));
            tts.setOnUtteranceProgressListener(new UtteranceProgressListener(){
                private void update(String id,boolean on,String message){main.post(()->{
                    if(panel==session && visible() && id.equals(speakingId) && conversation.state()==State.ANSWERING){
                        speaking=on;transition(State.ANSWERING,message);
                    }
                });}
                public void onStart(String id){update(id,true,"Cevabı okuyorum…");}
                public void onDone(String id){update(id,false,"Cevap hazır");}
                public void onError(String id){update(id,false,"Sesli okuma kullanılamadı; yazılı cevap hazır");}
                public void onStop(String id,boolean interrupted){update(id,false,"Cevap hazır");}
            });
        }catch(Exception e){ttsReady=false;}
        main.post(()->{if(panel==session && visible()){
            String q=pendingQuestion;pendingQuestion=null;
            if(q!=null)submit(q);else listen();
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
        LinearLayout root=column();root.setPadding(dp(18),dp(10),dp(18),dp(18));root.setFocusableInTouchMode(true);
        root.setBackground(round(card,border,26));
        View handle=new View(activity);handle.setBackground(round(border,border,2));
        LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(dp(40),dp(4));hp.gravity=Gravity.CENTER_HORIZONTAL;hp.bottomMargin=dp(12);root.addView(handle,hp);

        LinearLayout head=row();
        dot=new View(activity);LinearLayout.LayoutParams dl=new LinearLayout.LayoutParams(dp(10),dp(10));dl.rightMargin=dp(10);head.addView(dot,dl);
        LinearLayout titles=column();
        title=label("Zeytin Asistanı",15,text,true);titles.addView(title);
        state=label("",12,muted,false);state.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);titles.addView(state);
        head.addView(titles,new LinearLayout.LayoutParams(0,-2,1));
        ImageView close=icon(R.drawable.ic_op_close,soft);close.setPadding(dp(9),dp(9),dp(9),dp(9));close.setBackground(round(surface,border,18));
        close.setContentDescription("Asistanı kapat");close.setOnClickListener(v->{if(dialog!=null)dialog.dismiss();});
        head.addView(close,new LinearLayout.LayoutParams(dp(36),dp(36)));
        root.addView(head);

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

        answerCard=column();answerCard.setPadding(dp(14),dp(14),dp(14),dp(14));answerCard.setBackground(round(surface,border,18));
        source=label("",11,muted,true);source.setLetterSpacing(0.06f);answerCard.addView(source);
        answer=label("",17,text,true);answer.setLineSpacing(dp(3),1f);answer.setTextIsSelectable(true);answer.setPadding(0,dp(6),0,0);answerCard.addView(answer);
        hintBox=column();answerCard.addView(hintBox);
        ttsRow=row();ttsRow.setPadding(0,dp(10),0,0);
        ImageView mute=icon(R.drawable.ic_op_speaker,muted);ttsRow.addView(mute,new LinearLayout.LayoutParams(dp(16),dp(16)));
        ttsNotice=label("Türkçe ses paketi yok; cevap yalnızca yazılı.",12,muted,false);ttsNotice.setPadding(dp(8),0,dp(8),0);ttsRow.addView(ttsNotice,new LinearLayout.LayoutParams(0,-2,1));
        TextView fix=label("Ses ayarları",12,green,true);fix.setOnClickListener(v->openTtsSettings());fix.setPadding(dp(6),dp(6),0,dp(6));ttsRow.addView(fix);
        answerCard.addView(ttsRow);
        root.addView(answerCard,margins(-1,-2,dp(12),0));

        problemCard=column();problemCard.setPadding(dp(14),dp(14),dp(14),dp(14));problemCard.setBackground(round(surface,border,18));
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

        LinearLayout foot=row();
        speaker=icon(R.drawable.ic_op_speaker,text);speaker.setPadding(dp(14),dp(14),dp(14),dp(14));speaker.setBackground(round(surface,border,16));
        speaker.setOnClickListener(v->toggleSpeech());
        LinearLayout.LayoutParams spk=new LinearLayout.LayoutParams(dp(52),dp(48));spk.rightMargin=dp(10);foot.addView(speaker,spk);
        primary=button("Tekrar sor",R.drawable.ic_op_mic,true);primary.setOnClickListener(v->primaryAction());
        LinearLayout.LayoutParams prl=new LinearLayout.LayoutParams(0,dp(48),1);prl.rightMargin=dp(10);foot.addView(primary,prl);
        secondary=button("Yaz",R.drawable.ic_op_keyboard,false);secondary.setOnClickListener(v->secondaryAction());
        foot.addView(secondary,new LinearLayout.LayoutParams(dp(104),dp(48)));
        root.addView(foot,margins(-1,-2,dp(16),0));
        return root;
    }
    private int getPanelWidth(){return Math.min(activity.getResources().getDisplayMetrics().widthPixels-dp(20),dp(420));}

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
        if(state==null)return;
        if(!message.contentEquals(state.getText()))state.setText(message);
        boolean listening=next==State.LISTENING, processing=next==State.PROCESSING, answering=next==State.ANSWERING;
        title.setText(typing?"Yazarak sor":listening?"Dinliyorum":processing?"Kontrol ediyorum":"Zeytin Asistanı");
        int tone=typing?warning:next==State.ERROR?danger:answering && hinting?warning:green;
        dot.setBackground(round(tone,tone,5));
        wave.setVisibility(listening && !typing?View.VISIBLE:View.GONE);wave.listening(listening && !typing);
        heard.setVisibility(listening && !typing && heard.length()>0?View.VISIBLE:View.GONE);
        question.setVisibility(!listening && !typing && lastQuestion.length()>0 && next!=State.ERROR?View.VISIBLE:View.GONE);
        progress.setVisibility(processing?View.VISIBLE:View.GONE);
        answerCard.setVisibility(answering && !typing && lastAnswer.length()>0?View.VISIBLE:View.GONE);
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
                showProblem(fault==Fault.TIMEOUT?"Seni duyamadım":"Seni tam anlayamadım",
                        "Şöyle sorabilirsin: “Son sulama ne zaman?”, “Bu sezon masrafım kaç?”, “Elek aç”.",
                        "Tekrar sor",this::listen,true,"Örnekler",()->setTyping(true));return;
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
                public void onRmsChanged(float rms){if(valid() && conversation.state()==State.LISTENING){host.rms(rms);wave.rms(rms);}}
                public void onBufferReceived(byte[] bytes){}
                public void onEndOfSpeech(){if(valid())transition(State.PROCESSING,"Söylediklerini kontrol ediyorum…");}
                public void onError(int error){if(valid()){stopRecognizer();log("speech error="+error);problem(speechFault(error));}}
                public void onResults(Bundle results){
                    if(!valid())return;log("final result received");
                    ArrayList<String> words=results==null?null:results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    if(words==null||words.isEmpty()||words.get(0)==null||words.get(0).trim().isEmpty()){stopRecognizer();problem(Fault.NO_MATCH);}
                    else submit(words.get(0));
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
    private void submit(String raw){
        if(!visible()||raw.trim().isEmpty())return;
        typing=false;hideKeyboard();input.setText("");
        lastQuestion=raw.trim();lastRaw=raw;question.setText(lastQuestion);heard.setText("");
        run(raw,ZeytinAssistantCommand.parse(raw),null);
    }
    /** "Anladığım" etiketinden zaman ya da bahçe değişti: soru balonu aynı kalır, cevap yeniden hesaplanır. */
    private void rerun(ZeytinAssistantCommand cmd,Pick pick){
        if(!visible()||cmd==null||lastRaw.isEmpty())return;
        run(lastRaw,cmd,pick);
    }
    private void run(String raw,final ZeytinAssistantCommand cmd,final Pick pick){
        stopRecognizer();stopTts();
        answer.setText("");lastAnswer="";followUps.removeAllViews();clearAnswerExtras();
        transition(State.PROCESSING,"Kayıtlarını kontrol ediyorum…");
        final int token=conversation.nextTurn();final String owner=host.owner();final int season=host.season();final long active=host.activeGarden();
        log("intent="+cmd.kind);
        worker.execute(()->{
            try(ZeytinAssistantData data=new ZeytinAssistantData(activity.getApplicationContext())){
                if(!data.owner.equals(owner)||data.season!=season)return;
                List<Garden> gardens=data.gardens();
                Resolution resolution=pick!=null?pick.resolve(gardens):ZeytinAssistantCommand.resolve(raw,gardens,active,cmd.kind==Kind.MARKET);
                String gardenName=resolution.garden==null?"":resolution.garden.name;
                // "Bugün 3 numara 120 kilo (sattım)": cevap yerine elek formu dolu açılır; kayıt kullanıcının onayıyla.
                final ElekVoiceEntry voice=pick==null?ElekVoiceEntry.parse(raw):null;
                if(voice!=null){
                    final long gid=resolution.garden==null?0:resolution.garden.id;
                    main.post(()->{if(current(token,owner,season,active)){transition(State.ANSWERING,"Elek formunu dolduruyorum…");haptic();
                        Toast.makeText(activity,"Elek formu açılıyor: "+voice.summary(),Toast.LENGTH_SHORT).show();dialog.dismiss();host.openElekEntry(gid,voice);}});
                    return;
                }
                if(cmd.kind==Kind.UNKNOWN){
                    // Selam, teşekkür, kayıt silme, ilaç dozu: sabit cevap önce (dozda ilaçlama sorusu önerilmez).
                    String special=ZeytinAssistantText.special(raw);
                    if(special!=null){post(token,owner,season,active,cmd,false,"",special);return;}
                    // Kalıba uymadı: sorudaki konu kelimelerinden yakın sorular ya da konu kartları.
                    Set<ZeytinAssistantHints.Topic> recorded=data.recordedTopics(gardens);
                    String named=resolution.explicit && resolution.garden!=null?resolution.garden.name:"";
                    ZeytinAssistantHints.Hint hint=ZeytinAssistantHints.hint(raw,named,recorded);
                    if(data.unchanged())postHint(token,owner,season,active,hint,recorded);
                    return;
                }
                if(cmd.kind==Kind.NAVIGATION){
                    if(!resolution.error.isEmpty() && (!isGlobal(cmd.target) || !resolution.error.startsWith("Önce"))){post(token,owner,season,active,cmd,false,gardenName,resolution.error);return;}
                    main.post(()->{if(current(token,owner,season,active)){long id=resolution.garden==null?0:resolution.garden.id;transition(State.ANSWERING,"Ekranı açıyorum…");haptic();Toast.makeText(activity,"Ekranı açıyorum…",Toast.LENGTH_SHORT).show();dialog.dismiss();host.navigate(cmd.target,id);}});return;
                }
                if(cmd.kind==Kind.MARKET && !ZeytinAssistantMarket.ownSales(cmd.text)){
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
        final String shown=ZeytinAssistantText.humanDates(raw,System.currentTimeMillis());
        main.post(()->{
            if(!current(token,owner,season,active))return;
            clearAnswerExtras();
            lastAnswer=shown;answer.setText(shown);source.setText(sourceLabel!=null?sourceLabel:ZeytinAssistantText.source(cmd.kind,all,gardenName));
            lastCmd=scope==null?null:cmd;lastScope=scope;
            if(scope!=null)renderTags(ZeytinAssistantHints.understood(cmd,gardenName,all,scope.gardens.size()),cmd);
            followUps.removeAllViews();
            for(String q:ZeytinAssistantText.followUps(cmd.kind,lastQuestion))followUps.addView(chip(q));
            transition(State.ANSWERING,ttsReady?"Cevabı okuyorum…":"Cevap hazır");haptic();speak();
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
        session++;conversation.nextTurn();stopRecognizer();stopTts();hideKeyboard();
        if(tagSheet!=null){try{tagSheet.dismiss();}catch(Exception ignored){}tagSheet=null;}
        transition(State.IDLE,"Asistan kapatıldı");if(wave!=null)wave.listening(false);ttsReady=false;typing=false;
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
        int fg=filled?bg:text;b.setText(label);b.setTextColor(fg);b.setBackground(round(filled?green:surface,filled?green:border,16));
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
