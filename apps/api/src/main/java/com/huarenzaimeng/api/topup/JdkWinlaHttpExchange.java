package com.huarenzaimeng.api.topup;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Set;

final class JdkWinlaHttpExchange implements WinlaHttpTransport.HttpExchange {
    private static final Set<String> ENDPOINTS=Set.of(WinlaHttpTransport.SUBMIT_URL,WinlaHttpTransport.QUERY_URL,WinlaHttpTransport.BALANCE_URL);
    private final HttpClient client;private final Duration readTimeout;
    JdkWinlaHttpExchange(int connectTimeoutMs,int readTimeoutMs){if(connectTimeoutMs<200||connectTimeoutMs>30000||readTimeoutMs<200||readTimeoutMs>30000)throw new IllegalArgumentException("WINLA_TIMEOUT_INVALID");client=HttpClient.newBuilder().connectTimeout(Duration.ofMillis(connectTimeoutMs)).followRedirects(HttpClient.Redirect.NEVER).build();readTimeout=Duration.ofMillis(readTimeoutMs);}
    @Override public byte[] post(String url,String formBody){if(!ENDPOINTS.contains(url))throw new IllegalArgumentException("WINLA_ENDPOINT_FORBIDDEN");try{HttpRequest request=HttpRequest.newBuilder(URI.create(url)).timeout(readTimeout).header("Content-Type","application/x-www-form-urlencoded").header("Accept","application/json").POST(HttpRequest.BodyPublishers.ofString(formBody,StandardCharsets.UTF_8)).build();HttpResponse<byte[]> response=client.send(request,HttpResponse.BodyHandlers.ofByteArray());if(response.statusCode()<200||response.statusCode()>=300||response.body()==null||response.body().length>65536)throw new IllegalStateException("WINLA_HTTP_RESPONSE_INVALID");return response.body();}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException("WINLA_HTTP_INTERRUPTED");}catch(Exception e){throw new IllegalStateException("WINLA_HTTP_FAILED");}}
}
