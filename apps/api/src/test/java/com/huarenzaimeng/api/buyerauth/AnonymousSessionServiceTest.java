package com.huarenzaimeng.api.buyerauth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.*;

class AnonymousSessionServiceTest {
    private static final String KEY="test-only-anonymous-session-key-32-bytes";
    private final Instant now=Instant.parse("2026-08-22T00:00:00Z");
    private JdbcTemplate jdbc;
    private AnonymousSessionService service;

    @BeforeEach void setup(){
        var ds=new DriverManagerDataSource("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1","sa","");
        jdbc=new JdbcTemplate(ds);
        jdbc.execute("CREATE TABLE buyer_anonymous_session(anonymous_session_ref VARCHAR(64) PRIMARY KEY,anonymous_subject_ref VARCHAR(64) UNIQUE,request_ref VARCHAR(128) UNIQUE,request_digest CHAR(64),guest_ref_digest CHAR(64),token_digest CHAR(64) UNIQUE,status_code VARCHAR(24),user_agreement_version VARCHAR(64),privacy_policy_version VARCHAR(64),issued_at TIMESTAMP,absolute_expires_at TIMESTAMP,created_at TIMESTAMP)");
        service=new AnonymousSessionService(jdbc,new TransactionTemplate(new DataSourceTransactionManager(ds)),Clock.fixed(now,ZoneOffset.UTC),KEY,"UA-V1","PP-V1");
    }

    @Test void createsTwentyFourHourDigestOnlySessionAndExactlyReplaysSameRequest(){
        var command=valid("ANON-REQUEST-001","GUEST-000001");
        var first=service.establish(command);var replay=service.establish(command);
        assertThat(replay).isEqualTo(first);
        assertThat(first.absoluteExpiresAt()).isEqualTo(now.plusSeconds(24*60*60));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM buyer_anonymous_session",Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT token_digest FROM buyer_anonymous_session",String.class)).hasSize(64).isNotEqualTo(first.token());
        assertThat(jdbc.queryForObject("SELECT guest_ref_digest FROM buyer_anonymous_session",String.class)).hasSize(64).isNotEqualTo("GUEST-000001");
        assertThat(service.authenticate(first.token())).contains(new AnonymousTransactionPrincipal(first.subjectRef(),jdbc.queryForObject("SELECT anonymous_session_ref FROM buyer_anonymous_session",String.class)));
    }

    @Test void sameRequestWithDifferentGuestOrConsentConflictsWithoutNewSession(){
        service.establish(valid("ANON-REQUEST-001","GUEST-000001"));
        assertThatThrownBy(()->service.establish(valid("ANON-REQUEST-001","GUEST-000002")))
                .isInstanceOf(AnonymousSessionService.Rejected.class).hasMessage("ANONYMOUS_SESSION_IDEMPOTENCY_CONFLICT");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM buyer_anonymous_session",Integer.class)).isEqualTo(1);
    }

    @Test void concurrentExactReplayCreatesOneSessionAndReturnsOneToken() throws Exception {
        var pool=Executors.newFixedThreadPool(2);
        try{
            Callable<AnonymousSessionService.SessionResult> call=()->service.establish(valid("ANON-REQUEST-002","GUEST-000002"));
            var left=pool.submit(call);var right=pool.submit(call);
            assertThat(left.get()).isEqualTo(right.get());
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM buyer_anonymous_session",Integer.class)).isEqualTo(1);
        }finally{pool.shutdownNow();}
    }

    @Test void guestRefAloneCannotAuthenticateAndExpiredOrStaleConsentFailsClosed(){
        var created=service.establish(valid("ANON-REQUEST-001","GUEST-000001"));
        assertThat(service.authenticate("GUEST-000001")).isEmpty();
        jdbc.update("UPDATE buyer_anonymous_session SET absolute_expires_at=?",java.sql.Timestamp.from(now));
        assertThat(service.authenticate(created.token())).isEmpty();
        jdbc.update("UPDATE buyer_anonymous_session SET absolute_expires_at=?,user_agreement_version='OLD'",java.sql.Timestamp.from(now.plusSeconds(60)));
        assertThat(service.authenticate(created.token())).isEmpty();
    }

    @Test void missingKeyAndWrongConsentFailBeforeWrite(){
        var disabled=new AnonymousSessionService(jdbc,new TransactionTemplate(),Clock.fixed(now,ZoneOffset.UTC),"","UA-V1","PP-V1");
        assertThatThrownBy(()->disabled.establish(valid("ANON-REQUEST-001","GUEST-000001"))).isInstanceOf(AnonymousSessionService.Rejected.class).hasMessage("ANONYMOUS_SESSION_CONFIGURATION_UNAVAILABLE");
        assertThatThrownBy(()->service.establish(new AnonymousSessionService.Command("ANON-REQUEST-001","GUEST-000001","OLD","PP-V1",true,true))).isInstanceOf(AnonymousSessionService.Rejected.class).hasMessage("ANONYMOUS_SESSION_REQUEST_INVALID");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM buyer_anonymous_session",Integer.class)).isZero();
    }

    private static AnonymousSessionService.Command valid(String request,String guest){return new AnonymousSessionService.Command(request,guest,"UA-V1","PP-V1",true,true);}
}
