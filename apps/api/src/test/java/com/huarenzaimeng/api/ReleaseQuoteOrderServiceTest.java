package com.huarenzaimeng.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.*;

class ReleaseQuoteOrderServiceTest {
    private static final String PHONE_HMAC_SECRET="test-only-phone-digest-secret-32-bytes";
    private static final Instant MARKET_START=Instant.parse("2000-01-01T00:00:00Z");
    private static final Instant MARKET_END=Instant.parse("2100-01-01T00:00:00Z");
    private final Instant now=Instant.parse("2026-08-18T00:00:00Z");
    private JdbcTemplate jdbc; private ReleaseQuoteOrderService service;

    @BeforeEach void setup(){
        var ds=new DriverManagerDataSource("jdbc:h2:mem:"+ UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1","sa","");
        jdbc=new JdbcTemplate(ds); TransactionTemplate tx=new TransactionTemplate(new DataSourceTransactionManager(ds));
        ddl(); service=new ReleaseQuoteOrderService(jdbc,tx,new ObjectMapper(),Clock.fixed(now, ZoneOffset.UTC),PHONE_HMAC_SECRET); seed();
    }

    @Test void quoteUsesTrustedRowsAndReplaysOnlySameDigest(){
        var one=service.createQuote("BUYER-1","IDEM-Q","REQ-Q","01712345678","PRODUCT-1");
        var replay=service.createQuote("BUYER-1","IDEM-Q","REQ-Q","+8801712345678","PRODUCT-1");
        assertThat(replay.quoteRef()).isEqualTo(one.quoteRef());
        assertThat(one.phoneMasked()).doesNotContain("12345678");
        assertThat(one.phoneDigest()).hasSize(64); assertThat(one.finalAmountCny()).isEqualByComparingTo("12.34");
        assertThatThrownBy(()->service.createQuote("BUYER-1","IDEM-Q","REQ-Q","01812345678","PRODUCT-1"))
                .isInstanceOf(FlowRejectedException.class).hasMessage("QUOTE_IDEMPOTENCY_CONFLICT");
    }

    @Test void quoteUsesLatestEffectiveCatalogWhenPreviousCatalogIsStillValid(){
        jdbc.update("INSERT INTO hz_product_catalog VALUES(2,1,'CAT-2','APP','ACTIVE',?,?,?)",
                Timestamp.from(MARKET_START),Timestamp.from(MARKET_END),Timestamp.from(now));
        var quote=service.createQuote("BUYER-1","IDEM-Q2","REQ-Q2","01712345678","PRODUCT-1");
        assertThat(quote.catalogVersion()).isEqualTo(2);
    }

    @Test void quoteAllowsNullableSupplierSourceReference(){
        jdbc.update("UPDATE hz_price_version SET supplier_source_ref=NULL WHERE price_version_ref='PRICE-1'");
        var quote=service.createQuote("BUYER-1","IDEM-NULL-SOURCE","REQ-NULL-SOURCE","01712345678","PRODUCT-1");
        assertThat(quote.priceVersionRef()).isEqualTo("PRICE-1");
    }

    @Test void quoteUsesTheSameLatestEffectivePriceAsThePublicCatalog(){
        Instant newer=now.minusSeconds(60);
        jdbc.update("INSERT INTO hz_price_version VALUES('PRICE-2','PRODUCT-1',13.45,80,'BDT','BATCH/SKU-1','FIXTURE','FX-2','BDT_CNY',0.06,?,?,0,0,0,0,0,'HALF_UP','PLATFORM','GLOBAL','ACTIVE',?,?,'SUPER_ADMIN',2,?)",
                Timestamp.from(newer),Timestamp.from(MARKET_END),Timestamp.from(newer),Timestamp.from(MARKET_END),Timestamp.from(newer));
        var publicProducts=new V1DevelopmentDataService(jdbc,Clock.fixed(now,ZoneOffset.UTC)).products("GP","BALANCE");
        var quote=service.createQuote("BUYER-1","IDEM-PRICE-2","REQ-PRICE-2","01712345678","PRODUCT-1");
        assertThat(publicProducts).singleElement().satisfies(row->assertThat(row).containsValue("PRICE-2"));
        assertThat(quote.priceVersionRef()).isEqualTo("PRICE-2");
        assertThat(quote.finalAmountCny()).isEqualByComparingTo("13.45");
    }

    @Test void quoteUsesSameLatestSupportSetAndCatalogAsPublicCatalog(){
        jdbc.update("INSERT INTO hz_operator_support_batch VALUES(2,'OB-2','APP','ACTIVE',1,?,?,NULL,?)",
                Timestamp.from(MARKET_START),Timestamp.from(MARKET_END),Timestamp.from(now));
        jdbc.update("INSERT INTO hz_operator_membership VALUES(2,'GP','SUPPORTED','E-2',?)",Timestamp.from(now));
        jdbc.update("INSERT INTO hz_product_catalog VALUES(2,2,'CAT-2','APP','ACTIVE',?,?,?)",
                Timestamp.from(MARKET_START),Timestamp.from(MARKET_END),Timestamp.from(now));
        var publicCatalog=new V1DevelopmentDataService(jdbc,Clock.fixed(now,ZoneOffset.UTC)).catalog("GP",null);
        var quote=service.createQuote("BUYER-1","IDEM-Q3","REQ-Q3","01712345678","PRODUCT-1");
        assertThat(publicCatalog).containsEntry("supportedOperatorSetVersion",2L).containsEntry("catalogVersion",2L);
        assertThat(jdbc.queryForObject("SELECT supported_operator_set_version FROM hz_release_quote_snapshot WHERE quote_ref=?",Long.class,quote.quoteRef())).isEqualTo(2);
        assertThat(quote.catalogVersion()).isEqualTo(2);
    }

    @Test void orderBindsSubjectRejectsExpiryAndVersionDrift(){
        var quote=service.createQuote("BUYER-1","IDEM-Q","REQ-Q","01712345678","PRODUCT-1");
        assertThatThrownBy(()->service.createOrder("BUYER-2","IDEM-O","REQ-O",quote.quoteRef()))
                .isInstanceOf(FlowRejectedException.class).hasMessage("QUOTE_NOT_AVAILABLE");
        jdbc.update("UPDATE hz_platform_product SET aggregate_version=2 WHERE platform_product_ref='PRODUCT-1'");
        assertThatThrownBy(()->service.createOrder("BUYER-1","IDEM-O","REQ-O",quote.quoteRef()))
                .isInstanceOf(FlowRejectedException.class).hasMessage("QUOTE_VERSION_DRIFT");
        jdbc.update("UPDATE hz_platform_product SET aggregate_version=1");
        jdbc.update("UPDATE hz_release_quote_snapshot SET valid_until=?",Timestamp.from(now.minusSeconds(1)));
        assertThatThrownBy(()->service.createOrder("BUYER-1","IDEM-O2","REQ-O2",quote.quoteRef()))
                .isInstanceOf(FlowRejectedException.class).hasMessage("QUOTE_EXPIRED");
    }

    @Test void anonymousQuoteCanCreateOnlyAnOrderForTheSameAnonymousSubject(){
        var quote=service.createQuote("ANON-SUBJECT-1","ANON-Q-IDEMP","ANON-Q-REQ","01712345678","PRODUCT-1");
        var order=service.createOrder("ANON-SUBJECT-1","ANON-O-IDEMP","ANON-O-REQ",quote.quoteRef());
        assertThat(order.quoteRef()).isEqualTo(quote.quoteRef());
        assertThatThrownBy(()->service.createOrder("ANON-SUBJECT-2","ANON-O-IDEMP-2","ANON-O-REQ-2",quote.quoteRef()))
                .isInstanceOf(FlowRejectedException.class).hasMessage("QUOTE_NOT_AVAILABLE");
        assertThat(jdbc.queryForObject("SELECT project_subject_ref FROM hz_release_order_snapshot WHERE order_ref=?",String.class,order.orderRef())).isEqualTo("ANON-SUBJECT-1");
    }

    @Test void concurrentSameOrderKeyProducesOneImmutableOrder() throws Exception {
        var quote=service.createQuote("BUYER-1","IDEM-Q","REQ-Q","01712345678","PRODUCT-1");
        var pool=Executors.newFixedThreadPool(2);
        try{
            Callable<String> call=()->service.createOrder("BUYER-1","IDEM-O","REQ-O",quote.quoteRef()).orderRef();
            var a=pool.submit(call);var b=pool.submit(call);
            assertThat(a.get()).isEqualTo(b.get());
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM hz_release_order_snapshot",Integer.class)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT recipient_plain FROM hz_order_recipient_fulfillment",String.class)).isEqualTo("+8801712345678");
            assertThat(jdbc.queryForObject("SELECT recipient FROM hz_order_fulfillment_snapshot",String.class)).contains("****");
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM hz_quote_recipient_pending",Integer.class)).isEqualTo(1);
        }finally{pool.shutdownNow();}
    }

    @Test void orderRejectsRevokedOperatorExpiredCatalogAndCatalogCutover(){
        var quote=service.createQuote("BUYER-1","Q1","R1","01712345678","PRODUCT-1");
        jdbc.update("UPDATE hz_operator_support_batch SET batch_state='REVOKED' WHERE supported_operator_set_version=1");
        assertThatThrownBy(()->service.createOrder("BUYER-1","O1","OR1",quote.quoteRef())).isInstanceOf(FlowRejectedException.class).hasMessage("QUOTE_VERSION_DRIFT");
        jdbc.update("UPDATE hz_operator_support_batch SET batch_state='ACTIVE' WHERE supported_operator_set_version=1");
        jdbc.update("UPDATE hz_product_catalog SET expires_at=? WHERE catalog_version=1",Timestamp.from(now.minusSeconds(1)));
        assertThatThrownBy(()->service.createOrder("BUYER-1","O2","OR2",quote.quoteRef())).isInstanceOf(FlowRejectedException.class).hasMessage("QUOTE_VERSION_DRIFT");
        jdbc.update("UPDATE hz_product_catalog SET expires_at=? WHERE catalog_version=1",Timestamp.from(MARKET_END));
        jdbc.update("INSERT INTO hz_product_catalog VALUES(2,1,'CAT-2','APP','ACTIVE',?,?,?)",Timestamp.from(MARKET_START),Timestamp.from(MARKET_END),Timestamp.from(now));
        assertThatThrownBy(()->service.createOrder("BUYER-1","O3","OR3",quote.quoteRef())).isInstanceOf(FlowRejectedException.class).hasMessage("QUOTE_VERSION_DRIFT");
    }

    @Test void phoneDigestIsSecretKeyedAndMissingSecretFailsClosed(){
        var quote=service.createQuote("BUYER-1","Q1","R1","01712345678","PRODUCT-1");
        assertThat(quote.phoneDigest()).isNotEqualTo(sha256ForAssertion("BD_PHONE", "+8801712345678"));
        var publicJson=new ObjectMapper().findAndRegisterModules().valueToTree(quote);
        assertThat(publicJson.has("phoneDigest")).isFalse();
        assertThat(publicJson.has("requestDigest")).isFalse();
        assertThatThrownBy(()->new ReleaseQuoteOrderService(jdbc,new TransactionTemplate(),new ObjectMapper(),Clock.fixed(now,ZoneOffset.UTC),""))
                .isInstanceOf(IllegalStateException.class).hasMessage("PHONE_DIGEST_HMAC_SECRET_REQUIRED");
    }

    @Test void bangladeshPhoneNormalizationFailsClosed(){
        assertThat(ReleaseQuoteOrderService.normalizeBangladeshPhone("017 1234 5678")).isEqualTo("+8801712345678");
        assertThat(ReleaseQuoteOrderService.normalizeBangladeshPhone("008801300000000")).isEqualTo("+8801300000000");
        assertThatThrownBy(()->ReleaseQuoteOrderService.normalizeBangladeshPhone("123"))
                .isInstanceOf(FlowRejectedException.class).hasMessage("BANGLADESH_PHONE_INVALID");
    }

    private void seed(){Timestamp start=Timestamp.from(MARKET_START),end=Timestamp.from(MARKET_END);
        jdbc.update("INSERT INTO hz_provider_channel VALUES('CH','WINLA','Winla',1,'ENABLED',1,?)",start);
        jdbc.update("INSERT INTO hz_platform_product VALUES('PRODUCT-1','BD','GP','BALANCE','৳100','Balance',100,NULL,NULL,NULL,NULL,'WINLA','SKU-1','BATCH','raw','raw',80,'BDT','AVAILABLE',?,'BALANCE','GP','MAPPED',NULL,1,NULL,?,?,'ENABLED','FIXTURE',1,?)",start,start,end,start);
        jdbc.update("INSERT INTO hz_price_version VALUES('PRICE-1','PRODUCT-1',12.34,80,'BDT','BATCH/SKU-1','FIXTURE','FX-1','BDT_CNY',0.06,?,?,0,0,0,0,0,'HALF_UP','PLATFORM','GLOBAL','ACTIVE',?,?,'SUPER_ADMIN',1,?)",start,end,start,end,start);
        jdbc.update("INSERT INTO hz_operator_support_batch VALUES(1,'OB','APP','ACTIVE',1,?,?,NULL,?)",start,end,start);
        jdbc.update("INSERT INTO hz_operator_membership VALUES(1,'GP','SUPPORTED','E',?)",start);
        jdbc.update("INSERT INTO hz_product_catalog VALUES(1,1,'CAT','APP','ACTIVE',?,?,?)",start,end,start);
    }

    private void ddl(){
        jdbc.execute("CREATE TABLE hz_provider_channel(channel_ref VARCHAR(64),provider_code VARCHAR(32),display_name VARCHAR(120),channel_priority INT,channel_state VARCHAR(24),aggregate_version BIGINT,updated_at TIMESTAMP)");
        jdbc.execute("CREATE TABLE hz_platform_product(platform_product_ref VARCHAR(64) PRIMARY KEY,country_code CHAR(2),operator_code VARCHAR(64),product_type VARCHAR(24),display_name VARCHAR(160),benefit_text VARCHAR(500),denomination_bdt DECIMAL(19,2),data_allowance_mb BIGINT,voice_minutes INT,sms_count INT,validity_text VARCHAR(96),provider_code VARCHAR(32),provider_sku VARCHAR(128),catalog_batch_ref VARCHAR(64),raw_sku_name VARCHAR(255),raw_benefit_text VARCHAR(500),supplier_cost DECIMAL(19,4),settlement_currency CHAR(3),supplier_availability VARCHAR(24),catalog_synced_at TIMESTAMP,normalized_type VARCHAR(24),normalized_operator VARCHAR(32),mapping_state VARCHAR(24),mapping_failure_reason VARCHAR(500),channel_priority INT,phone_rule VARCHAR(160),sale_start_at TIMESTAMP,sale_end_at TIMESTAMP,enable_state VARCHAR(24),source_mode VARCHAR(32),aggregate_version BIGINT,updated_at TIMESTAMP)");
        jdbc.execute("CREATE TABLE hz_price_version(price_version_ref VARCHAR(64) PRIMARY KEY,platform_product_ref VARCHAR(64),final_amount_cny DECIMAL(19,2),supplier_cost DECIMAL(19,4),settlement_currency CHAR(3),supplier_source_ref VARCHAR(160),fx_source VARCHAR(32),fx_snapshot_ref VARCHAR(96),fx_direction VARCHAR(16),fx_rate DECIMAL(19,8),fx_updated_at TIMESTAMP,fx_valid_until TIMESTAMP,buffer_rate DECIMAL(9,6),markup_rate DECIMAL(9,6),wechat_fee_rate DECIMAL(9,6),tax_rate DECIMAL(9,6),minimum_margin_rate DECIMAL(9,6),rounding_rule VARCHAR(32),promotion_bearer VARCHAR(32),pricing_scope VARCHAR(64),price_state VARCHAR(24),effective_from TIMESTAMP,effective_until TIMESTAMP,enabled_by VARCHAR(96),aggregate_version BIGINT,updated_at TIMESTAMP)");
        jdbc.execute("CREATE TABLE hz_operator_support_batch(supported_operator_set_version BIGINT PRIMARY KEY,batch_ref VARCHAR(128),approval_ref VARCHAR(160),batch_state VARCHAR(24),qualification_known BOOLEAN,effective_from TIMESTAMP,expires_at TIMESTAMP,rollback_version BIGINT,created_at TIMESTAMP)");
        jdbc.execute("CREATE TABLE hz_operator_membership(supported_operator_set_version BIGINT,operator_code VARCHAR(64),membership_state VARCHAR(24),evidence_ref VARCHAR(160),created_at TIMESTAMP)");
        jdbc.execute("CREATE TABLE hz_product_catalog(catalog_version BIGINT PRIMARY KEY,supported_operator_set_version BIGINT,catalog_ref VARCHAR(128),approval_ref VARCHAR(160),catalog_state VARCHAR(24),effective_from TIMESTAMP,expires_at TIMESTAMP,created_at TIMESTAMP)");
        jdbc.execute("CREATE TABLE hz_quote(quote_ref VARCHAR(64) PRIMARY KEY,project_subject_ref VARCHAR(128),phone_masked VARCHAR(32),operator_code VARCHAR(64),product_code VARCHAR(64),denomination_ref VARCHAR(128),supported_operator_set_version BIGINT,catalog_version BIGINT,mnp_state VARCHAR(32),total_amount DECIMAL(19,4),total_amount_minor BIGINT,total_currency CHAR(3),price_snapshot JSON,expires_at TIMESTAMP,created_at TIMESTAMP)");
        jdbc.execute("CREATE TABLE hz_order(order_ref VARCHAR(64) PRIMARY KEY,project_subject_ref VARCHAR(128),quote_ref VARCHAR(64) UNIQUE,order_state VARCHAR(32),payment_state VARCHAR(32),upstream_debit_state VARCHAR(32),delivery_state VARCHAR(32),refund_state VARCHAR(32),projection_version BIGINT,aggregate_version BIGINT,allowed_action VARCHAR(64),environment VARCHAR(32),evidence_level VARCHAR(8),authority_state VARCHAR(24),created_at TIMESTAMP,updated_at TIMESTAMP)");
        jdbc.execute("CREATE TABLE hz_release_quote_snapshot(quote_ref VARCHAR(64) PRIMARY KEY,project_subject_ref VARCHAR(128),request_ref VARCHAR(128),idempotency_key VARCHAR(128),request_digest CHAR(64),phone_digest CHAR(64),phone_masked VARCHAR(32),operator_code VARCHAR(64),platform_product_ref VARCHAR(64),product_aggregate_version BIGINT,entitlement_snapshot JSON,price_version_ref VARCHAR(64),price_aggregate_version BIGINT,price_snapshot JSON,final_amount_minor BIGINT,currency CHAR(3),supported_operator_set_version BIGINT,catalog_version BIGINT,valid_until TIMESTAMP,snapshot_digest CHAR(64),created_at TIMESTAMP,UNIQUE(project_subject_ref,idempotency_key),UNIQUE(project_subject_ref,request_ref))");
        jdbc.execute("CREATE TABLE hz_release_order_snapshot(order_ref VARCHAR(64) PRIMARY KEY,quote_ref VARCHAR(64) UNIQUE,project_subject_ref VARCHAR(128),request_ref VARCHAR(128),idempotency_key VARCHAR(128),request_digest CHAR(64),quote_snapshot JSON,entitlement_snapshot JSON,price_snapshot JSON,price_version_ref VARCHAR(64),final_amount_minor BIGINT,currency CHAR(3),price_snapshot_digest CHAR(64),quote_snapshot_digest CHAR(64),snapshot_digest CHAR(64),created_at TIMESTAMP,UNIQUE(project_subject_ref,idempotency_key),UNIQUE(project_subject_ref,request_ref))");
        jdbc.execute("CREATE TABLE hz_quote_recipient_pending(quote_ref VARCHAR(64) PRIMARY KEY,recipient_plain VARCHAR(32),recipient_digest CHAR(64),recipient_masked VARCHAR(32),expires_at TIMESTAMP,created_at TIMESTAMP)");
        jdbc.execute("CREATE TABLE hz_order_recipient_fulfillment(order_ref VARCHAR(64) PRIMARY KEY,recipient_plain VARCHAR(32),recipient_digest CHAR(64),recipient_masked VARCHAR(32),retention_state VARCHAR(24),terminal_at TIMESTAMP,retention_until TIMESTAMP,created_at TIMESTAMP,updated_at TIMESTAMP)");
        jdbc.execute("CREATE TABLE hz_order_fulfillment_snapshot(merchant_order_ref VARCHAR(64) PRIMARY KEY,buyer_subject_ref VARCHAR(128),provider_sku VARCHAR(128),recipient VARCHAR(32),face_value_minor BIGINT,target_currency CHAR(3),entitlement_digest CHAR(64),entitlement_state VARCHAR(24),created_at TIMESTAMP)");
    }
    private static String sha256ForAssertion(String... parts){try{var md=java.security.MessageDigest.getInstance("SHA-256");for(String part:parts){md.update(part.getBytes(java.nio.charset.StandardCharsets.UTF_8));md.update((byte)0);}return java.util.HexFormat.of().formatHex(md.digest());}catch(Exception e){throw new AssertionError(e);}}
}
