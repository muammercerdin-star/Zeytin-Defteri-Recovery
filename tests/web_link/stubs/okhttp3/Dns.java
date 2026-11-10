package okhttp3;
/** Test stub: yalnız TezgahWebNet derlensin diye. */
public interface Dns { java.util.List<java.net.InetAddress> lookup(String host) throws java.net.UnknownHostException; Dns SYSTEM = h -> java.util.Arrays.asList(java.net.InetAddress.getAllByName(h)); }
