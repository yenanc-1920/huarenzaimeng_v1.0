package com.huarenzaimeng.api.topup;

import org.springframework.context.annotation.Profile;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Timestamp;

@Repository
@Profile("release-mysql")
public class JdbcProviderExchangeStore implements ProviderExchangeStore {
    private final JdbcTemplate jdbc;
    public JdbcProviderExchangeStore(JdbcTemplate jdbc){this.jdbc=jdbc;}

    @Override @Transactional
    public SaveResult record(Attempt a){
        validate(a);
        try{
            jdbc.update("INSERT INTO hz_provider_exchange_attempt (exchange_ref,provider_code,operation_code,merchant_order_ref,provider_request_ref,local_amount_minor,local_currency,provider_amount_raw,provider_currency_raw,provider_sku,recipient_digest,canonical_request_digest,raw_response_digest,provider_ref,contract_version,observed_at,normalized_amount_minor,normalized_currency,normalization_status,created_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,CURRENT_TIMESTAMP(3))",
                    a.exchangeRef(),a.providerCode(),a.operation().name(),a.merchantOrderRef(),a.providerRequestRef(),a.localAmountMinor(),a.localCurrency(),a.providerAmountRaw(),a.providerCurrencyRaw(),a.providerSku(),a.recipientDigest(),a.canonicalRequestDigest(),a.rawResponseDigest(),a.providerRef(),a.contractVersion(),Timestamp.from(a.observedAt()),a.normalizedAmountMinor(),a.normalizedCurrency(),a.normalizationStatus().name());
            return SaveResult.CREATED;
        }catch(DuplicateKeyException duplicate){
            Attempt existing=current(a.providerCode(),a.providerRequestRef());
            if(existing!=null&&same(existing,a))return SaveResult.REPLAY;
            throw new Conflict("PROVIDER_REQUEST_REF_CONFLICT");
        }
    }

    @Override @Transactional(readOnly=true)
    public Original requireOriginal(String providerCode,String merchantOrderRef){
        return jdbc.query("SELECT merchant_order_ref,provider_ref,local_amount_minor,local_currency,provider_sku,recipient_digest,canonical_request_digest,contract_version FROM hz_provider_exchange_attempt WHERE provider_code=? AND merchant_order_ref=? AND operation_code='SUBMIT' AND normalization_status='VALIDATED' ORDER BY observed_at ASC LIMIT 1",
                (rs,n)->new Original(rs.getString(1),rs.getString(2),unsigned(rs.getBigDecimal(3)),rs.getString(4),rs.getString(5),rs.getString(6),rs.getString(7),rs.getString(8)),providerCode,merchantOrderRef).stream().findFirst().orElseThrow(()->new Conflict("PROVIDER_ORIGINAL_EXCHANGE_NOT_FOUND"));
    }

    private Attempt current(String provider,String requestRef){
        return jdbc.query("SELECT exchange_ref,provider_code,operation_code,merchant_order_ref,provider_request_ref,local_amount_minor,local_currency,provider_amount_raw,provider_currency_raw,provider_sku,recipient_digest,canonical_request_digest,raw_response_digest,provider_ref,contract_version,observed_at,normalized_amount_minor,normalized_currency,normalization_status FROM hz_provider_exchange_attempt WHERE provider_code=? AND provider_request_ref=? FOR UPDATE",
                (rs,n)->new Attempt(rs.getString(1),rs.getString(2),Operation.valueOf(rs.getString(3)),rs.getString(4),rs.getString(5),unsigned(rs.getBigDecimal(6)),rs.getString(7),rs.getString(8),rs.getString(9),rs.getString(10),rs.getString(11),rs.getString(12),rs.getString(13),rs.getString(14),rs.getString(15),rs.getTimestamp(16).toInstant(),nullableUnsigned(rs.getBigDecimal(17)),rs.getString(18),NormalizationStatus.valueOf(rs.getString(19))),provider,requestRef).stream().findFirst().orElse(null);
    }
    private static boolean same(Attempt a,Attempt b){
        return a.providerCode().equals(b.providerCode())&&a.operation()==b.operation()&&a.merchantOrderRef().equals(b.merchantOrderRef())&&a.providerRequestRef().equals(b.providerRequestRef())&&a.localAmountMinor()==b.localAmountMinor()&&a.localCurrency().equals(b.localCurrency())&&a.providerAmountRaw().equals(b.providerAmountRaw())&&a.providerCurrencyRaw().equals(b.providerCurrencyRaw())&&a.providerSku().equals(b.providerSku())&&a.recipientDigest().equals(b.recipientDigest())&&a.canonicalRequestDigest().equals(b.canonicalRequestDigest())&&a.rawResponseDigest().equals(b.rawResponseDigest())&&java.util.Objects.equals(a.providerRef(),b.providerRef())&&a.contractVersion().equals(b.contractVersion())&&java.util.Objects.equals(a.normalizedAmountMinor(),b.normalizedAmountMinor())&&java.util.Objects.equals(a.normalizedCurrency(),b.normalizedCurrency())&&a.normalizationStatus()==b.normalizationStatus();
    }
    private static void validate(Attempt a){
        if(a==null||blank(a.exchangeRef())||blank(a.providerCode())||a.operation()==null||blank(a.merchantOrderRef())||blank(a.providerRequestRef())||a.localAmountMinor()<=0||!currency(a.localCurrency())||blank(a.providerAmountRaw())||blank(a.providerCurrencyRaw())||blank(a.providerSku())||!digest(a.recipientDigest())||!digest(a.canonicalRequestDigest())||!digest(a.rawResponseDigest())||blank(a.contractVersion())||a.observedAt()==null||a.normalizationStatus()==null)throw new Conflict("PROVIDER_EXCHANGE_INVALID");
        if(a.normalizationStatus()==NormalizationStatus.VALIDATED&&(a.normalizedAmountMinor()==null||a.normalizedAmountMinor()<=0||!currency(a.normalizedCurrency())||blank(a.providerRef())))throw new Conflict("PROVIDER_EXCHANGE_VALIDATION_INCOMPLETE");
    }
    private static boolean blank(String s){return s==null||s.isBlank();}
    private static boolean currency(String s){return s!=null&&s.matches("[A-Z]{3}");}
    private static boolean digest(String s){return s!=null&&s.matches("[a-f0-9]{64}");}
    private static long unsigned(BigDecimal value){try{long v=value.longValueExact();if(v<0)throw new ArithmeticException();return v;}catch(Exception e){throw new Conflict("PROVIDER_EXCHANGE_UNSIGNED_INVALID");}}
    private static Long nullableUnsigned(BigDecimal value){return value==null?null:unsigned(value);}
}
