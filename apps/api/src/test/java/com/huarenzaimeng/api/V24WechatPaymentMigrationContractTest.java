package com.huarenzaimeng.api;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.assertj.core.api.Assertions.assertThat;

class V24WechatPaymentMigrationContractTest {
    @Test void freezesPayerIdentityAndFivePrepayFieldsWithoutChangingEarlierMigrations() throws Exception {
        String sql=Files.readString(Path.of("src/main/resources/db/migration/V24__add_wechat_payment_identity_and_prepay.sql"));
        assertThat(sql).contains("buyer_wechat_payment_identity","openid_ref","prepay_timestamp","prepay_nonce","prepay_package","prepay_sign_type","prepay_pay_sign","prepay_expires_at");
        assertThat(sql.toLowerCase()).doesNotContain("drop table","drop database","flyway_schema_history");
    }
}
