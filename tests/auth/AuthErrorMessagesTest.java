package com.zeytindefteri.app;

import java.nio.file.Paths;
import java.util.Arrays;
import javax.tools.*;
import com.sun.source.util.JavacTask;

public final class AuthErrorMessagesTest {
    private static int checks;
    private static String message(String code, String operation) {
        return AuthErrorMessages.message(code, false, false, operation);
    }
    private static void contains(String value, String expected) {
        if (!value.contains(expected)) throw new AssertionError(value + " / " + expected);
        checks++;
    }
    public static void main(String[] args) throws Exception {
        contains(AuthErrorMessages.message("", true, false, "login"), "İnternet");
        contains(AuthErrorMessages.message("", false, true, "register"), "bekleyip");
        contains(message("ERROR_TOO_MANY_REQUESTS", "reset"), "bekleyip");
        contains(message("ERROR_OPERATION_NOT_ALLOWED", "login"), "kullanılamıyor");
        contains(message("ERROR_PASSWORD_LOGIN_DISABLED", "login"), "kullanılamıyor");
        contains(message("ERROR_USER_DISABLED", "login"), "devre dışı");
        contains(message("ERROR_INVALID_EMAIL", "reset"), "Geçerli");
        contains(message("ERROR_WEAK_PASSWORD", "register"), "güvenlik koşullarını");
        contains(message("ERROR_EMAIL_ALREADY_IN_USE", "register"), "Mevcut giriş yöntemini");
        contains(message("ERROR_ACCOUNT_EXISTS_WITH_DIFFERENT_CREDENTIAL", "register"), "Mevcut giriş yöntemini");
        for (String code : Arrays.asList("ERROR_WRONG_PASSWORD", "ERROR_USER_NOT_FOUND",
                "ERROR_INVALID_CREDENTIAL", "ERROR_INVALID_LOGIN_CREDENTIALS")) {
            if (!message(code, "login").equals(message("ERROR_WRONG_PASSWORD", "login"))) {
                throw new AssertionError("Credential errors must not disclose account existence");
            }
            checks++;
        }
        contains(message("unknown-provider-detail", "register"), "Hesap oluşturulamadı");
        contains(message("unknown-provider-detail", "login"), "Giriş tamamlanamadı");
        contains(message("unknown-provider-detail", "reset"), "isteği tamamlanamadı");
        contains(message(null, "login"), "Giriş tamamlanamadı");
        // Syntax-only parse of the real activity; this is NOT Android type checking/build.
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        try (StandardJavaFileManager files = compiler.getStandardFileManager(diagnostics, null, null)) {
            JavacTask task = (JavacTask) compiler.getTask(null, files, diagnostics,
                    Arrays.asList("-proc:none"), null, files.getJavaFileObjects(
                    Paths.get("app/src/main/java/com/zeytindefteri/app/LoginActivity.java").toFile()));
            task.parse();
            for (Diagnostic<?> diagnostic : diagnostics.getDiagnostics()) {
                if (diagnostic.getKind() == Diagnostic.Kind.ERROR) throw new AssertionError(diagnostic);
            }
        }
        System.out.println("PASS: " + checks + " message checks; LoginActivity syntax parse passed.");
    }
}
