package com.huarenzaimeng.api.topup;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Objects;

/** Only this adapter translates provider raw amount/currency into canonical minor units. */
public final class WinlaTopupAdapter implements TopupProviderPort {
    static final String PROVIDER="WINLA";
    private final Transport transport; private final boolean enabled;
    private final ProviderExchangeStore exchanges; private final Contract contract;
    public WinlaTopupAdapter(Transport transport,boolean enabled,ProviderExchangeStore exchanges,Contract contract){this.transport=transport;this.enabled=enabled;this.exchanges=exchanges;this.contract=contract;}
    public WinlaTopupAdapter(Transport transport,boolean enabled){this(transport,enabled,null,null);}
    public static WinlaTopupAdapter disabled(){return new WinlaTopupAdapter(null,false,null,null);}
    @Override public boolean available(){return enabled;}

    @Override public Result submit(Command command){if(!ready())return new Unknown("WINLA_ADAPTER_DISABLED");RawResult raw=transportSubmit(command);return raw==null?new Unknown("WINLA_SUBMIT_UNKNOWN"):normalize(raw,command,null,ProviderExchangeStore.Operation.SUBMIT);}
    @Override public Result query(String providerRef,String order){if(!ready())return new Unknown("WINLA_ADAPTER_DISABLED");ProviderExchangeStore.Original original=exchanges.requireOriginal(PROVIDER,order);if(!Objects.equals(original.providerRef(),providerRef))return new Unknown("WINLA_QUERY_BINDING_UNKNOWN");RawResult raw=transportQuery(providerRef,order);return raw==null?new Unknown("WINLA_QUERY_UNKNOWN"):normalize(raw,null,original,ProviderExchangeStore.Operation.QUERY);}
    @Override public BalanceResult balance(){if(!ready())return new BalanceUnknown("WINLA_ADAPTER_DISABLED");try{BalanceResult r=transport.balance();return r==null?new BalanceUnknown("WINLA_BALANCE_UNKNOWN"):r;}catch(RuntimeException e){return new BalanceUnknown("WINLA_BALANCE_UNKNOWN");}}
    @Override public CallbackResult verifyCallback(CallbackEnvelope envelope){
        if(!ready())return new CallbackUnknown("WINLA_ADAPTER_DISABLED");RawCallback raw;
        try{raw=transport.verifyCallback(envelope);}catch(RuntimeException failure){return new CallbackUnknown("WINLA_CALLBACK_UNKNOWN");}
        if(raw==null)return new CallbackUnknown("WINLA_CALLBACK_UNKNOWN");ProviderExchangeStore.Original original;
        try{original=exchanges.requireOriginal(PROVIDER,raw.merchantOrderRef());}catch(ProviderExchangeStore.Conflict missing){return new CallbackUnknown("WINLA_CALLBACK_BINDING_UNKNOWN");}
        Normalized normalized=normalized(raw.providerAmountRaw(),raw.providerCurrencyRaw(),original.localCurrency());String recipientDigest=digestRecipient(raw.recipientRaw());
        boolean bound=normalized.valid()&&normalized.minor()==original.localAmountMinor()&&original.providerSku().equals(raw.providerSku())&&original.recipientDigest().equals(recipientDigest)&&original.providerRef().equals(raw.providerRef())&&original.merchantOrderRef().equals(raw.merchantOrderRef());
        ProviderExchangeStore.NormalizationStatus status=status(bound,raw.stateRaw(),normalized);
        record(raw.providerRequestRef(),raw.merchantOrderRef(),original,raw.providerAmountRaw(),raw.providerCurrencyRaw(),raw.providerSku(),recipientDigest,raw.rawResponseDigest(),raw.providerRef(),raw.observedAt(),normalized,status,ProviderExchangeStore.Operation.CALLBACK);
        if(!bound||status!=ProviderExchangeStore.NormalizationStatus.VALIDATED)return new CallbackUnknown("WINLA_CALLBACK_BINDING_UNKNOWN");String state=canonicalState(raw.stateRaw());
        return state==null?new CallbackUnknown("WINLA_CALLBACK_STATE_UNKNOWN"):new VerifiedCallback(raw.callbackId(),raw.callbackDigest(),raw.merchantOrderRef(),raw.providerRef(),state,normalized.minor(),normalized.currency(),raw.providerSku(),BangladeshPhoneNumber.normalize(raw.recipientRaw()),raw.observedAt(),raw.evidenceRef());
    }
    private Result normalize(RawResult raw,Command command,ProviderExchangeStore.Original original,ProviderExchangeStore.Operation operation){
        long localMinor=command!=null?command.faceValueMinor():original.localAmountMinor();String localCurrency=command!=null?command.targetCurrency():original.localCurrency();String merchantOrder=command!=null?command.merchantOrderRef():original.merchantOrderRef();String sku=command!=null?command.providerSku():original.providerSku();String recipientDigest=command!=null?digestRecipient(command.recipient()):original.recipientDigest();String requestDigest=command!=null?command.requestDigest():original.canonicalRequestDigest();
        Normalized normalized=normalized(raw.providerAmountRaw(),raw.providerCurrencyRaw(),localCurrency);boolean bound=normalized.valid()&&normalized.minor()==localMinor&&merchantOrder.equals(raw.merchantOrderRef())&&sku.equals(raw.providerSku())&&recipientDigest.equals(digestRecipient(raw.recipientRaw()))&&(operation==ProviderExchangeStore.Operation.SUBMIT||Objects.equals(original.providerRef(),raw.providerRef()));
        ProviderExchangeStore.NormalizationStatus status=status(bound,raw.stateRaw(),normalized);ProviderExchangeStore.Original base=new ProviderExchangeStore.Original(merchantOrder,raw.providerRef(),localMinor,localCurrency,sku,recipientDigest,requestDigest,contract.version());
        record(raw.providerRequestRef(),merchantOrder,base,raw.providerAmountRaw(),raw.providerCurrencyRaw(),raw.providerSku(),digestRecipient(raw.recipientRaw()),raw.rawResponseDigest(),raw.providerRef(),raw.observedAt(),normalized,status,operation);
        if(!bound||status!=ProviderExchangeStore.NormalizationStatus.VALIDATED)return new Unknown("WINLA_RESULT_BINDING_UNKNOWN");String state=canonicalState(raw.stateRaw());if("REJECTED".equals(state))return new Rejected(raw.reasonCode()==null?"WINLA_REJECTED":raw.reasonCode());return state==null?new Unknown("WINLA_RESULT_STATE_UNKNOWN"):new Accepted(raw.providerRef(),state,raw.evidenceRef());
    }
    private void record(String providerRequestRef,String order,ProviderExchangeStore.Original original,String amountRaw,String currencyRaw,String sku,String recipientDigest,String responseDigest,String providerRef,Instant observedAt,Normalized normalized,ProviderExchangeStore.NormalizationStatus status,ProviderExchangeStore.Operation operation){exchanges.record(new ProviderExchangeStore.Attempt(exchangeRef(providerRequestRef,operation),PROVIDER,operation,order,providerRequestRef,original.localAmountMinor(),original.localCurrency(),amountRaw,currencyRaw,sku,recipientDigest,original.canonicalRequestDigest(),responseDigest,providerRef,contract.version(),observedAt,normalized.valid()?normalized.minor():null,normalized.valid()?normalized.currency():null,status));}
    private ProviderExchangeStore.NormalizationStatus stateStatus(String state,Normalized normalized){if(!normalized.valid())return normalized.status();return canonicalState(state)==null?ProviderExchangeStore.NormalizationStatus.UNKNOWN_STATE:ProviderExchangeStore.NormalizationStatus.VALIDATED;}
    private ProviderExchangeStore.NormalizationStatus status(boolean bound,String state,Normalized normalized){return !normalized.valid()?normalized.status():bound?stateStatus(state,normalized):ProviderExchangeStore.NormalizationStatus.BINDING_MISMATCH;}
    private Normalized normalized(String amountRaw,String currencyRaw,String expectedCurrency){String currency=currencyRaw==null?null:currencyRaw.trim().toUpperCase(Locale.ROOT);if(!expectedCurrency.equals(currency)||!contract.currency().equals(currency))return Normalized.invalid(ProviderExchangeStore.NormalizationStatus.INVALID_CURRENCY);try{if(amountRaw==null||!amountRaw.matches("[0-9]+(?:\\.[0-9]+)?"))return Normalized.invalid(ProviderExchangeStore.NormalizationStatus.INVALID_AMOUNT);long minor=new BigDecimal(amountRaw).movePointRight(contract.scale()).setScale(0,RoundingMode.UNNECESSARY).longValueExact();return minor<=0?Normalized.invalid(ProviderExchangeStore.NormalizationStatus.INVALID_AMOUNT):new Normalized(minor,currency,ProviderExchangeStore.NormalizationStatus.VALIDATED);}catch(ArithmeticException invalid){return Normalized.invalid(ProviderExchangeStore.NormalizationStatus.INVALID_AMOUNT);}}
    private RawResult transportSubmit(Command c){try{return transport.submit(c);}catch(RuntimeException e){return null;}}private RawResult transportQuery(String p,String o){try{return transport.query(p,o);}catch(RuntimeException e){return null;}}
    private boolean ready(){return enabled&&transport!=null&&exchanges!=null&&contract!=null;}
    private static String canonicalState(String raw){if(raw==null)return null;return switch(raw.trim().toUpperCase(Locale.ROOT)){case "SUBMITTED"->"SUBMITTED";case "PROCESSING"->"PROCESSING";case "DELIVERED","SUCCESS"->"DELIVERED";case "REJECTED","FAILED"->"REJECTED";default->null;};}
    private static String digestRecipient(String raw){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(BangladeshPhoneNumber.normalize(raw).getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new ProviderExchangeStore.Conflict("PROVIDER_RECIPIENT_INVALID");}}
    private static String exchangeRef(String request,ProviderExchangeStore.Operation op){return "EX-"+HexFormat.of().formatHex(sha256((op.name()+"|"+request).getBytes(StandardCharsets.UTF_8))).substring(0,48);}private static byte[] sha256(byte[] v){try{return MessageDigest.getInstance("SHA-256").digest(v);}catch(Exception e){throw new IllegalStateException(e);}}

    public record Contract(String version,String currency,int scale){public Contract{if(version==null||version.isBlank()||currency==null||!currency.matches("[A-Z]{3}")||scale<0||scale>6)throw new IllegalArgumentException("WINLA_CONTRACT_INVALID");}}
    public record RawResult(String providerRequestRef,String merchantOrderRef,String providerRef,String stateRaw,String providerAmountRaw,String providerCurrencyRaw,String providerSku,String recipientRaw,String rawResponseDigest,Instant observedAt,String evidenceRef,String reasonCode){}
    public record RawCallback(String providerRequestRef,String callbackId,String callbackDigest,String merchantOrderRef,String providerRef,String stateRaw,String providerAmountRaw,String providerCurrencyRaw,String providerSku,String recipientRaw,String rawResponseDigest,Instant observedAt,String evidenceRef){}
    private record Normalized(Long minor,String currency,ProviderExchangeStore.NormalizationStatus status){boolean valid(){return status==ProviderExchangeStore.NormalizationStatus.VALIDATED;}static Normalized invalid(ProviderExchangeStore.NormalizationStatus s){return new Normalized(null,null,s);}}
    public interface Transport{RawResult submit(Command command);RawResult query(String providerRef,String merchantOrderRef);BalanceResult balance();RawCallback verifyCallback(CallbackEnvelope envelope);}
}
