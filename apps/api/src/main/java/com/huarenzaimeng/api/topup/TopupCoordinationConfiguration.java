package com.huarenzaimeng.api.topup;
import com.huarenzaimeng.api.BusinessEventStore;
import org.springframework.context.annotation.*;
@Configuration(proxyBeanMethods=false) @Profile("release-mysql")
class TopupCoordinationConfiguration {
    @Bean TopupProviderPort topupProviderPort(){return WinlaTopupAdapter.disabled();}
    @Bean TopupCoordinator topupCoordinator(TopupProviderPort port,JdbcTopupStore store,TopupEligibilityPort eligibility,BusinessEventStore events){return new TopupCoordinator(port,store,eligibility,5,events);}
}
