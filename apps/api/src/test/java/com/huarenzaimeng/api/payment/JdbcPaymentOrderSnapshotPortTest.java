package com.huarenzaimeng.api.payment;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

class JdbcPaymentOrderSnapshotPortTest {
 @Test void paymentUsesImmutableOrderSnapshotAfterBaseQuoteMutationAndExpiry(){
  JdbcTemplate jdbc=database(); JdbcPaymentOrderSnapshotPort port=new JdbcPaymentOrderSnapshotPort(jdbc);
  jdbc.update("INSERT INTO hz_quote VALUES('Q1',9999,'USD',?)", Timestamp.from(Instant.EPOCH));
  jdbc.update("INSERT INTO hz_order VALUES('O1','BUYER-1','Q1','UNPAID')");
  jdbc.update("INSERT INTO buyer_identity VALUES('B1','BUYER-1','ACTIVE')");
  jdbc.update("INSERT INTO buyer_wechat_payment_identity VALUES('B1','wx-app','openid-1')");
  jdbc.update("INSERT INTO hz_release_order_snapshot VALUES('O1','Q1','BUYER-1','PRICE-1',1234,'CNY',?,?)","a".repeat(64),"b".repeat(64));
  var frozen=port.requirePayable("O1","BUYER-1");
  assertThat(frozen.amountMinor()).isEqualTo(1234);assertThat(frozen.currency()).isEqualTo("CNY");assertThat(frozen.priceSnapshotDigest()).isEqualTo("a".repeat(64));
  jdbc.update("UPDATE hz_quote SET total_amount_minor=1,total_currency='BDT',expires_at=? WHERE quote_ref='Q1'",Timestamp.from(Instant.EPOCH));
  assertThat(port.requirePayable("O1","BUYER-1")).isEqualTo(frozen);
  assertThatThrownBy(()->port.requirePayable("O1","BUYER-2")).isInstanceOf(WeChatPayCoordinator.Conflict.class).hasMessage("PAYMENT_ORDER_NOT_PAYABLE");
 }
 private JdbcTemplate database(){var ds=new DriverManagerDataSource("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1","sa","");var jdbc=new JdbcTemplate(ds);jdbc.execute("CREATE TABLE hz_quote(quote_ref VARCHAR PRIMARY KEY,total_amount_minor BIGINT,total_currency CHAR(3),expires_at TIMESTAMP)");jdbc.execute("CREATE TABLE hz_order(order_ref VARCHAR PRIMARY KEY,project_subject_ref VARCHAR,quote_ref VARCHAR,payment_state VARCHAR)");jdbc.execute("CREATE TABLE hz_release_order_snapshot(order_ref VARCHAR PRIMARY KEY,quote_ref VARCHAR,project_subject_ref VARCHAR,price_version_ref VARCHAR,final_amount_minor BIGINT,currency CHAR(3),price_snapshot_digest CHAR(64),snapshot_digest CHAR(64))");jdbc.execute("CREATE TABLE buyer_identity(buyer_id VARCHAR PRIMARY KEY,subject_ref VARCHAR,status_code VARCHAR)");jdbc.execute("CREATE TABLE buyer_wechat_payment_identity(buyer_id VARCHAR PRIMARY KEY,app_id_ref VARCHAR,openid_ref VARCHAR)");return jdbc;}
}
