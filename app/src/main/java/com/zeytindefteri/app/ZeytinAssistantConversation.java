package com.zeytindefteri.app;

/** Small UI/session policy only. Commands, answers and data scope remain in the V8 engine. */
public final class ZeytinAssistantConversation {
    public enum State { IDLE, LISTENING, PROCESSING, ANSWERING, ERROR }
    public enum Fault { NO_MATCH, TIMEOUT, NETWORK, PERMISSION, BUSY, UNAVAILABLE }
    private State state=State.IDLE;
    private int turn,spokenTurn=-1;
    public State state(){return state;}
    public int turn(){return turn;}
    public int nextTurn(){return ++turn;}
    public boolean current(int token){return token==turn;}
    public boolean move(State next){boolean changed=state!=next;state=next;return changed;}
    public boolean claimSpeech(int token){
        if(!current(token)||state!=State.ANSWERING||spokenTurn==token)return false;
        spokenTurn=token;return true;
    }
    public static float level(float rms){
        if(Float.isNaN(rms)||Float.isInfinite(rms))return 0;
        return Math.max(0f,Math.min(1f,rms/10f));
    }
    public static String error(Fault fault){
        switch(fault){
            case NO_MATCH:return "Seni tam anlayamadım. Tekrar deneyebilirsin.";
            case TIMEOUT:return "Seni duyamadım. Tekrar deneyebilirsin.";
            case NETWORK:return "Ses tanıma bağlantısında sorun oluştu. Tekrar deneyebilirsin.";
            case PERMISSION:return "Mikrofon izni olmadan sesli komut kullanılamıyor. Sorunu yazabilirsin.";
            case BUSY:return "Sesli asistan şu anda meşgul. Tekrar deneyelim.";
            default:return "Sesli komut şu anda kullanılamıyor. Sorunu yazabilirsin.";
        }
    }
}
