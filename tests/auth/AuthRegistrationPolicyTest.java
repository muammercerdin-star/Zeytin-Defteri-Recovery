package com.zeytindefteri.app;

public final class AuthRegistrationPolicyTest {
    private static int count;
    private static void check(boolean condition) {
        if (!condition) throw new AssertionError("Policy check " + (count + 1));
        count++;
    }
    public static void main(String[] args) {
        check(!AuthRegistrationPolicy.validPassword(null));
        check(!AuthRegistrationPolicy.validPassword(""));
        check(!AuthRegistrationPolicy.validPassword("Abcdef1"));
        check(!AuthRegistrationPolicy.validPassword("abcdefgh"));
        check(!AuthRegistrationPolicy.validPassword("abcdefg1"));
        check(!AuthRegistrationPolicy.validPassword("Abcdefgh"));
        check(AuthRegistrationPolicy.validPassword("Abcdefg1"));
        check(AuthRegistrationPolicy.validPassword("İstanbul1"));
        check(!AuthRegistrationPolicy.validPassword("A1🙂🙂🙂"));
        check(AuthRegistrationPolicy.strength("") == 0);
        check(AuthRegistrationPolicy.strength("a") == 1);
        check(AuthRegistrationPolicy.strength("abcdefgh") == 2);
        check(AuthRegistrationPolicy.strength("Abcdefg1") == 3);
        check(AuthRegistrationPolicy.strength("Abcdefghijk1") == 4);
        check(!AuthRegistrationPolicy.documentsReady());
        check(!AuthRegistrationPolicy.canRegister(false));
        check(!AuthRegistrationPolicy.canRegister(true));
        System.out.println("PASS: " + count + " registration policy checks (no Firebase calls).");
    }
}
