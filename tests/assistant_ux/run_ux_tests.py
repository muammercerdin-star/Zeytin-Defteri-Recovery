from pathlib import Path
import subprocess,tempfile
root=Path(__file__).resolve().parents[2]
with tempfile.TemporaryDirectory(prefix='zeytin-ux-') as directory:
 p=Path(directory)
 compiler=p/'Compile.java'
 compiler.write_text('import javax.tools.ToolProvider; public class Compile { public static void main(String[] a){System.exit(ToolProvider.getSystemJavaCompiler().run(null,System.out,System.err,a));}}')
 subprocess.run(['java',str(compiler),'--release','8','-encoding','UTF-8','-d',str(p/'classes'),str(root/'app/src/main/java/com/zeytindefteri/app/ZeytinAssistantConversation.java'),str(root/'tests/assistant_ux/ConversationTest.java')],check=True)
 subprocess.run(['java','-cp',str(p/'classes'),'ConversationTest'],check=True)
