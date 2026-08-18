package com.huarenzaimeng.api.payment;

import com.huarenzaimeng.api.BusinessEventStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Duration;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Configuration(proxyBeanMethods=false) @Profile("release-mysql")
class PaymentCoordinationConfiguration {
    @Bean WeChatPayPort weChatPayPort(ObjectMapper json,
            @Value("${hz.wechat-pay.mode:disabled}") String mode,@Value("${hz.wechat-pay.enabled:false}") boolean enabled,
            @Value("${hz.wechat-pay.expected-app-id:}") String appId,@Value("${hz.wechat-pay.merchant-id:}") String merchantId,
            @Value("${hz.wechat-pay.merchant-serial:}") String merchantSerial,@Value("${hz.wechat-pay.notify-url:}") String notifyUrl,
            @Value("${hz.wechat-pay.merchant-private-key-base64:}") String privateKey,@Value("${hz.wechat-pay.api-v3-key:}") String apiV3Key,
            @Value("${hz.wechat-pay.platform-public-keys:}") String keys,@Value("${hz.wechat-pay.connect-timeout-ms:2000}") long connectMs,
            @Value("${hz.wechat-pay.read-timeout-ms:3000}") long readMs){
        if(!enabled&&"disabled".equals(mode))return new DisabledWeChatPayAdapter();
        if(!enabled||!"api-v3".equals(mode)||connectMs<100||connectMs>10000||readMs<100||readMs>30000)throw new IllegalStateException("WECHAT_PAY_CONFIGURATION_INVALID");
        Map<String,String> platformKeys=Arrays.stream(keys.split(",")).map(String::trim).filter(v->!v.isEmpty()).map(v->v.split(":",2)).filter(v->v.length==2).collect(Collectors.toUnmodifiableMap(v->v[0],v->v[1]));
        return new WeChatPayApiV3Adapter(new JdkWeChatPayApiV3Transport(Duration.ofMillis(connectMs),Duration.ofMillis(readMs)),json,Clock.systemUTC(),new SecureRandom(),appId,merchantId,merchantSerial,notifyUrl,privateKey,apiV3Key,platformKeys);
    }
    @Bean WeChatPayCoordinator weChatPayCoordinator(WeChatPayPort port,JdbcWeChatPayStore store,PaymentOrderSnapshotPort orders,BusinessEventStore events,
            @Value("${hz.wechat-pay.expected-app-id:}") String appId,@Value("${hz.wechat-pay.merchant-id:}") String merchantId,
            @Value("${hz.wechat-pay.allowed-certificate-serials:}") String serials){Set<String> allowed=Arrays.stream(serials.split(",")).map(String::trim).filter(v->!v.isEmpty()).collect(Collectors.toUnmodifiableSet());return new WeChatPayCoordinator(port,store,orders,5,new WeChatPayCoordinator.NotificationAuthority(appId,merchantId,allowed),events);}
}
