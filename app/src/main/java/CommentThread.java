package com.zeytindefteri.app;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Linear-time forest ordering over the already loaded chronological page. No I/O. */
public final class CommentThread {
    private CommentThread() {}
    public static final class Node {
        final String id, parent;
        final boolean active;
        final List<Node> children = new ArrayList<>();
        public Node(String id, String parent, boolean active) {
            this.id=id; this.parent=parent==null?"":parent; this.active=active;
        }
    }
    public static final class Placement {
        public final String id;
        public final int depth;
        public int liveDescendants;
        final Node node;
        final Placement parent;
        Placement(Node node, Placement parent) {
            this.node=node;this.id=node.id;this.parent=parent;this.depth=parent==null?0:parent.depth+1;
        }
    }
    public static List<Placement> order(List<Node> chronological) {
        Map<String,Node> map=new LinkedHashMap<>();
        for(Node n:chronological){n.children.clear();map.put(n.id,n);}
        List<Node> roots=new ArrayList<>();
        for(Node n:map.values()) {
            Node parent=map.get(n.parent);
            if(parent==null || parent==n)roots.add(n);else parent.children.add(n);
        }
        List<Placement> result=new ArrayList<>();Set<String> seen=new HashSet<>();
        for(Node root:roots)walk(root,result,seen);
        // Corrupt cycles must neither hide messages nor recurse forever.
        for(Node n:map.values())if(!seen.contains(n.id))walk(n,result,seen);
        for(int i=result.size()-1;i>=0;i--) {
            Placement p=result.get(i);
            if(p.parent!=null)p.parent.liveDescendants+=p.liveDescendants+(p.node.active?1:0);
        }
        return result;
    }
    private static void walk(Node root,List<Placement> result,Set<String> seen) {
        ArrayDeque<Placement> stack=new ArrayDeque<>();stack.push(new Placement(root,null));
        while(!stack.isEmpty()) {
            Placement p=stack.pop();if(!seen.add(p.id))continue;result.add(p);
            for(int i=p.node.children.size()-1;i>=0;i--)stack.push(new Placement(p.node.children.get(i),p));
        }
    }
}
