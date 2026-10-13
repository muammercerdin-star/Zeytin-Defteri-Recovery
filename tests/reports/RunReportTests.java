import java.nio.file.*;
import java.net.*;
import javax.tools.ToolProvider;
public class RunReportTests {
    public static void main(String[] args) throws Exception {
        Path out=Files.createTempDirectory("report-tests-");
        int code=ToolProvider.getSystemJavaCompiler().run(null,null,null,"-source","8","-target","8","-d",out.toString(),
            "app/src/main/java/com/zeytindefteri/app/SocialReportTarget.java","tests/reports/SocialReportTargetTest.java");
        if(code!=0)throw new AssertionError("Compilation failed");
        try(URLClassLoader loader=new URLClassLoader(new URL[]{out.toUri().toURL()})) {
            loader.loadClass("SocialReportTargetTest").getMethod("main",String[].class).invoke(null,(Object)new String[0]);
        }
    }
}
