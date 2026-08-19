package com.huarenzaimeng.api.topup;
import com.huarenzaimeng.api.BusinessEventStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Arrays;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
@Configuration(proxyBeanMethods=false) @Profile("release-mysql")
class TopupCoordinationConfiguration {
    @Bean TopupProviderPort topupProviderPort(Environment env,ProviderExchangeStore exchanges,ObjectMapper json){
        String mode=value(env,"HZ_TOPUP_PROVIDER_MODE","disabled");boolean enabled=Boolean.parseBoolean(value(env,"HZ_WINLA_ENABLED","false"));
        if("disabled".equals(mode)&&!enabled)return WinlaTopupAdapter.disabled();
        if(!"winla".equals(mode)||!enabled)throw new IllegalStateException("WINLA_MODE_SWITCH_MISMATCH");
        String uid=required(env,"HZ_WINLA_UID");char[] key=required(env,"HZ_WINLA_API_KEY").toCharArray();
        try{return new WinlaTopupAdapter(new WinlaHttpTransport(uid,key,new JdkWinlaHttpExchange(integer(env,"HZ_WINLA_CONNECT_TIMEOUT_MS",2000),integer(env,"HZ_WINLA_READ_TIMEOUT_MS",3000)),json),true,exchanges,new WinlaTopupAdapter.Contract("WINLA-V5-CNY-20260819","CNY",2));}
        finally{Arrays.fill(key,'\0');}
    }
    @Bean TopupCoordinator topupCoordinator(TopupProviderPort port,JdbcTopupStore store,TopupEligibilityPort eligibility,BusinessEventStore events){return new TopupCoordinator(port,store,eligibility,5,events);}
    private static String value(Environment env,String key,String fallback){String v=env.getProperty(key);return v==null?fallback:v.trim();}
    private static String required(Environment env,String key){String v=value(env,key,"");if(v.isBlank()||v.startsWith("<SECRET:"))throw new IllegalStateException(key+"_REQUIRED");return v;}
    private static int integer(Environment env,String key,int fallback){try{return Integer.parseInt(value(env,key,Integer.toString(fallback)));}catch(Exception e){throw new IllegalStateException(key+"_INVALID");}}
}
