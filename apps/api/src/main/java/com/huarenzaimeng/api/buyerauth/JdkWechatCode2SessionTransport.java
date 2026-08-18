package com.huarenzaimeng.api.buyerauth;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

@Component
@Profile("release-mysql")
final class JdkWechatCode2SessionTransport implements WechatCode2SessionTransport {
    @Override
    public Response execute(Request request) {
        try {
            HttpClient client = HttpClient.newBuilder().connectTimeout(request.connectTimeout()).build();
            HttpRequest httpRequest = HttpRequest.newBuilder(requestUri(request))
                    .timeout(request.readTimeout()).GET().build();
            HttpResponse<String> response = client.send(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return new Response(response.statusCode(), response.body());
        } catch (java.net.http.HttpTimeoutException timeout) {
            throw new Failure(FailureKind.TIMEOUT);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new Failure(FailureKind.UNAVAILABLE);
        } catch (Exception unavailable) {
            throw new Failure(FailureKind.UNAVAILABLE);
        }
    }

    static URI requestUri(Request request) {
        if (!WechatCode2SessionClient.OFFICIAL_ENDPOINT.equals(request.endpoint())) {
            throw new Failure(FailureKind.UNAVAILABLE);
        }
        String query = "appid=" + encode(request.appId())
                + "&secret=" + encode(request.appSecret())
                + "&js_code=" + encode(request.oneTimeCode())
                + "&grant_type=authorization_code";
        return URI.create(request.endpoint() + "?" + query);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
