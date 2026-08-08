package com.huarenzaimeng.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.sql.Timestamp;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Set;

import static com.huarenzaimeng.api.P021OrderDetailDomain.*;

@Repository
@ConditionalOnProperty(name = "hz.persistence.mode", havingValue = "mysql")
class MyBatisP021Store implements P021Store {
    private final P021ProjectionMapper mapper;
    private final ObjectMapper json;

    MyBatisP021Store(P021ProjectionMapper mapper, ObjectMapper json) { this.mapper = mapper; this.json = json; }

    public Optional<Fixture> findAuthorized(String orderRef, String subjectRef, String sessionRef) {
        Map<String, Object> row = mapper.selectAuthorized(orderRef, subjectRef, sessionRef);
        if (row == null) return Optional.empty();
        try {
            Projection projection = json.readValue(String.valueOf(row.get("projection_json")), Projection.class);
            var quoteSnapshot = json.readTree(String.valueOf(row.get("authority_quote_snapshot")));
            if (!quoteSnapshot.isObject() || !keys(quoteSnapshot).equals(Set.of("amountMinor", "currency",
                    "denominationRef", "supportedOperatorSetVersion", "catalogVersion"))) return Optional.empty();
            String authorityQuoteDigest = quoteSnapshotDigest(quoteSnapshot.path("amountMinor").longValue(),
                    quoteSnapshot.path("currency").textValue(), quoteSnapshot.path("denominationRef").textValue(),
                    quoteSnapshot.path("supportedOperatorSetVersion").longValue(),
                    quoteSnapshot.path("catalogVersion").longValue());
            List<String> authorizedOrderRefs = json.readValue(String.valueOf(row.get("authorized_order_refs")),
                    json.getTypeFactory().constructCollectionType(List.class, String.class));
            PriceSnapshotSummary price = projection.priceSnapshotSummary();
            if (((Number) row.get("authority_aggregate_version")).longValue() != projection.aggregateVersion()
                    || ((Number) row.get("authority_projection_version")).longValue() != projection.projectionVersion()
                    || ((Number) row.get("authority_total_minor")).longValue() != price.totalMinor()
                    || !String.valueOf(row.get("authority_currency")).equals(price.currency())
                    || !String.valueOf(row.get("authority_masked_target")).equals(price.maskedTarget())
                    || !((Timestamp) row.get("authority_valid_until")).toInstant().equals(price.validUntil())
                    || !String.valueOf(row.get("price_snapshot_digest"))
                            .equals(P021OrderDetailService.snapshotDigest(price))
                    || !String.valueOf(row.get("quote_snapshot_digest")).equals(authorityQuoteDigest)
                    || !authorizedOrderRefs.contains(orderRef)) return Optional.empty();
            long sessionVersion = ((Number) row.get("session_version")).longValue();
            String authorizationSetRef = String.valueOf(row.get("authorization_set_ref"));
            String evidenceVersion = String.valueOf(row.get("authorization_evidence_version"));
            String snapshotDigest = P021OrderDetailService.snapshotDigest(projection.priceSnapshotSummary());
            Fixture draft = new Fixture(true, ENVIRONMENT, REALITY_LEVEL, subjectRef, ProjectSessionRole.BUYER,
                    sessionVersion, authorizationSetRef, evidenceVersion, List.copyOf(authorizedOrderRefs), projection,
                    snapshotDigest, "P021-MYSQL-V1", "PENDING");
            return Optional.of(new Fixture(draft.syntheticMarker(), draft.environment(), draft.realityEvidenceLevel(),
                    draft.projectSubjectRef(), draft.sessionRole(), draft.sessionVersion(), draft.authorizationSetRef(),
                    draft.authorizationEvidenceVersion(), draft.authorizedOrderRefs(), draft.projection(),
                    draft.priceSnapshotDigest(), draft.fixtureSchemaVersion(), P021OrderDetailService.fixtureDigest(draft)));
        } catch (RuntimeException | java.io.IOException error) {
            return Optional.empty();
        }
    }

    QualificationDiagnostic diagnose(String orderRef, SessionSnapshot session) {
        Map<String, Object> row = mapper.selectQualificationDiagnostic(orderRef);
        if (row == null) return QualificationDiagnostic.missing();
        try {
            List<String> refs = json.readValue(String.valueOf(row.get("authorized_order_refs")),
                    json.getTypeFactory().constructCollectionType(List.class, String.class));
            boolean subject = session != null && String.valueOf(row.get("project_subject_ref"))
                    .equals(session.projectSubjectRef());
            boolean sessionRef = session != null && String.valueOf(row.get("session_ref"))
                    .equals(session.sessionRef());
            boolean sessionVersion = session != null
                    && ((Number) row.get("session_version")).longValue() == session.sessionVersion();
            boolean setRef = session != null && String.valueOf(row.get("authorization_set_ref"))
                    .equals(session.authorizationSetRef());
            boolean evidence = session != null && String.valueOf(row.get("authorization_evidence_version"))
                    .equals(session.authorizationEvidenceVersion());
            boolean refsEqual = session != null && refs.equals(session.authorizedOrderRefs());
            Projection projection = json.readValue(String.valueOf(row.get("projection_json")), Projection.class);
            var quoteSnapshot = json.readTree(String.valueOf(row.get("authority_quote_snapshot")));
            boolean quoteShape = quoteSnapshot.isObject() && keys(quoteSnapshot).equals(Set.of("amountMinor",
                    "currency", "denominationRef", "supportedOperatorSetVersion", "catalogVersion"));
            String quoteDigest = quoteShape ? quoteSnapshotDigest(quoteSnapshot.path("amountMinor").longValue(),
                    quoteSnapshot.path("currency").textValue(), quoteSnapshot.path("denominationRef").textValue(),
                    quoteSnapshot.path("supportedOperatorSetVersion").longValue(),
                    quoteSnapshot.path("catalogVersion").longValue()) : "INVALID";
            PriceSnapshotSummary price = projection.priceSnapshotSummary();
            boolean aggregateVersion = numberEquals(row.get("authority_aggregate_version"), projection.aggregateVersion());
            boolean projectionVersion = numberEquals(row.get("authority_projection_version"), projection.projectionVersion());
            boolean totalMinor = price != null && numberEquals(row.get("authority_total_minor"), price.totalMinor());
            boolean currency = price != null && String.valueOf(row.get("authority_currency")).equals(price.currency());
            boolean maskedTarget = price != null && String.valueOf(row.get("authority_masked_target")).equals(price.maskedTarget());
            boolean validUntil = price != null && row.get("authority_valid_until") instanceof Timestamp timestamp
                    && timestamp.toInstant().equals(price.validUntil());
            boolean priceDigest = price != null && String.valueOf(row.get("price_snapshot_digest"))
                    .equals(P021OrderDetailService.snapshotDigest(price));
            boolean quoteDigestMatched = String.valueOf(row.get("quote_snapshot_digest")).equals(quoteDigest);
            return new QualificationDiagnostic(true, numberIsOne(row.get("authority_order_joined")),
                    numberIsOne(row.get("authority_quote_joined")), subject, sessionRef, sessionVersion, setRef,
                    evidence, refs.contains(orderRef), refsEqual, !numberIsOne(row.get("revoked")), true,
                    true, quoteShape, aggregateVersion, projectionVersion, totalMinor, currency, maskedTarget,
                    validUntil, priceDigest, quoteDigestMatched);
        } catch (RuntimeException | java.io.IOException error) {
            return new QualificationDiagnostic(true, numberIsOne(row.get("authority_order_joined")),
                    numberIsOne(row.get("authority_quote_joined")), false, false, false, false, false,
                    false, false, !numberIsOne(row.get("revoked")), false, false, false, false, false,
                    false, false, false, false, false, false);
        }
    }

    private static boolean numberIsOne(Object value) {
        return value instanceof Number number && number.longValue() == 1L;
    }

    private static boolean numberEquals(Object value, long expected) {
        return value instanceof Number number && number.longValue() == expected;
    }

    record QualificationDiagnostic(boolean projectionRowExists, boolean authorityOrderJoined,
                                   boolean authorityQuoteJoined, boolean subjectMatched,
                                   boolean sessionRefMatched, boolean sessionVersionMatched,
                                   boolean authorizationSetMatched, boolean authorizationEvidenceMatched,
                                   boolean orderIncludedInStoredAuthorization, boolean authorizedOrderRefsExactlyMatched,
                                   boolean notRevoked, boolean storedAuthorizationReadable,
                                   boolean projectionJsonParsed, boolean quoteSnapshotShapeMatched,
                                   boolean aggregateVersionMatched, boolean projectionVersionMatched,
                                   boolean totalMinorMatched, boolean currencyMatched, boolean maskedTargetMatched,
                                   boolean validUntilMatched, boolean priceSnapshotDigestMatched,
                                   boolean quoteSnapshotDigestMatched) {
        static QualificationDiagnostic missing() {
            return new QualificationDiagnostic(false, false, false, false, false, false, false, false,
                    false, false, false, false, false, false, false, false, false, false, false, false,
                    false, false);
        }

        boolean eligible() {
            return projectionRowExists && authorityOrderJoined && authorityQuoteJoined && subjectMatched
                    && sessionRefMatched && sessionVersionMatched && authorizationSetMatched
                    && authorizationEvidenceMatched && orderIncludedInStoredAuthorization
                    && authorizedOrderRefsExactlyMatched && notRevoked && storedAuthorizationReadable
                    && projectionJsonParsed && quoteSnapshotShapeMatched && aggregateVersionMatched
                    && projectionVersionMatched && totalMinorMatched && currencyMatched && maskedTargetMatched
                    && validUntilMatched && priceSnapshotDigestMatched && quoteSnapshotDigestMatched;
        }
    }

    private static Set<String> keys(com.fasterxml.jackson.databind.JsonNode node) {
        java.util.HashSet<String> result = new java.util.HashSet<>(); node.fieldNames().forEachRemaining(result::add);
        return Set.copyOf(result);
    }

    static String quoteSnapshotDigest(long amountMinor, String currency, String denominationRef,
                                      long supportedOperatorSetVersion, long catalogVersion) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (String value : List.of(Long.toString(amountMinor), nullSafe(currency), nullSafe(denominationRef),
                    Long.toString(supportedOperatorSetVersion), Long.toString(catalogVersion))) {
                digest.update(value.getBytes(StandardCharsets.UTF_8)); digest.update((byte) 0);
            }
            return HexFormat.of().withUpperCase().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException error) { throw new IllegalStateException(error); }
    }

    private static String nullSafe(String value) { return value == null ? "" : value; }
}
