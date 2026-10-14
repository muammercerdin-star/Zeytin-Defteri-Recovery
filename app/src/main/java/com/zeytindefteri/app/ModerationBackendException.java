package com.zeytindefteri.app;

final class ModerationBackendException extends Exception {
    /** UNAVAILABLE: servis yapılandırılmamış ya da ağa ulaşılamıyor; kullanıcıya "şu anda bağlanılamıyor" gösterilir. */
    enum Code { UNAUTHENTICATED, PERMISSION_DENIED, INVALID_ARGUMENT, NOT_FOUND, FAILED_PRECONDITION, INTERNAL, UNAVAILABLE }
    static final String UNAVAILABLE_MESSAGE = "Moderasyon servisine şu anda bağlanılamıyor. Lütfen daha sonra tekrar deneyin.";
    private final Code code;
    ModerationBackendException(Code code){super(code.name());this.code=code;}
    Code getCode(){return code;}
    static Code fromWire(String value){
        switch(value){case "UNAUTHENTICATED":return Code.UNAUTHENTICATED;case "FORBIDDEN":return Code.PERMISSION_DENIED;
            case "INVALID_ARGUMENT":return Code.INVALID_ARGUMENT;case "NOT_FOUND":return Code.NOT_FOUND;case "CONFLICT":return Code.FAILED_PRECONDITION;default:return Code.INTERNAL;}
    }
}
