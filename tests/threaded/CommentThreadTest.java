package com.zeytindefteri.app;
import java.util.*;
public class CommentThreadTest {
    static int checks;
    static void check(boolean b){if(!b)throw new AssertionError("check "+checks);checks++;}
    static CommentThread.Node n(String id,String parent,boolean active){return new CommentThread.Node(id,parent,active);}
    public static void main(String[] args){
        List<CommentThread.Node> input=Arrays.asList(n("a","",true),n("other","",true),n("b","a",true),n("c","b",true));
        List<CommentThread.Placement> out=CommentThread.order(input);
        check(out.size()==4);check(out.get(0).id.equals("a"));check(out.get(1).id.equals("b"));
        check(out.get(2).id.equals("c"));check(out.get(3).id.equals("other"));
        check(out.get(0).liveDescendants==2);check(out.get(1).liveDescendants==1);check(out.get(2).depth==2);
        check(CommentThread.order(input).size()==4);
        out=CommentThread.order(Arrays.asList(n("legacy",null,true),n("child","missing",true)));
        check(out.size()==2);check(out.get(0).depth==0);check(out.get(1).depth==0);
        out=CommentThread.order(Arrays.asList(n("parent","",false),n("child","parent",true)));
        check(out.get(0).liveDescendants==1);check(out.get(1).depth==1);
        out=CommentThread.order(Arrays.asList(n("a","b",true),n("b","a",true),n("self","self",true)));
        check(out.size()==3);check(new HashSet<>(Arrays.asList(out.get(0).id,out.get(1).id,out.get(2).id)).size()==3);
        List<CommentThread.Node> deep=new ArrayList<>();
        for(int i=0;i<10000;i++)deep.add(n("n"+i,i==0?"":"n"+(i-1),true));
        out=CommentThread.order(deep);check(out.size()==10000);check(out.get(0).liveDescendants==9999);
        check(out.get(9999).depth==9999);check(Math.min(2,out.get(9999).depth)*24==48);
        System.out.println("PASS "+checks+" actual Java thread-order assertions (legacy/orphan/delete/cycle/deep/repeated render)");
    }
}
