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
        MemoryStore unknownStore=new MemoryStore();FakePort unknown=new FakePort(new WechatCode2SessionPort.Unknown("TIMEOUT"));assertCode(service(unknownStore,unknown,true),"one-time-code-unknown","WECHAT_LOGIN_RESULT_UNKNOWN");
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
        BuyerAuthService service=new BuyerAuthService(store,delayed,true,ID,CODE,clock,new SecureRandom(new byte[]{4,5,6}));var result=service.establish("one-time-code-delayed","REQUEST-004");
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

    private static BuyerAuthService service(MemoryStore store,WechatCode2SessionPort port,boolean enabled){return new BuyerAuthService(store,port,enabled,ID,CODE,Clock.fixed(NOW,ZoneOffset.UTC),new SecureRandom(new byte[]{1,2,3}));}
    private static void assertCode(BuyerAuthService service,String code,String expected){assertThatThrownBy(()->service.establish(code,"REQUEST-VALID")).isInstanceOfSatisfying(BuyerAuthService.Rejected.class,r->assertThat(r.projectCode).isEqualTo(expected));}
    private static final class FakePort implements WechatCode2SessionPort {final Result result;int calls;FakePort(Result r){result=r;}public Result exchange(Command c){calls++;return result;}}
    private static final class MemoryStore implements BuyerAuthStore {
        final Set<String> codes=ConcurrentHashMap.newKeySet();int attemptWrites,sessionWrites,authReads,idleAdvances,revokes,lastSeenWrites;SessionState active;boolean logoutUnknown;Instant issuedAt;Audit audit;
        String codeDigest,subjectDigest,tokenDigest;
        public synchronized boolean beginLoginAttempt(String e,String a,String d,String ref,String req,Instant at){if(!codes.add(d))return false;attemptWrites++;codeDigest=d;return true;}
        public void finishLoginAttempt(String a,String r,String e,Instant at){}
        public synchronized Identity establishIdentityAndSession(String attempt,String evidence,String app,String sub,String ref,String sid,String token,Instant issued,Instant absolute,Instant idle,Audit audit){sessionWrites++;subjectDigest=sub;tokenDigest=token;issuedAt=issued;this.audit=audit;active=new SessionState(ref,sid,absolute,idle);return new Identity("buyer",ref);}
        public synchronized Optional<BuyerPrincipal> authenticateAndAdvanceIdle(String token,Instant now,Instant requested){authReads++;if(active==null||now.compareTo(active.absoluteExpiresAt)>=0||now.compareTo(active.idleExpiresAt)>=0)return Optional.empty();Instant next=requested.isBefore(active.absoluteExpiresAt)?requested:active.absoluteExpiresAt;active=new SessionState(active.subjectRef,active.sessionRef,active.absoluteExpiresAt,next);idleAdvances++;return Optional.of(new BuyerPrincipal(Eligibility.ELIGIBLE,active.subjectRef,active.sessionRef));}
        public synchronized LogoutResult revokeCurrentSession(String token,Instant now){if(logoutUnknown)throw new IllegalStateException("synthetic storage failure");if(active==null)return LogoutResult.UNAVAILABLE;active=null;revokes++;return LogoutResult.SUCCEEDED;}
        String persistedText(){return String.valueOf(codeDigest)+subjectDigest+tokenDigest;}
    }
    private record SessionState(String subjectRef,String sessionRef,Instant absoluteExpiresAt,Instant idleExpiresAt){}
    private static final class MutableClock extends Clock {Instant now;MutableClock(Instant now){this.now=now;}public ZoneId getZone(){return ZoneOffset.UTC;}public Clock withZone(ZoneId zone){return this;}public Instant instant(){return now;}}
}
