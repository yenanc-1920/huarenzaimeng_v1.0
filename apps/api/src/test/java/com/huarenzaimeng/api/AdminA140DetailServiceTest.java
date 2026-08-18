package com.huarenzaimeng.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

class AdminA140DetailServiceTest {
    private JdbcTemplate jdbc;
    private AdminA140DetailService service;

    @BeforeEach void setup() {
        JdbcDataSource ds=new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:a140_"+System.nanoTime()+";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        jdbc=new JdbcTemplate(ds);
        jdbc.execute("create table hz_order(order_ref varchar(64) primary key,order_state varchar(32),updated_at timestamp)");
        jdbc.execute("create table hz_release_quote_snapshot(quote_ref varchar(64) primary key,phone_masked varchar(32),operator_code varchar(64),platform_product_ref varchar(64))");
        jdbc.execute("create table hz_release_order_snapshot(order_ref varchar(64) primary key,quote_ref varchar(64),final_amount_minor bigint,currency varchar(3),price_version_ref varchar(64),entitlement_snapshot json,created_at timestamp)");
        jdbc.execute("create table hz_payment_coordination(merchant_order_ref varchar(64),state_code varchar(32),provider_ref varchar(128),amount_minor bigint,currency varchar(3),refunded_minor bigint,aggregate_version bigint,updated_at timestamp)");
        jdbc.execute("create table hz_topup_coordination(merchant_order_ref varchar(64),state_code varchar(32),provider_ref varchar(128),aggregate_version bigint,updated_at timestamp)");
        jdbc.execute("create table hz_payment_refund(refund_ref varchar(64),merchant_order_ref varchar(64),state_code varchar(32),amount_minor bigint,created_at timestamp,updated_at timestamp)");
        jdbc.execute("create table hz_business_event_link(event_ref varchar(64),merchant_order_ref varchar(64),event_type varchar(40),occurred_at timestamp,observed_at timestamp)");
        jdbc.execute("create table hz_business_event_outbox(event_ref varchar(64),merchant_order_ref varchar(64),event_type varchar(40),occurred_at timestamp,dispatched_at timestamp)");
        jdbc.execute("create table hz_customer_case(case_ref varchar(64),related_order_ref varchar(64))");
        jdbc.execute("create table hz_customer_case_subject_binding(case_ref varchar(64),merchant_order_ref varchar(64))");
        jdbc.execute("create table hz_reconciliation_case(reconciliation_ref varchar(64),order_ref varchar(64))");
        service=new AdminA140DetailService(jdbc,new ObjectMapper());
    }

    @Test void returnsFourFactsRefundTimelineAndOnlyMaskedPhone() {
        jdbc.update("insert into hz_order values('O-1','PAID',current_timestamp)");
        jdbc.update("insert into hz_release_quote_snapshot values('Q-1','017****5678','GP','P-1')");
        jdbc.update("insert into hz_release_order_snapshot values('O-1','Q-1',680,'CNY','PV-1','{\"productRef\":\"P-1\",\"productType\":\"AIRTIME\",\"displayName\":\"100 BDT\",\"benefitText\":\"话费到账\",\"denominationBdt\":100,\"validityText\":null,\"providerCode\":\"SECRET_PROVIDER\",\"providerSku\":\"SECRET_SKU\"}',current_timestamp)");
        jdbc.update("insert into hz_payment_coordination values('O-1','PAID','WX-1',680,'CNY',100,3,current_timestamp)");
        jdbc.update("insert into hz_topup_coordination values('O-1','DELIVERED','WIN-1',4,current_timestamp)");
        jdbc.update("insert into hz_payment_refund values('R-1','O-1','SUCCEEDED',100,current_timestamp,current_timestamp)");
        jdbc.update("insert into hz_business_event_outbox values('E-1','O-1','PAYMENT_CONFIRMED',current_timestamp,null)");
        jdbc.update("insert into hz_business_event_link values('E-1','O-1','PAYMENT_CONFIRMED',current_timestamp,current_timestamp)");
        jdbc.update("insert into hz_reconciliation_case values('REC-1','O-1')");
        jdbc.update("insert into hz_customer_case values('CASE-1','O-1')");

        var detail=service.read("O-1","FIN").orElseThrow();
        assertThat(detail.schemaVersion()).isEqualTo("ADMIN_READ_V1");
        assertThat(detail.order().phoneMasked()).isEqualTo("017****5678");
        assertThat(detail.order().entitlement().productType()).isEqualTo("AIRTIME");
        assertThat(detail.order().entitlement().displayName()).isEqualTo("100 BDT");
        assertThat(detail.payment().state()).isEqualTo("PAID");
        assertThat(detail.topup().state()).isEqualTo("DELIVERED");
        assertThat(detail.refunds()).extracting(AdminA140DetailService.RefundFact::refundRef).containsExactly("R-1");
        assertThat(detail.timeline()).hasSize(1);
        assertThat(detail.timeline().get(0).source()).isEqualTo("PROJECTED");
        assertThat(detail.reconciliationRefs()).containsExactly("REC-1");
        assertThat(detail.customerCaseRefs()).containsExactly("CASE-1");
        assertThat(detail.toString()).doesNotContain("01712345678","request_digest","buyer_subject_ref","SECRET_PROVIDER","SECRET_SKU","providerCode","providerSku");
    }

    @Test void missingCoordinationIsTypedUnknownAndMissingOrderIsAbsent() {
        jdbc.update("insert into hz_order values('O-2','CREATED',current_timestamp)");
        jdbc.update("insert into hz_release_quote_snapshot values('Q-2','018****0000','ROBI','P-2')");
        jdbc.update("insert into hz_release_order_snapshot values('O-2','Q-2',500,'CNY','PV-2','{\"productRef\":\"P-2\",\"productType\":\"DATA\",\"displayName\":\"1GB\",\"benefitText\":\"流量包\"}',current_timestamp)");
        var detail=service.read("O-2","CS").orElseThrow();
        assertThat(detail.payment().state()).isEqualTo("UNKNOWN");
        assertThat(detail.payment().providerRef()).isNull();
        assertThat(detail.topup().state()).isEqualTo("UNKNOWN");
        assertThat(service.read("MISSING","SUPER_ADMIN")).isEmpty();
    }

    @Test void malformedOrWronglyTypedEntitlementFailsClosedToNull() {
        jdbc.update("insert into hz_order values('O-3','CREATED',current_timestamp)");
        jdbc.update("insert into hz_release_quote_snapshot values('Q-3','019****0000','BL','P-3')");
        jdbc.update("insert into hz_release_order_snapshot values('O-3','Q-3',700,'CNY','PV-3','{\"productRef\":\"P-3\",\"productType\":\"DATA\",\"displayName\":{},\"benefitText\":\"流量包\",\"providerSku\":\"MUST_NOT_ESCAPE\"}',current_timestamp)");
        var detail=service.read("O-3","CS").orElseThrow();
        assertThat(detail.order().entitlement()).isNull();
        assertThat(detail.toString()).doesNotContain("MUST_NOT_ESCAPE","providerSku");
    }
}
