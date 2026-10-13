package com.zeytindefteri.app;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.json.JSONObject;

/** Native HTTPS adapter; no Supabase key and no Firebase Functions SDK. */
final class ModerationBackendClient {
    private static final ThreadPoolExecutor IO=new ThreadPoolExecutor(0,2,30,TimeUnit.SECONDS,new ArrayBlockingQueue<>(8));
    private static final ModerationBackendClient INSTANCE=new ModerationBackendClient();
    static ModerationBackendClient getInstance(){return INSTANCE;}
    Operation operation(String name){
        if(!Arrays.asList("moderationListReports","moderationUpdateReportStatus","moderationLookupUser","moderationGrantModerator","moderationRevokeModerator","moderationListModerators").contains(name))throw new IllegalArgumentException();
        return new Operation(name);
    }
    static final class Result {
        private final Object data;Result(Object data){this.data=data;}Object getData(){return data;}
    }
    static final class Operation {
        private final String action;private int timeout=30000;
        Operation(String action){this.action=action;}
        Operation withTimeout(long n,TimeUnit unit){timeout=(int)Math.max(1000,Math.min(30000,unit.toMillis(n)));return this;}
        Task<Result> call(Map<String,Object> data){
            TaskCompletionSource<Result> result=new TaskCompletionSource<>();
            FirebaseUser user=FirebaseAuth.getInstance().getCurrentUser();
            if(user==null||user.isAnonymous()){result.setException(new ModerationBackendException(ModerationBackendException.Code.UNAUTHENTICATED));return result.getTask();}
            final String payload;
            try{payload=new JSONObject().put("action",action).put("data",new JSONObject(data)).toString();}
            catch(Exception e){result.setException(new ModerationBackendException(ModerationBackendException.Code.INVALID_ARGUMENT));return result.getTask();}
            user.getIdToken(true).addOnCompleteListener(task->{
                if(!task.isSuccessful()||task.getResult()==null||task.getResult().getToken()==null){result.trySetException(new ModerationBackendException(ModerationBackendException.Code.UNAUTHENTICATED));return;}
                final String token=task.getResult().getToken();
                try{IO.execute(()->{
                    FirebaseUser current=FirebaseAuth.getInstance().getCurrentUser();
                    if(current==null||!user.getUid().equals(current.getUid())){result.trySetException(new ModerationBackendException(ModerationBackendException.Code.UNAUTHENTICATED));return;}
                    try{result.trySetResult(new Result(send(payload,token,timeout)));}
                    catch(ModerationBackendException e){result.trySetException(e);}
                    catch(Exception e){result.trySetException(new ModerationBackendException(ModerationBackendException.Code.INTERNAL));}
                });}catch(RuntimeException e){result.trySetException(new ModerationBackendException(ModerationBackendException.Code.INTERNAL));}
            });
            return result.getTask();
        }
    }
    private static Object send(String payload,String token,int timeout)throws Exception{
        HttpURLConnection connection=null;
        try{
            URL endpoint=new URL(MarketBackendConfig.SUPABASE_URL+"/functions/v1/moderation-api");
            if(!"https".equals(endpoint.getProtocol()))throw new IllegalArgumentException();
            connection=(HttpURLConnection)endpoint.openConnection();connection.setInstanceFollowRedirects(false);
            connection.setRequestMethod("POST");connection.setConnectTimeout(12000);connection.setReadTimeout(timeout);connection.setDoOutput(true);
            connection.setRequestProperty("Authorization","Bearer "+token);connection.setRequestProperty("Content-Type","application/json; charset=utf-8");connection.setRequestProperty("Accept","application/json");
            byte[] bytes=payload.getBytes(StandardCharsets.UTF_8);if(bytes.length>8192)throw new ModerationBackendException(ModerationBackendException.Code.INVALID_ARGUMENT);
            connection.setFixedLengthStreamingMode(bytes.length);try(OutputStream out=connection.getOutputStream()){out.write(bytes);}
            int status=connection.getResponseCode();InputStream stream=status>=200&&status<300?connection.getInputStream():connection.getErrorStream();
            String response="";
            if(stream!=null)try(InputStream in=stream;ByteArrayOutputStream out=new ByteArrayOutputStream()){
                byte[] chunk=new byte[4096];int n;while((n=in.read(chunk))!=-1){if(out.size()+n>1024*1024)throw new IllegalStateException();out.write(chunk,0,n);}response=new String(out.toByteArray(),StandardCharsets.UTF_8);
            }
            return ModerationWire.parse(status,response);
        }finally{if(connection!=null)connection.disconnect();}
    }
}
