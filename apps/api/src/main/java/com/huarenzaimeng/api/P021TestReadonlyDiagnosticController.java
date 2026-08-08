package com.huarenzaimeng.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huarenzaimeng.api.config.TestAccessTokenFilter;
import com.huarenzaimeng.api.config.TrustedTestSessionCookieFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Test-environment-only observation and rollback probe. Never enabled by release defaults. */
@RestController
@RequestMapping("/internal/test-readonly/p021")
@ConditionalOnProperty(name = "hz.p021.mode", havingValue = "test-readonly")
@ConditionalOnProperty(name = "hz.persistence.mode", havingValue = "mysql")
final class P021TestReadonlyDiagnosticController {
    private static final String FIXED_ORDER = "IT-P021-AWAITING";
    private static final List<String> FIXED_ORDERS = List.of(
            "IT-P021-AWAITING", "IT-P021-PAYMENT", "IT-P021-TOPUP", "IT-P021-UNKNOWN",
            "IT-P021-DELIVERED", "IT-P021-REFUNDED", "IT-P021-REVOKED");
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final P021OrderDetailService service;
    private final P021OrderDetailSideEffectProbe probe;
    private final ObjectMapper mapper;

    P021TestReadonlyDiagnosticController(JdbcTemplate jdbc, TransactionTemplate transactions,
                                         P021OrderDetailService service, P021OrderDetailSideEffectProbe probe,
                                         ObjectMapper mapper) {
        this.jdbc=jdbc; this.transactions=transactions; this.service=service; this.probe=probe; this.mapper=mapper;
    }

    @GetMapping("/counters") Map<String,Long> counters(HttpServletRequest request) {
        requireTrusted(request); return probe.snapshot();
    }

    @GetMapping("/challenge") Map<String,String> challenge(HttpServletRequest request,
                                                            @RequestParam String nonce) {
        requireBuyer(request);
        if (!nonce.matches("[A-F0-9]{32}")) throw new IllegalArgumentException("invalid nonce");
        String revision = firstNonBlank(System.getenv("K_REVISION"), System.getenv("TCB_CLOUD_RUN_VERSION"),
                System.getenv("CLOUD_RUN_REVISION"));
        if (revision == null) throw new IllegalStateException("trusted runtime revision unavailable");
        probe.observeQuery();
        String databaseIdentity = jdbc.queryForObject(
                "SELECT CONCAT(DATABASE(),'|',@@hostname,'|',@@port,'|',@@server_uuid)", String.class);
        if (databaseIdentity == null || !databaseIdentity.startsWith("huarenzaimeng_it_vnext|")) {
            throw new IllegalStateException("database identity mismatch");
        }
        return Map.of("nonce", nonce, "serviceVersion", revision, "mode", "test-readonly",
                "databaseIdentity", databaseIdentity);
    }

    @GetMapping("/snapshot") Map<String,Object> snapshot(HttpServletRequest request) {
        requireBuyer(request);
        probe.observeQuery();
        List<String> rows = jdbc.queryForList("""
                SELECT CONCAT(o.order_ref,'|',o.quote_ref,'|',o.order_state,'|',o.payment_state,'|',
                    o.upstream_debit_state,'|',o.delivery_state,'|',o.refund_state,'|',o.projection_version,'|',
                    o.aggregate_version,'|',p.projection_version,'|',p.revoked,'|',
                    SHA2(CAST(q.price_snapshot AS CHAR),256),'|',SHA2(CAST(p.projection_json AS CHAR),256))
                  FROM hz_order o
                  JOIN hz_quote q ON q.quote_ref=o.quote_ref
                  JOIN hz_order_detail_projection p ON p.order_ref=o.order_ref
                 WHERE o.order_ref IN (?,?,?,?,?,?,?)
                 ORDER BY o.order_ref
                """, String.class, FIXED_ORDERS.toArray());
        if (rows.size() != FIXED_ORDERS.size()) throw new IllegalStateException("snapshot row count mismatch");
        return Map.of("rowCount", rows.size(), "rows", rows, "canonicalSha256", sha256(String.join("\n", rows)));
    }

    @GetMapping("/projection") Object projection(HttpServletRequest request) {
        requireBuyer(request);
        probe.observeQuery();
        String json = jdbc.queryForObject(
                "SELECT CAST(projection_json AS CHAR) FROM hz_order_detail_projection WHERE order_ref=?",
                String.class, FIXED_ORDER);
        try { return mapper.readTree(json); }
        catch (JsonProcessingException exception) { throw new IllegalStateException("stored projection invalid", exception); }
    }

    @PostMapping("/conflict/{kind}") Object conflict(HttpServletRequest request, @PathVariable String kind) {
        requireBuyer(request);
        return transactions.execute(status -> {
            try {
                switch (kind) {
                    case "PROJECTION_LOW_VERSION" -> jdbc.update("""
                        UPDATE hz_order_detail_projection
                           SET projection_json=JSON_SET(projection_json,'$.projectionVersion',0)
                         WHERE order_ref=?""", FIXED_ORDER);
                    case "ORDER_VERSION_CONFLICT" -> jdbc.update("""
                        UPDATE hz_order SET projection_version=projection_version+1 WHERE order_ref=?""", FIXED_ORDER);
                    case "QUOTE_DIGEST_CONFLICT" -> jdbc.update("""
                        UPDATE hz_quote q JOIN hz_order o ON o.quote_ref=q.quote_ref
                           SET q.price_snapshot=JSON_SET(q.price_snapshot,'$.catalogVersion',999)
                         WHERE o.order_ref=?""", FIXED_ORDER);
                    default -> throw new IllegalArgumentException("unsupported conflict kind");
                }
                return service.read(attribute(request, TestAccessTokenFilter.LOCAL_ENVIRONMENT),
                        attribute(request, TestAccessTokenFilter.LOCAL_PROJECT_SUBJECT_REF),
                        attribute(request, TestAccessTokenFilter.LOCAL_SESSION_REF), FIXED_ORDER, session(request));
            } finally { status.setRollbackOnly(); }
        });
    }

    private static void requireTrusted(HttpServletRequest request) {
        if (attribute(request, TestAccessTokenFilter.LOCAL_PROJECT_SUBJECT_REF)==null
                && attribute(request, TrustedTestSessionCookieFilter.TRUSTED_ADMIN_ROLE)==null) {
            throw new IllegalArgumentException("trusted test session required");
        }
    }
    private static void requireBuyer(HttpServletRequest request) {
        if (attribute(request, TestAccessTokenFilter.LOCAL_PROJECT_SUBJECT_REF)==null) {
            throw new IllegalArgumentException("trusted buyer session required");
        }
    }
    private static String attribute(HttpServletRequest request,String name){Object v=request.getAttribute(name);return v==null?null:v.toString();}
    private static String firstNonBlank(String... values) {
        for (String value : values) if (value != null && !value.isBlank()) return value;
        return null;
    }
    private static String sha256(String value) {
        try {
            return HexFormat.of().withUpperCase().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
    }
    @SuppressWarnings("unchecked") private static SessionSnapshot session(HttpServletRequest request){
        Object version=request.getAttribute(TrustedTestSessionCookieFilter.TRUSTED_SESSION_VERSION);
        Object refs=request.getAttribute(TrustedTestSessionCookieFilter.TRUSTED_AUTHORIZED_ORDER_REFS);
        if(!(version instanceof Long value)||!(refs instanceof List<?>))return null;
        return new SessionSnapshot(attribute(request,TestAccessTokenFilter.LOCAL_PROJECT_SUBJECT_REF),
                attribute(request,TestAccessTokenFilter.LOCAL_SESSION_REF),value,
                attribute(request,TrustedTestSessionCookieFilter.TRUSTED_AUTHORIZATION_SET_REF),
                attribute(request,TrustedTestSessionCookieFilter.TRUSTED_AUTHORIZATION_EVIDENCE_VERSION),(List<String>)refs);
    }
}
