package com.huarenzaimeng.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Profile;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Profile("release-mysql")
final class ReleaseQuoteOrderService {
    private static final Duration QUOTE_TTL = Duration.ofMinutes(10);
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final ObjectMapper json;
    private final Clock clock;
    private final byte[] phoneDigestSecret;

    @Autowired
    ReleaseQuoteOrderService(JdbcTemplate jdbc, TransactionTemplate transactions, ObjectMapper json,
                             @Value("${HZ_PHONE_DIGEST_HMAC_SECRET:${hz.phone-digest-hmac-secret:}}") String phoneDigestSecret) {
        this(jdbc, transactions, json, Clock.systemUTC(),phoneDigestSecret);
    }

    ReleaseQuoteOrderService(JdbcTemplate jdbc, TransactionTemplate transactions, ObjectMapper json, Clock clock,String phoneDigestSecret) {
        this.jdbc = jdbc; this.transactions = transactions; this.json = json; this.clock = clock;
        if(phoneDigestSecret==null||phoneDigestSecret.length()<32)throw new IllegalStateException("PHONE_DIGEST_HMAC_SECRET_REQUIRED");
        this.phoneDigestSecret=phoneDigestSecret.getBytes(StandardCharsets.UTF_8);
    }

    QuoteView createQuote(String subject, String idempotencyKey, String requestRef, String phone, String productRef) {
        String normalizedPhone = normalizeBangladeshPhone(phone);
        String phoneDigest = phoneDigest(normalizedPhone);
        String requestDigest = sha256("QUOTE", subject, requestRef, idempotencyKey, phoneDigest, productRef);
        QuoteView replay = findQuote(subject, idempotencyKey);
        if (replay != null) return requireDigest(replay, requestDigest);
        try {
            QuoteView created = transactions.execute(status -> {
                QuoteView lockedReplay = findQuoteForUpdate(subject, idempotencyKey);
                if (lockedReplay != null) return requireDigest(lockedReplay, requestDigest);
                TrustedProduct p = loadTrustedProduct(productRef);
                String phoneMasked = mask(normalizedPhone);
                Instant now = clock.instant(); Instant validUntil = now.plus(QUOTE_TTL);
                long amountMinor = p.finalAmountCny().movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact();
                String entitlement = toJson(Map.ofEntries(
                        Map.entry("productRef", p.productRef()), Map.entry("productType", p.productType()), Map.entry("displayName", p.displayName()),
                        Map.entry("benefitText", p.benefitText()), Map.entry("providerCode", p.providerCode()), Map.entry("providerSku", p.providerSku()),
                        Map.entry("denominationBdt", nullable(p.denominationBdt())), Map.entry("dataAllowanceMb", nullable(p.dataAllowanceMb())),
                        Map.entry("voiceMinutes", nullable(p.voiceMinutes())), Map.entry("smsCount", nullable(p.smsCount())),
                        Map.entry("validityText", nullable(p.validityText()))));
                String price = toJson(Map.of(
                        "priceVersionRef", p.priceVersionRef(), "finalAmountCny", p.finalAmountCny(),
                        "fxSnapshotRef", p.fxSnapshotRef(), "supplierSourceRef", p.supplierSourceRef()));
                String snapshotDigest = sha256(phoneDigest, p.operatorCode(), p.productRef(), entitlement,
                        p.priceVersionRef(), price, Long.toString(p.catalogVersion()), Long.toString(validUntil.toEpochMilli()));
                String quoteRef = "Q-" + UUID.randomUUID();
                Timestamp nowTs = Timestamp.from(now), validTs = Timestamp.from(validUntil);
                jdbc.update("INSERT INTO hz_quote(quote_ref,project_subject_ref,phone_masked,operator_code,product_code,denomination_ref,supported_operator_set_version,catalog_version,mnp_state,total_amount,total_amount_minor,total_currency,price_snapshot,expires_at,created_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,CAST(? AS JSON),?,?)",
                        quoteRef, subject, phoneMasked, p.operatorCode(), p.productRef(), p.productRef(),
                        p.operatorSetVersion(), p.catalogVersion(), "NOT_REQUIRED", p.finalAmountCny(), amountMinor,
                        "CNY", price, validTs, nowTs);
                jdbc.update("INSERT INTO hz_release_quote_snapshot(quote_ref,project_subject_ref,request_ref,idempotency_key,request_digest,phone_digest,phone_masked,operator_code,platform_product_ref,product_aggregate_version,entitlement_snapshot,price_version_ref,price_aggregate_version,price_snapshot,final_amount_minor,currency,supported_operator_set_version,catalog_version,valid_until,snapshot_digest,created_at) VALUES(?,?,?,?,?,?,?,?,?,?,CAST(? AS JSON),?,?,CAST(? AS JSON),?,?,?,?,?,?,?)",
                        quoteRef, subject, requestRef, idempotencyKey, requestDigest, phoneDigest, phoneMasked,
                        p.operatorCode(), p.productRef(), p.productVersion(), entitlement, p.priceVersionRef(),
                        p.priceVersion(), price, amountMinor, "CNY", p.operatorSetVersion(), p.catalogVersion(),
                        validTs, snapshotDigest, nowTs);
                jdbc.update("INSERT INTO hz_quote_recipient_pending(quote_ref,recipient_plain,recipient_digest,recipient_masked,expires_at,created_at) VALUES(?,?,?,?,?,?)",
                        quoteRef, normalizedPhone, phoneDigest, phoneMasked, validTs, nowTs);
                return findQuote(subject, idempotencyKey);
            });
            if (created == null) throw new IllegalStateException("quote transaction returned no result");
            return created;
        } catch (DuplicateKeyException race) {
            QuoteView existing = findQuote(subject, idempotencyKey);
            if (existing == null) throw new FlowRejectedException("QUOTE_IDEMPOTENCY_CONFLICT");
            return requireDigest(existing, requestDigest);
        }
    }

    OrderView createOrder(String subject, String idempotencyKey, String requestRef, String quoteRef) {
        String requestDigest = sha256("ORDER", subject, requestRef, idempotencyKey, quoteRef);
        OrderView replay = findOrder(subject, idempotencyKey);
        if (replay != null) return requireDigest(replay, requestDigest);
        try {
            OrderView created = transactions.execute(status -> {
                QuoteRow q = lockQuote(subject, quoteRef);
                OrderView lockedReplay = findOrderForUpdate(subject, idempotencyKey);
                if (lockedReplay != null) return requireDigest(lockedReplay, requestDigest);
                if (!q.validUntil().isAfter(clock.instant())) throw new FlowRejectedException("QUOTE_EXPIRED");
                Integer current = jdbc.queryForObject("SELECT COUNT(*) FROM hz_platform_product p JOIN hz_price_version v ON v.platform_product_ref=p.platform_product_ref JOIN hz_operator_support_batch b ON b.supported_operator_set_version=? AND b.batch_state='ACTIVE' AND b.effective_from<=CURRENT_TIMESTAMP(3) AND b.expires_at>CURRENT_TIMESTAMP(3) JOIN hz_operator_membership m ON m.supported_operator_set_version=b.supported_operator_set_version AND m.operator_code=p.operator_code AND m.membership_state='SUPPORTED' JOIN hz_product_catalog c ON c.catalog_version=? AND c.supported_operator_set_version=b.supported_operator_set_version AND c.catalog_state='ACTIVE' AND c.effective_from<=CURRENT_TIMESTAMP(3) AND c.expires_at>CURRENT_TIMESTAMP(3) WHERE p.platform_product_ref=? AND p.operator_code=? AND p.aggregate_version=? AND p.enable_state='ENABLED' AND v.price_version_ref=? AND v.aggregate_version=? AND v.price_state='ACTIVE' AND v.effective_from<=CURRENT_TIMESTAMP(3) AND v.effective_until>CURRENT_TIMESTAMP(3) AND c.catalog_version=(SELECT MAX(c2.catalog_version) FROM hz_product_catalog c2 WHERE c2.supported_operator_set_version=b.supported_operator_set_version AND c2.catalog_state='ACTIVE' AND c2.effective_from<=CURRENT_TIMESTAMP(3) AND c2.expires_at>CURRENT_TIMESTAMP(3))", Integer.class,
                        q.operatorSetVersion(),q.catalogVersion(),q.productRef(),q.operatorCode(),q.productVersion(),q.priceVersionRef(),q.priceVersion());
                if (current == null || current != 1) throw new FlowRejectedException("QUOTE_VERSION_DRIFT");
                String orderRef = "O-" + UUID.randomUUID(); Instant now = clock.instant(); Timestamp nowTs=Timestamp.from(now);
                jdbc.update("INSERT INTO hz_order(order_ref,project_subject_ref,quote_ref,order_state,payment_state,upstream_debit_state,delivery_state,refund_state,projection_version,aggregate_version,allowed_action,environment,evidence_level,authority_state,created_at,updated_at) VALUES(?,?,?,'CREATED','UNPAID','NOT_STARTED','NOT_STARTED','NOT_REQUESTED',1,1,'CREATE_PAYMENT_INTENT','PREPRODUCTION','L1','NON_PRODUCTION',?,?)",
                        orderRef, subject, quoteRef, nowTs, nowTs);
                String quoteSnapshot = toJson(Map.ofEntries(Map.entry("quoteRef",q.quoteRef()),Map.entry("phoneDigest",q.phoneDigest()),
                        Map.entry("phoneMasked",q.phoneMasked()),Map.entry("operatorCode",q.operatorCode()),Map.entry("productRef",q.productRef()),
                        Map.entry("priceVersionRef",q.priceVersionRef()),Map.entry("finalAmountMinor",q.finalAmountMinor()),
                        Map.entry("currency",q.currency()),Map.entry("catalogVersion",q.catalogVersion()),Map.entry("validUntil",q.validUntil().toString()),
                        Map.entry("snapshotDigest",q.snapshotDigest())));
                String orderDigest=sha256(requestDigest,quoteSnapshot,q.entitlement(),q.price(),subject);
                String priceDigest=sha256("PRICE_SNAPSHOT",q.price());
                PendingRecipient recipient = lockPendingRecipient(q.quoteRef());
                if (!recipient.digest().equals(q.phoneDigest()) || !recipient.masked().equals(q.phoneMasked())) {
                    throw new FlowRejectedException("QUOTE_RECIPIENT_BINDING_CONFLICT");
                }
                jdbc.update("INSERT INTO hz_release_order_snapshot(order_ref,quote_ref,project_subject_ref,request_ref,idempotency_key,request_digest,quote_snapshot,entitlement_snapshot,price_snapshot,price_version_ref,final_amount_minor,currency,price_snapshot_digest,quote_snapshot_digest,snapshot_digest,created_at) VALUES(?,?,?,?,?,?,CAST(? AS JSON),CAST(? AS JSON),CAST(? AS JSON),?,?,?,?,?,?,?)",
                        orderRef,quoteRef,subject,requestRef,idempotencyKey,requestDigest,quoteSnapshot,q.entitlement(),q.price(),q.priceVersionRef(),q.finalAmountMinor(),q.currency(),priceDigest,q.snapshotDigest(),orderDigest,nowTs);
                jdbc.update("INSERT INTO hz_order_recipient_fulfillment(order_ref,recipient_plain,recipient_digest,recipient_masked,retention_state,terminal_at,retention_until,created_at,updated_at) VALUES(?,?,?,?,'ACTIVE',NULL,NULL,?,?)",
                        orderRef, recipient.plain(), recipient.digest(), recipient.masked(), nowTs, nowTs);
                FulfillmentEntitlement fulfillment = fulfillmentEntitlement(q.entitlement());
                jdbc.update("INSERT INTO hz_order_fulfillment_snapshot(merchant_order_ref,buyer_subject_ref,provider_sku,recipient,face_value_minor,target_currency,entitlement_digest,entitlement_state,created_at) VALUES(?,?,?,?,?,?,?,'READY',?)",
                        orderRef, subject, fulfillment.providerSku(), recipient.masked(), fulfillment.faceValueMinor(), fulfillment.targetCurrency(),
                        sha256("FULFILLMENT", orderRef, fulfillment.providerSku(), recipient.digest(), Long.toString(fulfillment.faceValueMinor()), fulfillment.targetCurrency()), nowTs);
                return findOrder(subject,idempotencyKey);
            });
            if (created == null) throw new IllegalStateException("order transaction returned no result");
            return created;
        } catch (FlowRejectedException race) {
            if (!"QUOTE_RECIPIENT_NOT_AVAILABLE".equals(race.getMessage())) throw race;
            OrderView existing=findOrder(subject,idempotencyKey);
            if(existing==null)throw race;
            return requireDigest(existing,requestDigest);
        } catch (DuplicateKeyException race) {
            OrderView existing=findOrder(subject,idempotencyKey);
            if(existing==null)throw new FlowRejectedException("ORDER_IDEMPOTENCY_CONFLICT");
            return requireDigest(existing,requestDigest);
        }
    }

    OrderView projection(String subject,String orderRef){
        List<OrderView> found=jdbc.query("SELECT s.order_ref,s.quote_ref,s.request_ref,s.request_digest,s.snapshot_digest,o.order_state,o.payment_state,o.delivery_state,o.refund_state,o.projection_version,o.aggregate_version FROM hz_release_order_snapshot s JOIN hz_order o ON o.order_ref=s.order_ref AND o.project_subject_ref=s.project_subject_ref WHERE s.project_subject_ref=? AND s.order_ref=?",
                (rs,n)->new OrderView(rs.getString(1),rs.getString(2),rs.getString(3),rs.getString(4),rs.getString(5),rs.getString(6),rs.getString(7),rs.getString(8),rs.getString(9),rs.getLong(10),rs.getLong(11)),subject,orderRef);
        return found.stream().findFirst().orElseThrow(()->new FlowRejectedException("ORDER_NOT_AVAILABLE"));
    }

    private TrustedProduct loadTrustedProduct(String ref){
        List<TrustedProduct> rows=jdbc.query("SELECT p.platform_product_ref,p.operator_code,p.product_type,p.display_name,p.benefit_text,p.provider_code,p.provider_sku,p.denomination_bdt,p.data_allowance_mb,p.voice_minutes,p.sms_count,p.validity_text,p.aggregate_version,v.price_version_ref,v.final_amount_cny,v.fx_snapshot_ref,v.supplier_source_ref,v.aggregate_version,b.supported_operator_set_version,c.catalog_version FROM hz_platform_product p JOIN hz_provider_channel ch ON ch.provider_code=p.provider_code AND ch.channel_state='ENABLED' JOIN hz_price_version v ON v.platform_product_ref=p.platform_product_ref AND v.price_state='ACTIVE' AND v.effective_from<=CURRENT_TIMESTAMP(3) AND v.effective_until>CURRENT_TIMESTAMP(3) JOIN hz_operator_support_batch b ON b.batch_state='ACTIVE' AND b.effective_from<=CURRENT_TIMESTAMP(3) AND b.expires_at>CURRENT_TIMESTAMP(3) AND b.supported_operator_set_version=(SELECT MAX(b2.supported_operator_set_version) FROM hz_operator_support_batch b2 JOIN hz_product_catalog c2 ON c2.supported_operator_set_version=b2.supported_operator_set_version AND c2.catalog_state='ACTIVE' AND c2.effective_from<=CURRENT_TIMESTAMP(3) AND c2.expires_at>CURRENT_TIMESTAMP(3) WHERE b2.batch_state='ACTIVE' AND b2.effective_from<=CURRENT_TIMESTAMP(3) AND b2.expires_at>CURRENT_TIMESTAMP(3)) JOIN hz_operator_membership m ON m.supported_operator_set_version=b.supported_operator_set_version AND m.operator_code=p.operator_code AND m.membership_state='SUPPORTED' JOIN hz_product_catalog c ON c.supported_operator_set_version=b.supported_operator_set_version AND c.catalog_state='ACTIVE' AND c.effective_from<=CURRENT_TIMESTAMP(3) AND c.expires_at>CURRENT_TIMESTAMP(3) AND c.catalog_version=(SELECT MAX(c3.catalog_version) FROM hz_product_catalog c3 WHERE c3.supported_operator_set_version=b.supported_operator_set_version AND c3.catalog_state='ACTIVE' AND c3.effective_from<=CURRENT_TIMESTAMP(3) AND c3.expires_at>CURRENT_TIMESTAMP(3)) WHERE p.platform_product_ref=? AND p.enable_state='ENABLED' AND p.mapping_state='MAPPED' AND p.supplier_availability='AVAILABLE' AND (p.sale_start_at IS NULL OR p.sale_start_at<=CURRENT_TIMESTAMP(3)) AND (p.sale_end_at IS NULL OR p.sale_end_at>CURRENT_TIMESTAMP(3)) ORDER BY v.effective_from DESC LIMIT 2",
                (rs,n)->new TrustedProduct(rs.getString(1),rs.getString(2),rs.getString(3),rs.getString(4),rs.getString(5),rs.getString(6),rs.getString(7),rs.getBigDecimal(8),(Long)rs.getObject(9),(Integer)rs.getObject(10),(Integer)rs.getObject(11),rs.getString(12),rs.getLong(13),rs.getString(14),rs.getBigDecimal(15),rs.getString(16),rs.getString(17),rs.getLong(18),rs.getLong(19),rs.getLong(20)),ref);
        if(rows.size()!=1)throw new FlowRejectedException("TRUSTED_PRODUCT_PRICE_NOT_UNIQUE"); return rows.get(0);
    }

    private QuoteRow lockQuote(String subject,String quoteRef){
        List<QuoteRow> rows=jdbc.query("SELECT quote_ref,request_digest,phone_digest,phone_masked,operator_code,platform_product_ref,product_aggregate_version,entitlement_snapshot,price_version_ref,price_aggregate_version,price_snapshot,final_amount_minor,currency,supported_operator_set_version,catalog_version,valid_until,snapshot_digest FROM hz_release_quote_snapshot WHERE project_subject_ref=? AND quote_ref=? FOR UPDATE",
                (rs,n)->new QuoteRow(rs.getString(1),rs.getString(2),rs.getString(3),rs.getString(4),rs.getString(5),rs.getString(6),rs.getLong(7),rs.getString(8),rs.getString(9),rs.getLong(10),rs.getString(11),rs.getLong(12),rs.getString(13),rs.getLong(14),rs.getLong(15),rs.getTimestamp(16).toInstant(),rs.getString(17)),subject,quoteRef);
        return rows.stream().findFirst().orElseThrow(()->new FlowRejectedException("QUOTE_NOT_AVAILABLE"));
    }
    private PendingRecipient lockPendingRecipient(String quoteRef){
        List<PendingRecipient> rows=jdbc.query("SELECT recipient_plain,recipient_digest,recipient_masked FROM hz_quote_recipient_pending WHERE quote_ref=? FOR UPDATE",
                (rs,n)->new PendingRecipient(rs.getString(1),rs.getString(2),rs.getString(3)),quoteRef);
        return rows.stream().findFirst().orElseThrow(()->new FlowRejectedException("QUOTE_RECIPIENT_NOT_AVAILABLE"));
    }
    private FulfillmentEntitlement fulfillmentEntitlement(String entitlement){
        try{
            JsonNode root=json.readTree(entitlement);
            if(root!=null&&root.isTextual())root=json.readTree(root.textValue());
            if(root==null||!root.isObject())throw new IllegalArgumentException();
            String sku=root.path("providerSku").asText("");
            BigDecimal denomination=root.path("denominationBdt").decimalValue();
            if(!sku.matches("[A-Za-z0-9._:-]{1,128}")||denomination.signum()<=0||denomination.scale()>2)throw new IllegalArgumentException();
            return new FulfillmentEntitlement(sku,denomination.movePointRight(2).longValueExact(),"BDT");
        }catch(Exception invalid){throw new FlowRejectedException("FULFILLMENT_ENTITLEMENT_INVALID");}
    }
    private QuoteView findQuote(String s,String k){return queryQuote("",s,k);}
    private QuoteView findQuoteForUpdate(String s,String k){return queryQuote(" FOR UPDATE",s,k);}
    private QuoteView queryQuote(String lock,String s,String k){return jdbc.query("SELECT quote_ref,request_ref,request_digest,phone_digest,phone_masked,operator_code,platform_product_ref,price_version_ref,final_amount_minor,currency,catalog_version,valid_until,snapshot_digest FROM hz_release_quote_snapshot WHERE project_subject_ref=? AND idempotency_key=?"+lock,(rs,n)->new QuoteView(rs.getString(1),rs.getString(2),rs.getString(3),rs.getString(4),rs.getString(5),rs.getString(6),rs.getString(7),rs.getString(8),BigDecimal.valueOf(rs.getLong(9),2),rs.getString(10),rs.getLong(11),rs.getTimestamp(12).toInstant(),rs.getString(13)),s,k).stream().findFirst().orElse(null);}
    private OrderView findOrder(String s,String k){return queryOrder("",s,k);}
    private OrderView findOrderForUpdate(String s,String k){return queryOrder(" FOR UPDATE",s,k);}
    private OrderView queryOrder(String lock,String s,String k){return jdbc.query("SELECT x.order_ref,x.quote_ref,x.request_ref,x.request_digest,x.snapshot_digest,o.order_state,o.payment_state,o.delivery_state,o.refund_state,o.projection_version,o.aggregate_version FROM hz_release_order_snapshot x JOIN hz_order o ON o.order_ref=x.order_ref WHERE x.project_subject_ref=? AND x.idempotency_key=?"+lock,(rs,n)->new OrderView(rs.getString(1),rs.getString(2),rs.getString(3),rs.getString(4),rs.getString(5),rs.getString(6),rs.getString(7),rs.getString(8),rs.getString(9),rs.getLong(10),rs.getLong(11)),s,k).stream().findFirst().orElse(null);}
    private static QuoteView requireDigest(QuoteView v,String d){if(!v.requestDigest().equals(d))throw new FlowRejectedException("QUOTE_IDEMPOTENCY_CONFLICT");return v;}
    private static OrderView requireDigest(OrderView v,String d){if(!v.requestDigest().equals(d))throw new FlowRejectedException("ORDER_IDEMPOTENCY_CONFLICT");return v;}
    private String toJson(Object value){try{return json.writeValueAsString(value);}catch(JsonProcessingException e){throw new IllegalStateException("SNAPSHOT_SERIALIZATION_FAILED",e);}}
    private static Object nullable(Object value){return value==null?"":value;}
    static String normalizeBangladeshPhone(String raw){String digits=raw==null?"":raw.replaceAll("[^0-9]","");if(digits.startsWith("880"))digits=digits.substring(3);if(digits.startsWith("0"))digits=digits.substring(1);if(!digits.matches("1[3-9][0-9]{8}"))throw new FlowRejectedException("BANGLADESH_PHONE_INVALID");return "+880"+digits;}
    private static String mask(String p){return p.substring(0,6)+"****"+p.substring(p.length()-3);}
    private static String sha256(String... parts){try{MessageDigest md=MessageDigest.getInstance("SHA-256");for(String p:parts){md.update(p.getBytes(StandardCharsets.UTF_8));md.update((byte)0);}return HexFormat.of().formatHex(md.digest());}catch(Exception e){throw new IllegalStateException(e);}}
    private String phoneDigest(String normalizedPhone){try{Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(phoneDigestSecret,"HmacSHA256"));mac.update("BD_PHONE\0".getBytes(StandardCharsets.UTF_8));return HexFormat.of().formatHex(mac.doFinal(normalizedPhone.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException("PHONE_DIGEST_HMAC_FAILED",e);}}

    record QuoteView(String quoteRef,String requestRef,@JsonIgnore String requestDigest,@JsonIgnore String phoneDigest,String phoneMasked,String operatorCode,String productRef,String priceVersionRef,BigDecimal finalAmountCny,String currency,long catalogVersion,Instant validUntil,String snapshotDigest){}
    record OrderView(String orderRef,String quoteRef,String requestRef,@JsonIgnore String requestDigest,String snapshotDigest,String orderState,String paymentState,String deliveryState,String refundState,long projectionVersion,long aggregateVersion){}
    private record TrustedProduct(String productRef,String operatorCode,String productType,String displayName,String benefitText,String providerCode,String providerSku,BigDecimal denominationBdt,Long dataAllowanceMb,Integer voiceMinutes,Integer smsCount,String validityText,long productVersion,String priceVersionRef,BigDecimal finalAmountCny,String fxSnapshotRef,String supplierSourceRef,long priceVersion,long operatorSetVersion,long catalogVersion){}
    private record QuoteRow(String quoteRef,String requestDigest,String phoneDigest,String phoneMasked,String operatorCode,String productRef,long productVersion,String entitlement,String priceVersionRef,long priceVersion,String price,long finalAmountMinor,String currency,long operatorSetVersion,long catalogVersion,Instant validUntil,String snapshotDigest){}
    private record PendingRecipient(String plain,String digest,String masked){}
    private record FulfillmentEntitlement(String providerSku,long faceValueMinor,String targetCurrency){}
}
