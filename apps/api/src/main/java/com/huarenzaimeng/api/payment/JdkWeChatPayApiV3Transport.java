package com.huarenzaimeng.api.payment;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

final class JdkWeChatPayApiV3Transport implements WeChatPayApiV3Transport {
    private static final URI ORIGIN=URI.create("https://api.mch.weixin.qq.com");
    private final HttpClient client;private final Duration readTimeout;
    JdkWeChatPayApiV3Transport(Duration connectTimeout,Duration readTimeout){
        this.client=HttpClient.newBuilder().connectTimeout(connectTimeout).followRedirects(HttpClient.Redirect.NEVER).build();this.readTimeout=readTimeout;
    }
    public Response execute(Request r){
        try{
            if(r.pathAndQuery()==null||!r.pathAndQuery().startsWith("/v3/")||r.pathAndQuery().contains("//"))throw new IllegalArgumentException("WECHAT_PAY_PATH_INVALID");
            HttpRequest.Builder b=HttpRequest.newBuilder(ORIGIN.resolve(r.pathAndQuery())).timeout(readTimeout);
            r.headers().forEach(b::header);String body=r.body()==null?"":r.body();
            b.method(r.method(),body.isEmpty()?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(body));
            HttpResponse<String> response=client.send(b.build(),HttpResponse.BodyHandlers.ofString());
            if(response.body()!=null&&response.body().length()>65536)throw new IllegalStateException("WECHAT_PAY_RESPONSE_TOO_LARGE");
            Map<String,String> headers=response.headers().map().entrySet().stream().collect(Collectors.toUnmodifiableMap(e->e.getKey().toLowerCase(Locale.ROOT),e->String.join(",",e.getValue())));
            return new Response(response.statusCode(),response.body()==null?"":response.body(),headers);
        }catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw new IllegalStateException("WECHAT_PAY_TRANSPORT_INTERRUPTED");}
        catch(RuntimeException runtime){throw runtime;}catch(Exception failure){throw new IllegalStateException("WECHAT_PAY_TRANSPORT_UNKNOWN");}
    }
}
