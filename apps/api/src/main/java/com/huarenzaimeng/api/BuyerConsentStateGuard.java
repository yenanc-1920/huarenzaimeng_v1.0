package com.huarenzaimeng.api;

import org.springframework.context.annotation.Profile;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@Profile("release-mysql")
public final class BuyerConsentStateGuard {
    private final JdbcTemplate jdbc;
    private final String userAgreementVersion;
    private final String privacyPolicyVersion;
    BuyerConsentStateGuard(JdbcTemplate jdbc,
                           @Value("${hz.buyer-consent.user-agreement-version:}") String userAgreementVersion,
                           @Value("${hz.buyer-consent.privacy-policy-version:}") String privacyPolicyVersion){
        this.jdbc=jdbc;
        this.userAgreementVersion=userAgreementVersion;
        this.privacyPolicyVersion=privacyPolicyVersion;
    }
    public void requireTransactionWrite(String subjectRef){
        if(userAgreementVersion.isBlank()||privacyPolicyVersion.isBlank())throw new FlowRejectedException("BUYER_CONSENT_STALE");
        var rows=jdbc.query("SELECT c.consent_state,c.user_agreement_version,c.privacy_policy_version FROM buyer_consent_state c JOIN buyer_identity i ON i.buyer_id=c.buyer_id WHERE i.subject_ref=? AND i.status_code='ACTIVE'",
                (rs,n)->new State(rs.getString(1),rs.getString(2),rs.getString(3)),subjectRef);
        if(rows.size()!=1||!"VALID".equals(rows.get(0).state()))throw new FlowRejectedException("BUYER_CONSENT_OR_ACCOUNT_STATE_REQUIRED");
        State state=rows.get(0);
        if(!userAgreementVersion.equals(state.userAgreementVersion())||!privacyPolicyVersion.equals(state.privacyPolicyVersion()))
            throw new FlowRejectedException("BUYER_CONSENT_STALE");
    }
    private record State(String state,String userAgreementVersion,String privacyPolicyVersion){}
}
