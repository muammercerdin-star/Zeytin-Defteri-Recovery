import java.nio.file.*;
import java.net.*;
import javax.tools.ToolProvider;
public class RunThreadTests {
    public static void main(String[] args)throws Exception {
        Path out=Files.createTempDirectory("thread-tests-");
        int result=ToolProvider.getSystemJavaCompiler().run(null,null,null,"-source","8","-target","8","-d",out.toString(),
            "app/src/main/java/CommentThread.java","tests/threaded/CommentThreadTest.java");
        if(result!=0)throw new AssertionError("Compilation failed");
        try(URLClassLoader loader=new URLClassLoader(new URL[]{out.toUri().toURL()})){
            loader.loadClass("com.zeytindefteri.app.CommentThreadTest").getMethod("main",String[].class).invoke(null,(Object)new String[0]);
        }
    }
}
