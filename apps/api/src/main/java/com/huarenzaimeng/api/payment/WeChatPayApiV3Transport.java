package com.huarenzaimeng.api.payment;

import java.util.Map;

interface WeChatPayApiV3Transport {
    Response execute(Request request);
    record Request(String method,String pathAndQuery,String body,Map<String,String> headers){public Request{headers=Map.copyOf(headers);}}
    record Response(int status,String body,Map<String,String> headers){public Response{headers=Map.copyOf(headers);}}
}
