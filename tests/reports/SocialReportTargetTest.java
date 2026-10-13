import com.zeytindefteri.app.SocialReportTarget;
public final class SocialReportTargetTest {
    private static int count;
    private static void check(boolean value) { if (!value) throw new AssertionError(); count++; }
    private static void invalid(String type,String id,String post,String uid) {
        try {new SocialReportTarget(type,id,post,uid,false);throw new AssertionError("accepted invalid target");}
        catch(IllegalArgumentException expected){count++;}
    }
    public static void main(String[] args) {
        SocialReportTarget p=new SocialReportTarget("POST","p","p","B",false);
        check(p.key().equals("POST|p|p"));
        check(new SocialReportTarget("POST","p","p","B",false).key().equals(p.key()));
        SocialReportTarget c=new SocialReportTarget("COMMENT","c","p","B",false);
        check(c.key().equals("COMMENT|p|c"));
        check(new SocialReportTarget("REPLY","r","p","B",false).key().equals("REPLY|p|r"));
        check(!new SocialReportTarget("COMMENT","c","p2","B",false).key().equals(c.key()));
        SocialReportTarget u1=new SocialReportTarget("POST","p","p","B",true);
        SocialReportTarget u2=new SocialReportTarget("REPLY","r","another-post","B",true);
        check(u1.key().equals("USER||B") && u1.key().equals(u2.key()));
        check(u2.commentId.equals("r") && u2.postId.equals("another-post"));
        check(!u1.key().equals(new SocialReportTarget("POST","p","p","C",true).key()));
        invalid("OTHER","p","p","B");invalid("POST","wrong","p","B");
        invalid("COMMENT","a|b","p","B");invalid("REPLY","a/b","p","B");
        invalid("POST","","","B");invalid("POST",null,"p","B");
        invalid("POST","p","p","");invalid("POST","p","p","a/b");
        invalid("COMMENT","c","p|a","B");
        check(SocialReportTarget.validId("e7c081f4-ae5d-dc4c-c3312808"));
        check(!SocialReportTarget.validId(new String(new char[129]).replace('\0','a')));
        System.out.println("PASS: "+count+" target identity / duplicate / invalid ID assertions");
    }
}
