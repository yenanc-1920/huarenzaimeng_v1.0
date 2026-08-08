package com.huarenzaimeng.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class P021MyBatisStoreContractTest {
    private static final DateTimeFormatter MYSQL_DATETIME =
            DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss.SSSSSS").withZone(ZoneId.of("Asia/Dhaka"));

    @Test void authorityVersionAndPriceDriftFailClosed() throws Exception {
        P021ProjectionMapper mapper = mock(P021ProjectionMapper.class);
        ObjectMapper json = new ObjectMapper().findAndRegisterModules();
        LocalSyntheticIdentity identity = LocalSyntheticIdentity.fromToken("mysql-store-contract");
        var fixture = P021OrderDetailFixtureLoader.fixture(identity, UserOrderStateCode.AWAITING_PAYMENT,
                1, 1, null, List.of(), List.of());
        Map<String, Object> row = new HashMap<>();
        row.put("projection_json", json.writeValueAsString(fixture.projection()));
        row.put("authorized_order_refs", json.writeValueAsString(List.of(fixture.projection().orderRef())));
        row.put("session_version", 1L); row.put("authorization_set_ref", fixture.authorizationSetRef());
        row.put("authorization_evidence_version", fixture.authorizationEvidenceVersion());
        row.put("authority_aggregate_version", 1L); row.put("authority_projection_version", 1L);
        row.put("authority_total_minor", fixture.projection().priceSnapshotSummary().totalMinor());
        row.put("authority_currency", fixture.projection().priceSnapshotSummary().currency());
        row.put("authority_masked_target", fixture.projection().priceSnapshotSummary().maskedTarget());
        row.put("authority_valid_until_local", mysqlWallClock(
                fixture.projection().priceSnapshotSummary().validUntil()));
        row.put("price_snapshot_digest", fixture.priceSnapshotDigest());
        row.put("authority_quote_snapshot", "{\"amountMinor\":125000,\"currency\":\"BDT\",\"denominationRef\":\"IT-DENOMINATION\",\"supportedOperatorSetVersion\":1,\"catalogVersion\":1}");
        row.put("quote_snapshot_digest", MyBatisP021Store.quoteSnapshotDigest(125000, "BDT",
                "IT-DENOMINATION", 1, 1));
        when(mapper.selectAuthorized(anyString(), anyString(), anyString())).thenReturn(row);
        MyBatisP021Store store = new MyBatisP021Store(mapper, json);
        TimeZone originalTimeZone = TimeZone.getDefault();
        try {
            for (String jvmZone : List.of("Asia/Shanghai", "UTC", "Asia/Dhaka")) {
                TimeZone.setDefault(TimeZone.getTimeZone(jvmZone));
                assertThat(store.findAuthorized(fixture.projection().orderRef(), identity.projectSubjectRef(),
                        identity.sessionRef())).as("JVM default zone %s must not affect P021", jvmZone).isPresent();
            }
        } finally {
            TimeZone.setDefault(originalTimeZone);
        }
        assertThat(store.findAuthorized(fixture.projection().orderRef(), new SessionSnapshot(
                identity.projectSubjectRef(), identity.sessionRef(), 2, fixture.authorizationSetRef(),
                fixture.authorizationEvidenceVersion(), List.of(fixture.projection().orderRef())))).isEmpty();
        row.put("authority_projection_version", 2L);
        assertThatThrownBy(() -> store.findAuthorized(fixture.projection().orderRef(), identity.projectSubjectRef(),
                identity.sessionRef())).isInstanceOf(P021StoreReadException.class);
        row.put("authority_projection_version", 1L); row.put("authority_total_minor", 1L);
        assertThatThrownBy(() -> store.findAuthorized(fixture.projection().orderRef(), identity.projectSubjectRef(),
                identity.sessionRef())).isInstanceOf(P021StoreReadException.class);
        row.put("authority_total_minor", fixture.projection().priceSnapshotSummary().totalMinor());
        row.put("authority_valid_until_local", mysqlWallClock(
                fixture.projection().priceSnapshotSummary().validUntil().plusMillis(1)));
        assertThatThrownBy(() -> store.findAuthorized(fixture.projection().orderRef(), identity.projectSubjectRef(),
                identity.sessionRef())).isInstanceOf(P021StoreReadException.class);
        row.put("authority_valid_until_local", mysqlWallClock(
                fixture.projection().priceSnapshotSummary().validUntil()));
        row.put("price_snapshot_digest", "0".repeat(64));
        assertThatThrownBy(() -> store.findAuthorized(fixture.projection().orderRef(), identity.projectSubjectRef(),
                identity.sessionRef())).isInstanceOf(P021StoreReadException.class);
        row.put("price_snapshot_digest", fixture.priceSnapshotDigest());
        row.put("authority_quote_snapshot", "{\"amountMinor\":125000,\"currency\":\"BDT\",\"denominationRef\":\"IT-DENOMINATION\",\"supportedOperatorSetVersion\":2,\"catalogVersion\":1}");
        assertThatThrownBy(() -> store.findAuthorized(fixture.projection().orderRef(), identity.projectSubjectRef(),
                identity.sessionRef())).isInstanceOf(P021StoreReadException.class);
    }

    @Test void storeContractFailureMapsToReadErrorInsteadOfNotAvailable() {
        P021Store store = mock(P021Store.class);
        when(store.findAuthorized(anyString(), any(SessionSnapshot.class))).thenThrow(new P021StoreReadException());
        org.springframework.mock.env.MockEnvironment environment = new org.springframework.mock.env.MockEnvironment();
        P021OrderDetailService service = new P021OrderDetailService(mock(LocalSyntheticOrderRecoveryService.class),
                new P021OrderDetailSideEffectProbe(), store, environment, "test-readonly", "mysql");
        var session = new SessionSnapshot("IT-SUBJECT-P021", "IT-SESSION-P021", 1,
                "IT-AUTHSET-P021", "IT-AUTH-EVIDENCE-P021-V1", List.of("IT-P021-AWAITING"));
        assertThat(service.read(P021OrderDetailDomain.ENVIRONMENT, session.projectSubjectRef(), session.sessionRef(),
                "IT-P021-AWAITING", session).projectCode()).isEqualTo(P021OrderDetailDomain.PROJECT_CODE_ERROR);
    }

    @Test void qualificationDiagnosticReportsOnlyBooleanPredicates() throws Exception {
        P021ProjectionMapper mapper = mock(P021ProjectionMapper.class);
        ObjectMapper json = new ObjectMapper().findAndRegisterModules();
        Map<String, Object> row = new HashMap<>();
        row.put("project_subject_ref", "IT-SUBJECT-P021");
        row.put("session_ref", "IT-SESSION-P021");
        row.put("session_version", 1L);
        row.put("authorization_set_ref", "IT-AUTHSET-P021");
        row.put("authorization_evidence_version", "IT-AUTH-EVIDENCE-P021-V1");
        row.put("authorized_order_refs", json.writeValueAsString(List.of("IT-P021-AWAITING")));
        LocalSyntheticIdentity identity = LocalSyntheticIdentity.fromToken("diagnostic");
        var fixture = P021OrderDetailFixtureLoader.fixture(identity, UserOrderStateCode.AWAITING_PAYMENT,
                1, 1, null, List.of(), List.of());
        row.put("projection_json", json.writeValueAsString(fixture.projection()));
        row.put("authority_quote_snapshot", "{\"amountMinor\":125000,\"currency\":\"BDT\",\"denominationRef\":\"IT-DENOMINATION\",\"supportedOperatorSetVersion\":1,\"catalogVersion\":1}");
        row.put("authority_aggregate_version", 1L); row.put("authority_projection_version", 1L);
        row.put("authority_total_minor", fixture.projection().priceSnapshotSummary().totalMinor());
        row.put("authority_currency", fixture.projection().priceSnapshotSummary().currency());
        row.put("authority_masked_target", fixture.projection().priceSnapshotSummary().maskedTarget());
        row.put("authority_valid_until_local", mysqlWallClock(
                fixture.projection().priceSnapshotSummary().validUntil()));
        row.put("price_snapshot_digest", fixture.priceSnapshotDigest());
        row.put("quote_snapshot_digest", MyBatisP021Store.quoteSnapshotDigest(125000, "BDT", "IT-DENOMINATION", 1, 1));
        row.put("revoked", 0); row.put("authority_order_joined", 1); row.put("authority_quote_joined", 1);
        when(mapper.selectQualificationDiagnostic("IT-P021-AWAITING")).thenReturn(row);
        MyBatisP021Store store = new MyBatisP021Store(mapper, json);
        var ok = store.diagnose("IT-P021-AWAITING", new SessionSnapshot("IT-SUBJECT-P021", "IT-SESSION-P021",
                1, "IT-AUTHSET-P021", "IT-AUTH-EVIDENCE-P021-V1", List.of("IT-P021-AWAITING")));
        assertThat(ok.eligible()).isTrue();
        var drift = store.diagnose("IT-P021-AWAITING", new SessionSnapshot("IT-SUBJECT-P021", "IT-SESSION-P021",
                1, "IT-AUTHSET-P021", "IT-AUTH-EVIDENCE-P021-V1", List.of("IT-P021-AWAITING", "EXTRA")));
        assertThat(drift.eligible()).isFalse();
        assertThat(drift.authorizedOrderRefsExactlyMatched()).isFalse();
    }

    private static String mysqlWallClock(Instant value) {
        return MYSQL_DATETIME.format(value);
    }
}
