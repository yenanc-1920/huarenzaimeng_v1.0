package com.huarenzaimeng.api.payment;

import com.huarenzaimeng.api.BusinessEventStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@Configuration(proxyBeanMethods=false) @Profile("release-mysql")
class PaymentCoordinationConfiguration {
    @Bean WeChatPayPort weChatPayPort(){return new DisabledWeChatPayAdapter();}
    @Bean WeChatPayCoordinator weChatPayCoordinator(WeChatPayPort port,JdbcWeChatPayStore store,PaymentOrderSnapshotPort orders,BusinessEventStore events,
            @Value("${hz.wechat-pay.expected-app-id:}") String appId,@Value("${hz.wechat-pay.merchant-id:}") String merchantId,
            @Value("${hz.wechat-pay.allowed-certificate-serials:}") String serials){Set<String> allowed=Arrays.stream(serials.split(",")).map(String::trim).filter(v->!v.isEmpty()).collect(Collectors.toUnmodifiableSet());return new WeChatPayCoordinator(port,store,orders,5,new WeChatPayCoordinator.NotificationAuthority(appId,merchantId,allowed),events);}
}
