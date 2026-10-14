package com.zeytindefteri.app;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.*;
import android.speech.*;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.net.Uri;
import android.provider.Settings;
import android.util.Log;
import android.view.inputmethod.EditorInfo;
import com.zeytindefteri.app.ZeytinAssistantConversation.State;
import com.zeytindefteri.app.ZeytinAssistantConversation.Fault;
import android.view.*;
import android.widget.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;
import com.zeytindefteri.app.ZeytinAssistantCommand.*;

/** One-shot, user-triggered speech + typed fallback. No continuous/background listening. */
public final class ZeytinAssistantController {
    public static final int MICROPHONE_REQUEST=8701;
    public interface Host {
        String owner(); int season(); long activeGarden();
        default void visual(State state) {}
        default void rms(float level) {}
        void navigate(String target,long gardenId);
        void weather(Garden garden,WeatherRepository.ForecastCallback callback);
    }
    private final Activity activity;
    private final Host host;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private AlertDialog dialog;
    private TextView state,heard,answer,hint;
    private Button mic;
    private ProgressBar progress;
    private AssistantVoiceWaveView wave;
    private final ZeytinAssistantConversation conversation=new ZeytinAssistantConversation();
    private enum PermissionStage { EXPLAIN, READY, REQUESTING, BLOCKED }
    private PermissionStage permissionStage=PermissionStage.EXPLAIN;
    private String speakingId="";
    private LinearLayout typed;
    private Button write;
    private EditText input;
    private SpeechRecognizer recognizer;
    private TextToSpeech tts;
    private boolean ttsReady,closed;
    private int session,listenVersion;
    private String lastAnswer="";
    private String pendingQuestion;
    public ZeytinAssistantController(Activity activity,Host host){this.activity=activity;this.host=host;}
    public void show() {
        if(closed || (dialog!=null && dialog.isShowing()))return;
        final int panel=++session;
        conversation.nextTurn();lastAnswer="";ttsReady=false;
        LinearLayout root=new LinearLayout(activity);root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18),dp(14),dp(18),dp(12));root.setFocusableInTouchMode(true);
        GradientDrawable bg=new GradientDrawable();bg.setColor(GreenTheme.color(R.color.zd_card));bg.setCornerRadius(dp(22));
        root.setBackground(bg);
        
        wave=new AssistantVoiceWaveView(activity);wave.setVisibility(View.GONE);root.addView(wave,new LinearLayout.LayoutParams(-1,0));
        state=label("Dinliyorum…",18);state.setGravity(Gravity.CENTER);root.addView(state);
        progress=new ProgressBar(activity);progress.setIndeterminate(true);
        LinearLayout.LayoutParams pp=new LinearLayout.LayoutParams(dp(22),dp(22));pp.gravity=Gravity.CENTER;
        root.addView(progress,pp);
        hint=label("",12);hint.setTextColor(GreenTheme.color(R.color.zd_text_soft));root.addView(hint);
        heard=label("",13);heard.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_NONE);root.addView(heard);
        answer=label("",16);answer.setTextIsSelectable(true);root.addView(answer);
        typed=new LinearLayout(activity);
        input=new EditText(activity);input.setTextColor(Color.WHITE);input.setHintTextColor(Color.LTGRAY);
        input.setHint("Sorunu yaz…");input.setContentDescription("Zeytin Asistanına yazılı soru");input.setSingleLine(true);
        input.setImeOptions(EditorInfo.IME_ACTION_SEND);
        input.setOnEditorActionListener((v,id,event)->{if(id==EditorInfo.IME_ACTION_SEND){submit(input.getText().toString());return true;}return false;});
        typed.addView(input,new LinearLayout.LayoutParams(0,dp(48),1));
        Button send=panelButton("Sor");send.setOnClickListener(v->submit(input.getText().toString()));typed.addView(send,new LinearLayout.LayoutParams(dp(64),dp(48)));root.addView(typed);
        write=panelButton("Yazarak sor");write.setOnClickListener(v->{typed.setVisibility(View.VISIBLE);input.requestFocus();});root.addView(write);
        LinearLayout buttons=new LinearLayout(activity);
        mic=panelButton("Tekrar Sor");mic.setOnClickListener(v->{
            if(conversation.state()==State.LISTENING){conversation.nextTurn();stopRecognizer();stopTts();transition(State.IDLE,"Dinleme durduruldu.");}
            else listen();
        });
        buttons.addView(mic,new LinearLayout.LayoutParams(0,dp(48),1));
        Button close=panelButton("Kapat");close.setOnClickListener(v->dialog.dismiss());buttons.addView(close,new LinearLayout.LayoutParams(dp(90),dp(48)));root.addView(buttons);
        ScrollView viewport=new ScrollView(activity){
            @Override protected void onMeasure(int widthSpec,int heightSpec){
                int cap=(int)(getResources().getDisplayMetrics().heightPixels*0.75f);
                if(View.MeasureSpec.getMode(heightSpec)!=View.MeasureSpec.UNSPECIFIED)cap=Math.min(cap,View.MeasureSpec.getSize(heightSpec));
                super.onMeasure(widthSpec,View.MeasureSpec.makeMeasureSpec(cap,View.MeasureSpec.AT_MOST));
            }
        };
        viewport.setFillViewport(false);viewport.addView(root);
        dialog=new AlertDialog.Builder(activity).setView(viewport).create();
        dialog.setCanceledOnTouchOutside(false);
        dialog.setOnDismissListener(d->releasePanel());dialog.show();root.requestFocus();
        Window window=dialog.getWindow();if(window!=null){
            window.setBackgroundDrawableResource(android.R.color.transparent);window.setGravity(Gravity.BOTTOM);window.setLayout(getPanelWidth(),-2);
            WindowManager.LayoutParams wp=window.getAttributes();wp.y=dp(88);wp.dimAmount=.08f;window.setAttributes(wp);
            window.addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL);
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE|WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        }
        transition(State.LISTENING,"Dinliyorum…");hint.setText("Mikrofon hazırlanıyor…");
        try {tts=new TextToSpeech(activity.getApplicationContext(),status->main.post(()->{
            if(panel!=session || !visible() || tts==null)return;
            try {ttsReady=status==TextToSpeech.SUCCESS && tts.setLanguage(new Locale("tr","TR"))>=0;}
            catch(Exception e){ttsReady=false;}
            if(conversation.state()==State.ANSWERING)speak();
        }));
            tts.setOnUtteranceProgressListener(new UtteranceProgressListener(){
                private void update(String id,String message){main.post(()->{
                    if(panel==session && visible() && id.equals(speakingId) && conversation.state()==State.ANSWERING)
                        transition(State.ANSWERING,message);
                });}
                public void onStart(String id){update(id,"Cevabı okuyorum…");}
                public void onDone(String id){update(id,"Cevap hazır.");}
                public void onError(String id){update(id,"Sesli okuma kullanılamadı; yazılı cevap hazır.");}
                public void onStop(String id,boolean interrupted){update(id,"Cevap hazır.");}
            });
        }catch(Exception e){ttsReady=false;}
        main.post(()->{if(panel==session && visible()){
            String question=pendingQuestion;pendingQuestion=null;
            if(question!=null)submit(question);else listen();
        }});
    }
    /** Ana sayfadaki hazır sorular için: paneli açar ve soruyu mikrofon açmadan yazılı gönderir. */
    public void ask(String question){
        if(question==null||question.trim().isEmpty())return;
        if(visible()){submit(question);return;}
        pendingQuestion=question;
        show();
        if(!visible())pendingQuestion=null;
    }
    public boolean isOpen(){return visible();}
    public void orbTap(){if(!visible())return;if(conversation.state()==State.LISTENING && recognizer!=null){try{recognizer.stopListening();transition(State.PROCESSING,"Kontrol ediyorum…");}catch(Exception e){stopRecognizer();transition(State.ERROR,"Seni duyamadım. Tekrar deneyebilirsin.");}}else if(conversation.state()!=State.PROCESSING)listen();}
    private int getPanelWidth(){return Math.min(activity.getResources().getDisplayMetrics().widthPixels-dp(24),dp(380));}
    private Button panelButton(String text){
        Button button=new Button(activity);button.setText(text);button.setTextSize(13);button.setAllCaps(false);
        button.setTextColor(GreenTheme.color(R.color.zd_text));button.setBackgroundTintList(android.content.res.ColorStateList.valueOf(GreenTheme.color(R.color.zd_green_soft)));
        button.setMinWidth(0);button.setMinimumWidth(0);return button;
    }
    /** The sole owner of the panel state, status, progress, waveform and microphone semantics. */
    private void transition(State next,String message){
        boolean changed=conversation.move(next);host.visual(next);
        if(state==null)return;
        if(!message.contentEquals(state.getText()))state.setText(message);
        progress.setVisibility(next==State.PROCESSING?View.VISIBLE:View.GONE);
        typed.setVisibility(next==State.LISTENING || next==State.PROCESSING?View.GONE:View.VISIBLE);
        write.setVisibility(next==State.LISTENING?View.VISIBLE:View.GONE);
        answer.setVisibility(next==State.ANSWERING?View.VISIBLE:View.GONE);
        heard.setMaxLines(next==State.LISTENING?2:8);
        if(next!=State.LISTENING)hint.setText("");
        if(changed)wave.listening(false); // Orb is the visible RMS surface.
        String caption=next==State.LISTENING?"Durdur":"Tekrar Sor";
        if(permissionStage==PermissionStage.BLOCKED)caption="Ayarlar";
        else if(next==State.IDLE && permissionStage==PermissionStage.READY && activity.checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED)caption="İzin Ver";
        mic.setText(caption);mic.setContentDescription(next==State.LISTENING?"Dinlemeyi durdur":caption);
        mic.setEnabled(permissionStage!=PermissionStage.REQUESTING);
        if(changed && visible())state.announceForAccessibility(message);
        log("state="+next);
    }
    private void haptic(){try{mic.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);}catch(Exception ignored){}}
    private void log(String event){if((activity.getApplicationInfo().flags & android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE)!=0)Log.d("ZeytinAssistant",event);}
    private boolean visible(){return !closed && !activity.isFinishing() && dialog!=null && dialog.isShowing();}
    private void listen() {
        if(!visible() || permissionStage==PermissionStage.REQUESTING)return;
        conversation.nextTurn();stopTts();stopRecognizer();
        if(activity.checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){
            if(permissionStage==PermissionStage.EXPLAIN){
                permissionStage=PermissionStage.READY;
                transition(State.IDLE,"Mikrofon izni gerekiyor.");
                hint.setText("Zeytin Asistanı söylediklerini komuta çevirebilmek için mikrofonu kullanır. İzin Ver’e dokunabilir veya sorunu yazabilirsin.");return;
            }
            if(permissionStage==PermissionStage.BLOCKED){
                try{activity.startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+activity.getPackageName())));}
                catch(Exception e){transition(State.ERROR,"Mikrofon iznini uygulama ayarlarından açabilirsin.");}return;
            }
            permissionStage=PermissionStage.REQUESTING;transition(State.IDLE,"Mikrofon izni bekleniyor…");
            try{activity.requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},MICROPHONE_REQUEST);}
            catch(Exception e){permissionStage=PermissionStage.READY;transition(State.ERROR,ZeytinAssistantConversation.error(Fault.PERMISSION));}return;
        }
        permissionStage=PermissionStage.READY;heard.setText("");answer.setText("");lastAnswer="";
        transition(State.LISTENING,"Dinliyorum…");hint.setText("Mikrofon hazırlanıyor…");
        try {
            if(!SpeechRecognizer.isRecognitionAvailable(activity)){transition(State.ERROR,"Bu cihazda ses tanıma hizmeti bulunamadı. Sorunu yazabilirsin.");return;}
            final int version=++listenVersion;
            recognizer=SpeechRecognizer.createSpeechRecognizer(activity);
            recognizer.setRecognitionListener(new RecognitionListener(){
                private boolean valid(){return visible() && version==listenVersion;}
                public void onReadyForSpeech(Bundle b){if(valid()){hint.setText("Konuşabilirsin. Bitirdiğinde sözlerin kontrol edilecek.");haptic();log("recognizer ready");}}
                public void onBeginningOfSpeech(){}
                public void onRmsChanged(float rms){if(valid() && conversation.state()==State.LISTENING)host.rms(rms);}
                public void onBufferReceived(byte[] bytes){}
                public void onEndOfSpeech(){if(valid())transition(State.PROCESSING,"Kontrol ediyorum…");}
                public void onError(int error){if(valid()){stopRecognizer();log("speech error="+error);transition(State.ERROR,speechError(error));hint.setText("Tekrar sorabilir veya yazılı devam edebilirsin.");}}
                public void onResults(Bundle results){
                    if(!valid())return;log("final result received");
                    ArrayList<String> words=results==null?null:results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    if(words==null||words.isEmpty()||words.get(0)==null||words.get(0).trim().isEmpty()){stopRecognizer();transition(State.ERROR,ZeytinAssistantConversation.error(Fault.NO_MATCH));}
                    else submit(words.get(0));
                }
                public void onPartialResults(Bundle b){
                    if(!valid() || conversation.state()!=State.LISTENING || b==null)return;
                    ArrayList<String> words=b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    if(words!=null && !words.isEmpty() && words.get(0)!=null && !words.get(0).trim().isEmpty()){
                        heard.setText("“"+words.get(0)+"”");log("partial result received");
                    }
                }
                public void onEvent(int type,Bundle b){}
            });
            Intent intent=new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE,"tr-TR");intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS,1);
            intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS,true);
            recognizer.startListening(intent);
        }catch(Exception e){stopRecognizer();transition(State.ERROR,ZeytinAssistantConversation.error(Fault.UNAVAILABLE));}
    }
    private String speechError(int error){
        switch(error){
            case SpeechRecognizer.ERROR_NO_MATCH:return ZeytinAssistantConversation.error(Fault.NO_MATCH);
            case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:return ZeytinAssistantConversation.error(Fault.TIMEOUT);
            case SpeechRecognizer.ERROR_NETWORK:case SpeechRecognizer.ERROR_NETWORK_TIMEOUT:return ZeytinAssistantConversation.error(Fault.NETWORK);
            case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS:return ZeytinAssistantConversation.error(Fault.PERMISSION);
            case SpeechRecognizer.ERROR_RECOGNIZER_BUSY:return ZeytinAssistantConversation.error(Fault.BUSY);
            default:return ZeytinAssistantConversation.error(Fault.UNAVAILABLE);
        }
    }
    public void permissionResult(int code,int[] results){
        if(code!=MICROPHONE_REQUEST)return;
        boolean granted=results.length>0 && results[0]==PackageManager.PERMISSION_GRANTED;
        permissionStage=granted || activity.shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)?PermissionStage.READY:PermissionStage.BLOCKED;
        if(!visible())return;
        if(granted)listen();
        else {transition(State.ERROR,ZeytinAssistantConversation.error(Fault.PERMISSION));hint.setText("İstersen yazılı devam edebilirsin.");}
    }
    private boolean current(int token,String owner,int season,long active){
        return visible() && conversation.current(token) && owner.equals(host.owner()) && season==host.season() && active==host.activeGarden();
    }
    private void submit(String raw){
        if(!visible()||raw.trim().isEmpty())return;
        stopRecognizer();stopTts();
        heard.setText("Duydum: “"+raw+"”");answer.setText("");lastAnswer="";hint.setText("");transition(State.PROCESSING,"Kontrol ediyorum…");
        final int token=conversation.nextTurn();final String owner=host.owner();final int season=host.season();final long active=host.activeGarden();
        final ZeytinAssistantCommand cmd=ZeytinAssistantCommand.parse(raw);
        log("intent="+cmd.kind);
        worker.execute(()->{
            try(ZeytinAssistantData data=new ZeytinAssistantData(activity.getApplicationContext())){
                if(!data.owner.equals(owner)||data.season!=season)return;
                List<Garden> gardens=data.gardens();
                Resolution resolution=ZeytinAssistantCommand.resolve(raw,gardens,active,cmd.kind==Kind.MARKET);
                if(cmd.kind==Kind.UNKNOWN){post(token,owner,season,active,"Bu komutu henüz bilmiyorum.\nÖrnekler: Son sulama ne zaman? • Bu sezon masrafım kaç? • Elek aç.");return;}
                if(cmd.kind==Kind.NAVIGATION){
                    if(!resolution.error.isEmpty() && (!isGlobal(cmd.target) || !resolution.error.startsWith("Önce"))){post(token,owner,season,active,resolution.error);return;}
                    main.post(()->{if(current(token,owner,season,active)){long id=resolution.garden==null?0:resolution.garden.id;transition(State.ANSWERING,"Ekranı açıyorum…");haptic();Toast.makeText(activity,"Ekranı açıyorum…",Toast.LENGTH_SHORT).show();dialog.dismiss();host.navigate(cmd.target,id);}});return;
                }
                boolean all=cmd.total && !resolution.explicit && cmd.kind!=Kind.ACTIVE;
                if(!resolution.error.isEmpty() && !(all && resolution.error.startsWith("Önce")) && cmd.kind!=Kind.MARKET && cmd.kind!=Kind.COMPARISON && !cmd.crossGarden){post(token,owner,season,active,resolution.error);return;}
                if(cmd.kind==Kind.TODAY){
                    main.post(()->{if(!current(token,owner,season,active))return;
                        host.weather(resolution.garden,new WeatherRepository.ForecastCallback(){
                            public void onSuccess(WeatherRepository.WeatherForecast forecast){advisorReply(token,owner,season,active,resolution.garden,forecast);}
                            public void onError(String message){advisorReply(token,owner,season,active,resolution.garden,null);}
                        });
                    });return;
                }
                if(cmd.kind==Kind.WEATHER){
                    main.post(()->{if(!current(token,owner,season,active))return;
                        host.weather(resolution.garden,new WeatherRepository.ForecastCallback(){
                            public void onSuccess(WeatherRepository.WeatherForecast forecast){
                                Calendar date=Calendar.getInstance(TimeZone.getTimeZone("Europe/Istanbul"));date.add(Calendar.DAY_OF_MONTH,1);
                                SimpleDateFormat format=new SimpleDateFormat("yyyy-MM-dd",Locale.US);format.setTimeZone(date.getTimeZone());
                                post(token,owner,season,active,ZeytinAssistantWeather.answer(cmd,resolution.garden.name,forecast,format.format(date.getTime())));
                            }
                            public void onError(String message){post(token,owner,season,active,"Hava verisi alınamadı. Yerel kayıtlarını sormaya devam edebilirsin.");}
                        });
                    });return;
                }
                String result=data.answer(cmd,resolution.garden,gardens,all);
                if(data.unchanged())post(token,owner,season,active,result);
            }catch(Exception e){post(token,owner,season,active,"Kayıtlar okunamadı. Daha sonra tekrar deneyebilirsin; herhangi bir kayıt değiştirilmedi.");}
        });
    }
    private void advisorReply(int token,String owner,int season,long active,Garden garden,WeatherRepository.WeatherForecast forecast){
        main.post(()->{
        if(!current(token,owner,season,active))return;
        worker.execute(()->{try(ZeytinAssistantData data=new ZeytinAssistantData(activity.getApplicationContext())){
            if(!owner.equals(data.owner)||season!=data.season)return;
            String result=data.advice(garden,forecast);if(data.unchanged())post(token,owner,season,active,result);
        }catch(Exception e){post(token,owner,season,active,"Bugünkü kayıtlar okunamadı. Tekrar deneyebilirsin.");}});
        });
    }
    private static boolean isGlobal(String target){return Arrays.asList("HOME","GARDENS","MARKET","MEYDAN","REPORTS","BRINE","ELEK").contains(target);}
    private void post(int token,String owner,int season,long active,String text){main.post(()->{
        if(!current(token,owner,season,active))return;
        lastAnswer=text;answer.setText(text);hint.setText("");transition(State.ANSWERING,"Zeytin Asistanı");haptic();speak();
    });}
    private void speak(){
        if(!visible()||lastAnswer.isEmpty()||conversation.state()!=State.ANSWERING)return;
        if(!ttsReady||tts==null){transition(State.ANSWERING,"Cevap hazır • Yazılı cevap");return;}
        if(!conversation.claimSpeech(conversation.turn()))return;
        stopRecognizer();stopTts();
        speakingId="zeytin-"+session+"-"+conversation.turn();
        try{
            // Use the exact displayed answer. No second answer or numeric rewriting.
            if(tts.speak(lastAnswer,TextToSpeech.QUEUE_FLUSH,null,speakingId)==TextToSpeech.ERROR)
                transition(State.ANSWERING,"Sesli okuma kullanılamadı; yazılı cevap hazır.");
        }catch(Exception e){transition(State.ANSWERING,"Sesli okuma kullanılamadı; yazılı cevap hazır.");}
    }
    private void stopTts(){speakingId="";if(tts!=null)try{tts.stop();}catch(Exception ignored){}}
    private void stopRecognizer(){
        listenVersion++;SpeechRecognizer previous=recognizer;recognizer=null;
        if(previous!=null){try{previous.cancel();}catch(Exception ignored){}try{previous.destroy();}catch(Exception ignored){}}
    }
    private void releasePanel(){
        session++;conversation.nextTurn();stopRecognizer();stopTts();
        transition(State.IDLE,"Asistan kapatıldı.");if(wave!=null)wave.listening(false);ttsReady=false;
        if(tts!=null){try{tts.shutdown();}catch(Exception ignored){}tts=null;}
    }
    public void pause(){
        conversation.nextTurn();stopRecognizer();stopTts();
        if(visible() && permissionStage!=PermissionStage.REQUESTING)transition(State.IDLE,"Devam etmek için Tekrar Sor’a dokun.");
        if(wave!=null)wave.listening(false);
    }
    public void stop(){if(dialog!=null&&dialog.isShowing())dialog.dismiss();else releasePanel();}
    public void destroy(){stop();closed=true;worker.shutdownNow();main.removeCallbacksAndMessages(null);}
    private int dp(int n){return Math.round(n*activity.getResources().getDisplayMetrics().density);}
    private TextView label(String text,int sp){TextView t=new TextView(activity);t.setText(text);t.setTextSize(sp);t.setTextColor(Color.WHITE);t.setPadding(0,dp(4),0,dp(4));return t;}
}
