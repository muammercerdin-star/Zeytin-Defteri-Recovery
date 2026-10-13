import com.zeytindefteri.app.ZeytinAssistantConversation;
import com.zeytindefteri.app.ZeytinAssistantConversation.State;
import com.zeytindefteri.app.ZeytinAssistantConversation.Fault;
public class ConversationTest {
 static int count;
 static void check(boolean value,String name){if(!value)throw new AssertionError(name);count++;System.out.println("PASS "+name);}
 public static void main(String[] args){
  ZeytinAssistantConversation c=new ZeytinAssistantConversation();
  check(c.state()==State.IDLE,"initial idle");
  int first=c.nextTurn();c.move(State.LISTENING);
  check(c.state()==State.LISTENING && c.current(first),"single tap new listening turn");
  check(!c.claimSpeech(first),"TTS forbidden while listening");
  int second=c.nextTurn();c.move(State.IDLE);
  check(!c.current(first) && c.current(second),"double tap cancellation invalidates old request");
  int third=c.nextTurn();c.move(State.LISTENING);c.move(State.PROCESSING);
  check(c.state()==State.PROCESSING,"final result processing state");
  check(!c.claimSpeech(third),"no speech during query");
  c.move(State.ANSWERING);
  check(c.claimSpeech(third),"ready answer can speak once");
  check(!c.claimSpeech(third),"duplicate answer and late TTS init cannot repeat speech");
  int retry=c.nextTurn();c.move(State.LISTENING);
  check(!c.current(third) && !c.claimSpeech(third),"interrupting answer invalidates previous utterance");
  check(!c.claimSpeech(retry),"no TTS echo in new listening session");
  c.move(State.PROCESSING);c.move(State.ANSWERING);
  check(c.claimSpeech(retry),"next question may read its own answer");
  c.nextTurn();c.move(State.IDLE);
  check(!c.current(retry),"panel close invalidates callbacks");
  int beforePause=c.nextTurn();c.move(State.PROCESSING);c.nextTurn();c.move(State.IDLE);
  check(!c.current(beforePause),"pause invalidates pending data reply");
  ZeytinAssistantConversation recreated=new ZeytinAssistantConversation();
  check(recreated.state()==State.IDLE,"recreated panel does not auto-listen");
  check(!recreated.claimSpeech(recreated.turn()),"no automatic old speech on recreation");
  c.move(State.ERROR);check(c.state()==State.ERROR,"speech errors have distinct state");
  c.move(State.ANSWERING);check(c.state()==State.ANSWERING,"unknown parsed command is a response not speech error");
  check(!c.move(State.ANSWERING),"unchanged state avoids repeated accessibility transition");
  check(ZeytinAssistantConversation.error(Fault.NO_MATCH).contains("tam anlayamadım"),"no match wording");
  check(ZeytinAssistantConversation.error(Fault.TIMEOUT).contains("duyamadım"),"silence wording");
  check(ZeytinAssistantConversation.error(Fault.NETWORK).contains("bağlantı"),"network wording");
  check(ZeytinAssistantConversation.error(Fault.PERMISSION).contains("izni"),"permission fallback wording");
  check(ZeytinAssistantConversation.error(Fault.BUSY).contains("meşgul"),"busy wording");
  check(ZeytinAssistantConversation.error(Fault.UNAVAILABLE).contains("yazabilirsin"),"service unavailable written fallback");
  check(ZeytinAssistantConversation.level(-10)==0,"quiet RMS clamped");
  check(ZeytinAssistantConversation.level(0)==0,"silent RMS no activity");
  check(ZeytinAssistantConversation.level(5)==0.5f,"measured RMS drives amplitude");
  check(ZeytinAssistantConversation.level(100)==1,"loud RMS bounded");
  check(ZeytinAssistantConversation.level(Float.NaN)==0,"invalid RMS quiet");
  check(ZeytinAssistantConversation.level(Float.POSITIVE_INFINITY)==0,"infinite RMS quiet");
  System.out.println(count+" UI/session policy assertions passed; not Android device tests");
 }
}
