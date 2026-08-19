package com.huarenzaimeng.api.buyerauth;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import java.security.SecureRandom;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;

class BuyerAuthServiceTest {
    private static final String ID="identity-pepper-synthetic-32-characters";
    private static final String CODE="code-pepper-synthetic-value-32-characters";
    private static final Instant NOW=Instant.parse("2026-08-10T12:00:00Z");

    @Test void releaseMysqlSpringContextSelectsProductionConstructorWithoutDatabaseOrSecret() {
        try (var context = new org.springframework.context.annotation.AnnotationConfigApplicationContext()) {
            context.getEnvironment().setActiveProfiles("release-mysql");
            context.registerBean(BuyerAuthStore.class, MemoryStore::new);
            context.registerBean(WechatCode2SessionPort.class,
                    () -> command -> new WechatCode2SessionPort.Unknown("LOCAL_CONTEXT_ONLY"));
            context.register(BuyerAuthService.class);

            context.refresh();

            assertThat(context.getBean(BuyerAuthService.class)).isNotNull();
            assertThat(context.getBean(BuyerAuthStore.class)).isInstanceOf(MemoryStore.class);
        }
    }

    @Test void releaseMysqlContextCreatesRealJdbcStoreWithTransactionalCglibAdvisor() {
        try (var context = new org.springframework.context.annotation.AnnotationConfigApplicationContext()) {
            context.getEnvironment().setActiveProfiles("release-mysql");
            context.register(TransactionProxyTestConfiguration.class, JdbcBuyerAuthStore.class);
            context.refresh();

            Object proxy = context.getBean(JdbcBuyerAuthStore.class);
            assertThat(org.springframework.aop.support.AopUtils.isCglibProxy(proxy)).isTrue();
            assertThat(org.springframework.aop.support.AopUtils.getTargetClass(proxy))
                    .isEqualTo(JdbcBuyerAuthStore.class);
            assertThat(proxy).isInstanceOf(BuyerAuthStore.class);
            assertThat(((org.springframework.aop.framework.Advised) proxy).getAdvisors())
                    .anySatisfy(advisor -> assertThat(advisor.getAdvice())
                            .isInstanceOf(org.springframework.transaction.interceptor.TransactionInterceptor.class));
            assertThat(org.mockito.Mockito.mockingDetails(
                    context.getBean(org.springframework.jdbc.core.JdbcTemplate.class)).getInvocations())
                    .allSatisfy(invocation -> assertThat(invocation.getMethod().getName())
                            .isNotIn("query", "queryForObject", "queryForList", "update", "execute", "batchUpdate"));
            org.mockito.Mockito.verifyNoInteractions(context.getBean(javax.sql.DataSource.class));
            org.mockito.Mockito.verifyNoInteractions(context.getBean(org.springframework.transaction.PlatformTransactionManager.class));
        }
    }

    @org.springframework.context.annotation.Configuration(proxyBeanMethods = false)
    @org.springframework.transaction.annotation.EnableTransactionManagement(proxyTargetClass = true)
    static class TransactionProxyTestConfiguration {
        @org.springframework.context.annotation.Bean
        javax.sql.DataSource dataSource() { return org.mockito.Mockito.mock(javax.sql.DataSource.class); }
        @org.springframework.context.annotation.Bean
        org.springframework.jdbc.core.JdbcTemplate jdbcTemplate() {
            return org.mockito.Mockito.mock(org.springframework.jdbc.core.JdbcTemplate.class);
        }
        @org.springframework.context.annotation.Bean
        org.springframework.transaction.PlatformTransactionManager transactionManager() {
            return org.mockito.Mockito.mock(org.springframework.transaction.PlatformTransactionManager.class);
        }
    }

    @Test void fakeSuccessCreatesOneSessionWithAbsoluteAndIdleExpiryAndNoRawProviderValues(){
        MemoryStore store=new MemoryStore();FakePort fake=new FakePort(new WechatCode2SessionPort.Success("APP_PRIMARY","provider_subject_synthetic","EVIDENCE-SYNTHETIC"));
        BuyerAuthService service=service(store,fake,true);
        var result=service.establish("one-time-code-synthetic","REQUEST-001");
        assertThat(result.absoluteExpiresAt()).isEqualTo(NOW.plus(Duration.ofHours(24)));
        assertThat(store.active.idleExpiresAt).isEqualTo(NOW.plus(Duration.ofHours(2)));
        assertThat(store.sessionWrites).isOne();assertThat(fake.calls).isOne();
        assertThat(store.persistedText()).doesNotContain("one-time-code-synthetic","provider_subject_synthetic");
    }

    @Test void disabledInvalidRejectedUnknownAndRepeatedCodeFailClosed(){
        MemoryStore disabledStore=new MemoryStore();FakePort success=new FakePort(new WechatCode2SessionPort.Success("APP_PRIMARY","provider_subject_synthetic","EVIDENCE-SYNTHETIC"));
        assertCode(service(disabledStore,success,false),"one-time-code-synthetic","BUYER_AUTH_DISABLED");
        assertThat(disabledStore.attemptWrites+disabledStore.sessionWrites+success.calls).isZero();
        MemoryStore rejectedStore=new MemoryStore();assertCode(service(rejectedStore,new FakePort(new WechatCode2SessionPort.Rejected("INVALID")),true),"one-time-code-rejected","WECHAT_LOGIN_REJECTED");
        MemoryStore unknownStore=new MemoryStore();FakePort unknown=new FakePort(new WechatCode2SessionPort.Unknown("WECHAT_PROVIDER_TIMEOUT"));assertCode(service(unknownStore,unknown,true),"one-time-code-unknown","WECHAT_PROVIDER_TIMEOUT");
        assertCode(service(unknownStore,unknown,true),"one-time-code-unknown","LOGIN_CODE_ALREADY_SUBMITTED");assertThat(unknown.calls).isOne();assertThat(unknownStore.sessionWrites).isZero();
    }

    @Test void concurrentSameCodeProducesOneAttemptOneProviderCallAndOneSession() throws Exception {
        MemoryStore store=new MemoryStore();FakePort fake=new FakePort(new WechatCode2SessionPort.Success("APP_PRIMARY","provider_subject_synthetic","EVIDENCE-SYNTHETIC"));BuyerAuthService service=service(store,fake,true);
        ExecutorService pool=Executors.newFixedThreadPool(2);CountDownLatch start=new CountDownLatch(1);
        Callable<String> call=()->{start.await();try{service.establish("concurrent-one-time-code","REQUEST-002");return"OK";}catch(BuyerAuthService.Rejected r){return r.projectCode;}};
        Future<String>a=pool.submit(call),b=pool.submit(call);start.countDown();assertThat(List.of(a.get(),b.get())).containsExactlyInAnyOrder("OK","LOGIN_CODE_ALREADY_SUBMITTED");pool.shutdownNow();
        assertThat(store.attemptWrites).isOne();assertThat(fake.calls).isOne();assertThat(store.sessionWrites).isOne();
    }

    @Test void successfulAuthorizationAdvancesOnlyIdleOnceAndHonorsAbsoluteExpiryAndLogout(){
        MemoryStore store=new MemoryStore();BuyerAuthService service=service(store,new FakePort(new WechatCode2SessionPort.Success("APP_PRIMARY","provider_subject_synthetic","EVIDENCE-SYNTHETIC")),true);
        var session=service.establish("one-time-code-authz","REQUEST-003");store.active=new SessionState("BUYER-SYN","SESSION-SYN",NOW.plus(Duration.ofHours(24)),NOW.plus(Duration.ofMinutes(30)));
        assertThat(service.authenticate(session.token())).contains(new BuyerAuthStore.BuyerPrincipal(BuyerAuthStore.Eligibility.ELIGIBLE,"BUYER-SYN","SESSION-SYN"));assertThat(store.idleAdvances).isOne();assertThat(store.active.idleExpiresAt).isEqualTo(NOW.plus(Duration.ofHours(2)));
        assertThat(service.logout(session.token())).isEqualTo(BuyerAuthStore.LogoutResult.SUCCEEDED);assertThat(store.revokes).isOne();assertThat(service.authenticate(session.token())).isEmpty();
        assertThat(store.lastSeenWrites).isZero();
    }

    @Test void failedUnknownAndBackgroundPathsNeverAdvanceIdle(){
        MemoryStore store=new MemoryStore();BuyerAuthService service=service(store,new FakePort(new WechatCode2SessionPort.Unknown("TIMEOUT")),true);
        assertCode(service,"one-time-code-no-idle","WECHAT_LOGIN_RESULT_UNKNOWN");assertThat(service.authenticate(null)).isEmpty();assertThat(store.idleAdvances).isZero();
    }

    @Test void disabledPreloadedSessionCannotReachStoreOrBuyerFilterProjection() throws Exception {
        MemoryStore store=new MemoryStore();store.active=new SessionState("BUYER-SYN","SESSION-SYN",NOW.plusSeconds(3600),NOW.plusSeconds(1800));
        BuyerAuthService service=service(store,new FakePort(new WechatCode2SessionPort.Unknown("UNUSED")),false);
        assertThat(service.authenticate("synthetic-old-token")).isEmpty();assertThat(store.authReads).isZero();
        BuyerSessionFilter filter=new BuyerSessionFilter(service);MockHttpServletRequest request=new MockHttpServletRequest("GET","/buyer-api/v1/orders");request.addHeader("Authorization","Bearer synthetic-old-token");MockHttpServletResponse response=new MockHttpServletResponse();int[] downstream={0};
        filter.doFilter(request,response,(a,b)->downstream[0]++);
        assertThat(response.getStatus()).isEqualTo(401);assertThat(request.getAttribute(BuyerSessionFilter.BUYER)).isNull();assertThat(downstream[0]).isZero();assertThat(store.authReads).isZero();
    }

    @Test void providerDelayUsesPostValidationSessionIssuedAtForAllSessionTimes(){
        MemoryStore store=new MemoryStore();MutableClock clock=new MutableClock(NOW);WechatCode2SessionPort delayed=command->{clock.now=clock.now.plus(Duration.ofMinutes(5));return new WechatCode2SessionPort.Success("APP_PRIMARY","provider_subject_synthetic","EVIDENCE-SYNTHETIC");};
        BuyerAuthService service=new BuyerAuthService(store,delayed,true,"APP_PRIMARY",ID,CODE,clock,new SecureRandom(new byte[]{4,5,6}));var result=service.establish("one-time-code-delayed","REQUEST-004");
        assertThat(store.issuedAt).isEqualTo(NOW.plus(Duration.ofMinutes(5)));assertThat(result.absoluteExpiresAt()).isEqualTo(store.issuedAt.plus(Duration.ofHours(24)));assertThat(store.active.idleExpiresAt).isEqualTo(store.issuedAt.plus(Duration.ofHours(2)));assertThat(store.audit.occurredAt()).isEqualTo(store.issuedAt);
    }

    @Test void logoutUnavailableAndStorageUnknownRemainDistinct(){
        MemoryStore store=new MemoryStore();BuyerAuthService service=service(store,new FakePort(new WechatCode2SessionPort.Unknown("UNUSED")),true);
        assertThat(service.logout("no-current-session")).isEqualTo(BuyerAuthStore.LogoutResult.UNAVAILABLE);store.logoutUnknown=true;assertThat(service.logout("synthetic-token")).isEqualTo(BuyerAuthStore.LogoutResult.UNKNOWN);
    }

    @Test void buyerPrincipalHasOnlySingleEligibleValueAndThreeFrozenFields(){
        assertThat(BuyerAuthStore.Eligibility.values()).containsExactly(BuyerAuthStore.Eligibility.ELIGIBLE);
        assertThat(Arrays.stream(BuyerAuthStore.BuyerPrincipal.class.getRecordComponents()).map(component->component.getName()+":"+component.getType().getSimpleName()))
                .containsExactly("eligibility:Eligibility","subjectRef:String","sessionRef:String")
                .noneMatch(component->component.contains("boolean")||component.contains("UNKNOWN")||component.contains("Instant")||component.contains("Time"));
    }

    @Test void appIdMismatchFailsClosedAndWindowCanRateLimit(){MemoryStore store=new MemoryStore();assertCode(service(store,new FakePort(new WechatCode2SessionPort.Success("APP_OTHER","provider_subject_synthetic","EVIDENCE-SYNTHETIC")),true),"appid-mismatch-code","WECHAT_LOGIN_APPID_MISMATCH");store.admitted=false;assertCode(service(store,new FakePort(new WechatCode2SessionPort.Unknown("UNUSED")),true),"rate-limited-code","LOGIN_RATE_LIMITED");}
    @Test void currentDualConsentIsPersistedWithTheSessionAndOutdatedConsentCallsNoProvider(){
        MemoryStore store=new MemoryStore();FakePort provider=new FakePort(new WechatCode2SessionPort.Success("APP_PRIMARY","provider_subject_synthetic","EVIDENCE-SYNTHETIC"));
        BuyerAuthService service=service(store,provider,true);
        assertThatThrownBy(()->service.establish(new BuyerAuthService.LoginCommand("one-time-code-old-policy","REQUEST-C01","GUEST-000001","UA-OLD","PP-V1",true,true)))
                .isInstanceOfSatisfying(BuyerAuthService.Rejected.class,r->assertThat(r.projectCode).isEqualTo("BUYER_CONSENT_REQUIRED"));
        assertThat(provider.calls).isZero();
        var result=service.establish(new BuyerAuthService.LoginCommand("one-time-code-consented","REQUEST-C02","GUEST-000001","UA-V1","PP-V1",true,true));
        assertThat(result.subjectRef()).isNotBlank();assertThat(store.consentWrites).isOne();
        assertThat(store.consent).extracting(BuyerAuthStore.Consent::guestRef,BuyerAuthStore.Consent::userAgreementVersion,BuyerAuthStore.Consent::privacyPolicyVersion)
                .containsExactly("GUEST-000001","UA-V1","PP-V1");
    }

    @Test void closureRequestedGuestIsRejectedBeforeProviderCall(){
        MemoryStore store=new MemoryStore();store.guestAllowed=false;FakePort provider=new FakePort(new WechatCode2SessionPort.Unknown("MUST_NOT_CALL"));
        BuyerAuthService service=service(store,provider,true);
        assertThatThrownBy(()->service.establish(new BuyerAuthService.LoginCommand("one-time-code-closure","REQUEST-C03","GUEST-000001","UA-V1","PP-V1",true,true)))
                .isInstanceOfSatisfying(BuyerAuthService.Rejected.class,r->assertThat(r.projectCode).isEqualTo("BUYER_ACCOUNT_CLOSURE_PENDING"));
        assertThat(provider.calls).isZero();assertThat(store.attemptWrites).isZero();
    }
    @Test void policyRotationInvalidatesExistingSessionWithoutIdleWrite(){
        MemoryStore store=new MemoryStore();store.active=new SessionState("BUYER-SYN","SESSION-SYN",NOW.plusSeconds(3600),NOW.plusSeconds(1800));
        store.acceptedUserAgreementVersion="UA-V0";
        BuyerAuthService service=service(store,new FakePort(new WechatCode2SessionPort.Unknown("UNUSED")),true);
        assertThat(service.authenticate("synthetic-existing-token")).isEmpty();
        assertThat(store.authReads).isOne();assertThat(store.idleAdvances).isZero();
    }
    private static BuyerAuthService service(MemoryStore store,WechatCode2SessionPort port,boolean enabled){return new BuyerAuthService(store,port,enabled,"APP_PRIMARY",ID,CODE,Clock.fixed(NOW,ZoneOffset.UTC),new SecureRandom(new byte[]{1,2,3}));}
    private static void assertCode(BuyerAuthService service,String code,String expected){assertThatThrownBy(()->service.establish(code,"REQUEST-VALID")).isInstanceOfSatisfying(BuyerAuthService.Rejected.class,r->assertThat(r.projectCode).isEqualTo(expected));}
    private static final class FakePort implements WechatCode2SessionPort {final Result result;int calls;FakePort(Result r){result=r;}public Result exchange(Command c){calls++;return result;}}
    private static final class MemoryStore implements BuyerAuthStore {
        final Set<String> codes=ConcurrentHashMap.newKeySet();int attemptWrites,sessionWrites,consentWrites,authReads,idleAdvances,revokes,lastSeenWrites;SessionState active;boolean logoutUnknown,admitted=true,guestAllowed=true,accountAllowed=true;Instant issuedAt;Audit audit;Consent consent;
        String codeDigest,subjectDigest,tokenDigest;
        public boolean admitLoginWindow(String key,Instant now,Instant end,int attempts,int failures){return admitted;}
        public void recordLoginWindowOutcome(String key,boolean succeeded,Instant occurredAt){}
        public synchronized boolean beginLoginAttempt(String e,String a,String d,String ref,String req,Instant at){if(!codes.add(d))return false;attemptWrites++;codeDigest=d;return true;}
        public void finishLoginAttempt(String a,String r,String e,Instant at){}
        public synchronized Identity establishIdentityAndSession(String attempt,String evidence,String app,String sub,String ref,String sid,String token,Instant issued,Instant absolute,Instant idle,Audit audit){sessionWrites++;subjectDigest=sub;tokenDigest=token;issuedAt=issued;this.audit=audit;active=new SessionState(ref,sid,absolute,idle);return new Identity("buyer",ref);}
        public synchronized Identity establishIdentityConsentAndSession(String attempt,String evidence,String app,String sub,String ref,String sid,String token,Instant issued,Instant absolute,Instant idle,Consent consent,Audit audit){this.consent=consent;consentWrites++;return establishIdentityAndSession(attempt,evidence,app,sub,ref,sid,token,issued,absolute,idle,audit);}
        public boolean guestMayLogin(String guestRef){return guestAllowed;}
        public boolean accountMayLogin(String appIdRef,String subjectDigest){return accountAllowed;}
        String acceptedUserAgreementVersion="UA-V1",acceptedPrivacyPolicyVersion="PP-V1";
        public synchronized Optional<BuyerPrincipal> authenticateAndAdvanceIdle(String token,Instant now,Instant requested,String userAgreementVersion,String privacyPolicyVersion){authReads++;if(active==null||!acceptedUserAgreementVersion.equals(userAgreementVersion)||!acceptedPrivacyPolicyVersion.equals(privacyPolicyVersion)||now.compareTo(active.absoluteExpiresAt)>=0||now.compareTo(active.idleExpiresAt)>=0)return Optional.empty();Instant next=requested.isBefore(active.absoluteExpiresAt)?requested:active.absoluteExpiresAt;active=new SessionState(active.subjectRef,active.sessionRef,active.absoluteExpiresAt,next);idleAdvances++;return Optional.of(new BuyerPrincipal(Eligibility.ELIGIBLE,active.subjectRef,active.sessionRef));}
        public synchronized LogoutResult revokeCurrentSession(String token,Instant now){if(logoutUnknown)throw new IllegalStateException("synthetic storage failure");if(active==null)return LogoutResult.UNAVAILABLE;active=null;revokes++;return LogoutResult.SUCCEEDED;}
        String persistedText(){return String.valueOf(codeDigest)+subjectDigest+tokenDigest;}
    }
    private record SessionState(String subjectRef,String sessionRef,Instant absoluteExpiresAt,Instant idleExpiresAt){}
    private static final class MutableClock extends Clock {Instant now;MutableClock(Instant now){this.now=now;}public ZoneId getZone(){return ZoneOffset.UTC;}public Clock withZone(ZoneId zone){return this;}public Instant instant(){return now;}}
}
