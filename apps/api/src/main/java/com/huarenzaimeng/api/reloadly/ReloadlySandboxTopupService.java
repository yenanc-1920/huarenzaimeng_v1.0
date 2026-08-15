package com.huarenzaimeng.api.reloadly;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

final class ReloadlySandboxTopupService {
    private final ReloadlySandboxTopupPort provider;
    private final ReloadlySandboxTopupStore store;

    ReloadlySandboxTopupService(ReloadlySandboxTopupPort provider, ReloadlySandboxTopupStore store) {
        this.provider=provider;this.store=store;
    }

    View create(String requestRef) {
        validate(requestRef);
        if(store.begin(requestRef,fingerprint(requestRef))==ReloadlySandboxTopupStore.Begin.EXISTING)
            return view(store.require(requestRef),"REPLAYED",0);
        ReloadlySandboxTopupPort.CreateResult result=provider.create(requestRef);
        if(result instanceof ReloadlySandboxTopupPort.Created accepted){
            store.created(requestRef,accepted.providerTransactionId(),accepted.amount(),accepted.currency());
            return view(store.require(requestRef),"CREATED",1);
        }
        if(result instanceof ReloadlySandboxTopupPort.Rejected){store.rejected(requestRef);return view(store.require(requestRef),"REJECTED",1);}
        store.unknown(requestRef);return view(store.require(requestRef),"UNKNOWN_NO_RETRY",1);
    }

    View read(String requestRef){validate(requestRef);return view(store.require(requestRef),"READ_ONLY",0);}

    View queryStatus(String requestRef){
        validate(requestRef);ReloadlySandboxTopupStore.Record record=store.require(requestRef);
        if(record.providerTransactionId()==null)return view(record,"STATUS_NOT_AVAILABLE",0);
        ReloadlySandboxTopupPort.StatusResult result=provider.status(record.providerTransactionId());
        if(result instanceof ReloadlySandboxTopupPort.Observed observed)
            store.observed(requestRef,record.providerTransactionId(),observed.statusCode());
        return view(store.require(requestRef),result instanceof ReloadlySandboxTopupPort.Observed?"STATUS_OBSERVED":"STATUS_UNKNOWN_NO_RETRY",1);
    }

    private static View view(ReloadlySandboxTopupStore.Record record,String outcome,int externalCalls){
        return new View(record.requestRef(),record.commandState(),outcome,record.amount(),record.currency(),record.lastStatus(),false,externalCalls);
    }
    private static void validate(String ref){if(ref==null||!ref.matches("RLD-A-[A-Z0-9-]{8,40}"))throw new IllegalArgumentException("REQUEST_REF_INVALID");}
    private static String fingerprint(String ref){
        try{return HexFormat.of().withUpperCase().formatHex(MessageDigest.getInstance("SHA-256").digest(("RLD-TOPUP-A|"+ref).getBytes(StandardCharsets.US_ASCII)));}
        catch(Exception impossible){throw new IllegalStateException("SHA256_UNAVAILABLE");}
    }
    record View(String requestRef,String state,String outcome,java.math.BigDecimal amount,String currency,
                String providerStatus,boolean automaticRetry,int externalCallCountReported){}
}

