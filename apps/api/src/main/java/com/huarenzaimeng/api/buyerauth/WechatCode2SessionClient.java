package com.huarenzaimeng.api.buyerauth;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** Production networking is intentionally absent until the real-enablement P0 gates close. */
@Component
@Profile("release-mysql")
final class WechatCode2SessionClient implements WechatCode2SessionPort {
    @Override public Result exchange(Command command) {
        return new Unknown("REAL_PROVIDER_ADAPTER_DISABLED");
    }
}
