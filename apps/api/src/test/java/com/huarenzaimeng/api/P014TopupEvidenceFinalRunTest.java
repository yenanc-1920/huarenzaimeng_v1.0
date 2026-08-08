package com.huarenzaimeng.api;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/** Prepared only. Never run without a fresh single-use authorization and externally supplied RunId. */
@EnabledIfSystemProperty(named="hz.p014.evidence.finalRun",matches="AUTHORIZED_ONCE")
class P014TopupEvidenceFinalRunTest {
    static final Map<String,List<String>> FIXED_MATRIX=Collections.unmodifiableMap(new TreeMap<>(Map.ofEntries(
            e("BE05-P014-001-APPROVED-CREATE","P001-VALID"), e("BE05-P014-002-EXACT-REPLAY","P001-UNCHANGED","P002-DECISION-ROTATED","P003-CATALOG-DRIFTED"),
            e("BE05-P014-003-COMMAND-KEY-CONFLICT","P001-SAME-COMMAND-NEW-IDEMPOTENCY","P002-SAME-COMMAND-DIFFERENT-REQUEST"),
            e("BE05-P014-004-IDEMPOTENCY-KEY-CONFLICT","P001-SAME-IDEMPOTENCY-NEW-COMMAND","P002-SAME-IDEMPOTENCY-DIFFERENT-REQUEST"),
            e("BE05-P014-005-SAME-SEMANTIC-KEY-SWITCH","P001-NEW-DOUBLE-KEY-SAME-FINGERPRINT","P002-NEW-DOUBLE-KEY-DIFFERENT-FINGERPRINT"),
            e("BE05-P014-006-B1-MNP-UNKNOWN-ZERO","P001-MNP-UNKNOWN","P002-MNP-UNAVAILABLE"), e("BE05-P014-007-PAYMENT-CONFIRMATION-UNKNOWN-ZERO","P001-PAYMENT-UNKNOWN","P002-PAYMENT-CONFLICT"),
            e("BE05-P014-008-AUTH-OR-EXISTENCE-SAME-SHAPE","P001-UNAUTHENTICATED","P002-REVOKED","P003-CROSS-SUBJECT","P004-NOT-FOUND"),
            e("BE05-P014-009-PRICE-OR-VERSION-DRIFT-ZERO","P001-PRICE-DIGEST","P002-ORDER-VERSION","P003-PROJECTION-VERSION","P004-AUTH-EVIDENCE-VERSION"),
            e("BE05-P014-010-CONCURRENT-UNIQUE-CREATE","P001-TWO-DISTINCT-DOUBLE-KEY-COMPETITORS","P002-N-DISTINCT-DOUBLE-KEY-COMPETITORS"),
            e("BE05-P014-011-U-CONFIRMED-D-UNKNOWN","P001-D-UNKNOWN-WITHIN-WINDOW","P002-D-UNKNOWN-LONG-RUNNING","P003-D-NOT-OBSERVED-AGE-UNKNOWN","P004-D-ABSENT-CONFIRMED"),
            e("BE05-P014-012-D-CONFIRMED-U-UNKNOWN","P001-U-UNKNOWN","P002-ACCOUNTING-UNKNOWN","P003-U-AND-ACCOUNTING-UNKNOWN"),
            e("BE05-P014-013-U-D-BOTH-UNKNOWN","P001-WITHIN-WINDOW","P002-LONG-RUNNING","P003-AGE-UNKNOWN"), e("BE05-P014-014-U-D-ACCOUNTING-CONFIRMED","P001-ALL-CONFIRMED"),
            e("BE05-P014-015-DUPLICATE-OBSERVATION-ZERO","P001-U-DUPLICATE","P002-D-DUPLICATE","P003-ACCOUNTING-DUPLICATE"),
            e("BE05-P014-016-FACT-CONFLICT-REVIEW","P001-U-CONFLICT","P002-D-CONFLICT","P003-ACCOUNTING-CONFLICT","P004-DUPLICATE-CANONICAL-IDENTITY"),
            e("BE05-P014-017-OUT-OF-ORDER-MONOTONIC","P001-CONFIRMED-THEN-UNKNOWN","P002-D-LATE-BEFORE-U","P003-CONFLICT-AFTER-CONFIRMED"),
            e("BE05-P014-018-ORIGINAL-KEY-QUERY-UNKNOWN","P001-NEXTPOLL-NULL","P002-NEXTPOLL-RFC3339"),
            e("BE05-P014-019-LATE-RESULT-SAME-KEY-CONVERGENCE","P001-UNKNOWN-TO-FOUND","P002-UNKNOWN-TO-REJECTED","P003-FOUND-NO-REGRESSION"),
            e("BE05-P014-020-STRICT-DTO-ZERO-REAL-SIDE-EFFECT","P001-MISSING","P002-ADDITIONAL","P003-UNKNOWN-ENUM","P004-ILLEGAL-NULL","P005-WRONG-TYPE","P006-CROSS-FIELD-MISMATCH"))));

    static final List<String> WRITE_COUNTERS=List.of("Command","CommandAlias","TopupBusinessKey","TopupSemanticAction",
            "TopupIntent","DispatchSemanticAction","DispatchIntent","OrderVersion","ProjectionVersion","SyntheticObservation",
            "PaymentAttempt","SendAttempt","RemoteAcceptance","WechatPrepay","RequestPayment","Notification","ExternalFact",
            "W","U","D","L","LedgerEntry","ExternalCall");

    @Test void authorized_runner_contract_requires_complete_single_run_package() {
        String runId=required("hz.p014.evidence.runId");
        String packageRef=required("hz.p014.evidence.packageRef");
        assertThat(FIXED_MATRIX).hasSize(20);
        assertThat(FIXED_MATRIX.values().stream().mapToInt(List::size).sum()).isEqualTo(56);
        assertThat(WRITE_COUNTERS).hasSize(23).doesNotHaveDuplicates();
        List<EvidenceCase> skeleton=new ArrayList<>();
        FIXED_MATRIX.forEach((scenario,parameters)->parameters.forEach(parameter->skeleton.add(new EvidenceCase(
                scenario,scenario,parameter,runId,Instant.now(),"FIXED_INPUTS_REQUIRED","EXPECTED_REQUIRED",
                "ACTUAL_REQUIRED","FULL_RESPONSE_REQUIRED",zero(),zero(),zero(),0,packageRef,"NOT_RUN",false))));
        assertThat(skeleton).allSatisfy(c->{assertThat(c.scenarioId()).isEqualTo(c.subcaseId());assertThat(c.consumable()).isFalse();});
        // A real authorized implementation must replace every NOT_RUN atomically; partial/failed output remains non-consumable.
    }

    private static Map.Entry<String,List<String>> e(String scenario,String...parameters){return Map.entry(scenario,List.of(parameters));}
    private static String required(String key){String value=System.getProperty(key);if(value==null||value.isBlank())throw new IllegalStateException(key+" is required");return value;}
    private static Map<String,Long> zero(){var m=new LinkedHashMap<String,Long>();WRITE_COUNTERS.forEach(k->m.put(k,0L));return Map.copyOf(m);}
    record EvidenceCase(String scenarioId,String subcaseId,String parameterId,String runId,Instant executedAt,
                        String fixedInputShaSet,String expected,String actual,String completeResponse,
                        Map<String,Long> before,Map<String,Long> after,Map<String,Long> delta,long queryCall,
                        String evidencePackageRef,String executionStatus,boolean consumable){}
}
